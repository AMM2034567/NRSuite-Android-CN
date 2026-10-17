package com.swp81x.nrsuite

import android.net.Uri
import com.swp81x.nrsuite.core.history.HistoryLevel
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.session.NrSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.update
import org.json.JSONObject

// BLE HID advertising, DuckyScript payloads, keyboard, mouse, and status polling.

internal fun MainViewModel.setBlePayloadImpl(uri: Uri, name: String?) {
    _blePayloadUri.value = uri
    _blePayloadName.value = name ?: uri.lastPathSegment ?: "payload.txt"
    appendLog("已选择 BLE Payload：${_blePayloadName.value}")
}

internal fun MainViewModel.clearBlePayloadImpl() {
    _blePayloadUri.value = null
    _blePayloadName.value = null
    _bleSavedScriptText.value = null
    appendLog("BLE Payload 已清除。")
}

internal fun MainViewModel.startBleImpl(advertiseName: String) {
    val activeSession = session
    if (activeSession == null) {
        appendLog("启动 BLE HID 前请先连接设备。")
        return
    }
    if (_bleAdvertising.value) return
    val activeWifi = activeRadioLabel(includeBle = false)
    if (activeWifi != null) {
        appendLog(
            "无法在 $activeWifi 运行时启动 BLE HID。请先停止 $activeWifi。",
            level = LogLevel.ERROR,
        )
        return
    }

    scope.launch {
        val name = advertiseName.trim().ifBlank { "NRSuite Keyboard" }
        appendLog("正在以 '$name' 启动 BLE HID 广播...")
        val response = activeSession.sendCommand(
            "BLE_START",
            JSONObject().put("name", name),
            timeoutMs = 8_000,
        )
        if (response?.optBoolean("ok") == true) {
            _bleAdvertising.value = true
            _bleConnected.value = false
            _blePeer.value = ""
            clearBleRealtimeStateImpl()
            startBleStatusPollingImpl(activeSession)
            updateForegroundService()
            appendLog("BLE HID 广播已启动。")
            addHistory("ble", "BLE HID 已作为 '$name' 启动广播", HistoryLevel.SUCCESS)
        } else {
            appendLog("启动 BLE HID 失败：${response?.optString("msg") ?: "超时或不受支持"}")
        }
    }
}

internal fun MainViewModel.stopBleImpl() {
    if (!_bleAdvertising.value && !_bleConnected.value) return
    bleStatusJob?.cancel()
    bleStatusJob = null
    _bleAdvertising.value = false
    _bleConnected.value = false
    _blePeer.value = ""
    clearBleRealtimeStateImpl()
    updateForegroundService()
    val activeSession = session
    scope.launch {
        runCatching { activeSession?.sendCommand("BLE_RELEASE_ALL", timeoutMs = 3_000) }
            .onFailure { e -> appendLog("BLE 按键释放失败：${e.message}", level = LogLevel.ERROR) }
        runCatching { activeSession?.sendCommand("BLE_MOUSE_RELEASE", timeoutMs = 3_000) }
            .onFailure { e -> appendLog("BLE 鼠标释放失败：${e.message}", level = LogLevel.ERROR) }
        val response = activeSession?.sendCommand("BLE_STOP", timeoutMs = 5_000)
        appendLog(
            if (response?.optBoolean("ok") == true) "BLE HID 已停止。"
            else "已发送 BLE 停止请求。"
        )
    }
}

internal fun MainViewModel.runBlePayloadImpl() {
    val activeSession = session
    val payloadUri = _blePayloadUri.value
    val savedScript = _bleSavedScriptText.value
    if (activeSession == null) {
        appendLog("运行 BLE Payload 前请先连接设备。")
        return
    }
    if (!_bleConnected.value) {
        appendLog("BLE 主机尚未连接。")
        return
    }
    if (_bleScriptRunning.value) {
        appendLog("已有 BLE Payload 正在运行中。")
        return
    }
    if (payloadUri == null && savedScript == null) {
        appendLog("请先选择 DuckyScript Payload。")
        return
    }
    scope.launch {
        val script = savedScript
            ?: withContext(Dispatchers.IO) {
                runCatching {
                    payloadUri?.let {
                        app.contentResolver
                            .openInputStream(it)
                            ?.use { stream -> stream.readBytes().toString(Charsets.UTF_8) }
                    }
                }.getOrNull()
            }
        if (script.isNullOrBlank()) {
            appendLog("无法读取所选的 BLE Payload。")
            return@launch
        }
        runBleScriptImpl(activeSession, script)
    }
}


internal fun MainViewModel.sendBleRealtimeInputImpl(inserted: String, backspaces: Int) {
    if (!_bleConnected.value || session == null) return
    if (inserted.isEmpty() && backspaces <= 0) return

    val payload = buildString {
        repeat(backspaces.coerceAtLeast(0)) { append('\b') }
        if (inserted.isNotEmpty()) append(inserted)
    }
    if (payload.isEmpty()) return

    ensureBleTypeConsumerImpl()
    bleTypeChannel.trySend(payload)
}

internal fun MainViewModel.sendBleSpecialKeyImpl(key: String) {
    val activeSession = session ?: return
    if (!_bleConnected.value || key.isBlank()) return
    scope.launch {
        runCatching {
            activeSession.sendCommandNoWait(
                "BLE_KEY_TAP",
                JSONObject().put("key", key),
            )
        }.onFailure { e ->
            appendLog("BLE 指令执行失败：${e.message}", level = LogLevel.ERROR)
        }
        releaseMomentaryModifiersImpl(activeSession)
    }
}

internal fun MainViewModel.setBleModifierHoldImpl(enabled: Boolean) {
    _bleModifierHold.value = enabled
}

internal fun MainViewModel.sendBleMouseMoveImpl(dx: Int, dy: Int) {
    val activeSession = session ?: return
    if (!_bleConnected.value || (dx == 0 && dy == 0)) return
    scope.launch {
        runCatching {
            activeSession.sendCommandNoWait(
                "BLE_MOUSE_MOVE",
                JSONObject().put("dx", dx).put("dy", dy),
            )
        }.onFailure { e ->
            appendLog("BLE 指令执行失败：${e.message}", level = LogLevel.ERROR)
        }
    }
}

internal fun MainViewModel.sendBleMouseScrollImpl(wheel: Int) {
    val activeSession = session ?: return
    if (!_bleConnected.value || wheel == 0) return
    scope.launch {
        runCatching {
            activeSession.sendCommandNoWait(
                "BLE_MOUSE_SCROLL",
                JSONObject().put("wheel", wheel),
            )
        }.onFailure { e ->
            appendLog("BLE 指令执行失败：${e.message}", level = LogLevel.ERROR)
        }
    }
}

internal fun MainViewModel.sendBleMouseButtonImpl(button: String, down: Boolean) {
    val activeSession = session ?: return
    if (!_bleConnected.value || button.isBlank()) return
    scope.launch {
        runCatching {
            activeSession.sendCommandNoWait(
                "BLE_MOUSE_BUTTON",
                JSONObject().put("button", button).put("down", down),
            )
        }.onFailure { e ->
            appendLog("BLE 指令执行失败：${e.message}", level = LogLevel.ERROR)
        }
    }
}

internal fun MainViewModel.releaseBleMouseButtonsImpl() {
    val activeSession = session ?: return
    if (!_bleConnected.value) return
    scope.launch {
        runCatching { activeSession.sendCommandNoWait("BLE_MOUSE_RELEASE") }
            .onFailure { e ->
                appendLog("BLE 指令执行失败：${e.message}", level = LogLevel.ERROR)
            }
    }
}

internal suspend fun MainViewModel.releaseMomentaryModifiersImpl(activeSession: NrSession?) {
    if (_bleModifierHold.value) return
    val active = _bleModifiers.value
    if (active.isEmpty()) return
    _bleModifiers.value = emptySet()
    active.forEach { key ->
        runCatching {
            activeSession?.sendCommandNoWait(
                "BLE_KEY_UP",
                JSONObject().put("key", key),
            )
        }.onFailure { e ->
            appendLog("BLE 指令执行失败：${e.message}", level = LogLevel.ERROR)
        }
    }
}

internal fun MainViewModel.setBleModifierImpl(key: String, down: Boolean) {
    val activeSession = session ?: return
    if (!_bleConnected.value || key.isBlank()) return

    _bleModifiers.update { current ->
        if (down) current + key else current - key
    }
    val cmd = if (down) "BLE_KEY_DOWN" else "BLE_KEY_UP"
    scope.launch {
        runCatching {
            activeSession.sendCommandNoWait(cmd, JSONObject().put("key", key))
        }.onFailure { e ->
            appendLog("BLE 指令执行失败：${e.message}", level = LogLevel.ERROR)
        }
    }
}

private fun MainViewModel.ensureBleTypeConsumerImpl() {
    if (bleTypeConsumerJob?.isActive == true) return
    bleTypeConsumerJob = scope.launch {
        for (payload in bleTypeChannel) {
            val activeSession = session ?: continue
            if (!_bleConnected.value) continue
            runCatching {
                activeSession.sendCommandNoWait(
                    "BLE_TYPE_TEXT",
                    JSONObject().put("text", payload),
                )
            }.onFailure { e ->
                appendLog("BLE 指令执行失败：${e.message}", level = LogLevel.ERROR)
            }
            releaseMomentaryModifiersImpl(activeSession)
        }
    }
}

internal fun MainViewModel.clearBleRealtimeStateImpl() {
    _bleModifiers.value = emptySet()
    while (bleTypeChannel.tryReceive().isSuccess) {
        // Drain queued keystrokes that belong to the previous BLE session.
    }
    bleTypeConsumerJob?.cancel()
    bleTypeConsumerJob = null
}

internal suspend fun MainViewModel.runBleScriptImpl(activeSession: NrSession, script: String) {
    if (!_bleConnected.value) {
        appendLog("BLE 主机尚未连接。")
        return
    }
    if (_bleScriptRunning.value) {
        appendLog("已有 BLE Payload 正在运行中。")
        return
    }

    _bleScriptRunning.value = true
    try {
        val response = runCatching {
            activeSession.sendCommand(
                "BLE_RUN_SCRIPT",
                JSONObject().put("script", script),
                timeoutMs = maxOf(10_000, script.length / 20L),
            )
        }.getOrNull()
        if (response?.optBoolean("ok") == true) {
            appendLog("BLE 脚本执行完毕 (共 ${response.optInt("lines")} 行)。")
        } else {
            appendLog("BLE 脚本执行失败：${response?.optString("msg") ?: "超时或已断开连接"}")
        }
    } finally {
        _bleScriptRunning.value = false
    }
}

internal fun MainViewModel.startBleStatusPollingImpl(activeSession: NrSession) {
    bleStatusJob?.cancel()
    bleStatusJob = scope.launch {
        while (isActive && (_bleAdvertising.value || _bleConnected.value)) {
            delay(2_000)
            val response = runCatching {
                activeSession.sendCommand("BLE_STATUS", timeoutMs = 4_000)
            }.getOrNull() ?: continue
            if (response.optBoolean("ok")) {
                _bleAdvertising.value = response.optBoolean("advertising", _bleAdvertising.value)
                _bleConnected.value = response.optBoolean("connected", _bleConnected.value)
                _blePeer.value = response.optString("peer", "")
            }
        }
    }
}
