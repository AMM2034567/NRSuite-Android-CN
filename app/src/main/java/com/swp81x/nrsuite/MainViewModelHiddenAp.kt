package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.defense.HiddenApObservation
import com.swp81x.nrsuite.core.defense.HiddenSsidCandidate
import com.swp81x.nrsuite.core.history.HistoryLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

// Hidden AP Revealer: passive detection of zero-length SSID beacons/probe
// responses, probe-request SSID candidates, and client association mappings.

internal fun MainViewModel.startHiddenApImpl(fixed: Boolean, channel: Int) {
    val activeSession = session
    if (activeSession == null) {
        appendLog("启动隐藏 AP 探测前请先连接设备。")
        return
    }
    if (_hiddenApRunning.value || _hiddenApStarting.value) return
    if (!ensureRadioIdle("隐藏 AP 探测器")) return

    val safeChannel = channel.coerceIn(1, 14)
    _hiddenApFixed.value = fixed
    _hiddenApChannel.value = safeChannel
    hiddenApSavedChannel = safeChannel
    _hiddenApCurrentHopChannel.value = if (fixed) null else 1
    _hiddenApObservations.value = emptyList()
    _hiddenApCandidates.value = emptyList()
    _hiddenApEventCount.value = 0
    _hiddenApStarting.value = true
    updateForegroundService()

    scope.launch {
        try {
            // One-time baseline scan. This is best-effort: if it fails or the
            // user moves, passive detection still runs without filtering.
            appendLog("正在为隐藏 AP 探测器执行 WiFi 基准扫描...")
            val baselineCount = activeSession.scanWifi(timeoutMs = 15_000)
            if (baselineCount != null && baselineCount >= 0) {
                delay(150)
                val hiddenSeeds = _networks.value.count { network ->
                    network.optString("bssid").isNotBlank() &&
                        network.optString("ssid").isBlank()
                }
                seedHiddenApObservationsFromScan()
                appendLog("探测器基准扫描：发现 $baselineCount 个扫描结果，$hiddenSeeds 个隐藏网络种子。")
            } else {
                appendLog("探测器基准扫描失败；仅继续进行被动探测。")
            }

            val args = JSONObject().apply {
                put("mode", if (fixed) "fixed" else "hop")
                put("channel", safeChannel)
                put("interval_ms", 300)
            }
            val response = activeSession.sendCommand("START_HIDDEN_AP", args, timeoutMs = 10_000)
            if (response?.optBoolean("ok") == true) {
                _hiddenApRunning.value = true
                appendLog("隐藏 AP 探测器已启动（${if (fixed) "固定" else "跳频"}信道）。")
                addHistory("hidden_ap", "隐藏 AP 探测器已启动", HistoryLevel.SUCCESS)
            } else {
                _hiddenApCurrentHopChannel.value = null
                appendLog("启动隐藏 AP 探测失败：${response?.optString("msg") ?: "超时"}")
            }
        } catch (t: Throwable) {
            appendLog("隐藏 AP 探测器启动失败：${t.message ?: t.javaClass.simpleName}")
        } finally {
            _hiddenApStarting.value = false
            updateForegroundService()
        }
    }
}

internal fun MainViewModel.stopHiddenApImpl() {
    if (!_hiddenApRunning.value) return
    _hiddenApRunning.value = false
    _hiddenApCurrentHopChannel.value = null
    updateForegroundService()

    val activeSession = session
    scope.launch {
        val response = activeSession?.sendCommand("STOP_HIDDEN_AP", timeoutMs = 6_000)
        if (response?.optBoolean("ok") == true) {
            appendLog("隐藏 AP 探测器已停止。")
            addHistory("hidden_ap", "隐藏 AP 探测器已停止", HistoryLevel.INFO)
        } else {
            appendLog("隐藏 AP 探测器停止请求已发送，未收到确认。")
        }
    }
}

internal fun MainViewModel.seedHiddenApObservationsFromScan() {
    val now = timeHmNow()
    val seeded = _networks.value.mapNotNull { network ->
        val bssid = network.optString("bssid").uppercase().takeIf { it.isNotBlank() } ?: return@mapNotNull null
        if (network.optString("ssid").isNotBlank()) return@mapNotNull null
        HiddenApObservation(
            bssid = bssid,
            channel = network.optInt("channel", _hiddenApChannel.value),
            rssi = network.optInt("rssi", -127),
            resolvedSsid = null,
            resolutionSource = null,
            firstSeen = now,
            lastSeen = now,
            sightings = 1,
            vendor = ouiDatabaseRepository.lookup(bssid)?.vendor,
        )
    }
    if (seeded.isEmpty()) return

    _hiddenApObservations.update { current ->
        val existingByBssid = current.associateBy { it.bssid }
        val mergedSeeds = seeded.map { seed ->
            existingByBssid[seed.bssid]?.copy(
                channel = seed.channel,
                rssi = seed.rssi,
                lastSeen = now,
                sightings = maxOf(existingByBssid[seed.bssid]?.sightings ?: 0, 1),
                vendor = existingByBssid[seed.bssid]?.vendor ?: seed.vendor,
            ) ?: seed
        }
        (mergedSeeds + current.filterNot { observation -> seeded.any { it.bssid == observation.bssid } })
            .take(300)
    }
}

internal fun MainViewModel.recordHiddenApObservation(event: JSONObject) {
    val bssid = event.optString("bssid").uppercase().takeIf { it.isNotBlank() } ?: return
    val now = timeHmNow()
    val channel = event.optInt("channel", _hiddenApChannel.value)
    val rssi = event.optInt("rssi", -127)
    val vendor = ouiDatabaseRepository.lookup(bssid)?.vendor

    val existing = _hiddenApObservations.value.firstOrNull { it.bssid == bssid }
    val updated = existing?.copy(
        channel = channel,
        rssi = rssi,
        lastSeen = now,
        sightings = existing.sightings + 1,
        vendor = vendor ?: existing.vendor,
    ) ?: HiddenApObservation(
        bssid = bssid,
        channel = channel,
        rssi = rssi,
        resolvedSsid = null,
        resolutionSource = null,
        firstSeen = now,
        lastSeen = now,
        sightings = 1,
        vendor = vendor,
    )

    _hiddenApObservations.update { current ->
        (listOf(updated) + current.filterNot { it.bssid == bssid }).take(300)
    }
    _hiddenApEventCount.update { it + 1 }
}

internal fun MainViewModel.recordHiddenSsidCandidate(event: JSONObject) {
    val ssid = event.optString("ssid").trim().takeIf { it.isNotBlank() } ?: return
    val client = event.optString("client").uppercase().takeIf { it.isNotBlank() }
    val now = timeHmNow()
    val channel = event.optInt("channel", _hiddenApChannel.value)
    val rssi = event.optInt("rssi", -127)

    val existing = _hiddenApCandidates.value.firstOrNull {
        it.ssid.equals(ssid, ignoreCase = true)
    }
    val updated = existing?.copy(
        client = client ?: existing.client,
        channel = channel,
        rssi = rssi,
        lastSeen = now,
        sightings = existing.sightings + 1,
    ) ?: HiddenSsidCandidate(
        ssid = ssid,
        client = client,
        channel = channel,
        rssi = rssi,
        firstSeen = now,
        lastSeen = now,
        sightings = 1,
    )

    _hiddenApCandidates.update { current ->
        (listOf(updated) + current.filterNot { it.ssid.equals(ssid, ignoreCase = true) }).take(200)
    }
    _hiddenApEventCount.update { it + 1 }
}

internal fun MainViewModel.recordHiddenSsidResolved(event: JSONObject) {
    val bssid = event.optString("bssid").uppercase().takeIf { it.isNotBlank() } ?: return
    val ssid = event.optString("ssid").trim().takeIf { it.isNotBlank() } ?: return
    val now = timeHmNow()
    val channel = event.optInt("channel", _hiddenApChannel.value)
    val rssi = event.optInt("rssi", -127)
    val source = when (event.optInt("subtype", 0)) {
        0x02 -> "通过重关联请求解析"
        else -> "通过关联请求解析"
    }

    resolvedHiddenSsidByBssid[bssid] = ssid
    applyResolvedSsidToScanTargetsImpl(bssid, ssid)

    val existing = _hiddenApObservations.value.firstOrNull { it.bssid == bssid }
    val updated = existing?.copy(
        resolvedSsid = ssid,
        resolutionSource = source,
        channel = channel,
        rssi = rssi,
        lastSeen = now,
        sightings = existing.sightings + 1,
    ) ?: HiddenApObservation(
        bssid = bssid,
        channel = channel,
        rssi = rssi,
        resolvedSsid = ssid,
        resolutionSource = source,
        firstSeen = now,
        lastSeen = now,
        sightings = 1,
        vendor = ouiDatabaseRepository.lookup(bssid)?.vendor,
    )

    _hiddenApObservations.update { current ->
        (listOf(updated) + current.filterNot { it.bssid == bssid }).take(300)
    }
    _hiddenApEventCount.update { it + 1 }
}


internal fun MainViewModel.forceHiddenApReconnectImpl(observation: HiddenApObservation) {
    if (!_hiddenApRunning.value) {
        appendLog("强制重连前请先启动隐藏 AP 探测。")
        return
    }
    if (!_hiddenApDeauthEnabled.value) {
        appendLog("请先在隐藏 AP 设置中启用 Deauth 重连功能。")
        return
    }
    val activeSession = session ?: return

    scope.launch {
        val response = activeSession.sendCommand(
            "HIDDEN_AP_FORCE_RECONNECT",
            JSONObject().apply {
                put("bssid", observation.bssid)
                put("channel", observation.channel)
                put("client", "FF:FF:FF:FF:FF:FF")
                put("count", 5)
                put("interval_ms", 60)
                put("reason", 7)
            },
            timeoutMs = 8_000,
        )
        if (response?.optBoolean("ok") == true) {
            appendLog("已向 ${observation.bssid} 发送重连突发；正在等待隐藏 SSID 解析。")
            addHistory("hidden_ap", "已强制 ${observation.bssid} 重新连接", HistoryLevel.INFO)
        } else {
            appendLog("强制 ${observation.bssid} 重新连接失败。")
        }
    }
}


internal fun MainViewModel.applyResolvedSsidToScanTargetsImpl(bssid: String, ssid: String) {
    if (bssid.isBlank() || ssid.isBlank()) return

    _networks.update { current ->
        current.map { network ->
            if (network.optString("bssid").equals(bssid, ignoreCase = true)) {
                network.put("ssid", ssid)
                network.put("hidden_resolved", true)
            }
            network
        }
    }

    _deauthDetectorTargets.update { current ->
        current.map { target ->
            if (target.bssid.equals(bssid, ignoreCase = true)) target.copy(ssid = ssid) else target
        }
    }
    _deauthDetectorSelectedTarget.update { selected ->
        if (selected?.bssid.equals(bssid, ignoreCase = true)) selected?.copy(ssid = ssid) else selected
    }
}


internal fun MainViewModel.clearHiddenApCandidatesImpl() {
    _hiddenApCandidates.value = emptyList()
}

internal fun MainViewModel.clearHiddenApObservationsImpl() {
    _hiddenApObservations.value = emptyList()
}
