package com.swp81x.nrsuite

import android.hardware.usb.UsbDevice
import android.net.Uri
import android.util.Base64
import com.swp81x.nrsuite.core.history.HistoryLevel
import com.swp81x.nrsuite.core.session.ConnectionState
import com.swp81x.nrsuite.core.storage.StorageFile
import com.swp81x.nrsuite.core.usb.UsbSerialDeviceCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.update
import org.json.JSONObject

private const val PREF_DUCKY_SCRIPTS = "ducky_scripts"
private const val BADUSB_RAW_CHUNK_SIZE = 693

// BadUSB, DuckyScript library, and mass-storage file operations.

internal fun MainViewModel.setBadUsbPayloadImpl(uri: Uri, name: String?) {
    _badUsbPayloadUri.value = uri
    _badUsbPayloadName.value = name ?: uri.lastPathSegment ?: "payload.txt"
    _badUsbProgress.value = 0
    _badUsbArmedFingerprints.value = emptySet()
    appendLog("已选择 BadUSB 有效载荷：${_badUsbPayloadName.value}")
}

internal fun MainViewModel.clearBadUsbPayloadImpl() {
    _badUsbPayloadUri.value = null
    _badUsbPayloadName.value = null
    _badUsbSavedScriptText.value = null
    _badUsbProgress.value = 0
    _badUsbArmedFingerprints.value = emptySet()
    appendLog("已清除 BadUSB 有效载荷。")
}

internal fun MainViewModel.saveDuckyScriptImpl(name: String, script: String) {
    val cleanName = name.trim()
    if (cleanName.isBlank()) return
    _duckyScriptMap.update { it + (cleanName to script) }
    persistDuckyScriptsImpl()
    appendLog("已保存 DuckyScript 脚本 '$cleanName'。")
}

internal fun MainViewModel.deleteDuckyScriptImpl(name: String) {
    _duckyScriptMap.update { it - name }
    persistDuckyScriptsImpl()
    appendLog("已删除 DuckyScript 脚本 '$name'。")
}

internal fun MainViewModel.useBadUsbSavedScriptImpl(name: String) {
    val script = _duckyScriptMap.value[name] ?: return
    _badUsbPayloadUri.value = null
    _badUsbPayloadName.value = name
    _badUsbSavedScriptText.value = script
    _badUsbArmedFingerprints.value = emptySet()
    appendLog("已选择 BadUSB 脚本：$name")
}

internal fun MainViewModel.useBleSavedScriptImpl(name: String) {
    val script = _duckyScriptMap.value[name] ?: return
    _blePayloadUri.value = null
    _blePayloadName.value = name
    _bleSavedScriptText.value = script
    appendLog("已选择 BLE 脚本：$name")
}

internal fun MainViewModel.loadDuckyScriptsImpl() {
    val raw = preferences.getString(PREF_DUCKY_SCRIPTS, null) ?: return
    runCatching {
        val json = JSONObject(raw)
        val map = mutableMapOf<String, String>()
        json.keys().forEach { key -> map[key] = json.optString(key, "") }
        _duckyScriptMap.value = map
    }
}

internal fun MainViewModel.persistDuckyScriptsImpl() {
    val json = JSONObject()
    _duckyScriptMap.value.forEach { (name, script) -> json.put(name, script) }
    preferences.edit().putString(PREF_DUCKY_SCRIPTS, json.toString()).apply()
}

internal fun MainViewModel.selectBadUsbTargetImpl(device: UsbDevice) {
    val entry = _devices.value.firstOrNull { it.device.deviceId == device.deviceId }
        ?: UsbSerialDeviceCatalog.find(usbManager, device)
    if (entry == null) {
        appendLog("未找到适用于 ${device.deviceName} 的支持的 USB 串口驱动。")
        return
    }
    val previousDeviceId = _badUsbTargetDevice.value?.device?.deviceId
    _badUsbTargetDevice.value = entry
    if (previousDeviceId != entry.device.deviceId) {
        _badUsbProgress.value = 0
    }
    appendLog("BadUSB 目标：${entry.displayName}")
}

internal fun MainViewModel.armBadUsbImpl(mscMode: Boolean) {
    val targetEntry = _badUsbTargetDevice.value
    if (targetEntry == null) {
        appendLog("请先选择 BadUSB 目标设备。")
        return
    }

    val targetFingerprint = deviceFingerprintImpl(targetEntry.device)
    val targetSession = deviceSessions[targetFingerprint]?.session
    if (targetSession == null) {
        appendLog("请先连接所选的 BadUSB 目标设备。")
        return
    }

    val targetState = targetSession.state.value as? ConnectionState.Connected
    if (targetState == null) {
        appendLog("所选的 BadUSB 目标设备未连接。")
        return
    }
    if (targetState.chip !in setOf("ESP32-S2", "ESP32-S3")) {
        appendLog("BadUSB 功能需要 ESP32-S2 或 ESP32-S3（当前芯片：${targetState.chip ?: "未知"}）。")
        return
    }
    if (targetState.features.isNotEmpty() && "badusb" !in targetState.features) {
        appendLog("所选固件不支持 BadUSB 功能。")
        return
    }
    if (_badUsbUploading.value) return

    val payloadUri = _badUsbPayloadUri.value
    val savedScript = _badUsbSavedScriptText.value
    if (payloadUri == null && savedScript == null) {
        appendLog("请先选择 DuckyScript 有效载荷。")
        return
    }

    scope.launch {
        _badUsbUploading.value = true
        _badUsbProgress.value = 0
        try {
            val bytes = savedScript?.toByteArray(Charsets.UTF_8)
                ?: withContext(Dispatchers.IO) {
                    runCatching {
                        payloadUri?.let {
                            app.contentResolver
                                .openInputStream(it)
                                ?.use { stream -> stream.readBytes() }
                        }
                    }.getOrNull()
                }
            if (bytes == null) {
                appendLog("无法读取所选的 BadUSB 有效载荷。")
                return@launch
            }
            if (bytes.isEmpty()) {
                appendLog("BadUSB 有效载荷为空。")
                return@launch
            }

            val remoteFilename = "ducky.txt"
            var offset = 0
            while (offset < bytes.size) {
                val end = minOf(offset + BADUSB_RAW_CHUNK_SIZE, bytes.size)
                val chunk = bytes.copyOfRange(offset, end)
                val encoded = android.util.Base64.encodeToString(chunk, android.util.Base64.NO_WRAP)
                val isLast = end == bytes.size

                var success = false
                for (attempt in 1..3) {
                    val response = targetSession.sendCommand(
                        "SET_FILE_CHUNK",
                        JSONObject().apply {
                            put("filename", remoteFilename)
                            put("data", encoded)
                            put("last", isLast)
                        },
                        timeoutMs = 6_000,
                    )
                    if (response?.optBoolean("ok") == true) {
                        success = true
                        break
                    }
                    appendLog("BadUSB 分块尝试第 $attempt 次失败；正在重试...")
                    delay(300)
                }
                if (!success) {
                    appendLog("BadUSB 在字节 $offset 处上传失败。")
                    return@launch
                }

                offset = end
                _badUsbProgress.value = ((offset * 100) / bytes.size)
            }

            val response = targetSession.sendCommand(
                "START_BADUSB",
                JSONObject().apply {
                    put("filename", remoteFilename)
                    put("msc", mscMode)
                },
                timeoutMs = 10_000,
            )
            if (response?.optBoolean("ok") == true) {
                _badUsbArmedFingerprints.update { it + targetFingerprint }
                appendLog(
                    "已在 ${targetEntry.displayName} 上装填 BadUSB 有效载荷。" +
                        "拔出并重新插入设备即可执行一次。"
                )
                addHistory("badusb", "已为 ${targetEntry.displayName} 装填有效载荷", HistoryLevel.SUCCESS)
            } else {
                appendLog("装填 BadUSB 有效载荷失败：${response?.optString("msg") ?: "超时"}")
            }
        } finally {
            _badUsbUploading.value = false
        }
    }
}

internal fun MainViewModel.refreshStorageImpl() {
    val activeSession = session
    if (activeSession == null) {
        appendLog("浏览存储前请先连接设备。")
        return
    }
    if (_storageLoading.value) return

    _storageLoading.value = true
    scope.launch {
        val response = activeSession.sendCommand("MSC_LIST", timeoutMs = 8_000)
        _storageLoading.value = false
        if (response?.optBoolean("ok") != true) {
            appendLog("获取存储列表失败：${response?.optString("msg") ?: "超时"}")
            return@launch
        }

        _storageTotal.value = response.optLong("total", 0L)
        _storageUsed.value = response.optLong("used", 0L)
        _storageFree.value = response.optLong("free", 0L)

        val files = mutableListOf<StorageFile>()
        val jsonFiles = response.optJSONArray("files")
        if (jsonFiles != null) {
            for (index in 0 until jsonFiles.length()) {
                val item = jsonFiles.optJSONObject(index) ?: continue
                files += StorageFile(
                    name = item.optString("name"),
                    size = item.optInt("size", 0),
                )
            }
        }
        _storageFiles.value = files
        appendLog("存储：${files.size} 个文件，剩余可用 ${_storageFree.value} 字节。")
    }
}

internal fun MainViewModel.deleteStorageFileImpl(name: String) {
    val activeSession = session
    if (activeSession == null) {
        appendLog("删除文件前请先连接设备。")
        return
    }
    scope.launch {
        val response = activeSession.sendCommand(
            "MSC_DELETE",
            JSONObject().put("path", name),
            timeoutMs = 8_000,
        )
        if (response?.optBoolean("ok") == true) {
            appendLog("已删除 $name。")
            refreshStorageImpl()
        } else {
            appendLog("删除 $name 失败：${response?.optString("msg") ?: "超时"}")
        }
    }
}

internal fun MainViewModel.startMassStorageImpl() {
    val activeSession = session
    if (activeSession == null) {
        appendLog("进入大容量存储模式前请先连接设备。")
        return
    }
    val chip = (_connectionState.value as? ConnectionState.Connected)?.chip
    if (chip !in setOf("ESP32-S2", "ESP32-S3")) {
        appendLog("启动大容量存储需要 ESP32-S2 或 ESP32-S3（当前芯片：${chip ?: "未知"}）。")
        return
    }

    scope.launch {
        appendLog("正在将设备切换至 USB 大容量存储模式...")
        val response = activeSession.sendCommand("START_MSC", timeoutMs = 5_000)
        if (response?.optBoolean("ok") == true) {
            appendLog("设备正在重启进入大容量存储模式；USB 桥接将被断开。")
        } else if (response == null) {
            appendLog("无响应（符合预期）：USB 正在重新枚举为大容量存储设备。")
        } else {
            appendLog("设备拒绝大容量存储模式：${response.optString("msg")}")
        }
    }
}
