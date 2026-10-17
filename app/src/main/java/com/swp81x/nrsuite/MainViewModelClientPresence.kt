package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.defense.ClientObservation
import com.swp81x.nrsuite.core.defense.ClientPresenceMode
import com.swp81x.nrsuite.core.history.HistoryLevel
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

// Client / Presence detector: passive management-frame monitoring plus an
// optional active deauth-trigger mode to force clients to re-announce.

internal fun MainViewModel.startClientPresenceImpl(
    mode: ClientPresenceMode,
    fixed: Boolean,
    channel: Int,
    targetBssid: String,
    intervalMs: Int,
) {
    val activeSession = session
    if (activeSession == null) {
        appendLog("启动客户端探测前请先连接设备。")
        return
    }
    if (_clientPresenceRunning.value) return
    if (!ensureRadioIdle("客户端探测器")) return

    val cleanTarget = targetBssid.trim().uppercase()
    if (mode == ClientPresenceMode.ACTIVE) {
        if (cleanTarget.isBlank() || !MainViewModel.MAC_PATTERN.matches(cleanTarget)) {
            _actionError.value = "启动主动客户端探测前请先选择有效的目标 AP。"
            return
        }
    }

    val safeChannel = channel.coerceIn(1, 14)
    _clientPresenceMode.value = mode
    _clientPresenceFixed.value = fixed
    _clientPresenceChannel.value = safeChannel
    _clientPresenceTargetBssid.value = cleanTarget
    clientPresenceSavedChannel = safeChannel
    clientPresenceSavedTarget = cleanTarget
    _clientPresenceClients.value = emptyList()
    _clientPresenceFrameCount.value = 0L

    scope.launch {
        if (mode == ClientPresenceMode.ACTIVE) {
            appendLog("正在向 $cleanTarget 发送重连突发数据包以进行客户端探测...")
            val deauth = activeSession.sendCommand(
                "DEAUTH",
                JSONObject().apply {
                    put("bssid", cleanTarget)
                    put("client", "FF:FF:FF:FF:FF:FF")
                    put("channel", safeChannel)
                    put("count", 10)
                    put("deauth_interval_ms", 100)
                    put("reason", 7)
                },
                timeoutMs = 20_000,
            )
            _clientPresenceLastTriggerAt.value = timeHmNow()
            if (deauth?.optBoolean("ok") != true) {
                appendLog("重连突发失败或超时；继续以被动模式探测。")
            }
        }

        val args = JSONObject().apply {
            put("mode", if (fixed) "fixed" else "hop")
            put("channel", safeChannel)
            put("interval_ms", intervalMs.coerceIn(50, 2_000))
        }
        val response = activeSession.sendCommand("START_CLIENT_DETECT", args, timeoutMs = 10_000)
        if (response?.optBoolean("ok") == true) {
            _clientPresenceRunning.value = true
            updateForegroundService()
            val modeDesc = if (mode == ClientPresenceMode.ACTIVE) "主动模式" else "被动模式"
            appendLog("客户端探测已启动 ($modeDesc)。")
            addHistory(
                "client_presence",
                "客户端探测已启动 ($modeDesc)",
                HistoryLevel.SUCCESS,
            )
        } else {
            appendLog("启动客户端探测失败: ${response?.optString("msg") ?: "超时"}")
        }
    }
}

internal fun MainViewModel.stopClientPresenceImpl() {
    if (!_clientPresenceRunning.value) return
    _clientPresenceRunning.value = false
    updateForegroundService()

    val activeSession = session
    scope.launch {
        val response = activeSession?.sendCommand("STOP_CLIENT_DETECT", timeoutMs = 6_000)
        if (response?.optBoolean("ok") == true) {
            appendLog("客户端探测已停止。")
            addHistory("client_presence", "客户端探测已停止", HistoryLevel.INFO)
        } else {
            appendLog("客户端探测停止请求已发送，但未收到确认。")
        }
    }
}

internal fun MainViewModel.triggerClientReconnectBurstImpl() {
    if (!_clientPresenceRunning.value ||
        _clientPresenceMode.value != ClientPresenceMode.ACTIVE) return

    val target = _clientPresenceTargetBssid.value
    if (target.isBlank() || !MainViewModel.MAC_PATTERN.matches(target)) return

    val activeSession = session ?: return
    scope.launch {
        appendLog("正在强制向 $target 发送重连突发数据包...")
        runCatching { activeSession.sendCommand("STOP_CLIENT_DETECT", timeoutMs = 4_000) }
        val deauth = activeSession.sendCommand(
            "DEAUTH",
            JSONObject().apply {
                put("bssid", target)
                put("client", "FF:FF:FF:FF:FF:FF")
                put("channel", _clientPresenceChannel.value)
                put("count", 10)
                put("deauth_interval_ms", 100)
                put("reason", 7)
            },
            timeoutMs = 20_000,
        )
        _clientPresenceLastTriggerAt.value = timeHmNow()
        if (deauth?.optBoolean("ok") != true) {
            appendLog("重连突发失败；正在重新启动被动探测。")
        }

        val args = JSONObject().apply {
            put("mode", if (_clientPresenceFixed.value) "fixed" else "hop")
            put("channel", _clientPresenceChannel.value)
            put("interval_ms", 300)
        }
        val response = activeSession.sendCommand("START_CLIENT_DETECT", args, timeoutMs = 10_000)
        if (response?.optBoolean("ok") != true) {
            _clientPresenceRunning.value = false
            updateForegroundService()
            appendLog("重连突发后重启客户端探测失败。")
        }
    }
}

internal fun MainViewModel.recordClientPresenceEvent(event: JSONObject) {
    val mac = event.optString("client").uppercase()
    if (mac.isBlank()) return

    val now = timeHmNow()
    val rssi = event.optInt("rssi", -127)
    val channel = event.optInt("channel", _clientPresenceChannel.value)
    val subtype = event.optString("subtype", "client")
    val bssid = event.optString("bssid").ifBlank { null }
    val ssid = event.optString("ssid").ifBlank { null }
    val vendor = ouiDatabaseRepository.lookup(mac)?.vendor

    val existing = _clientPresenceClients.value.firstOrNull { it.mac == mac }
    val updated = if (existing == null) {
        appendLog("检测到客户端: $mac ($subtype, $rssi dBm)。")
        ClientObservation(
            mac = mac,
            bssid = bssid,
            ssid = ssid,
            subtype = subtype,
            channel = channel,
            rssi = rssi,
            firstSeen = now,
            lastSeen = now,
            sightings = 1,
            vendor = vendor,
        )
    } else {
        existing.copy(
            bssid = bssid ?: existing.bssid,
            ssid = ssid ?: existing.ssid,
            subtype = subtype,
            channel = channel,
            rssi = rssi,
            lastSeen = now,
            sightings = existing.sightings + 1,
            vendor = vendor ?: existing.vendor,
        )
    }

    _clientPresenceClients.update { current ->
        (listOf(updated) + current.filterNot { it.mac == mac }).take(300)
    }
    _clientPresenceFrameCount.update { it + 1 }
}
