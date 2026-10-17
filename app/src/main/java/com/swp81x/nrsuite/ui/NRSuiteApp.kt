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
import androidx.compose.material.icons.filled.PersonSearch
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
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.WifiFind
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


internal enum class AppTab(val label: String) {
    HOME("首页"),
    MODULES("功能模块"),
    LOGS("运行日志"),
}

internal data class CategorySpec(
    val name: String,
    val icon: ImageVector,
    val iconTint: Color,
    val moduleIds: List<String>,
    val available: Boolean,
    val statusLabel: String? = null,
)

internal val modules = listOf(

    ModuleCardSpec(
        id = "wifi",
        title = "WiFi 扫描",
        description = "2.4 GHz 活跃扫描，获取 SSID、BSSID、信道、RSSI 及加密方式。",
        icon = Icons.Default.Wifi,
        category = "WiFi 侦察",
        radio = "2.4GHz",
        available = true,
    ),

    ModuleCardSpec(
        id = "sniff",
        title = "数据包嗅探器",
        description = "捕获 802.11 无线数据帧并导出 PCAP。",
        icon = Icons.Default.FilterCenterFocus,
        category = "WiFi 侦察",
        radio = "2.4GHz",
        available = true,
    ),

    ModuleCardSpec(
        id = "hidden_ap",
        title = "隐藏 AP 探测器",
        description = "通过 Beacon 及客户端流量发现隐藏 SSID。",
        icon = Icons.Default.VisibilityOff,
        category = "WiFi 侦察",
        radio = "2.4GHz",
        available = true,
    ),

    ModuleCardSpec(
        id = "beacon",
        title = "Beacon 广播",
        description = "伪造广播自定义或隐藏 SSID。",
        icon = Icons.Default.SettingsInputAntenna,
        category = "WiFi 攻击",
        radio = "2.4GHz",
        available = true,
    ),

    ModuleCardSpec(
        id = "deauth",
        title = "Deauth 攻击",
        description = "向指定 BSSID 发送定向解除认证攻击包。",
        icon = Icons.Default.WifiOff,
        category = "WiFi 攻击",
        radio = "2.4GHz",
        available = true,
    ),

    ModuleCardSpec(
        id = "portal",
        title = "强制认证门户",
        description = "开启钓鱼 AP 并托管自定义 HTML 页面。",
        icon = Icons.Default.Language,
        category = "WiFi 攻击",
        radio = "2.4GHz",
        available = true,
    ),

    ModuleCardSpec(
        id = "evil_twin",
        title = "Evil Twin (邪恶双胞胎)",
        description = "认证门户 + Deauth 踢网 + EAPOL 握手包抓取工作流。",
        icon = Icons.Default.ContentCopy,
        category = "WiFi 攻击",
        radio = "2.4GHz",
        available = true,
    ),

    ModuleCardSpec(
        id = "deauth_detector",
        title = "Deauth 检测器",
        description = "被动检测周围的 Deauth 与 Disassoc 异常解绑帧。",
        icon = Icons.Default.WifiFind,
        category = "WiFi 防御",
        radio = "2.4GHz",
        available = true,
    ),

    ModuleCardSpec(
        id = "rogue_ap",
        title = "伪造 AP 检测器",
        description = "将可见 AP 与受信任基线及 OUI 规则进行比对。",
        icon = Icons.Default.Router,
        category = "WiFi 防御",
        radio = "2.4GHz",
        available = true,
    ),

    ModuleCardSpec(
        id = "client_presence",
        title = "客户端探测器",
        description = "被动探测客户端管理帧，支持主动诱导重新连接。",
        icon = Icons.Default.PersonSearch,
        category = "WiFi 防御",
        radio = "2.4GHz",
        available = true,
    ),

    ModuleCardSpec(
        id = "ble_scanner",
        title = "BLE 扫描器",
        description = "扫描发现周围低功耗蓝牙设备、服务及厂商数据。",
        icon = Icons.Default.Bluetooth,
        category = "BLE",
        radio = "BLE",
        available = true,
    ),

    ModuleCardSpec(
        id = "ble_profile",
        title = "BLE GATT 服务特征",
        description = "枚举分析指定 BLE 设备的 GATT 服务与特征值（只读）。",
        icon = Icons.Default.DeveloperBoard,
        category = "BLE",
        radio = "BLE",
        available = true,
    ),

    ModuleCardSpec(
        id = "tracker_detector",
        title = "追踪器检测",
        description = "检测类似 Find My / AirTag 的 BLE 追踪器广播。",
        icon = Icons.Default.Radar,
        category = "BLE",
        radio = "BLE",
        available = true,
    ),

    ModuleCardSpec(
        id = "mesh",
        title = "Mesh 组网",
        description = "配置、监控并控制 ESP-NOW Mesh 网络。",
        icon = Icons.Default.SettingsInputAntenna,
        category = "Mesh",
        radio = "2.4GHz",
        available = true,
    ),

    ModuleCardSpec(
        id = "mac_lookup",
        title = "MAC 查询",
        description = "离线查询任意 MAC 地址的 OUI 及所属厂商。",
        icon = Icons.Default.Search,
        category = "工具",
        radio = "2.4GHz",
        available = true,
    ),

    ModuleCardSpec(
        id = "credential_manager",
        title = "凭据管理器",
        description = "查看 Evil Twin 抓取、离线破解结果及已存凭据。",
        icon = Icons.Default.Lock,
        category = "凭据",
        available = true,
    ),

    ModuleCardSpec(
        id = "wpa_cracker",
        title = "WPA/WPA2 破解器",
        description = "使用字典离线验证并破解握手包密码。",
        icon = Icons.Default.Key,
        category = "凭据",
        radio = "2.4GHz",
        available = true,
    ),

    ModuleCardSpec(
        id = "ducky",
        title = "Ducky 编辑器",
        description = "创建、导入及导出 DuckyScript 攻击脚本。",
        icon = Icons.Default.Code,
        category = "HID",
        radio = "USB",
        available = true,
    ),

    ModuleCardSpec(
        id = "badusb",
        title = "BadUSB",
        description = "原生 USB HID 键盘载荷注入。",
        icon = Icons.Default.Keyboard,
        category = "HID",
        radio = "USB",
        available = true,
    ),

    ModuleCardSpec(
        id = "ble",
        title = "BLE HID",
        description = "BadBLE 载荷注入与实时键盘模拟。",
        icon = Icons.Default.Bluetooth,
        category = "HID",
        radio = "BLE",
        available = true,
    ),

    ModuleCardSpec(
        id = "storage",
        title = "存储管理",
        description = "浏览并管理设备板载存储文件。",
        icon = Icons.Default.Folder,
        category = "存储",
        radio = "USB",
        available = true,
    ),

    ModuleCardSpec(
        id = "firmware",
        title = "固件烧录器",
        description = "通过 USB ROM Bootloader 烧录完整固件镜像。",
        icon = Icons.Default.Memory,
        category = "固件",
        radio = "USB",
        available = true,
    ),

    ModuleCardSpec(
        id = "serial_monitor",
        title = "串口监视器",
        description = "适用于 ESP32 及 USB-UART 设备的原始串口监视与调试。",
        icon = Icons.Default.DeveloperBoard,
        category = "固件",
        radio = "USB",
        available = true,
    ),

    ModuleCardSpec(
        id = "ir",
        title = "红外 (IR)",
        description = "红外发射/接收模块（规划中）。",
        icon = Icons.Default.SettingsRemote,
        category = "红外",
        radio = "IR",
        available = false,
        statusLabel = "暂不可用",
    ),

    ModuleCardSpec(
        id = "rf",
        title = "射频 (RF)",
        description = "Sub-GHz 射频无线通信模块（规划中）。",
        icon = Icons.Default.Radio,
        category = "射频",
        radio = "Sub-1GHz",
        available = false,
        statusLabel = "暂不可用",
    ),

    ModuleCardSpec(
        id = "rfid",
        title = "RFID",
        description = "RFID/NFC 读取与卡片模拟模块（规划中）。",
        icon = Icons.Default.Nfc,
        category = "RFID",
        radio = "13.56MHz",
        available = false,
        statusLabel = "暂不可用",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NRSuiteApp() {
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val viewModel = remember(appContext) {
        (appContext as NrSuiteApplication).controller
    }
    var showStartup by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(900)
        showStartup = false
    }
    Box(modifier = Modifier.fillMaxSize()) {
        NRSuiteContent(viewModel = viewModel)
        if (showStartup) {
            StartupScreen()
        }
    }
}
