package com.swp81x.nrsuite

import android.net.Uri
import com.swp81x.nrsuite.core.credentials.CapturedCredential
import com.swp81x.nrsuite.core.credentials.CredentialSession
import com.swp81x.nrsuite.core.credentials.CredentialSource
import com.swp81x.nrsuite.core.credentials.CredentialStatus
import com.swp81x.nrsuite.core.history.HistoryLevel
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.pcap.PcapReader
import com.swp81x.nrsuite.core.wifi.DetectedSsid
import com.swp81x.nrsuite.core.wifi.PcapSsidParser
import com.swp81x.nrsuite.core.wpa.WpaCracker
import com.swp81x.nrsuite.core.wpa.WpaHandshake
import com.swp81x.nrsuite.core.wpa.WpaHandshakeParser
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class CustomPcapAnalysis(
    val handshake: WpaHandshake,
    val detectedSsids: List<DetectedSsid>,
)

private suspend fun MainViewModel.analyzeCustomPcapImpl(uri: Uri): CustomPcapAnalysis =
    withContext(Dispatchers.IO) {
        val input = app.contentResolver.openInputStream(uri)
            ?: throw IOException("无法打开所选的 PCAP 文件。")
        val parsed = WpaHandshake()
        val detected = LinkedHashMap<String, DetectedSsid>()
        PcapReader(input).forEachPacket { frame ->
            WpaHandshakeParser.parse(frame, parsed)
            PcapSsidParser.parse(frame)?.let { ssid ->
                detected["${ssid.ssid}|${ssid.bssid}"] = ssid
            }
            true
        }
        CustomPcapAnalysis(
            handshake = parsed.copyHandshake(),
            detectedSsids = detected.values.toList(),
        )
    }

private suspend fun MainViewModel.parseHandshakeFromUriImpl(uri: Uri): WpaHandshake =
    withContext(Dispatchers.IO) {
        val input = app.contentResolver.openInputStream(uri)
            ?: throw IOException("无法打开所选的 PCAP 文件。")
        val parsed = WpaHandshake()
        PcapReader(input).forEachPacket { frame ->
            WpaHandshakeParser.parse(frame, parsed)
            !parsed.isComplete
        }
        parsed.copyHandshake()
    }

private fun ByteArray.toMacString(): String =
    joinToString(":") { value -> String.format("%02X", value.toInt() and 0xFF) }

internal fun MainViewModel.loadCredentialSessionsImpl() {
    scope.launch(Dispatchers.IO) {
        val loaded = runCatching { credentialStore.loadSessions() }
            .getOrDefault(emptyList())
            .sortedByDescending { it.startedAt }
        _credentialSessions.value = loaded
    }
}

internal fun MainViewModel.persistCrackedCredentialImpl(sessionId: String, password: String) {
    synchronized(credentialLock) {
        val session = _credentialSessions.value.firstOrNull { it.id == sessionId } ?: return
        if (session.source != CredentialSource.EVIL_TWIN) return
        if (session.credentials.any { it.status == CredentialStatus.CORRECT }) return

        val updated = session.copy(
            credentials = listOf(
                CapturedCredential(
                    value = password,
                    capturedAt = timeHmNow(),
                    status = CredentialStatus.CORRECT,
                    source = CredentialSource.EVIL_TWIN,
                ),
            ),
        )
        if (_crackerSelectedSession.value?.id == sessionId) {
            _crackerSelectedSession.value = updated
        }
        upsertCredentialSessionLocked(updated)
    }
}

internal suspend fun MainViewModel.saveCrackedCustomSessionImpl(sourceUri: Uri, displayName: String, ssid: String, bssid: ByteArray?, password: String) {
    val copiedFile = withContext(Dispatchers.IO) {
        runCatching {
            val directory = File(app.filesDir, "Credentials")
            if (!directory.exists() && !directory.mkdirs()) {
                throw IOException("无法创建凭据捕获目录")
            }
            val destination = File(directory, "wpa_cracker_${System.currentTimeMillis()}.pcap")
            val input = app.contentResolver.openInputStream(sourceUri)
                ?: throw IOException("无法打开自定义 PCAP 文件")
            input.use { source ->
                destination.outputStream().use { sink ->
                    source.copyTo(sink)
                }
            }
            destination
        }.onFailure { error ->
            appendLog("无法保存已破解的自定义 PCAP: ${error.message}", level = LogLevel.ERROR)
        }.getOrNull()
    }

    val session = CredentialSession(
        id = System.currentTimeMillis().toString(),
        source = CredentialSource.WPA_CRACKER,
        ssid = ssid.ifBlank { "(隐藏 SSID)" },
        bssid = bssid?.toMacString().orEmpty(),
        channel = null,
        startedAt = timestampNow(),
        endedAt = timestampNow(),
        pcapPath = copiedFile?.absolutePath,
        credentials = listOf(
            CapturedCredential(
                value = password,
                capturedAt = timeHmNow(),
                status = CredentialStatus.CORRECT,
                source = CredentialSource.WPA_CRACKER,
            ),
        ),
    )
    synchronized(credentialLock) {
        upsertCredentialSessionLocked(session)
    }
    appendLog("已保存 ${ssid.ifBlank { displayName }} 的 WPA 破解结果。")
}

internal fun MainViewModel.deleteCredentialSessionImpl(id: String) {
    val removed = synchronized(credentialLock) {
        val session = _credentialSessions.value.firstOrNull { it.id == id } ?: return@synchronized null
        _credentialSessions.value = _credentialSessions.value.filterNot { it.id == id }
        session
    } ?: return

    if (_crackerSelectedSession.value?.id == removed.id) {
        _crackerSelectedSession.value = null
    }

    scope.launch(Dispatchers.IO) {
        runCatching {
            credentialStore.deleteCaptureFile(removed)
            credentialStore.saveSessions(_credentialSessions.value)
        }
    }
}

internal fun MainViewModel.clearCredentialSessionsImpl() {
    val removed = synchronized(credentialLock) {
        val current = _credentialSessions.value
        _credentialSessions.value = emptyList()
        current
    }
    _crackerSelectedSession.value = null
    scope.launch(Dispatchers.IO) {
        runCatching {
            removed.forEach { credentialStore.deleteCaptureFile(it) }
            credentialStore.saveSessions(emptyList())
        }
    }
}

internal fun MainViewModel.selectCrackerSessionImpl(session: CredentialSession?) {
    if (_crackerRunning.value) return
    if (session != null) {
        resetCrackerCustomPcapStateImpl()
    }
    _crackerSelectedSession.value = session
    _crackerResult.value = null
    _crackerTested.value = 0
    _crackerSpeed.value = 0.0
    _crackerStatus.value = when {
        session == null -> "请选择已保存的握手包，或选择自定义 PCAP。"
        session.pcapPath.isNullOrBlank() -> "所选会话不包含 PCAP 抓包文件。"
        else -> "就绪: ${session.ssid.ifBlank { "目标 AP" }}"
    }
}

internal fun MainViewModel.setCrackerCustomPcapImpl(uri: Uri, name: String?) {
    if (_crackerRunning.value) return
    _crackerSelectedSession.value = null
    _crackerCustomPcapUri.value = uri
    _crackerCustomPcapName.value = name ?: uri.lastPathSegment ?: "capture.pcap"
    resetCrackerCustomPcapStateImpl()
    _crackerCustomPcapUri.value = uri
    _crackerCustomPcapName.value = name ?: uri.lastPathSegment ?: "capture.pcap"
    _crackerCustomPcapMessage.value = "正在校验 EAPOL 握手包..."
    _crackerResult.value = null
    _crackerTested.value = 0
    _crackerSpeed.value = 0.0
    _crackerStatus.value = "正在校验自定义 PCAP..."

    scope.launch(Dispatchers.IO) {
        _crackerCustomPcapValidating.value = true
        try {
            val analysis = analyzeCustomPcapImpl(uri)
            val parsed = analysis.handshake
            val sawM1 = parsed.m1Bssid != null || parsed.aNonce != null
            val sawM2 = parsed.m2Bssid != null || parsed.mic != null
            val detected = analysis.detectedSsids
            val preferred = detected.firstOrNull {
                parsed.bssid?.let { bytes -> it.bssid.equals(bytes.toMacString(), ignoreCase = true) } == true
            } ?: detected.singleOrNull()

            _crackerCustomSsidOptions.value = detected
            if (preferred != null) {
                _crackerCustomSsidSelected.value = preferred.ssid
                _crackerCustomSsid.value = preferred.ssid
                _crackerCustomSsidManual.value = false
            } else {
                _crackerCustomSsidSelected.value = null
                _crackerCustomSsid.value = ""
                _crackerCustomSsidManual.value = true
            }

            _crackerCustomPcapValid.value = parsed.isComplete
            _crackerCustomPcapMessage.value = when {
                parsed.isComplete && detected.isNotEmpty() ->
                    "检测到有效的 M1/M2 EAPOL 握手包。在 PCAP 中发现 ${detected.size} 个 SSID。"
                parsed.isComplete ->
                    "检测到有效的 M1/M2 EAPOL 握手包。未发现 SSID；请手动输入。"
                sawM1 || sawM2 ->
                    "发现 EAPOL 帧，但 M1+M2 握手包不完整。"
                else ->
                    "该 PCAP 中未发现 EAPOL 握手包。"
            }
            _crackerStatus.value = when {
                !parsed.isComplete -> _crackerCustomPcapMessage.value
                detected.isNotEmpty() -> "自定义 PCAP 校验成功。请选择目标 SSID 并选择字典文件。"
                else -> "自定义 PCAP 校验成功。请输入目标 SSID 并选择字典文件。"
            }
        } catch (t: Throwable) {
            _crackerCustomPcapValid.value = false
            _crackerCustomPcapValidating.value = false
            _crackerCustomPcapMessage.value = "PCAP 无效: ${t.message ?: t.javaClass.simpleName}"
            _crackerStatus.value = _crackerCustomPcapMessage.value
        } finally {
            _crackerCustomPcapValidating.value = false
        }
    }
}

internal fun MainViewModel.selectCrackerCustomSsidImpl(ssid: String) {
    if (_crackerRunning.value) return
    _crackerCustomSsidSelected.value = ssid
    _crackerCustomSsid.value = ssid.take(32)
    _crackerCustomSsidManual.value = false
}

internal fun MainViewModel.useManualCrackerSsidImpl() {
    if (_crackerRunning.value) return
    _crackerCustomSsidSelected.value = null
    _crackerCustomSsidManual.value = true
}

internal fun MainViewModel.setCrackerCustomSsidImpl(value: String) {
    if (!_crackerCustomSsidManual.value) return
    _crackerCustomSsid.value = value.take(32)
}

internal fun MainViewModel.clearCrackerCustomPcapImpl() {
    if (_crackerRunning.value) return
    resetCrackerCustomPcapStateImpl()
    _crackerResult.value = null
    _crackerStatus.value = "请选择已保存的握手包，或选择自定义 PCAP。"
}

internal fun MainViewModel.resetCrackerCustomPcapStateImpl() {
    _crackerCustomPcapUri.value = null
    _crackerCustomPcapName.value = null
    _crackerCustomSsid.value = ""
    _crackerCustomSsidOptions.value = emptyList()
    _crackerCustomSsidSelected.value = null
    _crackerCustomSsidManual.value = true
    _crackerCustomPcapValid.value = false
    _crackerCustomPcapValidating.value = false
    _crackerCustomPcapMessage.value = "未选择自定义 PCAP 文件。"
}

internal fun MainViewModel.setCrackerWordlistImpl(uri: Uri, name: String?) {
    if (_crackerRunning.value) return
    _crackerWordlistUri.value = uri
    _crackerWordlistName.value = name ?: uri.lastPathSegment ?: "wordlist.txt"
    _crackerResult.value = null
    _crackerStatus.value = "已选择字典: ${_crackerWordlistName.value}"
}

internal fun MainViewModel.clearCrackerWordlistImpl() {
    if (_crackerRunning.value) return
    _crackerWordlistUri.value = null
    _crackerWordlistName.value = null
    _crackerResult.value = null
    _crackerStatus.value = "请选择已保存的握手包与字典文件。"
}

internal fun MainViewModel.startCrackerImpl() {
    if (_crackerRunning.value) return

    val wordlistUri = _crackerWordlistUri.value
    if (wordlistUri == null) {
        appendLog("WPA 破解器: 请先选择字典文件。", level = LogLevel.ERROR)
        return
    }

    val customUri = _crackerCustomPcapUri.value
    val session = _crackerSelectedSession.value
    val ssid: String
    val pcapLabel: String
    val savedSessionId: String?
    val parseHandshake: suspend () -> WpaHandshake

    when {
        customUri != null -> {
            if (_crackerCustomPcapValidating.value) {
                appendLog("WPA 破解器: 自定义 PCAP 仍在校验中。", level = LogLevel.ERROR)
                return
            }
            val customSsid = _crackerCustomSsid.value.trim()
            if (!_crackerCustomPcapValid.value) {
                appendLog("WPA 破解器: 自定义 PCAP 中没有已校验的 M1/M2 EAPOL 握手包。", level = LogLevel.ERROR)
                return
            }
            if (customSsid.isBlank() || customSsid.length > 32) {
                appendLog("WPA 破解器: 请为自定义 PCAP 输入目标 SSID (1-32 字符)。", level = LogLevel.ERROR)
                return
            }
            ssid = customSsid
            pcapLabel = _crackerCustomPcapName.value ?: "自定义 PCAP"
            savedSessionId = null
            parseHandshake = { parseHandshakeFromUriImpl(customUri) }
        }

        session != null -> {
            val pcapPath = session.pcapPath
            if (pcapPath.isNullOrBlank()) {
                appendLog("WPA 破解器: 所选会话不包含 PCAP 抓包。", level = LogLevel.ERROR)
                return
            }
            val pcapFile = File(pcapPath)
            if (!pcapFile.exists()) {
                appendLog("WPA 破解器: PCAP 文件已不存在。", level = LogLevel.ERROR)
                return
            }
            if (session.ssid.isBlank() || session.ssid == "(hidden)" || session.ssid == "(隐藏 SSID)") {
                appendLog("WPA 破解器: 所选会话没有可用的 SSID。", level = LogLevel.ERROR)
                return
            }
            ssid = session.ssid
            pcapLabel = pcapFile.name
            savedSessionId = session.id
            parseHandshake = {
                withContext(Dispatchers.IO) {
                    val parsed = WpaHandshake()
                    PcapReader(pcapFile).forEachPacket { frame ->
                        WpaHandshakeParser.parse(frame, parsed)
                        !parsed.isComplete
                    }
                    parsed.copyHandshake()
                }
            }
        }

        else -> {
            appendLog("WPA 破解器: 请选择已保存的会话或选择自定义 PCAP。", level = LogLevel.ERROR)
            return
        }
    }

    _crackerRunning.value = true
    _crackerResult.value = null
    _crackerTested.value = 0
    _crackerSpeed.value = 0.0
    _crackerStatus.value = "正在解析握手包..."
    appendLog("WPA 破解器: 正在解析 $pcapLabel...")

    crackerJob = scope.launch(Dispatchers.Default) {
        try {
            val handshake = parseHandshake()
            if (!handshake.isComplete) {
                _crackerStatus.value = "抓包中未找到完整的 M1/M2 WPA2 握手包。"
                appendLog("WPA 破解器: 未找到完整的 M1/M2 握手包。", level = LogLevel.ERROR)
                return@launch
            }

            _crackerStatus.value = "正在使用字典测试..."
            appendLog("WPA 破解器: 握手包已就绪；正在测试字典...")

            val reader = withContext(Dispatchers.IO) {
                app.contentResolver.openInputStream(wordlistUri)?.bufferedReader()
            } ?: throw IOException("无法打开所选的字典文件。")

            val runningJob = coroutineContext[Job]
            val found = WpaCracker.crack(
                handshake = handshake,
                ssid = ssid,
                wordlist = reader,
                shouldStop = { runningJob?.isActive != true },
                onProgress = { progress ->
                    _crackerTested.update { maxOf(it, progress.tested) }
                    _crackerSpeed.value = progress.candidatesPerSecond
                },
            )

            if (runningJob?.isActive != true) {
                _crackerStatus.value = "破解器已停止。"
                return@launch
            }

            if (found != null) {
                _crackerResult.value = found
                _crackerStatus.value = "已找到密码: $found"
                if (savedSessionId != null) {
                    persistCrackedCredentialImpl(savedSessionId, found)
                } else if (customUri != null) {
                    saveCrackedCustomSessionImpl(
                        sourceUri = customUri,
                        displayName = pcapLabel,
                        ssid = ssid,
                        bssid = handshake.bssid,
                        password = found,
                    )
                }
                appendLog("WPA 破解器: 已找到密码。")
                addHistory(
                    "wpa_cracker",
                    "已破解 ${ssid.ifBlank { pcapLabel }} 的 WPA2 密码",
                    HistoryLevel.SUCCESS,
                )
            } else {
                _crackerStatus.value = "在所选字典中未找到匹配密码。"
                appendLog("WPA 破解器: 未找到密码。")
            }
        } catch (e: CancellationException) {
            _crackerStatus.value = "破解器已停止。"
            throw e
        } catch (t: Throwable) {
            _crackerStatus.value = "破解失败: ${t.message ?: t.javaClass.simpleName}"
            appendLog(
                "WPA 破解器失败: ${t.message ?: t.javaClass.simpleName}",
                level = LogLevel.ERROR,
            )
        } finally {
            _crackerRunning.value = false
            crackerJob = null
        }
    }
}

internal fun MainViewModel.stopCrackerImpl() {
    if (!_crackerRunning.value) return
    _crackerStatus.value = "正在停止..."
    crackerJob?.cancel()
}
