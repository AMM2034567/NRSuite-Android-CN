package com.swp81x.nrsuite.ui

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.WindowManager
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.swp81x.nrsuite.ui.theme.LogColorError
import com.swp81x.nrsuite.ui.theme.LogColorInfo
import com.swp81x.nrsuite.ui.theme.LogColorSuccess
import com.swp81x.nrsuite.ui.theme.LogColorUsb
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.swp81x.nrsuite.core.history.HistoryLevel
import com.swp81x.nrsuite.core.history.HistoryEntry
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.swp81x.nrsuite.MainViewModel
import com.swp81x.nrsuite.NrSuiteApplication
import com.swp81x.nrsuite.core.defense.OuiRule
import com.swp81x.nrsuite.core.oui.OuiDatabaseStatus
import com.swp81x.nrsuite.core.defense.OuiRuleAction
import com.swp81x.nrsuite.core.defense.normalizeOuiPrefix
import com.swp81x.nrsuite.core.session.ConnectionState
import com.swp81x.nrsuite.core.usb.UsbSerialDevice
import com.swp81x.nrsuite.ui.components.ModuleCard
import com.swp81x.nrsuite.ui.components.ModuleCardSpec
import com.swp81x.nrsuite.ui.components.NrFilterChip
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.theme.NrAccent
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrOnSurface
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrSurface
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed
import kotlinx.coroutines.launch
import org.json.JSONObject
import com.swp81x.nrsuite.core.log.LogEntry
import com.swp81x.nrsuite.core.log.LogLevel
import com.swp81x.nrsuite.ui.theme.LogBgError

private fun inferChipFromFirmwareName(name: String?): String? {
    val normalized = name?.lowercase() ?: return null
    return when {
        "esp32s3" in normalized || "esp32-s3" in normalized -> "ESP32-S3"
        "esp32c3" in normalized || "esp32-c3" in normalized -> "ESP32-C3"
        "esp32s2" in normalized || "esp32-s2" in normalized -> "ESP32-S2"
        "esp32-generic" in normalized || "esp32dev" in normalized || "esp32_dev" in normalized -> "ESP32"
        "esp32" in normalized -> "ESP32"
        else -> null
    }
}

@Composable
internal fun SettingsScreen(
    connectionState: ConnectionState,
    exportDirectoryName: String,
    firmwareFileName: String?,
    firmwareFlashSize: Long,
    firmwareFlashing: Boolean,
    firmwareFlashProgress: Int,
    firmwareFlashStatus: String?,
    devices: List<UsbSerialDevice>,
    usbManager: UsbManager,
    permissionRevision: Int,
    selectedFlashTarget: UsbSerialDevice?,
    onChooseExportDirectory: () -> Unit,
    onChooseFirmware: () -> Unit,
    onClearFirmware: () -> Unit,
    onRefreshDevices: () -> Unit,
    onRequestPermission: (UsbDevice) -> Unit,
    onSelectFlashTarget: (UsbDevice) -> Unit,
    onStartFirmwareFlash: (targetChip: String, skipReset: Boolean, eraseBeforeFlash: Boolean) -> Unit,
    ouiRules: List<OuiRule> = emptyList(),
    onAddOuiRule: (ouiPrefix: String, label: String, action: OuiRuleAction) -> Unit = { _, _, _ -> },
    onDeleteOuiRule: (id: String) -> Unit = {},
    ouiDatabaseStatus: OuiDatabaseStatus = OuiDatabaseStatus.NotDownloaded,
    ouiDatabaseProgress: Float? = null,
    onDownloadOuiDatabase: () -> Unit = {},
    flasherOnly: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val currentFirmware = (connectionState as? ConnectionState.Connected)?.firmwareVersion
    val context = androidx.compose.ui.platform.LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    val chip = (connectionState as? ConnectionState.Connected)?.chip
    val boardName = when (chip) {
        "ESP32-C3" -> "ESP32-C3"
        "ESP32-S3" -> "ESP32-S3"
        "ESP32-S2" -> "ESP32-S2"
        "ESP32" -> "标准 ESP32 开发板"
        else -> "未连接 / 未知"
    }
    var selectedTargetChip by remember { mutableStateOf(chip ?: "ESP32") }
    val targetHasPermission = selectedFlashTarget?.let { usbManager.hasPermission(it.device) } == true
    @Suppress("UNUSED_EXPRESSION")
    permissionRevision
    var manualBootloader by remember { mutableStateOf(false) }
    var advancedExpanded by remember { mutableStateOf(false) }
    var showFlashConfirm by remember { mutableStateOf(false) }
    LaunchedEffect(firmwareFileName, chip) {
        val inferred = inferChipFromFirmwareName(firmwareFileName)
        when {
            inferred != null -> selectedTargetChip = inferred
            !chip.isNullOrBlank() -> selectedTargetChip = chip
        }
    }
    LaunchedEffect(devices) {
        if (devices.size == 1 &&
            selectedFlashTarget == null &&
            usbManager.hasPermission(devices[0].device)
        ) {
            onSelectFlashTarget(devices[0].device)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!flasherOnly) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("NRSuite 工作根目录", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = exportDirectoryName,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = onChooseExportDirectory) {
                    Text("选择根目录")
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "抓包文件将保存在该目录下的 Pcap/ 子目录中。只需设置一次，应用会自动创建目录结构。",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
        }
        }

        if (flasherOnly) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        ) {
            Column(Modifier.padding(14.dp)) {

                Text(
                    text = "当前固件版本: ${currentFirmware ?: "未连接"}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
                Text(
                    text = "识别到的开发板: $boardName",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    text = "1. 目标烧录设备",
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "选择待烧录的开发板并授予 USB 访问权限。",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "烧录目标设备",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRefreshDevices, enabled = !firmwareFlashing) {
                        Text("刷新")
                    }
                }
                if (devices.isEmpty()) {
                    Text(
                        text = "未找到受支持的 USB 串口设备。请插入开发板并点击刷新。",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusAmber,
                    )
                } else {
                    devices.forEach { entry ->
                        val hasPermission = usbManager.hasPermission(entry.device)
                        val selected = hasPermission &&
                            selectedFlashTarget?.device?.deviceId == entry.device.deviceId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = entry.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                                Text(
                                    text = if (hasPermission) "已授权" else "需要 USB 权限",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NrOnSurfaceVariant,
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    if (hasPermission) {
                                        onSelectFlashTarget(entry.device)
                                    } else {
                                        onRequestPermission(entry.device)
                                    }
                                },
                                enabled = !firmwareFlashing && !selected,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                            ) {
                                Text(
                                    when {
                                        selected -> "已选择"
                                        !hasPermission -> "授予权限"
                                        else -> "选择"
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                Text(
                    text = "2. 固件镜像文件",
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (firmwareFileName != null && firmwareFlashSize > 0) {
                        "$firmwareFileName  (${firmwareFlashSize / 1024} KB)"
                    } else {
                        firmwareFileName ?: "未选择已合并的固件 .bin 文件"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                if (firmwareFileName == null) {
                    Button(
                        onClick = onChooseFirmware,
                        enabled = targetHasPermission && !firmwareFlashing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text("选择固件 .bin")
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onChooseFirmware,
                            enabled = targetHasPermission && !firmwareFlashing,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                        ) {
                            Text("更换")
                        }
                        TextButton(
                            onClick = onClearFirmware,
                            enabled = !firmwareFlashing,
                        ) {
                            Text("清除")
                        }
                    }
                }
                if (!targetHasPermission) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "请先在上方选择目标设备再选取固件。",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusAmber,
                    )
                }
                Spacer(Modifier.height(12.dp))

                Text(
                    text = "3. 参数确认与烧录",
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "目标芯片架构",
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("ESP32", "ESP32-S2", "ESP32-S3", "ESP32-C3").forEach { option ->
                        NrFilterChip(
                            selected = selectedTargetChip == option,
                            onClick = { selectedTargetChip = option },
                            enabled = !firmwareFlashing,
                            label = option,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "烧录偏移地址 (Offset): 0x0",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { advancedExpanded = !advancedExpanded }) {
                    Text(if (advancedExpanded) "收起高级选项" else "展开高级选项")
                }
                if (advancedExpanded) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = manualBootloader,
                            onCheckedChange = { manualBootloader = it },
                            enabled = !firmwareFlashing,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "跳过自动复位 (已处于 ROM Bootloader 模式)",
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "仅当开发板已处于 ROM Bootloader 模式时使用。否则可按住 BOOT 键并点按 RESET 键，再开启此选项进行烧录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "此操作将完整的合并固件 .bin 写入基地址 0x0。待写入的闪存扇区将被自动擦除。此操作非增量 OTA 升级。",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusAmber,
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { showFlashConfirm = true },
                    enabled = selectedFlashTarget != null &&
                        targetHasPermission &&
                        firmwareFileName != null &&
                        !firmwareFlashing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(if (firmwareFlashing) "正在烧录..." else "开始烧录固件")
                }
                if (showFlashConfirm) {
                    AlertDialog(
                        onDismissRequest = { showFlashConfirm = false },
                        title = { Text("确认烧录固件？") },
                        text = {
                            Text(
                                "即将向设备 ${selectedFlashTarget?.displayName ?: "选定设备"} 写入选定固件并在烧录完成后自动重启。" +
                                    "烧录过程中请勿拔出 USB 数据线。",
                                color = NrOnSurfaceVariant,
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showFlashConfirm = false
                                    onStartFirmwareFlash(selectedTargetChip, manualBootloader, false)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = StatusAmber,
                                    contentColor = com.swp81x.nrsuite.ui.theme.NrBackground,
                                ),
                            ) { Text("立即烧录") }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = { showFlashConfirm = false }) {
                                Text("取消")
                            }
                        },
                    )
                }
                if (firmwareFlashing || firmwareFlashProgress > 0) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { firmwareFlashProgress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = firmwareFlashStatus ?: "正在准备...",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = if (firmwareFlashStatus?.startsWith("Flash failed") == true ||
                            firmwareFlashStatus?.startsWith("烧录失败") == true
                        ) {
                            MaterialTheme.colorScheme.error
                        } else {
                            NrOnSurfaceVariant
                        },
                    )
                }
            }
        }
        }

        if (!flasherOnly) {

        OuiDatabaseCard(
            status = ouiDatabaseStatus,
            progress = ouiDatabaseProgress,
            onDownload = onDownloadOuiDatabase,
        )

        OuiRulesCard(
            ouiRules = ouiRules,
            onAddOuiRule = onAddOuiRule,
            onDeleteOuiRule = onDeleteOuiRule,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("外观设置", fontWeight = FontWeight.SemiBold)
                Text(
                    text = "已锁定深色极客技术主题风格。",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("开发者", fontWeight = FontWeight.SemiBold)
                Text(
                    text = "NRSuite 由 @7wp81x 开发维护。",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/7wp81x/NRSuite"))
                        )
                    }
                }) {
                    Text("访问 GitHub 主页")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("关于 NRSuite", fontWeight = FontWeight.SemiBold)
                Text(
                    text = "NRSuite Android 客户端。BLE、WiFi、HID 键鼠注入以及底层存储操作由外接 ESP32 硬件协同执行。",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
        }
        }

    }
}

@Composable
private fun OuiRulesCard(
    ouiRules: List<OuiRule>,
    onAddOuiRule: (ouiPrefix: String, label: String, action: OuiRuleAction) -> Unit,
    onDeleteOuiRule: (id: String) -> Unit,
) {
    var showAddForm by remember { mutableStateOf(false) }
    var ouiInput by remember { mutableStateOf("") }
    var labelInput by remember { mutableStateOf("") }
    var actionInput by remember { mutableStateOf(OuiRuleAction.WHITELIST) }

    val normalizedOui = normalizeOuiPrefix(ouiInput)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("伪造 AP 监测 — OUI 厂商规则", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "将您所在区域常见的合法 AP 硬件厂商加入白名单以减少误报。" +
                    "将常用的伪造 AP 黑客硬件工具厂商加入黑名单，以便高灵敏度告警。",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))

            if (ouiRules.isEmpty()) {
                Text(
                    text = "暂无自定义规则。",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ouiRules.forEach { rule ->
                        OuiRuleRow(rule = rule, onDelete = { onDeleteOuiRule(rule.id) })
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            if (!showAddForm) {
                OutlinedButton(onClick = { showAddForm = true }) {
                    Text("添加规则")
                }
            } else {
                Column {
                    OutlinedTextField(
                        value = ouiInput,
                        onValueChange = { ouiInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("OUI 前缀 (例如 AA:BB:CC)") },
                        isError = ouiInput.isNotBlank() && normalizedOui == null,
                        singleLine = true,
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = labelInput,
                        onValueChange = { labelInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("备注标签") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NrFilterChip(
                            selected = actionInput == OuiRuleAction.WHITELIST,
                            onClick = { actionInput = OuiRuleAction.WHITELIST },
                            label = "白名单",
                        )
                        NrFilterChip(
                            selected = actionInput == OuiRuleAction.BLACKLIST,
                            onClick = { actionInput = OuiRuleAction.BLACKLIST },
                            label = "黑名单",
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val normalized = normalizeOuiPrefix(ouiInput) ?: return@Button
                                onAddOuiRule(normalized, labelInput.trim(), actionInput)
                                ouiInput = ""
                                labelInput = ""
                                actionInput = OuiRuleAction.WHITELIST
                                showAddForm = false
                            },
                            enabled = normalizedOui != null && labelInput.isNotBlank(),
                        ) {
                            Text("保存")
                        }
                        TextButton(onClick = { showAddForm = false }) {
                            Text("取消")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OuiRuleRow(
    rule: OuiRule,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = rule.ouiPrefix,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                fontWeight = FontWeight.SemiBold,
            )
            if (rule.label.isNotBlank()) {
                Text(
                    text = rule.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
        }

        val badgeColor = when (rule.action) {
            OuiRuleAction.WHITELIST -> NrAccent
            OuiRuleAction.BLACKLIST -> StatusRed
        }
        Row(
            modifier = Modifier
                .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(
                text = when (rule.action) {
                    OuiRuleAction.WHITELIST -> "白名单"
                    OuiRuleAction.BLACKLIST -> "黑名单"
                },
                style = MaterialTheme.typography.labelSmall,
                color = badgeColor,
                maxLines = 1,
                softWrap = false,
            )
        }

        IconButton(onClick = { confirmDelete = true }) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "删除 OUI 规则",
                tint = NrOnSurfaceVariant,
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除规则？") },
            text = { Text("确定移除 OUI 前缀 ${rule.ouiPrefix} 的规则？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun OuiDatabaseCard(
    status: OuiDatabaseStatus,
    progress: Float?,
    onDownload: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("MAC 硬件厂商数据库", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "下载 IEEE 官方 OUI 注册表用于离线厂商与 MAC 地址查询。" +
                    "所有 MAC 查询完全在本地设备端离线运行，不泄露任何网络数据。",
                style = MaterialTheme.typography.bodySmall,
                color = NrOnSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))

            when (status) {
                OuiDatabaseStatus.NotDownloaded -> {
                    Text(
                        text = "数据库未下载。",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
                OuiDatabaseStatus.Downloading -> {
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { (progress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "正在下载… ${progress.toInt()}%",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = StatusAmber,
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "正在下载…",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusAmber,
                        )
                    }
                }
                is OuiDatabaseStatus.Ready -> {
                    Text(
                        text = "已收录 ${status.vendorCount} 条 OUI 记录 · 更新于 ${status.updatedAt}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = NrOnSurfaceVariant,
                    )
                }
                is OuiDatabaseStatus.Error -> {
                    Text(
                        text = status.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusRed,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onDownload,
                enabled = status !is OuiDatabaseStatus.Downloading,
            ) {
                Text(if (status is OuiDatabaseStatus.Ready) "更新数据库" else "下载数据库")
            }
        }
    }
}
