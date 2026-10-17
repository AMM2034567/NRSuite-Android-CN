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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import com.swp81x.nrsuite.core.oui.OuiDatabaseStatus
import com.swp81x.nrsuite.core.defense.NearbyAp
import com.swp81x.nrsuite.core.defense.RogueApAlert
import com.swp81x.nrsuite.core.defense.RogueApCategory
import com.swp81x.nrsuite.ui.components.ModuleStatusCard
import com.swp81x.nrsuite.ui.components.ModuleStatusState
import com.swp81x.nrsuite.ui.components.NetworkStatusBadge
import com.swp81x.nrsuite.ui.util.rssiToProximity
import com.swp81x.nrsuite.ui.util.signalQualityColor
import com.swp81x.nrsuite.ui.util.threatProximityColor
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed

@Composable
fun RogueApScreen(
    connected: Boolean,
    running: Boolean,
    scanning: Boolean,
    nearbyNetworks: List<NearbyAp>,
    alerts: List<RogueApAlert>,
    lastScanAt: String?,
    ouiDatabaseStatus: OuiDatabaseStatus,
    onDownloadOuiDatabase: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClearAlerts: () -> Unit,
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
            if (ouiDatabaseStatus !is OuiDatabaseStatus.Ready) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(0.5.dp, StatusAmber),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            tint = StatusAmber,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "MAC 厂商数据库未下载",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "在下载数据库之前，基于厂商信息的研判（未知硬件、厂商不匹配等）将不可用。",
                                style = MaterialTheme.typography.bodySmall,
                                color = NrOnSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = onDownloadOuiDatabase) {
                            Text("下载")
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            RogueApStatusCard(
                connected = connected,
                running = running,
                scanning = scanning,
                lastScanAt = lastScanAt,
            )
            Spacer(Modifier.height(10.dp))

            NearbyNetworksCard(nearbyNetworks = nearbyNetworks)

            Spacer(Modifier.height(10.dp))

            RogueApAlertsCard(
                alerts = alerts,
                running = running,
                onClearAlerts = onClearAlerts,
            )

            Spacer(Modifier.height(80.dp))
        }

        if (connected) {
            ModuleActionFab(
                onClick = {
                    if (running) onStop() else confirmStart = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                enabled = connected,
                containerColor = if (running) StatusRed else MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = if (running) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (running) {
                        "停止伪造 AP 检测器"
                    } else {
                        "启动伪造 AP 检测器"
                    },
                )
            }
        }
    }

    if (confirmStart) {
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text("启动伪造 AP (Rogue AP) 监测？") },
            text = {
                Text(
                    "ESP32 将持续扫描 WiFi 并对比周围相同 SSID 热点的 OUI 厂商信息及安全加密模式差异。" +
                        "该功能将占用 WiFi 射频。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmStart = false
                        onStart()
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
private fun RogueApStatusCard(
    connected: Boolean,
    running: Boolean,
    scanning: Boolean,
    lastScanAt: String?,
) {
    val state = when {
        !connected -> ModuleStatusState.DISCONNECTED
        running -> ModuleStatusState.RUNNING
        else -> ModuleStatusState.READY
    }
    ModuleStatusCard(
        icon = Icons.Default.Router,
        title = when {
            !connected -> "未连接设备"
            running -> "正在监测"
            else -> "就绪"
        },
        subtitle = when {
            !connected -> "启动检测器前请先连接设备。"
            running && scanning -> "正在扫描周围伪造 AP..."
            running -> "正在监测伪造 AP 异常行为"
            else -> "请先采集基线，然后启动监测"
        },
        state = state,
    ) {
        if (lastScanAt != null) {
            Text(
                text = "上次扫描: $lastScanAt",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = NrOnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NearbyNetworksCard(
    nearbyNetworks: List<NearbyAp>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "附近 WiFi 网络",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "最新扫描发现 ${nearbyNetworks.size} 个热点 AP",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )

            if (nearbyNetworks.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(nearbyNetworks, key = { it.bssid }) { ap ->
                        NearbyApRow(ap)
                    }
                }
            }
        }
    }
}

@Composable
private fun NearbyApRow(ap: NearbyAp) {
    val statusLabel = when {
        ap.suspicious && ap.category != null -> when (ap.category) {
            RogueApCategory.EVIL_TWIN -> "恶魔双胞胎"
            RogueApCategory.FAKE_PORTAL -> "虚假门户"
            RogueApCategory.SECURITY_DOWNGRADE -> "安全协议降级"
            RogueApCategory.UNKNOWN_ROGUE -> "未知伪造 AP"
        }
        ap.likelyInfrastructureVendor -> "疑似合法"
        else -> "未分类"
    }
    val statusColor = when {
        ap.suspicious && ap.category == RogueApCategory.EVIL_TWIN -> StatusRed
        ap.suspicious && ap.category == RogueApCategory.FAKE_PORTAL -> StatusAmber
        ap.suspicious && ap.category == RogueApCategory.SECURITY_DOWNGRADE -> StatusAmber
        ap.suspicious -> StatusNeutral
        ap.likelyInfrastructureVendor -> MaterialTheme.colorScheme.primary
        else -> StatusNeutral
    }

    val signalTint = if (ap.suspicious) {
        threatProximityColor(ap.rssi)
    } else {
        signalQualityColor(ap.rssi)
    }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = ap.ssid.ifBlank { "(隐藏)" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Row(
                modifier = Modifier
                    .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        Text(
            text = ap.bssid,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = NrOnSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "信道 ${ap.channel} · ${ap.rssi} dBm · ${rssiToProximity(ap.rssi).label} · ${ap.security}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = signalTint,
            )
            if (ap.wps) {
                Spacer(Modifier.width(6.dp))
                NetworkStatusBadge(text = "WPS", color = StatusAmber)
            }
        }
        if (!ap.vendor.isNullOrBlank()) {
            Text(
                text = "厂商: ${ap.vendor}",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RogueApAlertsCard(
    alerts: List<RogueApAlert>,
    running: Boolean,
    onClearAlerts: () -> Unit,
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
                        text = "可疑热点 AP",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = when {
                            alerts.isEmpty() && running -> "正在等待捕获可疑行为..."
                            alerts.isEmpty() -> "暂无可疑 AP"
                            else -> "${alerts.size} 条告警"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
                if (alerts.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onClearAlerts,
                        enabled = !running,
                    ) {
                        Text("清空")
                    }
                }
            }

            if (alerts.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(alerts, key = { it.id }) { alert ->
                        RogueApAlertRow(alert)
                    }
                }
            }
        }
    }
}

@Composable
private fun RogueApAlertRow(alert: RogueApAlert) {
    val categoryColor = when (alert.category) {
        RogueApCategory.EVIL_TWIN -> StatusRed
        RogueApCategory.FAKE_PORTAL -> StatusAmber
        RogueApCategory.SECURITY_DOWNGRADE -> StatusAmber
        RogueApCategory.UNKNOWN_ROGUE -> StatusNeutral
    }
    val categoryLabel = when (alert.category) {
        RogueApCategory.EVIL_TWIN -> "恶魔双胞胎"
        RogueApCategory.FAKE_PORTAL -> "虚假门户"
        RogueApCategory.SECURITY_DOWNGRADE -> "安全协议降级"
        RogueApCategory.UNKNOWN_ROGUE -> "未知伪造 AP"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .background(categoryColor.copy(alpha = 0.15f), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = categoryLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = categoryColor,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = alert.detectedAt,
                    style = MaterialTheme.typography.labelSmall,
                    color = NrOnSurfaceVariant,
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = alert.ssid,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${alert.bssid} · 信道 ${alert.channel} · ${alert.rssi} dBm · " +
                    rssiToProximity(alert.rssi).label,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = threatProximityColor(alert.rssi),
            )
            if (!alert.vendor.isNullOrBlank()) {
                Text(
                    text = "厂商: ${alert.vendor}",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }

            if (alert.reasons.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                alert.reasons.forEach { reason ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = categoryColor,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            val confidenceZh = when (alert.confidence) {
                AlertConfidence.HIGH -> "高"
                AlertConfidence.MEDIUM -> "中"
                AlertConfidence.LOW -> "低"
            }
            Text(
                text = "置信度: $confidenceZh",
                style = MaterialTheme.typography.labelSmall,
                color = when (alert.confidence) {
                    AlertConfidence.HIGH -> StatusRed
                    AlertConfidence.MEDIUM -> StatusAmber
                    AlertConfidence.LOW -> StatusNeutral
                },
            )
        }
    }
}
