package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.ble.BleCharacteristicProfile
import com.swp81x.nrsuite.core.ble.BleDeviceObservation
import com.swp81x.nrsuite.core.ble.BleServiceProfile
import com.swp81x.nrsuite.core.history.HistoryLevel
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

// Read-only BLE GATT profiling for a selected discovered device.

internal fun MainViewModel.selectBleProfileTargetImpl(device: BleDeviceObservation?) {
    _bleProfileTarget.value = device
    _bleProfileStatus.value = if (device != null) {
        "已选择目标: ${device.name ?: device.address}"
    } else {
        _bleProfileServices.value = emptyList()
        "请选择已发现的 BLE 设备"
    }
}

internal fun MainViewModel.startBleProfileImpl() {
    val activeSession = session
    if (activeSession == null) {
        appendLog("启动 BLE 画像采集前请先连接设备。")
        return
    }
    val target = _bleProfileTarget.value
    if (target == null) {
        appendLog("画像采集前请先选择已发现的 BLE 设备。")
        return
    }
    if (_bleProfileRunning.value) return
    if (!ensureRadioIdle("BLE GATT 画像")) return

    _bleProfileServices.value = emptyList()
    _bleProfileStatus.value = "正在启动画像采集..."
    _bleProfileRunning.value = true
    updateForegroundService()

    scope.launch {
        val response = activeSession.sendCommand(
            "BLE_PROFILE_START",
            JSONObject().apply {
                put("address", target.address)
                put("address_type", target.addressType)
            },
            timeoutMs = 10_000,
        )
        if (response?.optBoolean("ok") == true) {
            _bleProfileStatus.value = "正在连接..."
            appendLog("已对 ${target.name ?: target.address} 启动 BLE 画像采集。")
            addHistory("ble_profile", "BLE 画像采集已启动: ${target.name ?: target.address}", HistoryLevel.INFO)
        } else {
            _bleProfileRunning.value = false
            _bleProfileStatus.value = response?.optString("msg") ?: "启动失败"
            updateForegroundService()
            appendLog("启动 BLE 画像采集失败: ${response?.optString("msg") ?: "超时"}")
        }
    }
}

internal fun MainViewModel.stopBleProfileImpl() {
    if (!_bleProfileRunning.value) return
    _bleProfileStatus.value = "正在停止..."
    updateForegroundService()

    val activeSession = session
    scope.launch {
        val response = runCatching {
            activeSession?.sendCommand("BLE_PROFILE_STOP", timeoutMs = 5_000)
        }.getOrNull()

        _bleProfileRunning.value = false
        _bleProfileStatus.value = if (response?.optBoolean("ok") == true) {
            "画像采集已停止"
        } else {
            "已发送停止请求，但未收到确认"
        }
        updateForegroundService()
        appendLog(
            if (response?.optBoolean("ok") == true) "BLE 画像采集已停止。"
            else "BLE 画像采集停止请求已发送，但未收到确认。"
        )
    }
}

internal fun MainViewModel.clearBleProfileImpl() {
    _bleProfileServices.value = emptyList()
    _bleProfileStatus.value = _bleProfileTarget.value?.let {
        "已选择目标: ${it.name ?: it.address}"
    } ?: "请选择已发现的 BLE 设备"
}

internal fun MainViewModel.recordBleProfileEvent(event: JSONObject) {
    val status = event.optString("status").ifBlank { "unknown" }
    _bleProfileStatus.value = when (status) {
        "connecting" -> "正在连接..."
        "connected" -> "已连接；正在枚举服务..."
        "done" -> "画像采集完成"
        "failed" -> event.optString("msg").ifBlank { "画像采集失败" }
        else -> status
    }
    if (status == "done" || status == "failed") {
        _bleProfileRunning.value = false
        updateForegroundService()
        addHistory(
            "ble_profile",
            if (status == "done") "BLE 画像采集完成" else "BLE 画像采集失败",
            if (status == "done") HistoryLevel.SUCCESS else HistoryLevel.ERROR,
        )
    }
}

internal fun MainViewModel.recordBleServiceEvent(event: JSONObject) {
    val uuid = event.optString("service_uuid").uppercase().takeIf { it.isNotBlank() } ?: return
    _bleProfileServices.update { current ->
        if (current.any { it.uuid == uuid }) current
        else current + BleServiceProfile(uuid = uuid)
    }
}

internal fun MainViewModel.recordBleCharacteristicEvent(event: JSONObject) {
    val serviceUuid = event.optString("service_uuid").uppercase().takeIf { it.isNotBlank() } ?: return
    val uuid = event.optString("uuid").uppercase().takeIf { it.isNotBlank() } ?: return
    val props = event.optJSONObject("properties")
    val characteristic = BleCharacteristicProfile(
        uuid = uuid,
        read = props?.optBoolean("read") == true,
        write = props?.optBoolean("write") == true,
        writeNoResponse = props?.optBoolean("write_no_response") == true,
        notify = props?.optBoolean("notify") == true,
        indicate = props?.optBoolean("indicate") == true,
        broadcast = props?.optBoolean("broadcast") == true,
    )

    _bleProfileServices.update { current ->
        val existing = current.firstOrNull { it.uuid == serviceUuid }
        val withService = existing?.copy(
            characteristics = existing.characteristics
                .filterNot { it.uuid == uuid } + characteristic,
        ) ?: BleServiceProfile(serviceUuid, listOf(characteristic))

        (current.filterNot { it.uuid == serviceUuid } + withService)
            .sortedBy { it.uuid }
    }
}
