package com.swp81x.nrsuite.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.defense.HiddenApObservation
import com.swp81x.nrsuite.core.defense.HiddenSsidCandidate
import com.swp81x.nrsuite.ui.components.ChannelModeToggle
import com.swp81x.nrsuite.ui.components.ChannelStepper
import com.swp81x.nrsuite.ui.components.NrFilterChip
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed
import com.swp81x.nrsuite.ui.util.copyWithToast
import com.swp81x.nrsuite.ui.util.rssiToProximity
import com.swp81x.nrsuite.ui.util.threatProximityColor

@Composable
fun HiddenApScreen(
    connected: Boolean,
    running: Boolean,
    starting: Boolean,
    fixed: Boolean,
    channel: Int,
    currentHopChannel: Int?,
    observations: List<HiddenApObservation>,
    candidates: List<HiddenSsidCandidate>,
    deauthEnabled: Boolean,
    onFixedChange: (Boolean) -> Unit,
    onChannelChange: (Int) -> Unit,
    onDeauthToggle: (Boolean) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onForceReconnect: (HiddenApObservation) -> Unit,
    onClearCandidates: () -> Unit,
    onClearObservations: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var configExpanded by remember { mutableStateOf(true) }
    var confirmStart by remember { mutableStateOf(false) }

    LaunchedEffect(running) {
        if (running) configExpanded = false
    }

    val canStart = connected && !starting
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            HiddenApStatusCard(
                connected = connected,
                running = running,
                starting = starting,
                fixed = fixed,
                channel = channel,
                currentHopChannel = currentHopChannel,
            )

            Spacer(Modifier.height(10.dp))

            HiddenApConfigCard(
                running = running || starting,
                expanded = configExpanded,
                fixed = fixed,
                channel = channel,
                deauthEnabled = deauthEnabled,
                onToggle = { configExpanded = !configExpanded },
                onFixedChange = onFixedChange,
                onChannelChange = onChannelChange,
                onDeauthToggle = onDeauthToggle,
            )

            Spacer(Modifier.height(10.dp))

            HiddenApCandidateCard(
                candidates = candidates,
                running = running || starting,
                onClearCandidates = onClearCandidates,
            )

            Spacer(Modifier.height(10.dp))

            HiddenApResultsCard(
                observations = observations,
                running = running || starting,
                deauthEnabled = deauthEnabled,
                onForceReconnect = onForceReconnect,
                onClearObservations = onClearObservations,
            )

            Spacer(Modifier.height(80.dp))
        }

        if (connected || starting) {
            ModuleActionFab(
                onClick = {
                    when {
                        starting -> Unit
                        running -> onStop()
                        canStart -> confirmStart = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .alpha(if (!canStart && !running && !starting) 0.4f else 1f),
                containerColor = when {
                    running -> StatusRed
                    starting -> StatusAmber
                    else -> MaterialTheme.colorScheme.primary
                },
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                if (starting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = if (running) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                    )
                }
            }
        }
    }

    if (confirmStart) {
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text("启动隐藏 AP 探测？") },
            text = {
                Text(
                    "将首先执行一次基线扫描，随后开始被动监控 Beacon、Probe、关联 (Association) 及重关联 (Reassociation) 数据帧。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmStart = false
                        onStart()
                    },
                ) { Text("启动") }
            },
            dismissButton = {
                TextButton(onClick = { confirmStart = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun HiddenApStatusCard(
    connected: Boolean,
    running: Boolean,
    starting: Boolean,
    fixed: Boolean,
    channel: Int,
    currentHopChannel: Int?,
) {
    val color = when {
        !connected -> StatusNeutral
        starting -> StatusAmber
        running -> StatusGreen
        else -> StatusAmber
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, if (running) MaterialTheme.colorScheme.primary else NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.VisibilityOff,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = when {
                            !connected -> "未连接"
                            starting -> "正在扫描周围..."
                            running -> "正在监控"
                            else -> "就绪"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = when {
                            !connected -> "启动隐藏 AP 探测前请先连接设备。"
                            starting -> "被动探测前正在运行基线扫描"
                            running -> buildString {
                                append(if (fixed) "固定信道 $channel" else "跳频模式")
                                currentHopChannel?.let { append(" · 当前信道 $it") }
                            }
                            else -> "被动隐藏 AP 探测"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HiddenApConfigCard(
    running: Boolean,
    expanded: Boolean,
    fixed: Boolean,
    channel: Int,
    deauthEnabled: Boolean,
    onToggle: () -> Unit,
    onFixedChange: (Boolean) -> Unit,
    onChannelChange: (Int) -> Unit,
    onDeauthToggle: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.VisibilityOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "隐藏 AP 探测配置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "被动扫描与可选的重连诱导触发",
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
                Spacer(Modifier.height(10.dp))
                ChannelModeToggle(
                    fixed = fixed,
                    enabled = !running,
                    onFixedChange = onFixedChange,
                )

                if (fixed) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "信道",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    ChannelStepper(
                        value = channel,
                        min = 1,
                        max = 14,
                        enabled = !running,
                        onDecrease = { onChannelChange((channel - 1).coerceAtLeast(1)) },
                        onIncrease = { onChannelChange((channel + 1).coerceAtMost(14)) },
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Deauth 重连诱导",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "允许向隐藏 BSSID 发送广播 Deauth 包。仅限授权网络使用。",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusAmber,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = deauthEnabled,
                        onCheckedChange = onDeauthToggle,
                    )
                }
            }
        }
    }
}

@Composable
private fun HiddenApCandidateCard(
    candidates: List<HiddenSsidCandidate>,
    running: Boolean,
    onClearCandidates: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "隐藏 SSID 候选列表",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (candidates.isEmpty()) {
                            "探测请求中暂未发现候选 SSID"
                        } else {
                            "在探测请求中发现 ${candidates.size} 个候选 SSID"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
                if (candidates.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onClearCandidates,
                        enabled = !running,
                    ) {
                        Text("清空")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            if (candidates.isEmpty()) {
                Text(
                    text = "暂无 Probe 请求候选。",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(candidates, key = { it.ssid }) { candidate ->
                        HiddenSsidCandidateRow(candidate)
                    }
                }
            }
        }
    }
}

@Composable
private fun HiddenSsidCandidateRow(candidate: HiddenSsidCandidate) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = candidate.ssid,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        val text = buildString {
                            appendLine("候选 SSID: ${candidate.ssid}")
                            appendLine("信道: ${candidate.channel}")
                            appendLine("RSSI: ${candidate.rssi} dBm")
                            candidate.client?.let { appendLine("关联客户端: $it") }
                            append("捕获频次: ${candidate.sightings}")
                        }
                        clipboard.copyWithToast(context, text, "已复制候选 SSID")
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "复制候选 SSID",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "ch ${candidate.channel} · ${candidate.rssi} dBm · " +
                    "${rssiToProximity(candidate.rssi).label} · ${candidate.sightings} 次捕获",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = threatProximityColor(candidate.rssi),
            )
            candidate.client?.let { client ->
                Text(
                    text = "客户端: $client",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
            Text(
                text = "最后捕获: ${candidate.lastSeen}",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HiddenApResultsCard(
    observations: List<HiddenApObservation>,
    running: Boolean,
    deauthEnabled: Boolean,
    onForceReconnect: (HiddenApObservation) -> Unit,
    onClearObservations: () -> Unit,
) {
    val resolvedCount = observations.count { !it.resolvedSsid.isNullOrBlank() }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "已检测到的隐藏 AP",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = when {
                            observations.isEmpty() -> "黄色 = 隐藏中。绿色 = 已还原 SSID。"
                            else -> "${observations.size} 个隐藏 AP · 已还原 $resolvedCount 个"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
                if (observations.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onClearObservations,
                        enabled = !running,
                    ) {
                        Text("清空")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            if (observations.isEmpty()) {
                Text(
                    text = "暂未检测到隐藏 AP。",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 330.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(observations, key = { it.bssid }) { observation ->
                        HiddenApObservationRow(
                            observation = observation,
                            running = running,
                            deauthEnabled = deauthEnabled,
                            onForceReconnect = onForceReconnect,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HiddenApObservationRow(
    observation: HiddenApObservation,
    running: Boolean,
    deauthEnabled: Boolean,
    onForceReconnect: (HiddenApObservation) -> Unit,
) {
    val resolved = !observation.resolvedSsid.isNullOrBlank()
    val stateColor = if (resolved) StatusGreen else StatusAmber
    val stateLabel = if (resolved) "已还原" else "隐藏"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = stateColor.copy(alpha = 0.08f)),
        border = BorderStroke(0.5.dp, stateColor.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .background(stateColor.copy(alpha = 0.16f), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = stateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = stateColor,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = observation.lastSeen,
                    style = MaterialTheme.typography.labelSmall,
                    color = NrOnSurfaceVariant,
                )
                val clipboard = LocalClipboardManager.current
                val context = LocalContext.current
                IconButton(
                    onClick = {
                        val text = buildString {
                            appendLine("隐藏 AP: ${observation.resolvedSsid ?: "尚未揭示"}")
                            appendLine("BSSID: ${observation.bssid}")
                            appendLine("信道: ${observation.channel}")
                            appendLine("RSSI: ${observation.rssi} dBm")
                            appendLine("捕获频次: ${observation.sightings}")
                            observation.resolutionSource?.let { appendLine("揭示来源: $it") }
                            observation.vendor?.let { appendLine("设备厂商: $it") }
                        }
                        clipboard.copyWithToast(context, text, "已复制隐藏 AP 信息")
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "复制隐藏 AP 详情",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = observation.resolvedSsid ?: "尚未还原",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${observation.bssid} · ch ${observation.channel} · " +
                    "${observation.rssi} dBm · ${rssiToProximity(observation.rssi).label}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = threatProximityColor(observation.rssi),
            )
            observation.vendor?.let { vendor ->
                Text(
                    text = "厂商: $vendor",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
            Text(
                text = "${observation.sightings} 次捕获",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
            if (resolved) {
                Text(
                    text = observation.resolutionSource ?: "已还原",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusGreen,
                )
            } else if (running && deauthEnabled) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onForceReconnect(observation) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("强制重连诱导")
                }
            }
        }
    }
}
