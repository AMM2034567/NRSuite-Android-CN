package com.swp81x.nrsuite.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Radar
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.ble.MAX_TRACKER_OBSERVATIONS
import com.swp81x.nrsuite.core.ble.TrackerObservation
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed
import com.swp81x.nrsuite.ui.util.copyWithToast
import com.swp81x.nrsuite.ui.util.rssiToProximity

@Composable
fun TrackerDetectorScreen(
    connected: Boolean,
    scanning: Boolean,
    trackers: List<TrackerObservation>,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmStart by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            TrackerStatusCard(
                connected = connected,
                scanning = scanning,
                trackerCount = trackers.size,
            )

            Spacer(Modifier.height(10.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NrSurface),
                border = BorderStroke(0.5.dp, NrOutline),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        text = "防追踪检测 (Tracker Detection)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "在 BLE 广播扫描期间标记 Apple Find My / AirTag 类追踪器广播特征负载。",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "请仅在您获得授权评估的设备与环境中进行检测。",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusAmber,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            TrackerResultsCard(
                trackers = trackers,
                scanning = scanning,
                onClear = onClear,
            )

            Spacer(Modifier.height(80.dp))
        }

        if (connected) {
            ModuleActionFab(
                onClick = {
                    if (scanning) onStop() else confirmStart = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = if (scanning) StatusRed else MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = if (scanning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (scanning) "停止防追踪检测" else "启动防追踪检测",
                )
            }
        }
    }

    if (confirmStart) {
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text("启动防追踪检测？") },
            text = {
                Text(
                    "将启动 BLE 主动扫描并监听 Apple Find My / AirTag 类追踪器的广播负载。" +
                        "请仅在获得授权的合规场景下使用。"
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
private fun TrackerStatusCard(
    connected: Boolean,
    scanning: Boolean,
    trackerCount: Int,
) {
    val color = when {
        !connected -> StatusNeutral
        scanning -> StatusGreen
        else -> StatusAmber
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, if (scanning) MaterialTheme.colorScheme.primary else NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Radar,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = when {
                            !connected -> "未连接设备"
                            scanning -> "正在监测"
                            else -> "就绪"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = when {
                            !connected -> "请先连接设备以开始检测"
                            scanning -> "正在监听 BLE 广播 · 已发现 $trackerCount 个可疑目标"
                            else -> "启动 BLE 扫描以监测周围追踪器"
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
private fun TrackerResultsCard(
    trackers: List<TrackerObservation>,
    scanning: Boolean,
    onClear: () -> Unit,
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
                        text = "可疑追踪器候选",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (trackers.isEmpty()) {
                            "未检测到追踪器特征"
                        } else {
                            "检测到 ${trackers.size} 个可疑候选目标"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
                if (trackers.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onClear,
                        enabled = !scanning,
                    ) {
                        Text("清空")
                    }
                }
            }

            if (trackers.size >= MAX_TRACKER_OBSERVATIONS) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "已达 $MAX_TRACKER_OBSERVATIONS 条上限 — 最早的记录将被替换。",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusAmber,
                )
            }

            if (trackers.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(trackers, key = { it.address }) { tracker ->
                        TrackerRow(tracker)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackerRow(tracker: TrackerObservation) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = StatusAmber.copy(alpha = 0.08f)),
        border = BorderStroke(0.5.dp, StatusAmber.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .padding(end = 8.dp),
                ) {
                    Text(
                        text = tracker.trackerType ?: "可疑追踪器",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusAmber,
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        val text = buildString {
                            appendLine("可疑追踪器: ${tracker.name ?: "未命名"}")
                            appendLine("类型: ${tracker.trackerType ?: "未知"}")
                            appendLine("地址: ${tracker.address}")
                            appendLine("RSSI: ${tracker.rssi} dBm")
                            tracker.manufacturerData?.let { appendLine("厂商数据: $it") }
                            appendLine("首次发现: ${tracker.firstSeen}")
                            appendLine("最近活跃: ${tracker.lastSeen}")
                            append("发现次数: ${tracker.sightings}")
                        }
                        clipboard.copyWithToast(context, text, "追踪器信息已复制")
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "复制追踪器详情",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                text = tracker.name ?: "未命名追踪器候选",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = tracker.address,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = NrOnSurfaceVariant,
            )
            Text(
                text = "${tracker.rssi} dBm · ${rssiToProximity(tracker.rssi).label} · " +
                    "捕获 ${tracker.sightings} 次",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = StatusRed,
            )
            tracker.manufacturerData?.let { payload ->
                Text(
                    text = "负载数据: ${payload.take(40)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
            }
            Text(
                text = "首次: ${tracker.firstSeen} · 最近: ${tracker.lastSeen}",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
        }
    }
}
