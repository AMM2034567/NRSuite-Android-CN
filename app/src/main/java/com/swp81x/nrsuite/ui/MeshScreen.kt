package com.swp81x.nrsuite.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.mesh.MeshNodeStatus
import com.swp81x.nrsuite.ui.components.ChannelStepper
import com.swp81x.nrsuite.ui.components.NetworkStatusBadge
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.theme.CategoryBleBlue
import com.swp81x.nrsuite.ui.theme.CategoryMeshIndigo
import com.swp81x.nrsuite.ui.theme.NrAccent
import com.swp81x.nrsuite.ui.theme.NrBackground
import com.swp81x.nrsuite.ui.theme.NrOnSurface
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed
import java.util.UUID

enum class MeshTab { Setup, Network }

enum class MeshSetupStep { Connect, Passphrase, Activate }

enum class ActionTone { Positive, Warning, Neutral }

private fun currentMeshStep(connected: Boolean, initialized: Boolean): MeshSetupStep =
    when {
        !connected -> MeshSetupStep.Connect
        !initialized -> MeshSetupStep.Passphrase
        else -> MeshSetupStep.Activate
    }

private fun meshRoleColor(role: String): Color = when (role) {
    "master" -> StatusGreen
    "client" -> CategoryMeshIndigo
    "candidate" -> StatusAmber
    else -> StatusNeutral
}

private fun actionToneColor(tone: ActionTone): Color = when (tone) {
    ActionTone.Positive -> StatusGreen
    ActionTone.Warning -> StatusRed
    ActionTone.Neutral -> StatusAmber
}

@Composable
fun MeshScreen(
    connected: Boolean,
    initialized: Boolean,
    role: String,
    active: Boolean,
    sessionId: Long?,
    nodeId: String,
    channel: Int,
    passphrase: String,
    keyId: String,
    passphraseMatch: Boolean?,
    checkingPassphrase: Boolean,
    provisioning: Boolean,
    actionInProgress: Boolean,
    setupMessage: String?,
    lastError: String?,
    hasStoredCredentials: Boolean,
    storedKeyId: String?,
    hasGlobalCredentials: Boolean,
    globalKeyId: String?,
    nodes: List<MeshNodeStatus>,
    onRefresh: () -> Unit,
    onPassphraseChange: (String) -> Unit,
    onKeyIdChange: (String) -> Unit,
    onChannelChange: (Int) -> Unit,
    onCheckPassphrase: (String) -> Unit,
    onProvision: (passphrase: String, keyId: String?, channel: Int) -> Unit,
    onProvisionWithSavedCredentials: () -> Unit,
    onAuthenticateAndActivate: (String) -> Unit,
    onAuthenticateStored: () -> Unit,
    onDeactivate: () -> Unit,
    onApplyChannel: (Int) -> Unit,
    onForgetPassphrase: () -> Unit,
    onClearKeys: () -> Unit,
    onClearNodes: () -> Unit,
    onClearSetupMessage: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var confirmPassphrase by remember { mutableStateOf("") }
    var showPassphrase by remember { mutableStateOf(false) }
    var confirmReplace by remember { mutableStateOf(false) }
    var confirmClearKeys by remember { mutableStateOf(false) }
    var confirmForgetCredentials by remember { mutableStateOf(false) }
    var advancedExpanded by remember { mutableStateOf(false) }

    val tab = if (selectedTab == 0) MeshTab.Setup else MeshTab.Network
    val step = currentMeshStep(connected, initialized)
    val useSavedCredentials = initialized && hasStoredCredentials && passphrase.isBlank()
    val passphraseValid = passphrase.length >= 8
    val passphrasesMatch = confirmPassphrase.isNotBlank() && passphrase == confirmPassphrase
    val canProvision = connected && !provisioning && passphraseValid && passphrasesMatch
    val canCheck = connected && initialized && passphraseValid && !checkingPassphrase && !provisioning
    val sessionActive = role == "master" || role == "client" || role == "candidate"
    val busy = checkingPassphrase || provisioning || actionInProgress
    val canActivateStored = connected && initialized && useSavedCredentials && !provisioning && !sessionActive
    val canActivateWithPassphrase = connected && initialized && passphraseMatch == true &&
        passphraseValid && !provisioning && !sessionActive
    val canActivate = sessionActive || canActivateStored || canActivateWithPassphrase
    val fabEnabled = connected && !busy && canActivate
    val statusColor = meshRoleColor(role)
    val remoteNodes = remember(nodes, nodeId) {
        nodes.filterNot { it.nodeId.equals(nodeId, ignoreCase = true) }
    }

    val clipboard = LocalClipboardManager.current
    val statusSummary = remember(
        connected,
        initialized,
        role,
        sessionId,
        nodeId,
        sessionActive,
        remoteNodes,
    ) {
        buildString {
            appendLine("NRSuite Mesh")
            appendLine("connected=$connected")
            appendLine("initialized=$initialized")
            appendLine("role=$role")
            appendLine("active=$sessionActive")
            appendLine("node_id=${nodeId.ifBlank { "-" }}")
            appendLine("session_id=${sessionId ?: "-"}")
            appendLine("peer_count=${remoteNodes.count { it.online }}")
            if (remoteNodes.isNotEmpty()) {
                appendLine("nodes=")
                remoteNodes.forEach { node ->
                    appendLine(
                        "  ${node.nodeId} ${node.chip ?: "-"} ${node.role} " +
                            "online=${node.online} rssi=${node.rssi ?: "-"}"
                    )
                }
            }
        }.trimEnd()
    }

    fun generatePassphrase() {
        val generated = UUID.randomUUID().toString().replace("-", "")
        onPassphraseChange(generated)
        confirmPassphrase = generated
        showPassphrase = true
    }

    LaunchedEffect(connected) {
        if (connected) onRefresh()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("配置向导") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Mesh 拓扑网络") },
                )
            }

            Spacer(Modifier.height(10.dp))

            if (tab == MeshTab.Setup) {
                MeshSetupTab(
                    step = step,
                    connected = connected,
                    initialized = initialized,
                    active = sessionActive,
                    busy = busy,
                    channel = channel,
                    passphrase = passphrase,
                    keyId = keyId,
                    confirmPassphrase = confirmPassphrase,
                    onConfirmPassphraseChange = { confirmPassphrase = it },
                    showPassphrase = showPassphrase,
                    onToggleShowPassphrase = { showPassphrase = !showPassphrase },
                    passphraseValid = passphraseValid,
                    passphrasesMatch = passphrasesMatch,
                    passphraseMatch = passphraseMatch,
                    checkingPassphrase = checkingPassphrase,
                    provisioning = provisioning,
                    setupMessage = setupMessage,
                    hasStoredCredentials = hasStoredCredentials,
                    storedKeyId = storedKeyId,
                    hasGlobalCredentials = hasGlobalCredentials,
                    globalKeyId = globalKeyId,
                    useSavedCredentials = useSavedCredentials,
                    canProvision = canProvision,
                    canCheck = canCheck,
                    onPassphraseChange = onPassphraseChange,
                    onKeyIdChange = onKeyIdChange,
                    onChannelChange = onChannelChange,
                    onCheckPassphrase = onCheckPassphrase,
                    onProvision = onProvision,
                    onProvisionWithSavedCredentials = onProvisionWithSavedCredentials,
                    onGeneratePassphrase = { generatePassphrase() },
                    onReplaceKeys = { confirmReplace = true },
                    onRefresh = onRefresh,
                    onClearSetupMessage = onClearSetupMessage,
                )
            } else {
                MeshNetworkTab(
                    connected = connected,
                    initialized = initialized,
                    role = role,
                    sessionActive = sessionActive,
                    sessionId = sessionId,
                    nodeId = nodeId,
                    channel = channel,
                    busy = busy,
                    nodes = remoteNodes,
                    statusSummary = statusSummary,
                    onRefresh = onRefresh,
                    onClearNodes = onClearNodes,
                    onApplyChannel = onApplyChannel,
                    onCopyStatus = { clipboard.setText(AnnotatedString(statusSummary)) },
                    onSwitchToSetup = { selectedTab = 0 },
                )
            }

            if (tab == MeshTab.Setup) {
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { advancedExpanded = !advancedExpanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "高级设置",
                    style = MaterialTheme.typography.labelMedium,
                    color = NrOnSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (advancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = NrOnSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (advancedExpanded) {
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = NrSurface),
                    border = BorderStroke(0.5.dp, NrOutline),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "高风险操作区",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = StatusRed,
                        )
                        Text(
                            text = "清除设备密钥将彻底移除 ESP32 的 Mesh 组网能力。遗忘凭据将从本机删除加密备份。",
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = { confirmClearKeys = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = connected && initialized && !provisioning,
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("清除设备密钥")
                        }
                        OutlinedButton(
                            onClick = { confirmForgetCredentials = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = hasGlobalCredentials || hasStoredCredentials ||
                                passphrase.isNotBlank() || keyId.isNotBlank(),
                        ) {
                            Text("遗忘已存凭据")
                        }
                    }
                }
            }

            }

            if (!lastError.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = NrSurface),
                    border = BorderStroke(0.5.dp, NrOutline),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Mesh 组网错误",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = StatusRed,
                        )
                        SelectionContainer {
                            Text(
                                text = lastError,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = NrOnSurface,
                            )
                        }
                        TextButton(onClick = onClearError) {
                            Text("忽略")
                        }
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }

        FloatingActionButton(
            onClick = {
                if (!fabEnabled) return@FloatingActionButton
                when {
                    sessionActive -> onDeactivate()
                    useSavedCredentials -> onAuthenticateStored()
                    else -> onAuthenticateAndActivate(passphrase)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .alpha(if (fabEnabled) 1f else 0.4f),
            containerColor = if (sessionActive) StatusRed else NrAccent,
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = NrBackground,
                )
            } else {
                Icon(
                    imageVector = if (sessionActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (sessionActive) "停止 Mesh 组网" else "认证并启动 Mesh 组网",
                )
            }
        }
    }

    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("替换现有 Mesh 密钥？") },
            text = {
                Text(
                    "当前设备已完成入网烧录。替换其密钥后，在其他节点也烧录新密钥之前，" +
                        "该设备将从现有 Mesh 网络中脱离并无法与其他节点通信。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReplace = false
                        onProvision(passphrase, keyId.ifBlank { null }, channel)
                    },
                ) {
                    Text("替换密钥", color = StatusRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReplace = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (confirmClearKeys) {
        AlertDialog(
            onDismissRequest = { confirmClearKeys = false },
            title = { Text("清除 Mesh 密钥？") },
            text = {
                Text(
                    "此操作将从设备的 NVS 存储中彻底抹除派生密钥。该节点将恢复独立状态，" +
                        "直到使用相同或新的预共享密钥重新配置。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearKeys = false
                        onClearKeys()
                    },
                ) {
                    Text("清除密钥", color = StatusRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearKeys = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (confirmForgetCredentials) {
        AlertDialog(
            onDismissRequest = { confirmForgetCredentials = false },
            title = { Text("遗忘已存凭据？") },
            text = {
                Text(
                    "此操作将从本机删除加密保存的 Mesh 凭据。ESP32 仍会保留其 NVS 密钥，" +
                        "但下次认证该节点时需要手动重新输入预共享密钥。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmForgetCredentials = false
                        onForgetPassphrase()
                    },
                ) {
                    Text("遗忘凭据", color = StatusRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmForgetCredentials = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun MeshSetupTab(
    step: MeshSetupStep,
    connected: Boolean,
    initialized: Boolean,
    active: Boolean,
    busy: Boolean,
    channel: Int,
    passphrase: String,
    keyId: String,
    confirmPassphrase: String,
    onConfirmPassphraseChange: (String) -> Unit,
    showPassphrase: Boolean,
    onToggleShowPassphrase: () -> Unit,
    passphraseValid: Boolean,
    passphrasesMatch: Boolean,
    passphraseMatch: Boolean?,
    checkingPassphrase: Boolean,
    provisioning: Boolean,
    setupMessage: String?,
    hasStoredCredentials: Boolean,
    storedKeyId: String?,
    hasGlobalCredentials: Boolean,
    globalKeyId: String?,
    useSavedCredentials: Boolean,
    canProvision: Boolean,
    canCheck: Boolean,
    onPassphraseChange: (String) -> Unit,
    onKeyIdChange: (String) -> Unit,
    onChannelChange: (Int) -> Unit,
    onCheckPassphrase: (String) -> Unit,
    onProvision: (passphrase: String, keyId: String?, channel: Int) -> Unit,
    onProvisionWithSavedCredentials: () -> Unit,
    onGeneratePassphrase: () -> Unit,
    onReplaceKeys: () -> Unit,
    onRefresh: () -> Unit,
    onClearSetupMessage: () -> Unit,
) {
    MeshStepIndicator(step)

    Spacer(Modifier.height(10.dp))

    when (step) {
        MeshSetupStep.Connect -> MeshStepCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "请通过 USB 连接 NRSuite 硬件设备以继续。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NrOnSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        MeshSetupStep.Passphrase -> MeshStepCard {
            Text(
                text = "配置此节点设备",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = NrOnSurface,
            )

            if (hasGlobalCredentials) {
                SavedCredentialsCard(
                    keyLabel = globalKeyId,
                    onUse = onProvisionWithSavedCredentials,
                )
                MeshOrDivider()
            }

            MeshPassphraseFields(
                passphrase = passphrase,
                keyId = keyId,
                confirmPassphrase = confirmPassphrase,
                onConfirmPassphraseChange = onConfirmPassphraseChange,
                showPassphrase = showPassphrase,
                onToggleShowPassphrase = onToggleShowPassphrase,
                passphraseValid = passphraseValid,
                passphrasesMatch = passphrasesMatch,
                enabled = connected && !provisioning && !checkingPassphrase,
                onPassphraseChange = onPassphraseChange,
                onKeyIdChange = onKeyIdChange,
                onGeneratePassphrase = onGeneratePassphrase,
            )

            Text(
                text = "Mesh 射频信道",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = NrOnSurface,
            )
            ChannelStepper(
                value = channel,
                enabled = connected && !provisioning && !checkingPassphrase,
                onDecrease = { onChannelChange((channel - 1).coerceAtLeast(1)) },
                onIncrease = { onChannelChange((channel + 1).coerceAtMost(13)) },
            )
            if (channel >= 12) {
                Text(
                    text = "信道 12-13 受国家/地区法规限制，请仅在允许的合规区域使用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusAmber,
                )
            }

            Button(
                onClick = { onProvision(passphrase, keyId.ifBlank { null }, channel) },
                modifier = Modifier.fillMaxWidth(),
                enabled = canProvision,
                colors = ButtonDefaults.buttonColors(containerColor = NrAccent),
            ) {
                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(if (provisioning) "正在配置设备..." else "写入配置并烧录")
            }

            if (!setupMessage.isNullOrBlank()) {
                MeshSetupMessage(message = setupMessage)
            }
        }

        MeshSetupStep.Activate -> {
            if (busy) {
                MeshStepCard {
                    Text(
                        text = "正在识别并校验密钥...",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = StatusAmber,
                    )
                    Text(
                        text = "正在对比设备与本机保存/输入的预共享密钥...",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
            } else if (active) {
                MeshStepCard {
                    Text(
                        text = "组网已就绪",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = StatusGreen,
                    )
                    Text(
                        text = "切换至“Mesh 拓扑网络”标签页即可查看在线节点。",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
            } else {
                val tone: ActionTone
                val title: String
                val description: String
                val buttonLabel: String
                val buttonEnabled: Boolean
                val buttonAction: () -> Unit

                when {
                    useSavedCredentials -> {
                        tone = ActionTone.Positive
                        title = "已检测到本机保存的凭据"
                        description = "该节点无需重新输入密钥即可完成认证。点击右下角操作按钮启动 Mesh。"
                        buttonLabel = "刷新状态"
                        buttonEnabled = connected
                        buttonAction = onRefresh
                    }
                    passphraseMatch == true -> {
                        tone = ActionTone.Positive
                        title = "密钥校验一致"
                        description = "输入的预共享密钥与设备存储的密钥一致。点击右下角按钮启动 Mesh。"
                        buttonLabel = "刷新状态"
                        buttonEnabled = connected
                        buttonAction = onRefresh
                    }
                    passphraseMatch == false -> {
                        tone = ActionTone.Warning
                        title = "密钥不一致"
                        description = "此设备已使用不同的密钥完成过烧录。替换其密钥将导致其脱离当前的 Mesh 网络。"
                        buttonLabel = "替换设备密钥"
                        buttonEnabled = canProvision
                        buttonAction = onReplaceKeys
                    }
                    else -> {
                        tone = ActionTone.Neutral
                        title = "设备已烧录配置"
                        description = "请输入已烧录的预共享密钥并验证一致性，然后启动 Mesh。"
                        buttonLabel = "校验密钥"
                        buttonEnabled = canCheck
                        buttonAction = { onCheckPassphrase(passphrase) }
                    }
                }

                MeshActionCard(
                    title = title,
                    description = description,
                    tone = tone,
                    buttonLabel = buttonLabel,
                    buttonEnabled = buttonEnabled,
                    onClick = buttonAction,
                )

                if (!useSavedCredentials && passphraseMatch != true) {
                    Spacer(Modifier.height(8.dp))
                    MeshPassphraseFields(
                        passphrase = passphrase,
                        keyId = keyId,
                        confirmPassphrase = confirmPassphrase,
                        onConfirmPassphraseChange = onConfirmPassphraseChange,
                        showPassphrase = showPassphrase,
                        onToggleShowPassphrase = onToggleShowPassphrase,
                        passphraseValid = passphraseValid,
                        passphrasesMatch = passphrasesMatch,
                        enabled = connected && !provisioning && !checkingPassphrase,
                        onPassphraseChange = onPassphraseChange,
                        onKeyIdChange = onKeyIdChange,
                        onGeneratePassphrase = onGeneratePassphrase,
                    )
                }

                if (!setupMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    MeshSetupMessage(message = setupMessage)
                }
            }
        }
    }
}

@Composable
private fun MeshNetworkTab(
    connected: Boolean,
    initialized: Boolean,
    role: String,
    sessionActive: Boolean,
    sessionId: Long?,
    nodeId: String,
    channel: Int,
    busy: Boolean,
    nodes: List<MeshNodeStatus>,
    statusSummary: String,
    onRefresh: () -> Unit,
    onClearNodes: () -> Unit,
    onApplyChannel: (Int) -> Unit,
    onCopyStatus: () -> Unit,
    onSwitchToSetup: () -> Unit,
) {
    var selectedChannel by remember(channel) { mutableStateOf(channel) }
    var confirmChannelChange by remember { mutableStateOf(false) }
    if (!initialized) {
        MeshStepCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "此设备尚未配置 Mesh 组网",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = StatusAmber,
                )
                Text(
                    text = "请先配置并烧录预共享密钥。",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                OutlinedButton(
                    onClick = onSwitchToSetup,
                    enabled = connected,
                ) {
                    Text("前往配置向导")
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val localColor = meshRoleColor(role)
            StatusIndicator(
                label = nodeId.ifBlank { "-" },
                subtitle = if (sessionActive) {
                    "运行中 · 会话 #${sessionId ?: "-"}"
                } else {
                    "未运行"
                },
                color = localColor,
                trailingBadge = when (role.lowercase()) {
                    "master" -> "主节点"
                    "client" -> "子节点"
                    "candidate" -> "候选节点"
                    else -> role.takeIf { it in setOf("master", "client", "candidate") }?.uppercase()
                },
                trailingBadgeColor = localColor,
                monospace = true,
            )
            Text(
                text = "Mesh 网络控制",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = NrOnSurface,
            )
            Text(
                text = "主从角色分配与组网启停由应用与设备间的主节点选举机制动态协同管理。",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Mesh 通信信道",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = NrOnSurface,
            )
            if (role == "master") {
                ChannelStepper(
                    value = selectedChannel,
                    enabled = connected && !busy,
                    onDecrease = { selectedChannel = (selectedChannel - 1).coerceAtLeast(1) },
                    onIncrease = { selectedChannel = (selectedChannel + 1).coerceAtMost(13) },
                )
                if (selectedChannel != channel) {
                    OutlinedButton(
                        onClick = { confirmChannelChange = true },
                        enabled = connected && !busy,
                    ) {
                        Text("应用信道 $selectedChannel")
                    }
                }
            } else {
                Text(
                    text = channel.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = NrOnSurface,
                )
            }
            if (selectedChannel >= 12) {
                Text(
                    text = "信道 12-13 受国家/地区法规限制，请仅在允许的合规区域使用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusAmber,
                )
            }
        }
    }

    Spacer(Modifier.height(10.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "远端节点 (${nodes.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = NrOnSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onRefresh,
                    enabled = connected && !busy,
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新 Mesh 状态")
                }
            }

            if (nodes.isEmpty()) {
                MeshEmptyNodesState()
            } else {
                nodes.forEach { node ->
                    MeshNodeRow(node)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    onClick = onCopyStatus,
                    enabled = nodes.isNotEmpty(),
                ) {
                    Text("复制网络拓扑")
                }
                TextButton(
                    onClick = onClearNodes,
                    enabled = nodes.isNotEmpty(),
                ) {
                    Text("清空节点列表")
                }
            }
        }
    }

    if (confirmChannelChange) {
        AlertDialog(
            onDismissRequest = { confirmChannelChange = false },
            title = { Text("更改 Mesh 信道？") },
            text = {
                Text(
                    "网络中的所有节点都将收到切换到信道 $selectedChannel 的指令。" +
                        "任何错过同步指令的节点将通过信道扫描自动重新寻找主节点以恢复连接。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmChannelChange = false
                        onApplyChannel(selectedChannel)
                    },
                ) {
                    Text("确认更改")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmChannelChange = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun MeshStepIndicator(current: MeshSetupStep) {
    val steps = MeshSetupStep.entries
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        steps.forEachIndexed { index, step ->
            val done = index < current.ordinal
            val active = step == current
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        color = when {
                            done -> StatusGreen
                            active -> CategoryMeshIndigo
                            else -> Color.Transparent
                        },
                        shape = CircleShape,
                    )
                    .then(
                        if (!done && !active) {
                            Modifier.border(1.dp, NrOutline, CircleShape)
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "已完成步骤",
                        tint = NrBackground,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (active) NrBackground else NrOnSurfaceVariant,
                    )
                }
            }
            if (index < steps.lastIndex) {
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .weight(1f)
                        .background(NrOutline),
                )
            }
        }
    }
}

@Composable
private fun MeshStepCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun SavedCredentialsCard(keyLabel: String?, onUse: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "已存 Mesh 凭据",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = NrOnSurface,
            )
            Text(
                text = "直接使用本机保存的加密凭据" +
                    (keyLabel?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "") +
                    " 配置此新节点，无需重新手动输入密钥。",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
            Button(
                onClick = onUse,
                colors = ButtonDefaults.buttonColors(containerColor = NrAccent),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("使用已存凭据配置")
            }
        }
    }
}

@Composable
private fun MeshPassphraseFields(
    passphrase: String,
    keyId: String,
    confirmPassphrase: String,
    onConfirmPassphraseChange: (String) -> Unit,
    showPassphrase: Boolean,
    onToggleShowPassphrase: () -> Unit,
    passphraseValid: Boolean,
    passphrasesMatch: Boolean,
    enabled: Boolean,
    onPassphraseChange: (String) -> Unit,
    onKeyIdChange: (String) -> Unit,
    onGeneratePassphrase: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = passphrase,
            onValueChange = onPassphraseChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
            isError = passphrase.isNotBlank() && !passphraseValid,
            label = { Text("Mesh 预共享密钥 (Passphrase)") },
            supportingText = {
                if (passphrase.isNotBlank() && !passphraseValid) {
                    Text("密钥长度至少为 8 位字符。", color = StatusRed)
                }
            },
            visualTransformation = if (showPassphrase) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (passphrase.isBlank()) {
                OutlinedButton(
                    onClick = onGeneratePassphrase,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("随机生成密钥")
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            OutlinedButton(
                onClick = onToggleShowPassphrase,
                enabled = passphrase.isNotEmpty(),
            ) {
                Text(if (showPassphrase) "隐藏" else "显示")
            }
        }
        OutlinedTextField(
            value = confirmPassphrase,
            onValueChange = onConfirmPassphraseChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
            isError = confirmPassphrase.isNotBlank() && !passphrasesMatch,
            label = { Text("确认预共享密钥") },
            supportingText = {
                if (confirmPassphrase.isNotBlank() && !passphrasesMatch) {
                    Text("两次输入的密钥不一致。", color = StatusRed)
                }
            },
            visualTransformation = if (showPassphrase) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
        )
        OutlinedTextField(
            value = keyId,
            onValueChange = onKeyIdChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
            label = { Text("密钥备注名称 / 标识 (可选)") },
        )
    }
}

@Composable
private fun MeshOrDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .height(1.dp)
                .weight(1f)
                .background(NrOutline),
        )
        Text(
            text = "或手动输入配置",
            style = MaterialTheme.typography.labelSmall,
            color = NrOnSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Box(
            modifier = Modifier
                .height(1.dp)
                .weight(1f)
                .background(NrOutline),
        )
    }
}

@Composable
private fun MeshActionCard(
    title: String,
    description: String,
    tone: ActionTone,
    buttonLabel: String,
    buttonEnabled: Boolean,
    onClick: () -> Unit,
) {
    val toneColor = actionToneColor(tone)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = toneColor,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
            Button(
                onClick = onClick,
                enabled = buttonEnabled,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = toneColor),
            ) {
                Text(buttonLabel)
            }
        }
    }
}

@Composable
private fun MeshSetupMessage(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = NrOnSurface,
            modifier = Modifier.padding(10.dp),
        )
    }
}

@Composable
private fun MeshEmptyNodesState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Default.SettingsInputAntenna,
            contentDescription = null,
            tint = NrOnSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = "暂无远端节点",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = NrOnSurface,
        )
        Text(
            text = "烧录了相同密钥的节点加入 Mesh 拓扑网络后将在此显示。",
            style = MaterialTheme.typography.bodySmall,
            color = NrOnSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MeshNodeRow(node: MeshNodeStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (node.online) StatusGreen else StatusNeutral, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = buildString {
                        append(node.nodeId)
                        node.chip?.takeIf { it.isNotBlank() }?.let { chip ->
                            append(" - ")
                            append(chip)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = NrOnSurface,
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NetworkStatusBadge(
                        text = when (node.role.lowercase()) {
                            "master" -> "主节点"
                            "client" -> "子节点"
                            "candidate" -> "候选节点"
                            else -> node.role.ifBlank { "未知" }
                        },
                        color = meshRoleColor(node.role),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = node.rssi?.takeIf { it != 0 }?.let { "$it dBm" } ?: "RSSI -",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = NrOnSurfaceVariant,
                    )
                }
                val healthText = buildString {
                    node.heap?.let {
                        append("内存 ")
                        append(it / 1024)
                        append(" KB")
                    }
                    node.uptimeMs?.let {
                        if (isNotEmpty()) append(" · ")
                        append("在线 ")
                        append(it / 1000)
                        append(" 秒")
                    }
                    node.healthChannel?.let {
                        if (isNotEmpty()) append(" · ")
                        append("信道 ")
                        append(it)
                    }
                    node.healthSeq?.let {
                        if (isNotEmpty()) append(" · ")
                        append("序号 ")
                        append(it)
                    }
                }
                if (healthText.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = healthText,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = NrOnSurfaceVariant,
                    )
                }
            }
            Text(
                text = if (node.online) "在线" else "离线",
                style = MaterialTheme.typography.labelSmall,
                color = if (node.online) StatusGreen else StatusNeutral,
            )
        }
    }
}
