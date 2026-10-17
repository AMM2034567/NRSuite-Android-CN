package com.swp81x.nrsuite.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
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
import com.swp81x.nrsuite.core.ble.BleDeviceObservation
import com.swp81x.nrsuite.core.ble.MAX_BLE_SCAN_DEVICES
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
import com.swp81x.nrsuite.ui.util.signalQualityColor

@Composable
fun BleScannerScreen(
    connected: Boolean,
    running: Boolean,
    starting: Boolean,
    stopping: Boolean,
    active: Boolean,
    devices: List<BleDeviceObservation>,
    onActiveChange: (Boolean) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var configExpanded by remember { mutableStateOf(true) }
    var confirmStart by remember { mutableStateOf(false) }
    var connectableOnly by remember { mutableStateOf(false) }

    val shownDevices = remember(devices, connectableOnly) {
        if (connectableOnly) devices.filter { it.connectable } else devices
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            BleScannerStatusCard(
                connected = connected,
                running = running,
                active = active,
                deviceCount = devices.size,
            )

            Spacer(Modifier.height(10.dp))

            BleScannerConfigCard(
                expanded = configExpanded,
                running = running,
                active = active,
                onToggle = { configExpanded = !configExpanded },
                onActiveChange = onActiveChange,
            )

            Spacer(Modifier.height(10.dp))

            BleScannerResultsCard(
                devices = shownDevices,
                totalCount = devices.size,
                running = running,
                connectableOnly = connectableOnly,
                onConnectableOnlyChange = { connectableOnly = it },
                onClear = onClear,
            )

            Spacer(Modifier.height(80.dp))
        }

        if (connected) {
            ModuleActionFab(
                onClick = {
                    when {
                        starting || stopping -> Unit
                        running -> onStop()
                        else -> confirmStart = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = when {
                    starting || stopping -> StatusAmber
                    running -> StatusRed
                    else -> MaterialTheme.colorScheme.primary
                },
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                if (starting || stopping) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        imageVector = if (running) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (running) "停止 BLE 扫描" else "启动 BLE 扫描",
                    )
                }
            }
        }
    }

    if (confirmStart) {
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text("启动 BLE 扫描？") },
            text = {
                Text(
                    if (active) {
                        "主动模式将向周围发送 BLE 扫描请求 (Scan Request)。" +
                            "请仅在您获得授权评估的设备与环境中进行测试。"
                    } else {
                        "被动模式仅监听周围设备的 BLE 广播包，不主动发送扫描请求。"
                    }
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
private fun BleScannerStatusCard(
    connected: Boolean,
    running: Boolean,
    active: Boolean,
    deviceCount: Int,
) {
    val color = when {
        !connected -> StatusNeutral
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
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = when {
                            !connected -> "未连接设备"
                            running -> "正在扫描"
                            else -> "就绪"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = when {
                            !connected -> "请先连接设备以开始扫描"
                            running -> "${if (active) "主动" else "被动"}扫描 · 已发现 $deviceCount 台设备"
                            else -> "低功耗蓝牙 (BLE) 设备发现"
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
private fun BleScannerConfigCard(
    expanded: Boolean,
    running: Boolean,
    active: Boolean,
    onToggle: () -> Unit,
    onActiveChange: (Boolean) -> Unit,
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
                        text = "BLE 扫描配置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "BLE 扫描器与 BLE HID 键鼠模拟不能同时运行。",
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NrFilterChip(
                        selected = active,
                        onClick = { onActiveChange(true) },
                        label = "主动扫描",
                        enabled = !running,
                    )
                    NrFilterChip(
                        selected = !active,
                        onClick = { onActiveChange(false) },
                        label = "被动监听",
                        enabled = !running,
                    )
                }
            }
        }
    }
}

@Composable
private fun BleScannerResultsCard(
    devices: List<BleDeviceObservation>,
    totalCount: Int,
    running: Boolean,
    connectableOnly: Boolean,
    onConnectableOnlyChange: (Boolean) -> Unit,
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
                        text = "已发现 BLE 设备",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (devices.isEmpty()) {
                            "暂未发现 BLE 设备"
                        } else {
                            "显示 ${devices.size} / 共 $totalCount 台设备"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
                if (totalCount > 0) {
                    OutlinedButton(
                        onClick = onClear,
                        enabled = !running,
                    ) {
                        Text("清空")
                    }
                }
            }

            if (totalCount >= MAX_BLE_SCAN_DEVICES) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "已达 $MAX_BLE_SCAN_DEVICES 台设备上限 — 最早的记录将被替换。",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusAmber,
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "仅显示可连接设备",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = connectableOnly,
                    onCheckedChange = onConnectableOnlyChange,
                )
            }

            Spacer(Modifier.height(8.dp))
            if (devices.isEmpty()) {
                Text(
                    text = "启动扫描或清除过滤条件以查看发现的设备。",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(devices, key = { it.address }) { device ->
                        BleDeviceRow(device)
                    }
                }
            }
        }
    }
}

@Composable
private fun BleDeviceRow(device: BleDeviceObservation) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val signalColor = signalQualityColor(device.rssi)
    val serviceLabels = remember(device.services) {
        device.services.mapNotNull { service ->
            when {
                service.contains("FE2C") -> "FastPair"
                service.contains("111E") || service.contains("111F") -> "HFP"
                service.contains("1812") -> "HID"
                service.contains("180F") -> "电池服务"
                else -> null
            }
        }.distinct()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = device.name ?: "未命名设备",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        val text = buildString {
                            appendLine("名称: ${device.name ?: "未知"}")
                            appendLine("地址: ${device.address}")
                            appendLine("地址类型: ${device.addressType}")
                            appendLine("RSSI: ${device.rssi} dBm")
                            appendLine("可连接: ${if (device.connectable) "是" else "否"}")
                            if (device.services.isNotEmpty()) {
                                appendLine("服务 UUID: ${device.services.joinToString(", ")}")
                            }
                            device.manufacturerData?.let { appendLine("厂商数据: $it") }
                            device.rawPayload?.let { appendLine("原始负载: $it") }
                        }
                        clipboard.copyWithToast(context, text, "BLE 设备信息已复制")
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "复制 BLE 设备详情",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                text = device.address,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = NrOnSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${device.rssi} dBm · ${rssiToProximity(device.rssi).label} · " +
                    if (device.connectable) "可连接" else "不可连接",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = signalColor,
            )
            if (serviceLabels.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    serviceLabels.forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            device.manufacturerData?.let { manufacturer ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "厂商数据: ${manufacturer.take(32)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
            }
            device.rawPayload?.let { rawPayload ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "原始负载: ${rawPayload.take(48)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
            }
            Text(
                text = "最近活跃: ${device.lastSeen} · 发现 ${device.sightings} 次",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
        }
    }
}
