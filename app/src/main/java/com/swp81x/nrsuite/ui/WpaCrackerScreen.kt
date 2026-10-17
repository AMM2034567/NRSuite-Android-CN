package com.swp81x.nrsuite.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.credentials.CredentialSession
import com.swp81x.nrsuite.core.wifi.DetectedSsid
import com.swp81x.nrsuite.ui.components.NrFilterChip
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.util.copyWithToast
import com.swp81x.nrsuite.ui.theme.NrOnSurface
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusRed
import java.io.File

@Composable
fun WpaCrackerScreen(
    sessions: List<CredentialSession>,
    selectedSession: CredentialSession?,
    customPcapName: String?,
    customPcapValid: Boolean,
    customPcapValidating: Boolean,
    customPcapMessage: String,
    customSsidOptions: List<DetectedSsid>,
    customSsidSelected: String?,
    customSsidManualEnabled: Boolean,
    customSsid: String,
    wordlistName: String?,
    running: Boolean,
    tested: Long,
    speed: Double,
    result: String?,
    status: String,
    onSelectSession: (CredentialSession?) -> Unit,
    onChooseCustomPcap: () -> Unit,
    onClearCustomPcap: () -> Unit,
    onSelectCustomSsid: (String) -> Unit,
    onUseManualCustomSsid: () -> Unit,
    onCustomSsidChange: (String) -> Unit,
    onChooseWordlist: () -> Unit,
    onClearWordlist: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val candidates = sessions.filter { path ->
        !path.pcapPath.isNullOrBlank() && File(path.pcapPath!!).exists()
    }
    val customSelected = customPcapName != null
    val canStart = !running && wordlistName != null && (
        if (customSelected) customPcapValid && customSsid.isNotBlank()
        else selectedSession != null
    )

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatusIndicator(
                label = when {
                    running -> "正在破解..."
                    result != null -> "密码已破解"
                    else -> "WPA/WPA2 离线字典破解"
                },
                color = when {
                    running -> StatusAmber
                    result != null -> StatusGreen
                    else -> NrOnSurfaceVariant
                },
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NrSurface),
                border = BorderStroke(0.5.dp, NrOutline),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "目标握手包",
                                fontWeight = FontWeight.SemiBold,
                                color = NrOnSurface,
                            )
                            Text(
                                text = when {
                                    selectedSession != null -> {
                                        "${selectedSession.ssid.ifBlank { "(隐藏 SSID)" }} · " +
                                            "${selectedSession.bssid.ifBlank { "未知 BSSID" }}"
                                    }
                                    customSelected -> "自定义 PCAP: ${customPcapName ?: "文件"}"
                                    else -> "选择已保存会话或加载自定义 PCAP 抓包文件"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = NrOnSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onChooseCustomPcap, enabled = !running) {
                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (customSelected) "更换自定义 PCAP" else "选择自定义 PCAP")
                        }
                        if (customSelected) {
                            OutlinedButton(onClick = onClearCustomPcap, enabled = !running) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("清除")
                            }
                        }
                    }

                    if (customSelected) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = customPcapName.orEmpty(),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = NrOnSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        if (customPcapValidating) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "正在校验 EAPOL 握手包...",
                                style = MaterialTheme.typography.bodySmall,
                                color = NrOnSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = customPcapMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (customPcapValid) StatusGreen else StatusAmber,
                            )
                        }
                        Spacer(Modifier.height(8.dp))

                        if (customSsidOptions.isNotEmpty()) {
                            Text(
                                text = "已识别出的 SSID",
                                style = MaterialTheme.typography.labelMedium,
                                color = NrOnSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                customSsidOptions.forEach { option ->
                                    NrFilterChip(
                                        selected = !customSsidManualEnabled &&
                                            customSsidSelected == option.ssid,
                                        onClick = { onSelectCustomSsid(option.ssid) },
                                        label = option.ssid,
                                        enabled = !running,
                                    )
                                }
                                NrFilterChip(
                                    selected = customSsidManualEnabled,
                                    onClick = onUseManualCustomSsid,
                                    label = "手动输入",
                                    enabled = !running,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        OutlinedTextField(
                            value = customSsid,
                            onValueChange = onCustomSsidChange,
                            label = { Text(if (customSsidOptions.isEmpty()) "目标 SSID" else "所选 SSID") },
                            placeholder = { Text("无线网络名称") },
                            singleLine = true,
                            enabled = !running && customSsidManualEnabled,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                    }

                    Text(
                        text = "已存会话",
                        style = MaterialTheme.typography.labelMedium,
                        color = NrOnSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))

                    if (candidates.isEmpty()) {
                        Text(
                            text = "暂无包含 PCAP 抓包的已存会话。请先运行恶魔双胞胎或抓包模块捕获握手包。",
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                    } else {
                        candidates.forEach { session ->
                            val selected = session.id == selectedSession?.id
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable(enabled = !running) { onSelectSession(session) },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selected) NrSurfaceVariant else NrSurface,
                                ),
                                border = BorderStroke(
                                    width = if (selected) 1.dp else 0.5.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else NrOutline,
                                ),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(
                                        text = session.ssid.ifBlank { "(隐藏 SSID)" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = NrOnSurface,
                                    )
                                    Text(
                                        text = session.bssid,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = NrOnSurfaceVariant,
                                    )
                                    Text(
                                        text = session.pcapPath?.substringAfterLast('/') ?: "无 PCAP",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = NrOnSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NrSurface),
                border = BorderStroke(0.5.dp, NrOutline),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        text = "字典文件 (Wordlist)",
                        fontWeight = FontWeight.SemiBold,
                        color = NrOnSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = wordlistName ?: "未选择字典文件",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = NrOnSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onChooseWordlist, enabled = !running) {
                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (wordlistName == null) "选择字典" else "更换字典")
                        }
                        if (wordlistName != null) {
                            OutlinedButton(onClick = onClearWordlist, enabled = !running) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("清除")
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NrSurface),
                border = BorderStroke(0.5.dp, NrOutline),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        text = "破解结果",
                        fontWeight = FontWeight.SemiBold,
                        color = NrOnSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (result != null) StatusGreen else NrOnSurfaceVariant,
                    )

                    if (running || tested > 0L) {
                        Spacer(Modifier.height(10.dp))
                        if (running) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(6.dp))
                        }
                        Text(
                            text = "已测试 $tested 个候选密码  ·  ${"%.1f".format(speed)} 次/秒",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = NrOnSurfaceVariant,
                        )
                    }

                    result?.let { password ->
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = password,
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                                color = StatusGreen,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = {
                                clipboard.copyWithToast(context, password, "密码已复制")
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "复制破解出的密码",
                                    tint = NrOnSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }

        ModuleActionFab(
            onClick = { if (running) onStop() else onStart() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .alpha(if (running || canStart) 1f else 0.4f),
            containerColor = if (running) StatusRed else MaterialTheme.colorScheme.primary,
            contentColor = if (running) NrOnSurface else NrSurface,
        ) {
            Icon(
                imageVector = if (running) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (running) "停止破解" else "开始破解",
            )
        }
    }
}
