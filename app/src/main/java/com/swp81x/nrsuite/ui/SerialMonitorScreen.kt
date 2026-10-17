package com.swp81x.nrsuite.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.SerialLogLine
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.core.usb.UsbSerialDevice
import com.swp81x.nrsuite.ui.components.NrFilterChip
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.theme.LogColorError
import com.swp81x.nrsuite.ui.theme.LogColorInfo
import com.swp81x.nrsuite.ui.theme.LogColorSuccess
import com.swp81x.nrsuite.ui.theme.LogColorUsb
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed
import com.swp81x.nrsuite.ui.util.copyWithToast

private val BAUD_RATES = listOf(9600, 19200, 38400, 57600, 115200, 230400, 460800, 921600)
private val LINE_ENDINGS = listOf("None", "LF", "CR", "CRLF")

private fun formatLineEndingLabel(ending: String): String = when (ending) {
    "None" -> "无换行"
    "LF" -> "LF (\\n)"
    "CR" -> "CR (\\r)"
    "CRLF" -> "CRLF (\\r\\n)"
    else -> ending
}

@Composable
fun SerialMonitorScreen(
    devices: List<UsbSerialDevice>,
    selectedDevice: UsbSerialDevice?,
    connected: Boolean,
    connecting: Boolean,
    baudRate: Int,
    logLines: List<SerialLogLine>,
    hexMode: Boolean,
    inputText: String,
    lineEnding: String,
    blockingMessage: String?,
    errorMessage: String?,
    permissionRevision: Int,
    hasPermission: (UsbSerialDevice) -> Boolean,
    onRefreshDevices: () -> Unit,
    onSelectDevice: (UsbSerialDevice) -> Unit,
    onRequestPermission: (UsbSerialDevice) -> Unit,
    onConnect: (UsbSerialDevice, Int) -> Unit,
    onDisconnect: () -> Unit,
    onBaudChange: (Int) -> Unit,
    onHexModeChange: (Boolean) -> Unit,
    onInputChange: (String) -> Unit,
    onLineEndingChange: (String) -> Unit,
    onSend: () -> Unit,
    onClearLog: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    @Suppress("UNUSED_EXPRESSION")
    permissionRevision

    var configExpanded by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(connected) {
        if (connected) configExpanded = false
    }

    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val logListState = rememberLazyListState()
    val logTextForCopy = remember(logLines) {
        logLines.joinToString("\n") { "[${it.timestamp}] ${it.text}" }
    }

    LaunchedEffect(logLines.size) {
        if (logLines.isNotEmpty()) {
            logListState.animateScrollToItem(logLines.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SerialMonitorConnectionCard(
            devices = devices,
            selectedDevice = selectedDevice,
            connected = connected,
            connecting = connecting,
            baudRate = baudRate,
            hexMode = hexMode,
            expanded = configExpanded,
            hasPermission = hasPermission,
            onToggle = { configExpanded = !configExpanded },
            onRefreshDevices = onRefreshDevices,
            onSelectDevice = onSelectDevice,
            onRequestPermission = onRequestPermission,
            onConnect = onConnect,
            onDisconnect = onDisconnect,
            onBaudChange = onBaudChange,
            onHexModeChange = onHexModeChange,
        )

        blockingMessage?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = StatusAmber,
            )
        }
        errorMessage?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = StatusRed,
            )
        }

        SerialMonitorLogCard(
            modifier = Modifier.weight(1f),
            logLines = logLines,
            hexMode = hexMode,
            logTextForCopy = logTextForCopy,
            logListState = logListState,
            inputText = inputText,
            lineEnding = lineEnding,
            connected = connected,
            onInputChange = onInputChange,
            onLineEndingChange = onLineEndingChange,
            onSend = onSend,
            onHexModeChange = onHexModeChange,
            onCopy = { clipboard.copyWithToast(context, logTextForCopy, "已复制串口日志") },
            onClearLog = onClearLog,
            onExport = onExport,
        )
    }
}

@Composable
private fun SerialMonitorConnectionCard(
    devices: List<UsbSerialDevice>,
    selectedDevice: UsbSerialDevice?,
    connected: Boolean,
    connecting: Boolean,
    baudRate: Int,
    hexMode: Boolean,
    expanded: Boolean,
    hasPermission: (UsbSerialDevice) -> Boolean,
    onToggle: () -> Unit,
    onRefreshDevices: () -> Unit,
    onSelectDevice: (UsbSerialDevice) -> Unit,
    onRequestPermission: (UsbSerialDevice) -> Unit,
    onConnect: (UsbSerialDevice, Int) -> Unit,
    onDisconnect: () -> Unit,
    onBaudChange: (Int) -> Unit,
    onHexModeChange: (Boolean) -> Unit,
) {
    var deviceMenuExpanded by remember { mutableStateOf(false) }
    var baudMenuExpanded by remember { mutableStateOf(false) }
    val currentSelected = selectedDevice?.takeIf { selected ->
        devices.any { it.device.deviceId == selected.device.deviceId }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, if (connected) StatusGreen else NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            if (connected && !expanded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusIndicator(label = "已打开", color = StatusGreen)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${currentSelected?.displayName ?: "串口设备"} @ $baudRate 波特率",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = onToggle) {
                        Icon(Icons.Default.ExpandMore, contentDescription = "展开")
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Usb,
                        contentDescription = null,
                        tint = if (connected) StatusGreen else StatusNeutral,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "串口监视器",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = when {
                                connecting -> "正在打开串口..."
                                connected -> "${currentSelected?.displayName ?: "串口设备"} @ $baudRate 波特率"
                                currentSelected == null -> "请选择 USB 串口设备"
                                !hasPermission(currentSelected) -> "需要授予 USB 权限"
                                else -> "就绪，可打开 ${currentSelected.displayName}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (connected) StatusGreen else NrOnSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onToggle) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "收起" else "展开",
                        )
                    }
                }

                if (expanded) {
                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DropdownField(
                            modifier = Modifier.weight(1f),
                            label = "目标设备",
                            value = currentSelected?.displayName ?: "选择设备",
                            enabled = !connected && devices.isNotEmpty(),
                            expanded = deviceMenuExpanded,
                            onToggle = { deviceMenuExpanded = true },
                            onDismiss = { deviceMenuExpanded = false },
                        ) {
                            devices.forEach { device ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = device.displayName,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    onClick = {
                                        onSelectDevice(device)
                                        deviceMenuExpanded = false
                                    },
                                )
                            }
                        }
                        IconButton(onClick = onRefreshDevices) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "刷新 USB 串口设备列表",
                                tint = NrOnSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Button(
                            onClick = {
                                when {
                                    connected -> onDisconnect()
                                    connecting -> Unit
                                    currentSelected == null -> Unit
                                    !hasPermission(currentSelected) -> onRequestPermission(currentSelected)
                                    else -> onConnect(currentSelected, baudRate)
                                }
                            },
                            enabled = connected || (!connecting && currentSelected != null),
                        ) {
                            Text(
                                text = when {
                                    connected -> "断开连接"
                                    connecting -> "连接中..."
                                    currentSelected == null -> "连接"
                                    !hasPermission(currentSelected) -> "未授权"
                                    else -> "连接"
                                },
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DropdownField(
                            modifier = Modifier.weight(1f),
                            label = "波特率",
                            value = baudRate.toString(),
                            enabled = !connected,
                            expanded = baudMenuExpanded,
                            onToggle = { baudMenuExpanded = true },
                            onDismiss = { baudMenuExpanded = false },
                        ) {
                            BAUD_RATES.forEach { rate ->
                                DropdownMenuItem(
                                    text = { Text(rate.toString()) },
                                    onClick = {
                                        onBaudChange(rate)
                                        baudMenuExpanded = false
                                    },
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            NrFilterChip(
                                selected = !hexMode,
                                onClick = { onHexModeChange(false) },
                                label = "文本",
                                selectedColor = MaterialTheme.colorScheme.primary,
                            )
                            NrFilterChip(
                                selected = hexMode,
                                onClick = { onHexModeChange(true) },
                                label = "十六进制 (Hex)",
                                selectedColor = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    // TODO: Add DTR/RTS outline NrFilterChip toggles here once the
                    // ViewModel exposes modem-control state and actions.
                }
            }
        }
    }
}

@Composable
private fun SerialMonitorLogCard(
    modifier: Modifier = Modifier,
    logLines: List<SerialLogLine>,
    hexMode: Boolean,
    logTextForCopy: String,
    logListState: androidx.compose.foundation.lazy.LazyListState,
    inputText: String,
    lineEnding: String,
    connected: Boolean,
    onInputChange: (String) -> Unit,
    onLineEndingChange: (String) -> Unit,
    onSend: () -> Unit,
    onHexModeChange: (Boolean) -> Unit,
    onCopy: () -> Unit,
    onClearLog: () -> Unit,
    onExport: () -> Unit,
) {
    var lineEndingExpanded by remember { mutableStateOf(false) }
    val logHorizontalScroll = rememberScrollState()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NrSurface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "串口输出日志",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onCopy, enabled = logLines.isNotEmpty()) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "复制串口日志")
                }
                IconButton(onClick = onClearLog, enabled = logLines.isNotEmpty()) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "清空串口日志")
                }
                IconButton(onClick = onExport, enabled = logLines.isNotEmpty()) {
                    Icon(Icons.Default.Download, contentDescription = "导出串口日志")
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black)
                    .padding(8.dp),
            ) {
                if (logLines.isEmpty()) {
                    Text(
                        text = "暂无串口数据。请先连接设备并打开串口。",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                } else {
                    SelectionContainer {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = logListState,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(logLines) { line ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(logHorizontalScroll),
                                 ) {
                                    Column {
                                        Text(
                                            text = "[${line.timestamp}]",
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                            color = NrOnSurfaceVariant,
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                        Text(
                                            text = if (hexMode) line.text.toByteArray().joinToString(" ") {
                                                "%02X".format(it.toInt() and 0xFF)
                                            } else {
                                                line.text
                                            },
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                            color = logLevelColor(line.level),
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    enabled = connected,
                    placeholder = { Text("发送指令/数据") },
                    singleLine = true,
                )
                DropdownField(
                    modifier = Modifier.width(125.dp),
                    label = "",
                    value = formatLineEndingLabel(lineEnding),
                    enabled = true,
                    expanded = lineEndingExpanded,
                    onToggle = { lineEndingExpanded = true },
                    onDismiss = { lineEndingExpanded = false },
                ) {
                    LINE_ENDINGS.forEach { ending ->
                        DropdownMenuItem(
                            text = { Text(formatLineEndingLabel(ending)) },
                            onClick = {
                                onLineEndingChange(ending)
                                lineEndingExpanded = false
                            },
                        )
                    }
                }
                IconButton(
                    onClick = onSend,
                    enabled = connected,
                ) {
                    Icon(Icons.Default.Send, contentDescription = "发送串口数据")
                }
            }
        }
    }
}

@Composable
private fun DropdownField(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    enabled: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    menuContent: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onToggle() },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(0.5.dp, NrOutline),
            shape = RoundedCornerShape(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    if (label.isNotBlank()) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = NrOnSurfaceVariant,
                        )
                    }
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "展开 $label 列表",
                    tint = if (enabled) MaterialTheme.colorScheme.onSurface else NrOnSurfaceVariant,
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss,
            content = menuContent,
        )
    }
}

private fun logLevelColor(level: LogLevel): Color = when (level) {
    LogLevel.ERROR -> LogColorError
    LogLevel.SUCCESS -> LogColorSuccess
    LogLevel.USB -> LogColorUsb
    LogLevel.INFO -> LogColorInfo
}
