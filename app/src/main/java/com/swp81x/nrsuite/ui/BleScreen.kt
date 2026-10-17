package com.swp81x.nrsuite.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.swp81x.nrsuite.ui.components.SavedScriptPicker
import com.swp81x.nrsuite.ui.components.StatusIndicator
import com.swp81x.nrsuite.ui.theme.NrOutline
import com.swp81x.nrsuite.ui.theme.NrSurfaceVariant
import com.swp81x.nrsuite.ui.theme.NrOnSurface
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusAmber
import com.swp81x.nrsuite.ui.theme.StatusGreen
import com.swp81x.nrsuite.ui.theme.StatusNeutral
import com.swp81x.nrsuite.ui.theme.StatusRed

private val specialKeys = listOf(
    "ENTER", "TAB", "ESC", "BACKSPACE", "DELETE",
    "UP", "DOWN", "LEFT", "RIGHT", "HOME", "END",
    "PAGEUP", "PAGEDOWN",
)

@Composable
fun BleScreen(
    connected: Boolean,
    advertising: Boolean,
    bleConnected: Boolean,
    peer: String,
    selectedPayloadName: String?,
    savedScripts: Map<String, String>,
    modifiers: Set<String>,
    modifierHold: Boolean,
    bleScriptRunning: Boolean,
    onUseSavedScript: (String) -> Unit,
    onChoosePayload: () -> Unit,
    onClearPayload: () -> Unit,
    onStartAdvertising: (String) -> Unit,
    onStop: () -> Unit,
    onRunPayload: () -> Unit,
    onRealtimeInput: (inserted: String, backspaces: Int) -> Unit,
    onSpecialKey: (String) -> Unit,
    onModifierChange: (String, Boolean) -> Unit,
    onModifierHoldChange: (Boolean) -> Unit,
    onMouseMove: (Int, Int) -> Unit,
    onMouseScroll: (Int) -> Unit,
    onMouseButton: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var advertiseName by rememberSaveable { mutableStateOf("NRSuite Keyboard") }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var realtimeText by rememberSaveable { mutableStateOf("") }
    val canRealtime = connected && bleConnected
    val context = LocalContext.current

    LaunchedEffect(bleConnected) {
        if (!bleConnected) {
            realtimeText = ""
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("载荷执行") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        if (!canRealtime) {
                            Toast.makeText(
                                context,
                                "请先连接并配对 BLE 主机以使用实时 HID 交互。",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    text = {
                        Text(
                            text = "实时 HID 模拟",
                            color = if (canRealtime) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                NrOnSurfaceVariant.copy(alpha = 0.65f)
                            },
                        )
                    },
                )
            }

            Spacer(Modifier.height(10.dp))

            if (selectedTab == 0) {
                PayloadTab(
                    connected = connected,
                    advertising = advertising,
                    bleConnected = bleConnected,
                    bleScriptRunning = bleScriptRunning,
                    peer = peer,
                    advertiseName = advertiseName,
                    onAdvertiseNameChange = { advertiseName = it },
                    selectedPayloadName = selectedPayloadName,
                    savedScripts = savedScripts,
                    onUseSavedScript = onUseSavedScript,
                    onChoosePayload = onChoosePayload,
                    onClearPayload = onClearPayload,
                    onRunPayload = onRunPayload,
                )
            } else {
                RealtimeHidTab(
                    connected = connected,
                    bleConnected = bleConnected,
                    modifiers = modifiers,
                    modifierHold = modifierHold,
                    realtimeText = realtimeText,
                    onRealtimeTextChange = { newText ->
                        val oldText = realtimeText
                        val prefix = commonPrefixLength(oldText, newText)
                        val backspaces = oldText.length - prefix
                        val inserted = newText.substring(prefix)
                        realtimeText = newText
                        onRealtimeInput(inserted, backspaces)
                    },
                    onClearText = { realtimeText = "" },
                    onSpecialKey = onSpecialKey,
                    onModifierChange = onModifierChange,
                    onModifierHoldChange = onModifierHoldChange,
                    onMouseMove = onMouseMove,
                    onMouseScroll = onMouseScroll,
                    onMouseButton = onMouseButton,
                )
            }
        }

        if (connected && advertising) {
            ModuleActionFab(
                onClick = onStop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = StatusRed,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Stop, contentDescription = "停止 BLE")
            }
        } else if (connected) {
            ModuleActionFab(
                onClick = { onStartAdvertising(advertiseName) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "启动 BLE")
            }
        }
    }
}

@Composable
private fun PayloadTab(
    connected: Boolean,
    advertising: Boolean,
    bleConnected: Boolean,
    bleScriptRunning: Boolean,
    peer: String,
    advertiseName: String,
    onAdvertiseNameChange: (String) -> Unit,
    selectedPayloadName: String?,
    savedScripts: Map<String, String>,
    onUseSavedScript: (String) -> Unit,
    onChoosePayload: () -> Unit,
    onClearPayload: () -> Unit,
    onRunPayload: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ConfigCard(
            connected = connected,
            bleConnected = bleConnected,
            bleScriptRunning = bleScriptRunning,
            advertising = advertising,
            advertiseName = advertiseName,
            onAdvertiseNameChange = onAdvertiseNameChange,
            selectedPayloadName = selectedPayloadName,
            savedScripts = savedScripts,
            onUseSavedScript = onUseSavedScript,
            onChoosePayload = onChoosePayload,
            onClearPayload = onClearPayload,
            onRunPayload = onRunPayload,
        )

        StatusCard(connected = connected, advertising = advertising, bleConnected = bleConnected, peer = peer)
    }
}

@Composable
private fun RealtimeHidTab(
    connected: Boolean,
    bleConnected: Boolean,
    modifiers: Set<String>,
    modifierHold: Boolean,
    realtimeText: String,
    onRealtimeTextChange: (String) -> Unit,
    onClearText: () -> Unit,
    onSpecialKey: (String) -> Unit,
    onModifierChange: (String, Boolean) -> Unit,
    onModifierHoldChange: (Boolean) -> Unit,
    onMouseMove: (Int, Int) -> Unit,
    onMouseScroll: (Int) -> Unit,
    onMouseButton: (String, Boolean) -> Unit,
) {
    val enabled = connected && bleConnected

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(0.5.dp, NrOutline),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    text = "实时虚拟键盘",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (enabled) "即打即发，实时输入。Enter 发送回车换行。" else "请先连接 BLE 主机以启用实时输入。",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) StatusGreen else StatusAmber,
                )

                Spacer(Modifier.height(10.dp))
                Text(
                    text = "修饰键 (Modifiers)",
                    style = MaterialTheme.typography.labelMedium,
                    color = NrOnSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf("CTRL", "SHIFT", "ALT", "GUI").forEach { key ->
                        val active = key in modifiers
                        OutlinedButton(
                            onClick = { onModifierChange(key, !active) },
                            enabled = enabled,
                        ) {
                            Text(if (active) "$key [开]" else key)
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("保持修饰键锁定", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (modifierHold) {
                                "修饰键将持续保持按下状态，直到手动点灭。"
                            } else {
                                "在下一次按键或文本输入后，修饰键将自动释放。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = NrOnSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = modifierHold,
                        onCheckedChange = onModifierHoldChange,
                        enabled = enabled,
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = "特殊控制键",
                    style = MaterialTheme.typography.labelMedium,
                    color = NrOnSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    specialKeys.forEach { key ->
                        OutlinedButton(
                            onClick = { onSpecialKey(key) },
                            enabled = enabled,
                        ) {
                            Text(key)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = realtimeText,
                    onValueChange = onRealtimeTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    minLines = 2,
                    maxLines = 4,
                    label = { Text("在此输入文本") },
                    placeholder = { Text("实时发送键盘按键") },
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onClearText, enabled = enabled && realtimeText.isNotEmpty()) {
                        Text("清空输入框")
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(0.5.dp, NrOutline),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    text = "虚拟鼠标",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "在触控板区域滑动以移动指针，按键对应鼠标按键，滚轮按钮控制上下滚动。",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )

                Spacer(Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.45f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NrSurfaceVariant)
                        .pointerInput(enabled) {
                            if (!enabled) return@pointerInput
                            var accumulatedX = 0f
                            var accumulatedY = 0f
                            detectDragGestures(
                                onDragStart = {
                                    accumulatedX = 0f
                                    accumulatedY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    accumulatedX += dragAmount.x
                                    accumulatedY += dragAmount.y
                                    val dx = accumulatedX.roundToInt()
                                    val dy = accumulatedY.roundToInt()
                                    if (dx != 0 || dy != 0) {
                                        accumulatedX -= dx
                                        accumulatedY -= dy
                                        onMouseMove(dx, dy)
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (enabled) "触控板滑动区域" else "请先连接主机",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            onMouseButton("LEFT", true)
                            onMouseButton("LEFT", false)
                        },
                        enabled = enabled,
                    ) { Text("左键") }
                    OutlinedButton(
                        onClick = {
                            onMouseButton("MIDDLE", true)
                            onMouseButton("MIDDLE", false)
                        },
                        enabled = enabled,
                    ) { Text("中键") }
                    OutlinedButton(
                        onClick = {
                            onMouseButton("RIGHT", true)
                            onMouseButton("RIGHT", false)
                        },
                        enabled = enabled,
                    ) { Text("右键") }
                }

                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onMouseScroll(1) },
                        enabled = enabled,
                    ) { Text("向上滚动") }
                    OutlinedButton(
                        onClick = { onMouseScroll(-1) },
                        enabled = enabled,
                    ) { Text("向下滚动") }
                }
            }
        }
    }
}

@Composable
private fun ConfigCard(
    connected: Boolean,
    bleConnected: Boolean,
    bleScriptRunning: Boolean,
    advertising: Boolean,
    advertiseName: String,
    onAdvertiseNameChange: (String) -> Unit,
    selectedPayloadName: String?,
    savedScripts: Map<String, String>,
    onUseSavedScript: (String) -> Unit,
    onChoosePayload: () -> Unit,
    onClearPayload: () -> Unit,
    onRunPayload: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "BLE HID 键鼠模拟",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "BadBLE 脚本载荷与实时交互控制",
                        style = MaterialTheme.typography.bodySmall,
                        color = NrOnSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = advertiseName,
                onValueChange = onAdvertiseNameChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !advertising,
                label = { Text("BLE 广播名称") },
                singleLine = true,
            )

            Spacer(Modifier.height(10.dp))
            SavedScriptPicker(
                names = savedScripts.keys.toList(),
                selectedName = selectedPayloadName,
                onSelect = onUseSavedScript,
                enabled = !bleScriptRunning,
                label = "DuckyScript 脚本载荷",
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onChoosePayload, enabled = !bleScriptRunning) {
                    Text("选择载荷")
                }
                OutlinedButton(
                    onClick = onClearPayload,
                    enabled = !bleScriptRunning && selectedPayloadName != null,
                ) {
                    Text("清除")
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onRunPayload,
                enabled = connected && bleConnected && selectedPayloadName != null && !bleScriptRunning,
            ) {
                Text(if (bleScriptRunning) "正在执行载荷..." else "执行载荷")
            }
        }
    }
}

@Composable
private fun StatusCard(
    connected: Boolean,
    advertising: Boolean,
    bleConnected: Boolean,
    peer: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, NrOutline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("BLE 状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            StatusIndicator(
                label = when {
                    !connected -> "未连接硬件设备"
                    bleConnected -> "主机已连接"
                    advertising -> "正在广播 / 等待主机连接"
                    else -> "空闲"
                },
                color = when {
                    !connected -> StatusNeutral
                    bleConnected -> StatusGreen
                    advertising -> StatusAmber
                    else -> StatusNeutral
                },
            )
            if (peer.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "对端主机: $peer",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = NrOnSurfaceVariant,
                )
            }
        }
    }
}

private fun commonPrefixLength(a: String, b: String): Int {
    val max = minOf(a.length, b.length)
    var index = 0
    while (index < max && a[index] == b[index]) index++
    return index
}
