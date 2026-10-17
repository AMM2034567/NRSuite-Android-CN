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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.defense.AlertConfidence
import com.swp81x.nrsuite.core.defense.DeauthAlert
import com.swp81x.nrsuite.core.defense.DeauthChannelMode
import com.swp81x.nrsuite.core.defense.DeauthFeedEntry
import com.swp81x.nrsuite.core.defense.DeauthFeedFilter
import com.swp81x.nrsuite.core.wifi.NetworkTarget
import com.swp81x.nrsuite.ui.util.rssiToProximity
import com.swp81x.nrsuite.ui.util.threatProximityColor
import com.swp81x.nrsuite.ui.components.NetworkTargetRow
import com.swp81x.nrsuite.ui.components.ChannelModeToggle
import com.swp81x.nrsuite.ui.components.ModuleStatusCard
import com.swp81x.nrsuite.ui.components.ModuleStatusState
import com.swp81x.nrsuite.ui.components.NrFilterChip
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed

@Composable
fun DeauthDetectorScreen(
    connected: Boolean,
    running: Boolean,
    channelMode: DeauthChannelMode,
    onChannelModeChange: (DeauthChannelMode) -> Unit,
    hopIntervalMs: Int,
    onHopIntervalChange: (Int) -> Unit,
    currentHopChannel: Int? = null,
    framesPerSecond: Int,
    totalFrames: Int,
    uniqueSourceCount: Int,
    thresholdFramesPerSecond: Int,
    activeAlert: DeauthAlert?,
    feed: List<DeauthFeedEntry>,
    feedFilter: DeauthFeedFilter,
    scanResults: List<NetworkTarget>,
    selectedTarget: NetworkTarget?,
    channel: Int,
    onSelectTarget: (NetworkTarget) -> Unit,
    onScanClick: () -> Unit,
    isScanning: Boolean,
    onFeedFilterChange: (DeauthFeedFilter) -> Unit,
    onClearFeed: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onLocateClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var configExpanded by remember { mutableStateOf(true) }
    var confirmStart by remember { mutableStateOf(false) }

    LaunchedEffect(running) {
        if (running) configExpanded = false
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            DetectorStatusCard(
                running = running,
                connected = connected,
                alert = activeAlert,
                onLocateClick = onLocateClick,
            )
            Spacer(Modifier.height(10.dp))

            if (running || activeAlert != null) {
                LiveStatsRow(
                    framesPerSecond = framesPerSecond,
                    totalFrames = totalFrames,
                    uniqueSourceCount = uniqueSourceCount,
                    thresholdFramesPerSecond = thresholdFramesPerSecond,
                )
                Spacer(Modifier.height(10.dp))
            }

            ConfigZone(
                connected = connected,
                running = running,
                expanded = configExpanded,
                channelMode = channelMode,
                hopIntervalMs = hopIntervalMs,
                currentHopChannel = currentHopChannel,
                scanResults = scanResults,
                selectedTarget = selectedTarget,
                isScanning = isScanning,
                onToggle = { configExpanded = !configExpanded },
                onChannelModeChange = onChannelModeChange,
                onHopIntervalChange = onHopIntervalChange,
                onScanClick = onScanClick,
                onSelectTarget = onSelectTarget,
            )

            Spacer(Modifier.height(10.dp))

            if (feed.isNotEmpty()) {
                DetectionFeedCard(
                    feed = feed,
                    feedFilter = feedFilter,
                    onFeedFilterChange = onFeedFilterChange,
                    onClearFeed = onClearFeed,
                )
            }

            Spacer(Modifier.height(80.dp))
        }

        val canStart = when (channelMode) {
            DeauthChannelMode.TARGETED -> selectedTarget != null
            DeauthChannelMode.HOPPING -> true
        }
        ModuleActionFab(
            onClick = {
                if (running) {
                    onStop()
                } else if (canStart) {
                    confirmStart = true
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            enabled = connected && (running || canStart),
            containerColor = if (running) StatusRed else MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(
                imageVector = if (running) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
            )
        }
    }

    if (confirmStart) {
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text("启动 Deauth 监测？") },
            text = {
                Text(
                    if (channelMode == DeauthChannelMode.HOPPING) {
                        "ESP32 将切换至混杂监听模式，每隔 $hopIntervalMs 毫秒在所有 2.4 GHz 信道间轮询跳频。"
                    } else {
                        "ESP32 将切换至信道 $channel 上的混杂监听模式，" +
                            "专门监测 ${selectedTarget?.ssid ?: "选定目标"} 的 Deauth 活动。"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onStart()
                        confirmStart = false
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
private fun DetectorStatusCard(
    running: Boolean,
    connected: Boolean,
    alert: DeauthAlert?,
    onLocateClick: () -> Unit,
) {
    val state = when {
        alert != null && alert.confidence == AlertConfidence.HIGH -> ModuleStatusState.ALERT
        alert != null -> ModuleStatusState.RUNNING
        !connected -> ModuleStatusState.DISCONNECTED
        running -> ModuleStatusState.RUNNING
        else -> ModuleStatusState.READY
    }
    ModuleStatusCard(
        icon = Icons.Default.Shield,
        title = when {
            alert != null -> "检测到 Deauth 攻击"
            !connected -> "未连接设备"
            running -> "未检测到攻击"
            else -> "就绪"
        },
        subtitle = when {
            alert != null -> "${alert.ssid} · 信道 ${alert.channel}"
            !connected -> "启动监测前请先连接设备。"
            running -> "正在监测空口 Deauth 攻击活动"
            else -> "启动监测以实时监听 Deauth 异常流量"
        },
        state = state,
    ) {
        if (alert != null) {
            if (alert.possiblySpoofed) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = StatusAmber,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "源 MAC 地址可能已被伪造",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusAmber,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = "${rssiToProximity(alert.dominantSourceRssi).label} " +
                    "(${alert.dominantSourceRssi} dBm)",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = threatProximityColor(alert.dominantSourceRssi),
            )

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onLocateClick,
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text("测向定位")
                }
                Spacer(Modifier.weight(1f))
                ConfidenceBadge(alert.confidence)
            }
        }
    }
}

@Composable
private fun ConfidenceBadge(confidence: AlertConfidence) {
    val (label, color) = when (confidence) {
        AlertConfidence.HIGH -> "高置信度" to StatusRed
        AlertConfidence.MEDIUM -> "中等置信度" to StatusAmber
        AlertConfidence.LOW -> "疑似误报" to StatusNeutral
    }
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun LiveStatsRow(
    framesPerSecond: Int,
    totalFrames: Int,
    uniqueSourceCount: Int,
    thresholdFramesPerSecond: Int,
) {
    val fpsColor = if (framesPerSecond > thresholdFramesPerSecond) StatusRed else MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatCell(
            label = "帧率 (帧/秒)",
            value = framesPerSecond.toString(),
            valueColor = fpsColor,
            modifier = Modifier.weight(1f),
        )
        StatCell(
            label = "累计帧数",
            value = totalFrames.toString(),
            modifier = Modifier.weight(1f),
        )
        StatCell(
            label = "来源数",
            value = uniqueSourceCount.toString(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = valueColor,
            )
        }
    }
}

@Composable
private fun ConfigZone(
    connected: Boolean,
    running: Boolean,
    expanded: Boolean,
    channelMode: DeauthChannelMode,
    hopIntervalMs: Int,
    currentHopChannel: Int?,
    scanResults: List<NetworkTarget>,
    selectedTarget: NetworkTarget?,
    isScanning: Boolean,
    onToggle: () -> Unit,
    onChannelModeChange: (DeauthChannelMode) -> Unit,
    onHopIntervalChange: (Int) -> Unit,
    onScanClick: () -> Unit,
    onSelectTarget: (NetworkTarget) -> Unit,
) {
    val controlsEnabled = connected && !running

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
                    imageVector = Icons.Default.WifiTethering,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "目标与信道",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = when {
                            running && channelMode == DeauthChannelMode.HOPPING ->
                                "跳频模式 · 信道 ${currentHopChannel ?: "-"}"
                            running -> "正在监测 ${selectedTarget?.ssid ?: "目标"}"
                            channelMode == DeauthChannelMode.TARGETED ->
                                selectedTarget?.ssid ?: "未选择目标"
                            else -> "全信道轮询跳频"
                        },
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
                    Text(
                        text = "信道模式",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.height(6.dp))
                    ChannelModeToggle(
                        fixed = channelMode == DeauthChannelMode.TARGETED,
                        enabled = controlsEnabled,
                        onFixedChange = { fixed ->
                            onChannelModeChange(
                                if (fixed) DeauthChannelMode.TARGETED else DeauthChannelMode.HOPPING,
                            )
                        },
                    )
                    Spacer(Modifier.height(10.dp))

                    if (channelMode == DeauthChannelMode.TARGETED) {
                        OutlinedButton(
                            onClick = onScanClick,
                            enabled = controlsEnabled && !isScanning,
                        ) {
                            Text(if (isScanning) "正在扫描..." else "扫描周围 WiFi 目标")
                        }

                        if (scanResults.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                scanResults.forEach { target ->
                                    NetworkTargetRow(
                                        ssid = target.ssid,
                                        bssid = target.bssid,
                                        channel = target.channel,
                                        rssi = target.rssi,
                                        security = target.security,
                                        wps = target.wps,
                                        vendor = target.vendor,
                                        ouiWhitelisted = target.ouiWhitelisted,
                                        ouiBlacklisted = target.ouiBlacklisted,
                                        selected = selectedTarget?.bssid == target.bssid,
                                        enabled = controlsEnabled,
                                        onClick = { onSelectTarget(target) },
                                    )
                                }
                            }
                        }

                    } else {
                        NumberStepper(
                            label = "驻留时间 (毫秒)",
                            valueText = hopIntervalMs.toString(),
                            enabled = controlsEnabled,
                            onDecrease = {
                                onHopIntervalChange((hopIntervalMs - 100).coerceAtLeast(100))
                            },
                            onIncrease = {
                                onHopIntervalChange((hopIntervalMs + 100).coerceAtMost(2_000))
                            },
                        )

                        if (running) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "当前驻留信道: ${currentHopChannel ?: "-"}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = NrOnSurfaceVariant,
                            )
                        }
                    }

                    if (!connected) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "启动监测前请先连接设备。",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusRed,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetectionFeedCard(
    feed: List<DeauthFeedEntry>,
    feedFilter: DeauthFeedFilter,
    onFeedFilterChange: (DeauthFeedFilter) -> Unit,
    onClearFeed: () -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(feed.size) {
        if (feed.isNotEmpty()) {
            listState.animateScrollToItem(feed.lastIndex)
        }
    }

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
                        text = "检测实时流",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "共显示 ${feed.size} 条",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = onClearFeed,
                    enabled = feed.isNotEmpty(),
                ) {
                    Text("清空")
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeauthFeedFilter.entries.forEach { filter ->
                    NrFilterChip(
                        selected = feedFilter == filter,
                        onClick = { onFeedFilterChange(filter) },
                        label = when (filter) {
                            DeauthFeedFilter.ALL -> "全部"
                            DeauthFeedFilter.BROADCAST -> "广播攻击"
                            DeauthFeedFilter.TARGETED -> "定向攻击"
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            SelectionContainer {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                ) {
                    itemsIndexed(feed) { index, entry ->
                        FeedRow(entry)
                        if (index != feed.lastIndex) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(0.5.dp)
                                    .background(NrOutline),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedRow(entry: DeauthFeedEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.sourceMac,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = entry.timestamp,
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
        }
        val targetAddress = entry.targetMac ?: "FF:FF:FF:FF:FF:FF"
        val targetKind = if (entry.targetMac == null) "广播" else "定向"
        Text(
            text = "目标 $targetAddress ($targetKind) · 原因代码 ${entry.reasonCode} · " +
                "${entry.rssi} dBm · ${rssiToProximity(entry.rssi).label}",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = threatProximityColor(entry.rssi),
        )
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
            Icon(Icons.Default.Remove, contentDescription = "减小 $label")
        }
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        IconButton(onClick = onIncrease, enabled = enabled) {
            Icon(Icons.Default.Add, contentDescription = "增加 $label")
        }
    }
}
