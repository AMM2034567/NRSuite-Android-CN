package com.swp81x.nrsuite.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Wifi
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swp81x.nrsuite.core.eapol.EapolHandshake
import com.swp81x.nrsuite.core.wpa.EvilTwinResult
import com.swp81x.nrsuite.ui.components.HtmlUploadSection
import com.swp81x.nrsuite.ui.components.NetworkTargetRow
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.util.copyWithToast
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusRed
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import org.json.JSONObject

@Composable
fun EvilTwinScreen(
    connected: Boolean,
    running: Boolean,
    scanning: Boolean,
    networks: List<JSONObject>,
    handshake: EapolHandshake,
    results: List<EvilTwinResult>,
    eventLog: List<String>,
    selectedHtmlName: String?,
    htmlUploading: Boolean,
    htmlProgress: Int,
    htmlComplete: Boolean,
    onClearEventLog: () -> Unit,
    onScanWifi: () -> Unit,
    onChooseHtml: () -> Unit,
    onClearHtml: () -> Unit,
    onStart: (ssid: String, channel: Int, targetBssid: String) -> Unit,
    onStop: () -> Unit,
    onClearPasswords: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var ssid by rememberSaveable { mutableStateOf("Free WiFi") }
    var channel by rememberSaveable { mutableStateOf(6) }
    var targetBssid by rememberSaveable { mutableStateOf("") }
    var confirmStart by rememberSaveable { mutableStateOf(false) }
    var confirmStop by rememberSaveable { mutableStateOf(false) }
    var targetExpanded by rememberSaveable { mutableStateOf(true) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(running) {
        if (running) targetExpanded = false
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("概览") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("日志 (${eventLog.size})") })
        }

        if (selectedTab == 0) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    text = "恶魔双胞胎 (Evil Twin)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "钓鱼门户 + Deauth 干扰 + EAPOL 握手包捕获工作流",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                StatusIndicator(
                    label = when {
                        !connected -> "未连接设备"
                        running -> "恶魔双胞胎运行中"
                        else -> "就绪"
                    },
                    color = when {
                        !connected -> StatusNeutral
                        running -> StatusGreen
                        else -> StatusNeutral
                    },
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EapolBadge("M1", handshake.m1)
                    EapolBadge("M2", handshake.m2)
                    EapolBadge("M3", handshake.m3)
                    EapolBadge("M4", handshake.m4)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("目标网络", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = targetBssid.ifBlank { "请扫描并选择热点 AP" },
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = NrOnSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { targetExpanded = !targetExpanded }) {
                        Icon(
                            imageVector = if (targetExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (targetExpanded) "折叠目标选择" else "展开目标选择",
                        )
                    }
                }
                if (targetExpanded && !running) {
                    OutlinedButton(
                        onClick = onScanWifi,
                        enabled = connected && !scanning,
                    ) {
                        Text(if (scanning) "正在扫描..." else "扫描周围 WiFi 目标")
                    }
                    if (networks.isEmpty() && !scanning) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "点击“扫描周围 WiFi 目标”以发现附近的热点。",
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                    }
                    if (networks.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = NrSurfaceVariant,
                                    shape = RoundedCornerShape(10.dp),
                                 )
                                .padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            networks.forEach { network ->
                                val bssidValue = network.optString("bssid")
                                NetworkTargetRow(
                                    ssid = network.optString("ssid").ifBlank { "(隐藏)" },
                                    bssid = bssidValue,
                                    channel = network.optInt("channel", 1),
                                    rssi = network.optInt("rssi", -100),
                                    security = network.optString("security", "?"),
                                    wps = network.optBoolean("wps"),
                                    vendor = network.optString("vendor").takeIf { it.isNotBlank() },
                                    ouiWhitelisted = network.optBoolean("oui_whitelisted"),
                                    ouiBlacklisted = network.optBoolean("oui_blacklisted"),
                                    selected = targetBssid.equals(bssidValue, ignoreCase = true),
                                    enabled = true,
                                    onClick = {
                                        targetBssid = bssidValue
                                        channel = network.optInt("channel", channel)
                                        val apSsid = network.optString("ssid")
                                        if (apSsid.isNotBlank()) ssid = apSsid
                                    },
                                )
                            }
                        }
                    }
                }
                if (targetExpanded) {
                    Spacer(Modifier.height(8.dp))
                    HtmlUploadSection(
                        selectedName = selectedHtmlName,
                        uploading = htmlUploading,
                        progress = htmlProgress,
                        completed = htmlComplete,
                        required = true,
                        enabled = !running,
                        onChoose = onChooseHtml,
                        onClear = onClearHtml,
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "已捕获密码 (${results.size})",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            clipboard.copyWithToast(context, results.joinToString("\n") { it.password }, "密码已复制")
                        },
                        enabled = results.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "复制所有密码")
                    }
                    IconButton(onClick = onClearPasswords, enabled = results.isNotEmpty()) {
                        Icon(Icons.Default.Delete, contentDescription = "清空密码")
                    }
                }
                if (results.isEmpty()) {
                    Text(
                        text = "暂未捕获到密码。",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                } else {
                    results.reversed().forEach { result ->
                        val statusSymbol = when (result.status) {
                            EvilTwinResult.Status.CORRECT -> "✓"
                            EvilTwinResult.Status.INCORRECT -> "✗"
                            EvilTwinResult.Status.PENDING -> "–"
                            EvilTwinResult.Status.INVALID_LENGTH -> "!"
                        }
                        val statusColor = when (result.status) {
                            EvilTwinResult.Status.CORRECT -> StatusGreen
                            EvilTwinResult.Status.INCORRECT -> MaterialTheme.colorScheme.error
                            EvilTwinResult.Status.PENDING -> Color(0xFF3D9BFF)
                            EvilTwinResult.Status.INVALID_LENGTH -> StatusAmber
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "[${result.timestamp}] ${result.password}  :  $statusSymbol",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = statusColor,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { clipboard.copyWithToast(context, result.password, "密码已复制") },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "复制密码",
                                    tint = NrOnSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                val verificationText = when {
                    results.any { it.status == EvilTwinResult.Status.CORRECT } ->
                        "已提交密码与捕获的 WPA2 握手包匹配成功。"
                    results.any { it.status == EvilTwinResult.Status.INCORRECT } ->
                        "已根据捕获的 WPA2 握手包对提交的密码进行了校验。"
                    results.any { it.status == EvilTwinResult.Status.PENDING } ->
                        "等待捕获匹配 BSSID/STA 的完整 M1 + M2 握手包后再进行校验。"
                    results.any { it.status == EvilTwinResult.Status.INVALID_LENGTH } ->
                        "提交的一个或多个密码长度不符合 WPA2 规范 (8–63 字符)。"
                    else ->
                        "当用户提交密码且捕获到匹配的 M1 + M2 握手包时，系统将自动进行 WPA2 校验。"
                }
                Text(
                    text = verificationText,
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        results.any { it.status == EvilTwinResult.Status.CORRECT } -> StatusGreen
                        results.any { it.status == EvilTwinResult.Status.INCORRECT } -> MaterialTheme.colorScheme.error
                        results.any { it.status == EvilTwinResult.Status.PENDING } -> StatusAmber
                        results.any { it.status == EvilTwinResult.Status.INVALID_LENGTH } -> StatusAmber
                        else -> NrOnSurfaceVariant
                    },
                )
            }
        }
        } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "恶魔双胞胎日志 (${eventLog.size})",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            clipboard.copyWithToast(context, eventLog.joinToString("\n"), "日志已复制")
                        },
                        enabled = eventLog.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "复制日志")
                    }
                    IconButton(onClick = onClearEventLog, enabled = eventLog.isNotEmpty()) {
                        Icon(Icons.Default.Delete, contentDescription = "清空日志")
                    }
                }
                if (eventLog.isEmpty()) {
                    Text(
                        text = "门户页面访问、客户端连接及 POST 提交数据将显示在此处。",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                } else {
                    eventLog.reversed().forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = NrOnSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }
        }

        if (confirmStart) {
            AlertDialog(
                onDismissRequest = { confirmStart = false },
                title = { Text("启动恶魔双胞胎 (Evil Twin)？") },
                text = {
                    Text(
                        "将启动仿冒热点 AP，并向选定目标发送 Deauth 解除认证干扰，" +
                            "同时捕获客户端提交的密码与 EAPOL 握手包。\n\n" +
                            "SSID: $ssid\n" +
                            "信道: $channel\n" +
                            "目标: ${targetBssid.ifBlank { "未选择" }}\n\n" +
                            "请仅在您拥有或获得授权测试的网络上使用。",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmStart = false
                            onStart(ssid.trim(), channel, targetBssid.trim())
                        },
                    ) { Text("启动") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmStart = false }) { Text("取消") }
                },
            )
        }

        if (confirmStop) {
            AlertDialog(
                onDismissRequest = { confirmStop = false },
                title = { Text("停止恶魔双胞胎？") },
                text = { Text("将停止认证门户、Deauth 解除认证工作流以及 EAPOL 捕获。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmStop = false
                            onStop()
                        },
                    ) { Text("停止") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmStop = false }) { Text("取消") }
                },
            )
        }

        Spacer(Modifier.height(80.dp))
        }

        if (connected) {
            ModuleActionFab(
                onClick = {
                    when {
                        running -> confirmStop = true
                        targetBssid.isBlank() -> Toast.makeText(
                            context,
                            "请先选择目标网络。",
                            Toast.LENGTH_SHORT,
                        ).show()
                        selectedHtmlName == null -> Toast.makeText(
                            context,
                            "请先选择 HTML 文件。",
                            Toast.LENGTH_SHORT,
                        ).show()
                        htmlUploading -> Toast.makeText(
                            context,
                            "请等待 HTML 上传完成。",
                            Toast.LENGTH_SHORT,
                        ).show()
                        else -> confirmStart = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = if (running) StatusRed else MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = if (running) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (running) "停止恶魔双胞胎" else "启动恶魔双胞胎",
                )
            }
        }
    }
}


@Composable
private fun EapolBadge(label: String, captured: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (captured) StatusGreen.copy(alpha = 0.16f) else NrSurfaceVariant,
        ),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(0.5.dp, if (captured) StatusGreen else NrOutline),
    ) {
        Text(
            text = if (captured) "$label ✓" else label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = if (captured) StatusGreen else NrOnSurfaceVariant,
        )
    }
}
