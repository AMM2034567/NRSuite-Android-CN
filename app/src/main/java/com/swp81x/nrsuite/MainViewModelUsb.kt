package com.swp81x.nrsuite

import android.hardware.usb.UsbDevice
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.session.ConnectionState
import com.swp81x.nrsuite.core.session.NrSession
import com.swp81x.nrsuite.core.usb.UsbSerialDeviceCatalog
import com.swp81x.nrsuite.core.usb.UsbSerialTransport
import com.swp81x.nrsuite.core.usb.buildDeviceFingerprintKey
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

// USB discovery, permission handling, connection lifecycle, and per-device sessions.

internal fun MainViewModel.refreshDevicesImpl() {
    val found = UsbSerialDeviceCatalog.list(usbManager)
    _devices.value = found

    val busyDeviceIds = (deviceSessions + idleDeviceSessions).values
        .map { it.device.device.deviceId }
        .toSet()
    _unacknowledgedDevices.value = found
        .filter { !usbManager.hasPermission(it.device) && it.device.deviceId !in busyDeviceIds }
        .map { it.device.deviceId }
        .toSet()

    appendLog("发现 ${found.size} 个受支持的 USB 串口设备。")

    // Reconcile active sessions against Android's authoritative attached-device
    // list. The serial prober can transiently miss an already-open device; only
    // close a session when the USB device itself is no longer attached.
    val attachedDeviceIds = usbManager.deviceList.values
        .map { it.deviceId }
        .toSet()
    (deviceSessions + idleDeviceSessions).entries
        .filter { (_, entry) ->
            entry.openedOnce && entry.device.device.deviceId !in attachedDeviceIds
        }
        .map { it.key }
        .toList()
        .forEach { staleFingerprint ->
            appendLog("正在关闭已断开连接 USB 设备的陈旧会话。", level = LogLevel.USB)
            disconnectSession(staleFingerprint, stopOperations = staleFingerprint == activeDeviceFingerprint)
        }

    // If the selected flash target vanished or lost permission, clear it so
    // the flasher asks for permission instead of showing a stale "Selected".
    val selectedTarget = _firmwareTargetDevice.value
    if (selectedTarget != null) {
        val stillPresent = found.firstOrNull {
            it.device.deviceId == selectedTarget.device.deviceId
        }
        if (stillPresent == null || !usbManager.hasPermission(stillPresent.device)) {
            _firmwareTargetDevice.value = null
        } else {
            _firmwareTargetDevice.value = stillPresent
        }
    }

    // BadUSB target selection is independent of the primary feature session.
    // Keep it if the USB device is still attached; update it to the freshest
    // catalog entry so its driver/UsbDevice references do not go stale.
    val badUsbTarget = _badUsbTargetDevice.value
    if (badUsbTarget != null) {
        val stillAttached = usbManager.deviceList.values.any {
            it.deviceId == badUsbTarget.device.deviceId
        }
        if (!stillAttached) {
            val targetFingerprint = deviceFingerprintImpl(badUsbTarget.device)
            _badUsbTargetDevice.value = null
            _badUsbProgress.value = 0
            _badUsbArmedFingerprints.update { it - targetFingerprint }
            appendLog("BadUSB 目标已断开连接；已清除选择。", tag = "USB")
        } else {
            _badUsbTargetDevice.value = found.firstOrNull {
                it.device.deviceId == badUsbTarget.device.deviceId
            } ?: badUsbTarget
        }
    }
}

internal fun MainViewModel.onUsbDeviceAttachedImpl() {
    if (deviceSessions.isEmpty() && _connectionState.value is ConnectionState.Failed) {
        _connectionState.value = ConnectionState.Disconnected
    }
    refreshDevices()
    appendLog("USB 设备已插入。", level = LogLevel.USB)
    appendLog("准备就绪后请点击“连接”以建立会话。", level = LogLevel.USB)
}

internal fun MainViewModel.deviceFingerprintImpl(device: UsbDevice): String {
    val serial = runCatching { device.serialNumber }.getOrNull().orEmpty()
    return buildDeviceFingerprintKey(
        serial = serial,
        vendorId = device.vendorId,
        productId = device.productId,
        manufacturer = device.manufacturerName,
        productName = device.productName,
        deviceName = device.deviceName,
        deviceId = device.deviceId,
    )
}

internal fun MainViewModel.onUsbDeviceDetachedImpl(device: UsbDevice) {
    clearPermissionRequestImpl(device.deviceId)
    runCatching { deviceFingerprintImpl(device) }.getOrNull()?.let { fingerprint ->
        _badUsbArmedFingerprints.update { it - fingerprint }
    }
    refreshDevices()
    appendLog("USB 设备已拔出：${device.deviceName}", level = LogLevel.USB)

    val detachedFingerprint = runCatching { deviceFingerprintImpl(device) }.getOrNull()
    val allSessions = deviceSessions + idleDeviceSessions
    val matchedFingerprint = detachedFingerprint
        ?.takeIf { allSessions.containsKey(it) }
        ?: allSessions.entries.firstOrNull { (_, session) ->
            session.device.device.deviceId == device.deviceId ||
                session.device.device.deviceName == device.deviceName
        }?.key

    if (matchedFingerprint == null) {
        appendLog("拔出的设备未开启任何会话。", level = LogLevel.USB)
        return
    }

    if (_firmwareTargetDevice.value?.device?.deviceId == device.deviceId) {
        _firmwareTargetDevice.value = null
    }
    if (_badUsbTargetDevice.value?.device?.deviceId == device.deviceId) {
        _badUsbTargetDevice.value = null
        _badUsbProgress.value = 0
        appendLog("BadUSB 目标已拔出；已清除选择。", tag = "USB")
    }

    // Only the detached device's session is torn down. Every other session,
    // its connection state, and its active operation remain untouched.
    disconnectSession(matchedFingerprint, stopOperations = true)
}

internal fun MainViewModel.hasPermissionImpl(device: UsbDevice): Boolean =
    usbManager.hasPermission(device)

internal fun MainViewModel.onPermissionResultImpl(device: UsbDevice, granted: Boolean) {
    clearPermissionRequestImpl(device.deviceId)
    val reallyGranted = granted || usbManager.hasPermission(device)
    appendLog(
        if (reallyGranted) "已授予 ${device.deviceName} 的 USB 权限。"
        else "已拒绝 ${device.deviceName} 的 USB 权限。",
        level = LogLevel.USB,
    )

    if (serialMonitorPermissionDeviceId == device.deviceId) {
        serialMonitorPermissionDeviceId = null
        if (reallyGranted) {
            refreshDevicesImpl()
            val entry = _devices.value.firstOrNull { it.device.deviceId == device.deviceId }
                ?: UsbSerialDeviceCatalog.find(usbManager, device)
            if (entry != null) {
                startSerialMonitorImpl(entry, _serialMonitorBaud.value)
            } else {
                appendLog("未找到适用于串口监视器的支持的 USB 串口驱动。", level = LogLevel.ERROR)
            }
        }
        return
    }

    if (firmwarePermissionDeviceId == device.deviceId) {
        firmwarePermissionDeviceId = null
        if (reallyGranted) {
            refreshDevicesImpl()
            selectFirmwareTargetImpl(device)
        }
        return
    }

    if (reallyGranted) {
        refreshDevices()
        connect(device)
    }
}

internal fun MainViewModel.connectImpl(device: UsbDevice) {
    if (!usbManager.hasPermission(device)) {
        appendLog("连接前需要获取 USB 权限。")
        return
    }

    refreshDevicesImpl()
    if (usbManager.deviceList.values.none { it.deviceId == device.deviceId }) {
        appendLog("USB 设备 ${device.deviceName} 已断开连接。")
        return
    }

    val entry = _devices.value.firstOrNull { it.device.deviceId == device.deviceId }
        ?: UsbSerialDeviceCatalog.find(usbManager, device)
    if (entry == null) {
        appendLog("未找到适用于 ${device.deviceName} 的支持的 USB 串口驱动。")
        return
    }

    val fingerprint = deviceFingerprintImpl(entry.device)
    val previousPrimary = activeDeviceFingerprint

    if (fingerprint in _disconnectingFingerprints.value) {
        appendLog("${entry.displayName} 正在断开连接；请稍后重试。", tag = "USB")
        return
    }

    // Adopt an idle-but-still-open session instead of closing/reopening the
    // USB port. This is the workaround for TinyUSB S2 devices that stop
    // answering after a host-side close/reopen cycle.
    val idle = idleDeviceSessions.remove(fingerprint)
    if (idle != null && idle.session.state.value is ConnectionState.Connected) {
        val reactivated = DeviceSession(
            fingerprint = fingerprint,
            device = entry,
            session = idle.session,
            opening = true,
        )
        deviceSessions[fingerprint] = reactivated
        _deviceConnectionStates.update { it + (fingerprint to ConnectionState.Connecting) }

        val shouldBecomePrimary = previousPrimary == null || previousPrimary == fingerprint
        if (shouldBecomePrimary) {
            _primaryFingerprint.value = fingerprint
            _connectionState.value = ConnectionState.Connecting
            if (_firmwareTargetDevice.value == null) {
                _firmwareTargetDevice.value = entry
            }
        }

        observe(fingerprint, idle.session)
        // observe() seeds from the session's existing Connected state; keep the
        // UI in Connecting until the PING validation completes.
        _deviceConnectionStates.update { it + (fingerprint to ConnectionState.Connecting) }
        if (fingerprint == activeDeviceFingerprint) {
            _connectionState.value = ConnectionState.Connecting
        }

        scope.launch {
            appendLog("正在重新激活 ${entry.displayName} [$fingerprint]...", tag = "USB")
            idle.session.prepareForReuse()
            val pong = idle.session.sendCommand(
                "PING",
                JSONObject().put("pad", 0),
                timeoutMs = 2_000,
            )
            if (pong?.optBoolean("ok") == true) {
                val connected = idle.session.state.value
                reactivated.opening = false
                reactivated.openedOnce = true
                _deviceConnectionStates.update { it + (fingerprint to connected) }
                if (fingerprint == activeDeviceFingerprint) {
                    _connectionState.value = connected
                }
                appendLog("已连接到 ${(connected as? ConnectionState.Connected)?.chip ?: "NRSuite 设备"}（复用空闲会话）。", tag = "USB")
            } else {
                appendLog("空闲会话未响应 PING；正在重新打开。", tag = "USB")
                deviceSessions.remove(fingerprint)
                reactivated.observers.forEach { it.cancel() }
                _deviceConnectionStates.update { it - fingerprint }
                if (fingerprint == activeDeviceFingerprint) {
                    val next = deviceSessions.keys.firstOrNull()
                    _primaryFingerprint.value = next
                    _connectionState.value = next
                        ?.let { _deviceConnectionStates.value[it] }
                        ?: ConnectionState.Disconnected
                }
                usbOperationMutex.withLock {
                    runCatching { idle.session.disconnect() }
                }
            }
        }
        return
    }
    if (idle != null) {
        // Stale idle transport; close it before opening a fresh session.
        scope.launch {
            usbOperationMutex.withLock {
                runCatching { idle.session.disconnect() }
            }
        }
    }

    val anotherDeviceOpening = deviceSessions.values.any {
        it.opening && it.fingerprint != fingerprint
    }
    if (anotherDeviceOpening) {
        appendLog("另一个 USB 设备正在连接中；请稍后重试。", tag = "USB")
        return
    }

    val replacing = deviceSessions[fingerprint]
    val replacingState = replacing?.session?.state?.value
    if (replacing?.opening == true ||
        replacingState is ConnectionState.Connecting ||
        replacingState is ConnectionState.Connected
    ) {
        appendLog("${entry.displayName} 已连接或正在连接中。")
        return
    }

    // Tear down only this fingerprint's prior session. The actual USB close is
    // serialized below and completes before the replacement session opens.
    if (replacing != null) {
        val replacingPrimary = previousPrimary == fingerprint
        if (replacingPrimary) {
            stopActiveOperations()
        }
        deviceSessions.remove(fingerprint)
        replacing.observers.forEach { it.cancel() }
        _deviceConnectionStates.update { it - fingerprint }
        if (replacingPrimary) {
            val next = deviceSessions.keys.firstOrNull()
            _primaryFingerprint.value = next
            _connectionState.value = next
                ?.let { _deviceConnectionStates.value[it] }
                ?: ConnectionState.Disconnected
        }
    }

    val newSession = NrSession(
        transport = UsbSerialTransport(usbManager, entry.driver),
        scope = scope,
    )
    deviceSessions[fingerprint] = DeviceSession(
        fingerprint = fingerprint,
        device = entry,
        session = newSession,
        opening = true,
    )
    _deviceConnectionStates.update { it + (fingerprint to ConnectionState.Connecting) }

    // Preserve the existing primary unless this is the first session or the
    // same device is being replaced/reconnected.
    val shouldBecomePrimary = previousPrimary == null || previousPrimary == fingerprint
    if (shouldBecomePrimary) {
        _primaryFingerprint.value = fingerprint
        _connectionState.value = ConnectionState.Connecting
        if (_firmwareTargetDevice.value == null) {
            _firmwareTargetDevice.value = entry
        }
    }

    observe(fingerprint, newSession)
    scope.launch {
        appendLog("正在打开 ${entry.displayName} [$fingerprint]...", tag = "USB")
        usbOperationMutex.withLock {
            // Close any prior session for this same fingerprint before opening.
            replacing?.let { old ->
                if (old.session.state.value is ConnectionState.Connected) {
                    runCatching { old.session.sendCommand("STOP_ALL", timeoutMs = 1_500) }
                }
                runCatching { old.session.disconnect() }
                kotlinx.coroutines.delay(250)
            }
            newSession.connect()
        }
    }
}

internal fun MainViewModel.markPermissionRequestedImpl(device: UsbDevice) {
    _pendingPermissionRequests.update { it + device.deviceId }
}

internal fun MainViewModel.clearPermissionRequestImpl(deviceId: Int) {
    _pendingPermissionRequests.update { it - deviceId }
}

internal fun MainViewModel.disconnectDeviceImpl(device: UsbDevice) {
    val exactFingerprint = runCatching { deviceFingerprintImpl(device) }.getOrNull()
    val fingerprint = exactFingerprint
        ?.takeIf { deviceSessions.containsKey(it) }
        ?: deviceSessions.entries.firstOrNull { (_, session) ->
            session.device.device.deviceId == device.deviceId ||
                session.device.device.deviceName == device.deviceName
        }?.key
        ?: return
    idleSession(
        fingerprint = fingerprint,
        stopOperations = fingerprint == activeDeviceFingerprint,
    )
}
