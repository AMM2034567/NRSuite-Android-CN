package com.swp81x.nrsuite

import android.hardware.usb.UsbDevice
import android.provider.DocumentsContract
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.usb.UsbSerialDevice
import com.swp81x.nrsuite.core.usb.UsbSerialTransport
import java.io.IOException
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Raw USB serial monitor. This is intentionally independent of NrSession /
// BridgeProtocol so it can be used with any ESP32 or USB-UART firmware.

data class SerialLogLine(
    val timestamp: String,
    val text: String,
    val level: LogLevel,
)

private val SERIAL_LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

internal fun MainViewModel.markSerialMonitorPermissionRequestedImpl(device: UsbDevice) {
    serialMonitorPermissionDeviceId = device.deviceId
    markPermissionRequested(device)
}

internal fun MainViewModel.selectSerialMonitorDeviceImpl(device: UsbSerialDevice?) {
    if (_serialMonitorConnected.value || _serialMonitorConnecting.value) return
    _serialMonitorSelected.value = device
    _serialMonitorBlockingMessage.value = null
    _serialMonitorError.value = null
}

internal fun MainViewModel.setSerialMonitorBaudImpl(baudRate: Int) {
    if (_serialMonitorConnected.value || _serialMonitorConnecting.value) return
    _serialMonitorBaud.value = baudRate
}

internal fun MainViewModel.setSerialMonitorInputImpl(value: String) {
    _serialMonitorInput.value = value
}

internal fun MainViewModel.setSerialMonitorLineEndingImpl(value: String) {
    if (value in setOf("None", "LF", "CR", "CRLF")) {
        _serialMonitorLineEnding.value = value
    }
}

internal fun MainViewModel.setSerialMonitorHexModeImpl(enabled: Boolean) {
    _serialMonitorHexMode.value = enabled
}

internal fun MainViewModel.clearSerialMonitorLogImpl() {
    _serialMonitorLogLines.value = emptyList()
}

internal fun MainViewModel.startSerialMonitorImpl(device: UsbSerialDevice, baudRate: Int) {
    if (_serialMonitorConnected.value || _serialMonitorConnecting.value) return

    if (!usbManager.hasPermission(device.device)) {
        _serialMonitorError.value = "需要 USB 权限。"
        appendLog("串口监视器：需要 ${device.displayName} 的 USB 权限。", level = LogLevel.ERROR)
        return
    }

    val fingerprint = deviceFingerprintImpl(device.device)
    if (deviceSessions.containsKey(fingerprint) || idleDeviceSessions.containsKey(fingerprint)) {
        val message = "打开串口监视器前，请先断开当前活跃的 NRSuite 会话。"
        _serialMonitorBlockingMessage.value = message
        _serialMonitorError.value = null
        appendLog(message, level = LogLevel.ERROR)
        return
    }

    _serialMonitorSelected.value = device
    _serialMonitorBaud.value = baudRate
    _serialMonitorBlockingMessage.value = null
    _serialMonitorError.value = null
    _serialMonitorConnecting.value = true

    val transport = UsbSerialTransport(usbManager, device.driver, baudRate)
    serialMonitorTransport = transport

    serialMonitorReadJob = scope.launch(Dispatchers.IO) {
        try {
            transport.open()
            withContext(Dispatchers.Main) {
                _serialMonitorConnected.value = true
                _serialMonitorConnecting.value = false
                appendSerialMonitorLine(
                    text = "串口监视器已打开 ${device.displayName} @ $baudRate 波特率。",
                    level = LogLevel.USB,
                )
                appendLog("串口监视器已打开 ${device.displayName} @ $baudRate 波特率。", level = LogLevel.USB)
            }

            val buffer = ByteArray(4096)
            while (isActive && transport.isOpen) {
                val read = try {
                    transport.read(buffer, 250)
                } catch (error: IOException) {
                    withContext(Dispatchers.Main) {
                        _serialMonitorError.value = "串口读取失败：${error.message}"
                        appendSerialMonitorLine(
                            text = "串口读取失败：${error.message}",
                            level = LogLevel.ERROR,
                        )
                    }
                    break
                }
                if (read > 0) {
                    appendSerialMonitorChunk(String(buffer, 0, read, Charsets.UTF_8))
                }
            }
        } catch (error: Throwable) {
            withContext(Dispatchers.Main) {
                _serialMonitorError.value = error.message ?: "串口监视器运行失败。"
                appendSerialMonitorLine(
                    text = "串口监视器运行失败：${error.message ?: "未知错误"}",
                    level = LogLevel.ERROR,
                )
                appendLog("串口监视器运行失败：${error.message}", level = LogLevel.ERROR)
            }
        } finally {
            runCatching { transport.close() }
            withContext(Dispatchers.Main) {
                if (serialMonitorTransport === transport) {
                    serialMonitorTransport = null
                }
                _serialMonitorConnected.value = false
                _serialMonitorConnecting.value = false
            }
        }
    }
}

internal fun MainViewModel.stopSerialMonitorImpl() {
    serialMonitorReadJob?.cancel()
    serialMonitorReadJob = null

    val transport = serialMonitorTransport
    serialMonitorTransport = null
    _serialMonitorConnected.value = false
    _serialMonitorConnecting.value = false
    _serialMonitorBlockingMessage.value = null

    if (transport != null) {
        scope.launch(Dispatchers.IO) {
            runCatching { transport.close() }
        }
        appendSerialMonitorLine(text = "串口监视器已关闭。", level = LogLevel.USB)
        appendLog("串口监视器已关闭。", level = LogLevel.USB)
    }
}

internal fun MainViewModel.sendSerialMonitorInputImpl() {
    val transport = serialMonitorTransport
    if (!_serialMonitorConnected.value || transport == null) {
        _serialMonitorError.value = "发送数据前请先连接串口监视器。"
        return
    }

    val ending = when (_serialMonitorLineEnding.value) {
        "LF" -> "\n"
        "CR" -> "\r"
        "CRLF" -> "\r\n"
        else -> ""
    }
    val payload = (_serialMonitorInput.value + ending).toByteArray(Charsets.UTF_8)
    _serialMonitorInput.value = ""

    scope.launch(Dispatchers.IO) {
        try {
            transport.write(payload, 1000)
        } catch (error: Throwable) {
            withContext(Dispatchers.Main) {
                _serialMonitorError.value = "串口写入失败：${error.message}"
                appendSerialMonitorLine(
                    text = "串口写入失败：${error.message}",
                    level = LogLevel.ERROR,
                )
            }
        }
    }
}

internal fun MainViewModel.exportSerialMonitorLogImpl() {
    val lines = _serialMonitorLogLines.value
    if (lines.isEmpty()) {
        _serialMonitorError.value = "没有可导出的串口监视器日志。"
        return
    }

    val rootUri = _exportDirectory.value
    if (rootUri == null) {
        _serialMonitorError.value = "导出前请先设置 NRSuite 根目录。"
        return
    }

    val logText = lines.joinToString(separator = "\n") { line ->
        "[${line.timestamp}] ${line.text}"
    }

    scope.launch(Dispatchers.IO) {
        try {
            val logsDirectory = ensureChildDirectory(rootUri, "Logs")?.uri ?: rootUri
            val timestamp = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"),
            )
            val fileName = "serial-monitor-$timestamp.txt"
            val documentUri = DocumentsContract.createDocument(
                app.contentResolver,
                logsDirectory,
                "text/plain",
                fileName,
            ) ?: throw IOException("存储提供程序无法创建日志文件")

            app.contentResolver.openOutputStream(documentUri, "wt")?.use { output ->
                output.write(logText.toByteArray(Charsets.UTF_8))
            } ?: throw IOException("无法打开导出文件")

            withContext(Dispatchers.Main) {
                appendLog("串口监视器日志已导出至 ${displayNameForTreeUri(rootUri)}/Logs/$fileName。")
            }
        } catch (error: Throwable) {
            withContext(Dispatchers.Main) {
                _serialMonitorError.value = "串口日志导出失败：${error.message}"
                appendLog("串口监视器导出失败：${error.message}", level = LogLevel.ERROR)
            }
        }
    }
}

private fun MainViewModel.appendSerialMonitorChunk(chunk: String) {
    val normalized = chunk.replace("\r\n", "\n").replace('\r', '\n')
    val parts = normalized.split('\n')
    for ((index, part) in parts.withIndex()) {
        if (part.isNotEmpty() || index < parts.lastIndex) {
            appendSerialMonitorLine(text = part)
        }
    }
}

private fun MainViewModel.appendSerialMonitorLine(
    text: String,
    level: LogLevel = inferSerialLogLevel(text),
) {
    val line = SerialLogLine(
        timestamp = LocalTime.now().format(SERIAL_LOG_TIME_FORMAT),
        text = text,
        level = level,
    )
    _serialMonitorLogLines.update { current -> current + line }
}

private fun inferSerialLogLevel(text: String): LogLevel {
    val lower = text.lowercase()
    return when {
        "error" in lower || "fail" in lower -> LogLevel.ERROR
        "success" in lower || "ok" in lower -> LogLevel.SUCCESS
        "usb" in lower -> LogLevel.USB
        else -> LogLevel.INFO
    }
}
