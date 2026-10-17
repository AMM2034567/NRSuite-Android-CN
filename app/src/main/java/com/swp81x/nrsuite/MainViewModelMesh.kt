package com.swp81x.nrsuite

import com.swp81x.nrsuite.core.history.HistoryLevel
import com.swp81x.nrsuite.core.mesh.MeshCrypto
import com.swp81x.nrsuite.core.mesh.MeshNodeStatus
import com.swp81x.nrsuite.core.mesh.StoredMeshCredentials
import com.swp81x.nrsuite.core.session.ConnectionState
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import org.json.JSONObject

// Mesh Foundation Phase 1/2: app-side key derivation, provisioning, auth,
// activation, and live status/event handling.

private const val MIN_MESH_PASSPHRASE_LENGTH = 8
private const val MESH_COMMAND_TIMEOUT_MS = 8_000L
private const val MESH_SENSOR_STALE_MS = 15_000L
private const val MESH_SENSOR_SWEEP_MS = 5_000L

internal fun MainViewModel.refreshMeshStatusImpl() {
    val activeSession = session ?: run {
        appendLog("读取 Mesh 状态前请先连接设备。")
        return
    }

    scope.launch {
        val response = activeSession.sendCommand("MESH_STATUS", timeoutMs = 5_000)
        if (response?.optBoolean("ok") == true) {
            updateMeshStatusFromJsonImpl(response)
        } else {
            appendLog("Mesh 状态请求失败：${response?.optString("msg") ?: "超时"}")
        }
    }
}

internal fun MainViewModel.startMeshHealthSweepImpl() {
    scope.launch {
        while (isActive) {
            delay(MESH_SENSOR_SWEEP_MS)
            pruneStaleMeshNodesImpl()
        }
    }
}

internal fun MainViewModel.pruneStaleMeshNodesImpl() {
    val now = System.currentTimeMillis()
    _meshNodes.update { current ->
        current.map { node ->
            if (!node.online) {
                node
            } else {
                val lastActivity = maxOf(node.lastSeenAtMs, node.lastHealthAtMs)
                if (lastActivity > 0L && now - lastActivity > MESH_SENSOR_STALE_MS) {
                    node.copy(online = false)
                } else {
                    node
                }
            }
        }
    }
}

internal fun MainViewModel.setMeshPassphraseImpl(value: String) {
    _meshPassphrase.value = value
    _meshPassphraseMatch.value = null
    _meshSetupMessage.value = null
}

internal fun MainViewModel.setMeshKeyIdImpl(value: String) {
    _meshKeyId.value = value
}

internal fun MainViewModel.setMeshChannelImpl(value: Int) {
    _meshChannel.value = value.coerceIn(1, 13)
}

internal fun MainViewModel.changeMeshChannelImpl(channel: Int) {
    val safeChannel = channel.coerceIn(1, 13)
    val activeSession = session ?: run {
        val message = "更改 Mesh 信道前请先连接设备。"
        _meshLastError.value = message
        appendLog(message, tag = "mesh")
        return
    }

    _meshActionInProgress.value = true
    _meshLastError.value = null
    scope.launch {
        try {
            val response = activeSession.sendCommand(
                "MESH_SET_CHANNEL",
                JSONObject().put("channel", safeChannel),
                timeoutMs = MESH_COMMAND_TIMEOUT_MS,
            )
            if (response?.optBoolean("ok") == true) {
                _meshChannel.value = safeChannel
                _meshSetupMessage.value = "已请求更改 Mesh 信道。"
                appendLog("已请求更改 Mesh 信道：$safeChannel", tag = "mesh")
            } else {
                val message = response?.optString("msg") ?: "超时"
                _meshLastError.value = message
                appendLog("更改 Mesh 信道失败：$message", tag = "mesh")
            }
        } finally {
            _meshActionInProgress.value = false
        }
    }
}

internal fun MainViewModel.forgetMeshPassphraseImpl() {
    removeStoredCredentialsForNodeImpl()
    removeGlobalCredentialsImpl()
    _meshPassphrase.value = ""
    _meshKeyId.value = ""
    _meshPassphraseMatch.value = null
    _meshSetupMessage.value = "已从本应用中移除保存的 Mesh 凭据。"
    _meshCheckingPassphrase.value = false
    appendLog("本应用已清除保存的 Mesh 凭据。", tag = "mesh")
}

internal fun MainViewModel.verifyMeshPassphraseImpl(passphrase: String) {
    val cleanPassphrase = passphrase.trim()
    if (cleanPassphrase.length < MIN_MESH_PASSPHRASE_LENGTH) {
        val message = "Mesh 通信密码至少需要 $MIN_MESH_PASSPHRASE_LENGTH 个字符。"
        _meshSetupMessage.value = message
        _meshLastError.value = message
        return
    }

    val activeSession = session ?: run {
        val message = "检查 Mesh 成员身份前请先连接设备。"
        _meshSetupMessage.value = message
        _meshLastError.value = message
        return
    }

    if (!_meshInitialized.value) {
        _meshPassphraseMatch.value = null
        _meshSetupMessage.value = "该设备尚未配网配置。"
        return
    }

    val keys = runCatching { MeshCrypto.deriveKeys(cleanPassphrase) }.getOrElse { error ->
        val message = "Mesh 密钥派生失败：${error.message}"
        _meshSetupMessage.value = message
        _meshLastError.value = message
        return
    }

    _meshCheckingPassphrase.value = true
    _meshLastError.value = null
    _meshSetupMessage.value = null
    _meshPassphraseMatch.value = null

    scope.launch {
        try {
            val nonce = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val challenge = activeSession.sendCommand(
                "MESH_AUTH_CHALLENGE",
                JSONObject().put("nonce", MeshCrypto.base64Encode(nonce)),
                timeoutMs = MESH_COMMAND_TIMEOUT_MS,
            )
            if (challenge?.optBoolean("ok") != true) {
                val message = challenge?.optString("msg") ?: "质询超时"
                _meshSetupMessage.value = "无法校验密码：$message"
                _meshLastError.value = message
                return@launch
            }

            val returnedHmac = MeshCrypto.base64Decode(challenge.optString("hmac"))
            val expectedHmac = MeshCrypto.hmacSha256(keys.authKey, nonce)
            val match = returnedHmac != null && MessageDigest.isEqual(expectedHmac, returnedHmac)
            _meshPassphraseMatch.value = match
            _meshSetupMessage.value = if (match) {
                "该设备已使用此密码完成配网。"
            } else {
                "该设备使用了其他密码进行配网。"
            }
        } finally {
            _meshCheckingPassphrase.value = false
        }
    }
}

internal fun MainViewModel.provisionMeshImpl(passphrase: String, keyId: String?, channel: Int) {
    val cleanPassphrase = passphrase.trim()
    val safeChannel = channel.coerceIn(1, 13)
    if (cleanPassphrase.length < MIN_MESH_PASSPHRASE_LENGTH) {
        val message = "Mesh 通信密码至少需要 $MIN_MESH_PASSPHRASE_LENGTH 个字符。"
        _meshLastError.value = message
        appendLog(message, tag = "mesh")
        return
    }

    val activeSession = session ?: run {
        appendLog("配网 Mesh 密钥前请先连接设备。", tag = "mesh")
        return
    }

    val keys = runCatching { MeshCrypto.deriveKeys(cleanPassphrase) }.getOrElse { error ->
        appendLog("Mesh 密钥派生失败：${error.message}", tag = "mesh")
        return
    }

    _meshProvisioning.value = true
    _meshLastError.value = null
    _meshSetupMessage.value = null
    _meshPassphrase.value = cleanPassphrase
    _meshKeyId.value = keyId.orEmpty().trim()
    _meshPassphraseMatch.value = null

    scope.launch {
        try {
            val args = JSONObject().apply {
                put("auth_key", MeshCrypto.base64Encode(keys.authKey))
                put("transport_key", MeshCrypto.base64Encode(keys.transportKey))
                put("channel", safeChannel)
                if (!keyId.isNullOrBlank()) put("key_id", keyId.trim())
            }
            val response = activeSession.sendCommand(
                "MESH_PROVISION_KEY",
                args,
                timeoutMs = MESH_COMMAND_TIMEOUT_MS,
            )
            if (response?.optBoolean("ok") == true) {
                val stored = StoredMeshCredentials(
                    authKey = keys.authKey,
                    transportKey = keys.transportKey,
                    keyId = keyId?.trim()?.takeIf { it.isNotBlank() },
                    channel = safeChannel,
                )
                _meshChannel.value = safeChannel
                persistProvisionedCredentialsImpl(stored)
                persistGlobalCredentialsImpl(stored)
                _meshPassphraseMatch.value = true
                _meshSetupMessage.value = "Mesh 配置完成。已保存的凭据将用于后续激活。"
                appendLog("Mesh 派生密钥已配网注入；原始密码从未在链路上传输。", tag = "mesh")
                addHistory("mesh", "Mesh 密钥已配网注入", HistoryLevel.SUCCESS)
                refreshMeshFeaturesImpl()
                refreshMeshStatusImpl()
            } else {
                val message = response?.optString("msg") ?: "超时"
                _meshLastError.value = message
                _meshSetupMessage.value = "Mesh 配网失败：$message"
                appendLog("Mesh 配网失败：$message", tag = "mesh")
            }
        } finally {
            _meshProvisioning.value = false
        }
    }
}

internal fun MainViewModel.provisionMeshWithSavedCredentialsImpl() {
    val credentials = meshGlobalCredentials ?: run {
        refreshMeshGlobalCredentialsImpl()
        meshGlobalCredentials
    } ?: run {
        val message = "本应用中暂无已保存的 Mesh 凭据。请输入一次密码以建立 Mesh。"
        _meshSetupMessage.value = message
        _meshLastError.value = message
        appendLog(message, tag = "mesh")
        return
    }

    val activeSession = session ?: run {
        val message = "配网 Mesh 密钥前请先连接设备。"
        _meshSetupMessage.value = message
        _meshLastError.value = message
        appendLog(message, tag = "mesh")
        return
    }

    _meshProvisioning.value = true
    _meshLastError.value = null
    _meshSetupMessage.value = null

    scope.launch {
        try {
            val args = JSONObject().apply {
                put("auth_key", MeshCrypto.base64Encode(credentials.authKey))
                put("transport_key", MeshCrypto.base64Encode(credentials.transportKey))
                put("channel", credentials.channel.coerceIn(1, 13))
                credentials.keyId?.takeIf { it.isNotBlank() }?.let { put("key_id", it) }
            }
            val response = activeSession.sendCommand(
                "MESH_PROVISION_KEY",
                args,
                timeoutMs = MESH_COMMAND_TIMEOUT_MS,
            )
            if (response?.optBoolean("ok") == true) {
                _meshChannel.value = credentials.channel.coerceIn(1, 13)
                persistProvisionedCredentialsImpl(credentials)
                _meshPassphraseMatch.value = true
                _meshSetupMessage.value = "已使用保存的 Mesh 凭据完成设备配置。"
                appendLog("已从应用保存的凭据中配网注入 Mesh 密钥。", tag = "mesh")
                addHistory("mesh", "已通过保存的凭据配网 Mesh 密钥", HistoryLevel.SUCCESS)
                refreshMeshFeaturesImpl()
                refreshMeshStatusImpl()
            } else {
                val message = response?.optString("msg") ?: "超时"
                _meshLastError.value = message
                _meshSetupMessage.value = "Mesh 配网失败：$message"
                appendLog("Mesh 配网失败：$message", tag = "mesh")
            }
        } finally {
            _meshProvisioning.value = false
        }
    }
}

internal fun MainViewModel.authenticateAndActivateMeshImpl(passphrase: String) {
    val cleanPassphrase = passphrase.trim()
    if (cleanPassphrase.length < MIN_MESH_PASSPHRASE_LENGTH) {
        val message = "认证前请输入 Mesh 通信密码。"
        _meshLastError.value = message
        appendLog(message, tag = "mesh")
        return
    }

    val keys = runCatching { MeshCrypto.deriveKeys(cleanPassphrase) }.getOrElse { error ->
        appendLog("Mesh 密钥派生失败：${error.message}", tag = "mesh")
        return
    }

    _meshPassphrase.value = cleanPassphrase
    authenticateAndActivateWithAuthKey(keys.authKey, "通信密码")
}

internal fun MainViewModel.authenticateAndActivateStoredMeshImpl() {
    val credentials = meshStoredCredentials ?: run {
        val message = "此节点暂无保存的凭据。请重新输入 Mesh 通信密码。"
        _meshLastError.value = message
        _meshSetupMessage.value = message
        appendLog(message, tag = "mesh")
        refreshStoredCredentialsForNodeImpl()
        return
    }

    authenticateAndActivateWithAuthKey(
        authKey = credentials.authKey,
        sourceLabel = credentials.keyId ?: "保存的凭据",
    )
}

private fun MainViewModel.authenticateAndActivateWithAuthKey(
    authKey: ByteArray,
    sourceLabel: String,
) {
    val activeSession = session ?: run {
        val message = "认证 Mesh 成员身份前请先连接设备。"
        _meshLastError.value = message
        _meshSetupMessage.value = message
        appendLog(message, tag = "mesh")
        return
    }

    val nonce = ByteArray(32).also { SecureRandom().nextBytes(it) }
    _meshLastError.value = null
    _meshSetupMessage.value = null
    _meshActionInProgress.value = true

    scope.launch {
        try {
        val challenge = activeSession.sendCommand(
            "MESH_AUTH_CHALLENGE",
            JSONObject().put("nonce", MeshCrypto.base64Encode(nonce)),
            timeoutMs = MESH_COMMAND_TIMEOUT_MS,
        )
        if (challenge?.optBoolean("ok") != true) {
            val message = challenge?.optString("msg") ?: "质询超时"
            _meshLastError.value = message
            _meshSetupMessage.value = "Mesh 认证质询失败：$message"
            appendLog("Mesh 认证质询失败：$message", tag = "mesh")
            return@launch
        }

        val nodeId = challenge.optString("node_id")
        if (nodeId.isNotBlank()) {
            _meshNodeId.value = nodeId
        }

        val returnedHmac = MeshCrypto.base64Decode(challenge.optString("hmac"))
        val expectedHmac = MeshCrypto.hmacSha256(authKey, nonce)

        if (returnedHmac == null || !MessageDigest.isEqual(expectedHmac, returnedHmac)) {
            _meshPassphraseMatch.value = false
            val message = "Mesh 认证失败：节点 HMAC 与存储的凭据不一致。"
            _meshLastError.value = message
            _meshSetupMessage.value = "保存的凭据与此节点不匹配。请重新输入 Mesh 通信密码。"
            appendLog(message, tag = "mesh")
            if (sourceLabel != "通信密码") {
                removeStoredCredentialsForNodeImpl()
            }
            return@launch
        }

        _meshPassphraseMatch.value = true
        _meshSetupMessage.value = "已使用 $sourceLabel 完成身份验证。"
        appendLog("已验证 ${nodeId.ifBlank { "设备" }} 的 Mesh 身份。", tag = "mesh")

        val activation = activeSession.sendCommand(
            "MESH_ACTIVATE",
            timeoutMs = MESH_COMMAND_TIMEOUT_MS,
        )
        if (activation?.optBoolean("ok") == true) {
            _meshActive.value = true
            _meshRole.value = activation.optString("role", "candidate")
            appendLog("已请求激活 Mesh；等待节点选举。", tag = "mesh")
            addHistory("mesh", "已请求激活 Mesh", HistoryLevel.SUCCESS)
        } else {
            val message = activation?.optString("msg") ?: "超时"
            _meshLastError.value = message
            _meshSetupMessage.value = "Mesh 激活失败：$message"
            appendLog("Mesh 激活失败：$message", tag = "mesh")
        }
        } finally {
            _meshActionInProgress.value = false
        }
    }
}

internal fun MainViewModel.deactivateMeshImpl() {
    val activeSession = session
    _meshActionInProgress.value = true
    scope.launch {
        try {
            val response = activeSession?.sendCommand("MESH_DEACTIVATE", timeoutMs = 5_000)
            if (response?.optBoolean("ok") == true) {
                appendLog("Mesh 已停用；已保存的密钥配置仍保持保留。", tag = "mesh")
                resetMeshRuntimeStateImpl()
            } else {
                appendLog("停用 Mesh 请求失败或超时。", tag = "mesh")
                refreshMeshStatusImpl()
            }
        } finally {
            _meshActionInProgress.value = false
        }
    }
}

internal fun MainViewModel.clearMeshKeysImpl() {
    val activeSession = session
    scope.launch {
        val response = activeSession?.sendCommand("MESH_CLEAR_KEY", timeoutMs = 5_000)
        if (response?.optBoolean("ok") == true) {
            _meshInitialized.value = false
            _meshPassphraseMatch.value = null
            removeStoredCredentialsForNodeImpl()
            _meshSetupMessage.value = "Mesh 密钥已清除。此设备已取消配网。"
            appendLog("设备上的 Mesh 密钥已清除。", tag = "mesh")
            addHistory("mesh", "Mesh 密钥已清除", HistoryLevel.SUCCESS)
            refreshMeshFeaturesImpl()
        } else {
            appendLog("清除 Mesh 密钥请求失败或超时。", tag = "mesh")
        }
        resetMeshRuntimeStateImpl()
    }
}

internal fun MainViewModel.handleMeshEventImpl(event: JSONObject) {
    when (event.optString("type")) {
        "mesh_status" -> {
            updateMeshStatusFromJsonImpl(event)
            event.optString("reason").takeIf { it.isNotBlank() }?.let { reason ->
                appendLog("Mesh 状态：${event.optString("role", "已禁用")} ($reason)。", tag = "mesh")
            }
        }

        "mesh_activation_result" -> {
            val role = event.optString("role", "disabled")
            val ok = event.optBoolean("ok", false)
            val sessionId = event.optLong("session_id", 0L).takeIf { it > 0L }
            _meshRole.value = role
            _meshActive.value = role == "master" || role == "client" || role == "candidate"
            _meshSessionId.value = sessionId
            if (ok) {
                appendLog("Mesh 激活完成：角色=$role" +
                    (sessionId?.let { "，会话=$it" } ?: ""), tag = "mesh")
            } else {
                val message = event.optString("reason", "election conflict")
                _meshLastError.value = message
                appendLog("Mesh 激活失败：$message", tag = "mesh")
            }
        }

        "mesh_heartbeat" -> {
            val nodeId = event.optString("node_id")
            if (nodeId.isBlank()) return
            val role = event.optString("role", "unknown")
            val sessionId = event.optLong("session_id", 0L).takeIf { it > 0L }
            val rssi = if (event.has("rssi")) event.optInt("rssi") else null
            val chip = event.optString("chip").takeIf { it.isNotBlank() }
            val now = System.currentTimeMillis()
            _meshNodes.update { current ->
                val existing = current.firstOrNull { it.nodeId == nodeId }
                (current.filterNot { it.nodeId == nodeId } + MeshNodeStatus(
                    nodeId = nodeId,
                    role = role,
                    sessionId = sessionId ?: existing?.sessionId,
                    rssi = rssi ?: existing?.rssi,
                    online = true,
                    lastSeenAtMs = now,
                    chip = chip ?: existing?.chip,
                    uptimeMs = existing?.uptimeMs,
                    heap = existing?.heap,
                    healthChannel = existing?.healthChannel,
                    healthSeq = existing?.healthSeq,
                    lastHealthAtMs = existing?.lastHealthAtMs ?: 0L,
                )).sortedByDescending { it.lastSeenAtMs }
            }
        }

        "mesh_node_joined" -> {
            val nodeId = event.optString("node_id")
            if (nodeId.isBlank()) return
            val sessionId = event.optLong("session_id", 0L).takeIf { it > 0L }
            val rssi = if (event.has("rssi")) event.optInt("rssi") else null
            val chip = event.optString("chip").takeIf { it.isNotBlank() }
            val now = System.currentTimeMillis()
            _meshNodes.update { current ->
                val existing = current.firstOrNull { it.nodeId == nodeId }
                (current.filterNot { it.nodeId == nodeId } + MeshNodeStatus(
                    nodeId = nodeId,
                    role = "client",
                    sessionId = sessionId ?: existing?.sessionId,
                    rssi = rssi ?: existing?.rssi,
                    online = true,
                    lastSeenAtMs = now,
                    chip = chip ?: existing?.chip,
                    uptimeMs = existing?.uptimeMs,
                    heap = existing?.heap,
                    healthChannel = existing?.healthChannel,
                    healthSeq = existing?.healthSeq,
                    lastHealthAtMs = existing?.lastHealthAtMs ?: 0L,
                )).sortedByDescending { it.lastSeenAtMs }
            }
            appendLog("Mesh 节点已加入：$nodeId", tag = "mesh")
        }

        "mesh_node_left" -> {
            val nodeId = event.optString("node_id")
            if (nodeId.isBlank()) return
            val reason = event.optString("reason", "timeout")
            _meshNodes.update { current ->
                current.map { node ->
                    if (node.nodeId == nodeId) node.copy(online = false) else node
                }
            }
            appendLog("Mesh 节点已离开：$nodeId ($reason)", tag = "mesh")
            refreshMeshStatusImpl()
        }

        "mesh_sensor_report" -> {
            val nodeId = event.optString("node_id")
            if (nodeId.isBlank()) return
            val kind = event.optString("kind")
            val role = event.optString("role", "client").ifBlank { "client" }
            val sessionId = event.optLong("session_id", 0L).takeIf { it > 0L }
            val rssi = if (event.has("rssi")) event.optInt("rssi") else null
            val chip = event.optString("chip").takeIf { it.isNotBlank() }
            val now = System.currentTimeMillis()

            _meshNodes.update { current ->
                val existing = current.firstOrNull { it.nodeId == nodeId }
                val base = existing ?: MeshNodeStatus(
                    nodeId = nodeId,
                    role = role,
                    sessionId = sessionId,
                    rssi = rssi,
                    online = true,
                    lastSeenAtMs = now,
                    chip = chip,
                )
                val updated = base.copy(
                    role = role,
                    sessionId = sessionId ?: base.sessionId,
                    rssi = rssi ?: base.rssi,
                    chip = chip ?: base.chip,
                    online = true,
                    lastSeenAtMs = now,
                    uptimeMs = if (event.has("uptime_ms")) {
                        event.optLong("uptime_ms")
                    } else {
                        base.uptimeMs
                    },
                    heap = if (event.has("heap")) event.optLong("heap") else base.heap,
                    healthChannel = if (event.has("channel")) {
                        event.optInt("channel").takeIf { it in 1..13 } ?: base.healthChannel
                    } else {
                        base.healthChannel
                    },
                    healthSeq = if (event.has("seq")) event.optLong("seq") else base.healthSeq,
                    lastHealthAtMs = if (kind == "node_health") now else base.lastHealthAtMs,
                )
                (current.filterNot { it.nodeId == nodeId } + updated)
                    .sortedByDescending { it.lastSeenAtMs }
            }
        }

        "mesh_error" -> {
            val message = "${event.optString("code", "mesh_error")}: ${event.optString("msg")}"
            _meshLastError.value = message
            appendLog("Mesh 错误：$message", tag = "mesh")
        }
    }
}

internal fun MainViewModel.resetMeshRuntimeStateImpl() {
    _meshActive.value = false
    _meshRole.value = "disabled"
    _meshSessionId.value = null
    _meshPeerCount.value = 0
    _meshNodes.value = emptyList()
    _meshProvisioning.value = false
}

internal fun MainViewModel.refreshMeshGlobalCredentialsImpl() {
    val credentials = runCatching { meshCredentialStore.loadGlobal() }
        .onFailure { error ->
            android.util.Log.w("NRSuite", "Mesh credential load failed during startup", error)
        }
        .getOrNull()
    meshGlobalCredentials = credentials
    _meshHasGlobalCredentials.value = credentials != null
    _meshGlobalKeyId.value = credentials?.keyId
}

private fun MainViewModel.persistGlobalCredentialsImpl(credentials: StoredMeshCredentials) {
    meshCredentialStore.saveGlobal(credentials)
    meshGlobalCredentials = credentials
    _meshHasGlobalCredentials.value = true
    _meshGlobalKeyId.value = credentials.keyId
}

private fun MainViewModel.removeGlobalCredentialsImpl() {
    meshCredentialStore.removeGlobal()
    meshGlobalCredentials = null
    _meshHasGlobalCredentials.value = false
    _meshGlobalKeyId.value = null
}

private fun MainViewModel.persistProvisionedCredentialsImpl(credentials: StoredMeshCredentials) {
    val nodeId = _meshNodeId.value.trim()
    if (nodeId.isNotBlank()) {
        meshCredentialStore.save(nodeId, credentials)
        pendingMeshCredentials = null
    } else {
        pendingMeshCredentials = credentials
    }
    meshStoredCredentials = credentials
    _meshHasStoredCredentials.value = true
    _meshStoredKeyId.value = credentials.keyId
}

private fun MainViewModel.refreshStoredCredentialsForNodeImpl() {
    val nodeId = _meshNodeId.value.trim()
    if (nodeId.isBlank()) {
        meshStoredCredentials = null
        _meshHasStoredCredentials.value = false
        _meshStoredKeyId.value = null
        return
    }

    val credentials = meshCredentialStore.load(nodeId)
    meshStoredCredentials = credentials
    _meshHasStoredCredentials.value = credentials != null
    _meshStoredKeyId.value = credentials?.keyId

    // Seed the app-wide saved mesh credentials when this is the first stored
    // node we encounter. This lets new nodes join the same mesh without
    // re-typing the passphrase, even for users upgrading from an earlier build.
    if (credentials != null && meshGlobalCredentials == null) {
        persistGlobalCredentialsImpl(credentials)
    }
}

private fun MainViewModel.removeStoredCredentialsForNodeImpl() {
    val nodeId = _meshNodeId.value.trim()
    if (nodeId.isNotBlank()) {
        meshCredentialStore.remove(nodeId)
    }
    pendingMeshCredentials = null
    meshStoredCredentials = null
    _meshHasStoredCredentials.value = false
    _meshStoredKeyId.value = null
}

private fun MainViewModel.refreshMeshFeaturesImpl() {
    val activeSession = session ?: return
    scope.launch {
        val status = activeSession.sendCommand("STATUS", timeoutMs = 5_000) ?: return@launch
        val features = mutableSetOf<String>()
        status.optJSONArray("features")?.let { array ->
            for (index in 0 until array.length()) {
                val value = array.optString(index)
                if (value.isNotBlank()) features += value
            }
        }

        val connected = _connectionState.value as? ConnectionState.Connected ?: return@launch
        val updated = connected.copy(features = features)
        _connectionState.value = updated
        activeDeviceFingerprint?.let { fingerprint ->
            _deviceConnectionStates.update { it + (fingerprint to updated) }
        }
    }
}

private fun MainViewModel.updateMeshStatusFromJsonImpl(json: JSONObject) {
    val initialized = json.optBoolean("initialized", _meshInitialized.value)
    _meshInitialized.value = initialized
    if (!initialized) {
        _meshPassphraseMatch.value = null
    }
    val role = json.optString("role", "disabled").ifBlank { "disabled" }
    _meshRole.value = role
    _meshActive.value = json.optBoolean(
        "active",
        role == "master" || role == "client" || role == "candidate" || role == "idle",
    )
    _meshSessionId.value = json.optLong("session_id", 0L).takeIf { it > 0L }
    _meshNodeId.value = json.optString("node_id", _meshNodeId.value).ifBlank { _meshNodeId.value }
    _meshChannel.value = json.optInt("channel", _meshChannel.value).coerceIn(1, 13)
    _meshPeerCount.value = json.optInt("peer_count", _meshPeerCount.value)

    json.optJSONArray("peers")?.let { peersArray ->
        _meshNodes.update { current ->
            val merged = current.associateBy { it.nodeId }.toMutableMap()
            for (index in 0 until peersArray.length()) {
                val peer = peersArray.optJSONObject(index) ?: continue
                val peerNodeId = peer.optString("node_id")
                if (peerNodeId.isBlank()) continue
                val existing = merged[peerNodeId]
                merged[peerNodeId] = MeshNodeStatus(
                    nodeId = peerNodeId,
                    role = peer.optString("role", "unknown"),
                    sessionId = peer.optLong("session_id", 0L).takeIf { it > 0L }
                        ?: existing?.sessionId,
                    rssi = peer.optInt("rssi", 0).takeIf { it != 0 } ?: existing?.rssi,
                    online = peer.optBoolean("online", true),
                    lastSeenAtMs = System.currentTimeMillis() -
                        peer.optLong("last_seen_ms", 0L).coerceAtLeast(0L),
                    chip = peer.optString("chip").takeIf { it.isNotBlank() }
                        ?: existing?.chip,
                    uptimeMs = existing?.uptimeMs,
                    heap = existing?.heap,
                    healthChannel = existing?.healthChannel,
                    healthSeq = existing?.healthSeq,
                    lastHealthAtMs = existing?.lastHealthAtMs ?: 0L,
                )
            }
            merged.values.sortedByDescending { it.lastSeenAtMs }
        }
    }

    if (_meshNodeId.value.isNotBlank()) {
        pendingMeshCredentials?.let { credentials ->
            meshCredentialStore.save(_meshNodeId.value, credentials)
            pendingMeshCredentials = null
            meshStoredCredentials = credentials
            _meshHasStoredCredentials.value = true
            _meshStoredKeyId.value = credentials.keyId
        }
        if (initialized) {
            refreshStoredCredentialsForNodeImpl()
        } else {
            removeStoredCredentialsForNodeImpl()
        }
    }
}
