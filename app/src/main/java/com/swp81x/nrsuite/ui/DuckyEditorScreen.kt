package com.swp81x.nrsuite.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.swp81x.nrsuite.ui.components.SavedScriptPicker
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swp81x.nrsuite.ui.theme.NrOnSurfaceVariant
import com.swp81x.nrsuite.ui.theme.StatusRed

private const val SAMPLE_DUCKY = """REM NRSuite DuckyScript
DELAY 1000
STRING Hello from NRSuite
ENTER
"""

@Composable
fun DuckyEditorScreen(
    savedScripts: Map<String, String>,
    onSaveScript: (String, String) -> Unit,
    onDeleteScript: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    var script by remember { mutableStateOf(SAMPLE_DUCKY) }
    var selectedName by remember { mutableStateOf<String?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }
    var confirmDeleteName by remember { mutableStateOf<String?>(null) }

    val importPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    script = stream.readBytes().toString(Charsets.UTF_8)
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(script.toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    text = "DuckyScript 脚本编辑器",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "编写载荷脚本并导出，可直接在 BadUSB 或 BLE 模块中调用执行。",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    SavedScriptPicker(
                        names = savedScripts.keys.toList(),
                        selectedName = selectedName,
                        onSelect = { name ->
                            selectedName = name
                            script = savedScripts[name].orEmpty()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        onClick = { showSaveDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                    ) {
                        Text("保存")
                    }
                    if (selectedName != null) {
                        IconButton(
                            onClick = { confirmDeleteName = selectedName },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "删除选中的 DuckyScript 脚本",
                                tint = StatusRed,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.align(androidx.compose.ui.Alignment.End),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { importPicker.launch(arrayOf("text/plain", "*/*")) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                    ) {
                        Text("导入 .txt")
                    }
                    Button(
                        onClick = { exportLauncher.launch("ducky.txt") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text("导出 .txt")
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "常用指令: STRING, STRINGLN, DELAY, REM 以及 CTRL/ALT/GUI/SHIFT 组合键。",
                    style = MaterialTheme.typography.bodySmall,
                    color = NrOnSurfaceVariant,
                )
            }
        }

        OutlinedTextField(
            value = script,
            onValueChange = { script = it },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            label = { Text("DuckyScript 脚本代码") },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
    }

    confirmDeleteName?.let { name ->
        AlertDialog(
            onDismissRequest = { confirmDeleteName = null },
            title = { Text("删除 DuckyScript 脚本？") },
            text = {
                Text("确定删除脚本 \"$name\"？此操作不可撤销。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteScript(name)
                        if (selectedName == name) selectedName = null
                        confirmDeleteName = null
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteName = null }) {
                    Text("取消")
                }
            },
        )
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("保存 DuckyScript 脚本") },
            text = {
                OutlinedTextField(
                    value = saveName,
                    onValueChange = { saveName = it },
                    label = { Text("脚本名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSaveScript(saveName, script)
                        selectedName = saveName.trim()
                        saveName = ""
                        showSaveDialog = false
                    },
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}
