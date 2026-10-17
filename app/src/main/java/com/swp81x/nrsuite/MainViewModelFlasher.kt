package com.swp81x.nrsuite

import android.hardware.usb.UsbDevice
import android.net.Uri
import android.provider.OpenableColumns
import com.swp81x.nrsuite.core.flasher.Esp32Flasher
import com.swp81x.nrsuite.core.flasher.UsbSerialFlasherTransport
import com.swp81x.nrsuite.core.history.HistoryLevel
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.usb.UsbSerialDeviceCatalog
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Firmware image selection, flash-target selection, and ROM-bootloader flashing.

internal fun MainViewModel.markFirmwarePermissionRequestedImpl(device: UsbDevice) {
    firmwarePermissionDeviceId = device.deviceId
    markPermissionRequested(device)
}

internal fun MainViewModel.setFirmwareFlashFileImpl(uri: Uri, name: String?) {
    _firmwareFlashUri.value = uri
    val resolvedName = queryDocumentDisplayNameImpl(uri)
        ?.takeIf { it.isNotBlank() }
        ?: name?.takeIf { it.isNotBlank() && !it.startsWith("msf:") && !it.startsWith("primary:") }
        ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?: "firmware.bin"
    _firmwareFlashName.value = resolvedName
    _firmwareFlashSize.value = queryDocumentSizeImpl(uri)
    appendLog("已选择固件镜像：${_firmwareFlashName.value}")
}

internal fun MainViewModel.queryDocumentDisplayNameImpl(uri: Uri): String? {
    return runCatching {
        app.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
            } else {
                null
            }
        }
    }.getOrNull()
}

internal fun MainViewModel.queryDocumentSizeImpl(uri: Uri): Long {
    return runCatching {
        app.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else 0L
            } else {
                0L
            }
        } ?: 0L
    }.getOrDefault(0L)
}

internal fun MainViewModel.clearFirmwareFlashFileImpl() {
    _firmwareFlashUri.value = null
    _firmwareFlashName.value = null
    _firmwareFlashSize.value = 0
    _firmwareFlashProgress.value = 0
    _firmwareFlashStatus.value = null
}

internal fun MainViewModel.selectFirmwareTargetImpl(device: UsbDevice) {
    if (!usbManager.hasPermission(device)) {
        _firmwareTargetDevice.value = null
        appendLog("选择烧录目标前需要先获取 USB 权限。")
        return
    }

    val entry = _devices.value.firstOrNull { it.device.deviceId == device.deviceId }
        ?: UsbSerialDeviceCatalog.find(usbManager, device)
    if (entry == null) {
        appendLog("没有适用于 ${device.deviceName} 的支持 USB 串口驱动。")
        return
    }
    _firmwareTargetDevice.value = entry
    appendLog("固件烧录目标：${entry.displayName}")
}

internal fun MainViewModel.startFirmwareFlashImpl(
    targetChip: String,
    skipReset: Boolean,
    eraseBeforeFlash: Boolean = false,
) {
    val uri = _firmwareFlashUri.value
    if (uri == null) {
        appendLog("烧录前请先选择合并后的固件 .bin 文件。")
        return
    }
    if (_firmwareFlashing.value) return

    val entry = _firmwareTargetDevice.value
    if (entry == null) {
        appendLog("烧录前请先选择目标设备。")
        return
    }
    if (!usbManager.hasPermission(entry.device)) {
        appendLog("所选烧录目标需要 USB 权限。")
        return
    }

    _firmwareFlashing.value = true
    _firmwareFlashProgress.value = 0
    _firmwareFlashStatus.value = "正在读取固件镜像..."

    scope.launch {
        try {
            val bytes = withContext(Dispatchers.IO) {
                app.contentResolver
                    .openInputStream(uri)
                    ?.use { it.readBytes() }
            }
            if (bytes == null || bytes.isEmpty()) {
                throw IOException("无法读取所选固件镜像。")
            }

            _firmwareFlashStatus.value = "正在停止活跃模块..."
            val targetFingerprint = deviceFingerprintImpl(entry.device)
            if (targetFingerprint == activeDeviceFingerprint) {
                stopActiveOperations()
            }
            // Tear down only the selected target's session. Other devices,
            // including sessions used for Wireless/BLE/etc., stay connected.
            disconnectSessionAndAwait(targetFingerprint, stopOperations = false)

            val resetMode = when {
                skipReset -> Esp32Flasher.ResetMode.NONE
                entry.device.vendorId == 0x303A && entry.device.productId == 0x1001 ->
                    Esp32Flasher.ResetMode.USB_JTAG
                else -> Esp32Flasher.ResetMode.CLASSIC
            }

            val flasher = Esp32Flasher(
                transport = UsbSerialFlasherTransport(usbManager, entry.driver),
                supportsEncryptedFlash = targetChip in setOf("ESP32-S2", "ESP32-S3", "ESP32-C3"),
            )

            _firmwareFlashStatus.value = if (skipReset) {
                "正在连接 ROM Bootloader..."
            } else {
                "正在复位进入 ROM Bootloader..."
            }

            withContext(Dispatchers.IO) {
                flasher.flash(
                    firmware = bytes,
                    offset = 0,
                    resetMode = resetMode,
                    eraseBeforeFlash = eraseBeforeFlash,
                    onStage = { stage -> _firmwareFlashStatus.value = stage },
                ) { percent ->
                    val written = (bytes.size.toLong() * percent / 100L).toInt()
                    _firmwareFlashProgress.value = percent
                    _firmwareFlashStatus.value = if (percent >= 100) {
                        "固件写入完毕 — 正在重启设备..."
                    } else {
                        "正在写入... $percent%  (${written / 1024} / ${bytes.size / 1024} KB)"
                    }
                }
            }

            _firmwareFlashProgress.value = 100
            _firmwareFlashStatus.value = "烧录完成。设备正在重启；待其重新识别后请重新连接。"
            appendLog("固件烧录完成（0x0 处共 ${bytes.size} 字节）。", level = LogLevel.SUCCESS)
            addHistory("firmware", "已在 0x0 处烧录 ${bytes.size} 字节", HistoryLevel.SUCCESS)
            delay(2_000)
            refreshDevices()
        } catch (t: Throwable) {
            _firmwareFlashProgress.value = 0
            _firmwareFlashStatus.value = "烧录失败：${t.message ?: t.javaClass.simpleName}"
            appendLog(
                "固件烧录失败：${t.message ?: t.javaClass.simpleName}",
                level = LogLevel.ERROR,
            )
            addHistory("firmware", "固件烧录失败", HistoryLevel.ERROR)
        } finally {
            _firmwareFlashing.value = false
            updateForegroundService()
        }
    }
}
