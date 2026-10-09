package com.krishna.kalam.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.krishna.kalam.core.Logger
import com.krishna.kalam.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClockBackupControls(f: Fonts) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<ClockArchive?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val data = ClockBackup.encode(Prefs.clockArchive(ctx))
                withContext(Dispatchers.IO) {
                    val stream = ctx.contentResolver.openOutputStream(uri, "wt")
                        ?: error("Cannot open backup file")
                    stream.use { it.write(data.toByteArray(Charsets.UTF_8)) }
                }
                message = "Clock backup saved. Keep this file in Downloads or another folder outside Kalam before uninstalling."
                Logger.e("ClockBackup", "clock backup saved")
            } catch (t: Exception) {
                if (t is CancellationException) throw t
                Logger.e("ClockBackup", "backup failed", t)
                message = "Backup could not be saved. Try again before uninstalling."
            } finally { busy = false }
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                pending = withContext(Dispatchers.IO) {
                    val stream = ctx.contentResolver.openInputStream(uri) ?: error("Cannot open backup")
                    stream.use { ClockBackup.read(it) }
                }
            } catch (t: Exception) {
                if (t is CancellationException) throw t
                Logger.e("ClockBackup", "backup read failed", t)
                message = "This file could not be restored. Choose a valid Kalam clock backup. Your clocks have not changed."
            } finally { busy = false }
        }
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !busy, onClick = { export.launch("Kalam_clocks_backup.json") }) {
                Text("Back up clocks", fontSize = f.data)
            }
            OutlinedButton(enabled = !busy, onClick = { restore.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                Text("Restore clocks", fontSize = f.data)
            }
        }
        TextButton(onClick = { help = true }) { Text("Keep clocks when reinstalling", fontSize = f.data) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text("Keep your clocks") },
        text = { Text("Before uninstalling, tap Back up clocks and save the file in Downloads. After reinstalling, tap Restore clocks and choose that file. Back up again after changing your clocks.\n\nOn supported phones, select Keep app data when uninstalling. Android backup may also restore your data, but it depends on your phone and backup settings.\n\nThe file includes your clock locations, names, aliases and display preferences. Weather refreshes after restore.") },
        confirmButton = { TextButton(onClick = { help = false }) { Text("OK") } })
    message?.let { text -> AlertDialog(onDismissRequest = { message = null }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } }) }
    pending?.let { archive -> AlertDialog(onDismissRequest = { if (!busy) pending = null },
        title = { Text("Restore ${archive.clocks.size} clocks?") },
        text = { Text("This replaces your current clock list and restores names, aliases, locations, order and grouping. Other settings stay as they are.") },
        dismissButton = { TextButton(enabled = !busy, onClick = { pending = null }) { Text("Cancel") } },
        confirmButton = { TextButton(enabled = !busy, onClick = { scope.launch {
            busy = true
            try {
                Prefs.restoreClocks(ctx, archive)
                pending = null
                message = "${archive.clocks.size} clocks restored."
                Logger.e("ClockBackup", "restored ${archive.clocks.size} clocks")
            } catch (t: Exception) {
                if (t is CancellationException) throw t
                Logger.e("ClockBackup", "restore failed", t)
                pending = null
                message = "Restore failed. Your previous clocks have been kept."
            } finally { busy = false }
        } }) { Text("Restore") } }) }
}
