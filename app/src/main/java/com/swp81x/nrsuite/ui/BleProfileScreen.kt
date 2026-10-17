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
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.core.ble.BleCharacteristicProfile
import com.swp81x.nrsuite.core.ble.BleDeviceObservation
import com.swp81x.nrsuite.core.ble.BleServiceProfile
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
import kotlinx.coroutines.launch

@Composable
fun BleProfileScreen(
    connected: Boolean,
    running: Boolean,
    scanStarting: Boolean,
    scanRunning: Boolean,
    scanStopping: Boolean,
    target: BleDeviceObservation?,
    devices: List<BleDeviceObservation>,
    services: List<BleServiceProfile>,
    status: String,
    onSelectTarget: (BleDeviceObservation) -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmStart by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scanBusy = scanStarting || scanRunning || scanStopping

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            BleProfileStatusCard(
                connected = connected,
                running = running,
                target = target,
                status = status,
            )

            Spacer(Modifier.height(10.dp))

            BleProfileTargetCard(
                connected = connected,
                devices = devices,
                selectedTarget = target,
                profileRunning = running,
                scanStarting = scanStarting,
                scanRunning = scanRunning,
                scanStopping = scanStopping,
                onSelectTarget = onSelectTarget,
                onStartScan = onStartScan,
                onStopScan = onStopScan,
            )

            Spacer(Modifier.height(10.dp))

            BleProfileResultsCard(
                services = services,
                running = running,
                onClear = onClear,
            )

            Spacer(Modifier.height(80.dp))
        }

        if (connected) {
            ModuleActionFab(
                onClick = {
                    when {
                        scanBusy -> Unit
                        running -> onStop()
                        target == null -> scope.launch {
                            snackbarHostState.showSnackbar("请先选择已发现的 BLE 设备。")
                        }
                        else -> confirmStart = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .alpha(if (!scanBusy && !running && target == null) 0.4f else 1f),
                containerColor = when {
                    running -> StatusRed
                    scanBusy -> StatusAmber
                    target == null -> StatusNeutral
                    else -> MaterialTheme.colorScheme.primary
                },
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                when {
                    scanBusy -> CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    running -> Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "停止 BLE 属性画像",
                    )
                    else -> Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "启动 BLE 属性画像",
                    )
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp),
            )
        }
    }

    if (confirmStart) {
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text("启动 BLE 属性画像？") },
            text = {
                Text(
                    "将连接至 ${target?.name ?: target?.address ?: "所选设备"} " +
                        "并执行只读的服务与特征值枚举枚举分析。" +
                        "请仅在获得授权的测试环境中使用。"
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
private fun BleProfileStatusCard(
    connected: Boolean,
    running: Boolean,
    target: BleDeviceObservation?,
    status: String,
) {
    val color = when {
        !connected -> StatusNeutral
        running -> StatusAmber
        target != null && (status.startsWith("Profile complete") || status.startsWith("画像采集完成")) -> StatusGreen
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
                    imageVector = Icons.Default.Usb,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = when {
                            !connected -> "未连接设备"
                            running -> "正在采集画像..."
                            servicesStatus(status) -> "就绪"
                            else -> "就绪"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                    target?.let {
                        Text(
                            text = "${it.name ?: "未命名设备"} · ${it.address}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = NrOnSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun servicesStatus(status: String): Boolean =
    status.startsWith("Profile complete") || status.startsWith("Target selected") ||
        status.startsWith("画像采集完成") || status.startsWith("已选择目标")

@Composable
private fun BleProfileTargetCard(
    connected: Boolean,
    devices: List<BleDeviceObservation>,
    selectedTarget: BleDeviceObservation?,
    profileRunning: Boolean,
    scanStarting: Boolean,
    scanRunning: Boolean,
    scanStopping: Boolean,
    onSelectTarget: (BleDeviceObservation) -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
) {
    val scanBusy = scanStarting || scanRunning || scanStopping
    val scanStatusText = when {
        !connected -> "请先连接 NRSuite 硬件设备以进行扫描。"
        profileRunning -> "属性画像分析进行中。请先停止画像再进行扫描。"
        scanStarting -> "正在启动 BLE 扫描..."
        scanRunning -> "正在扫描... 已发现 ${devices.size} 台设备。目标出现后点击停止扫描。"
        scanStopping -> "正在停止 BLE 扫描..."
        devices.isEmpty() -> "暂无发现的 BLE 设备。请启动扫描。"
        else -> "已发现 ${devices.size} 台设备。请选择目标进行画像。"
    }
    val scanStatusColor = when {
        !connected -> StatusNeutral
        scanStarting || scanRunning || scanStopping -> StatusAmber
        devices.isEmpty() -> NrOnSurfaceVariant
        else -> StatusGreen
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "画像目标设备",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "扫描附近 BLE 设备，停止扫描后选择目标进行画像。",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    when {
                        scanRunning -> onStopScan()
                        scanStarting || scanStopping -> Unit
                        else -> onStartScan()
                    }
                },
                enabled = connected && !profileRunning && !scanStarting && !scanStopping,
            ) {
                Text(
                    when {
                        scanStarting -> "正在启动 BLE 扫描..."
                        scanRunning -> "停止 BLE 扫描"
                        scanStopping -> "正在停止 BLE 扫描..."
                        else -> "扫描附近 BLE"
                    }
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = scanStatusText,
                style = MaterialTheme.typography.bodySmall,
                color = scanStatusColor,
            )
            Spacer(Modifier.height(8.dp))

            if (devices.isEmpty()) {
                Text(
                    text = if (scanBusy) {
                        "正在等待附近 BLE 设备..."
                    } else {
                        "扫描器发现的设备将显示在此处。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (scanBusy) StatusAmber else NrOnSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(devices, key = { it.address }) { device ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
                            border = BorderStroke(
                                0.5.dp,
                                if (selectedTarget?.address == device.address) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    NrOutline
                                },
                            ),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = device.name ?: "未命名设备",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text = "${device.address} · ${device.rssi} dBm · ${rssiToProximity(device.rssi).label}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = NrOnSurfaceVariant,
                                    )
                                }
                                OutlinedButton(
                                    onClick = { onSelectTarget(device) },
                                    enabled = !profileRunning && !scanBusy,
                                ) {
                                    Text(if (selectedTarget?.address == device.address) "已选择" else "选择")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BleProfileResultsCard(
    services: List<BleServiceProfile>,
    running: Boolean,
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
                        text = "服务与特征值",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (services.isEmpty()) {
                            "暂无属性画像数据"
                        } else {
                            "${services.size} 个服务, 共 ${services.sumOf { it.characteristics.size }} 个特征值"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
                if (services.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onClear,
                        enabled = !running,
                    ) {
                        Text("清空")
                    }
                }
            }

            if (services.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(services, key = { it.uuid }) { service ->
                        BleServiceRow(service)
                    }
                }
            }
        }
    }
}

@Composable
private fun BleServiceRow(service: BleServiceProfile) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurfaceVariant),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                text = "服务 ${service.uuid}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (service.characteristics.isEmpty()) {
                Text(
                    text = "未发现特征值",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            } else {
                Spacer(Modifier.height(6.dp))
                service.characteristics.forEach { characteristic ->
                    BleCharacteristicRow(characteristic)
                }
            }
        }
    }
}

@Composable
private fun BleCharacteristicRow(characteristic: BleCharacteristicProfile) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val props = buildList {
        if (characteristic.read) add("读 (Read)")
        if (characteristic.write) add("写 (Write)")
        if (characteristic.writeNoResponse) add("无响应写 (Write NR)")
        if (characteristic.notify) add("通知 (Notify)")
        if (characteristic.indicate) add("指示 (Indicate)")
        if (characteristic.broadcast) add("广播 (Broadcast)")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = characteristic.uuid,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
            Text(
                text = if (props.isEmpty()) "无属性" else props.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = NrOnSurfaceVariant,
            )
        }
        IconButton(
            onClick = {
                clipboard.copyWithToast(
                    context,
                    "特征值: ${characteristic.uuid}\n" +
                        "属性: ${props.joinToString(", ").ifBlank { "无" }}",
                    "特征值已复制",
                )
            },
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "复制特征值",
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
