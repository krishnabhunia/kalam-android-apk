package com.krishna.kalam.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.krishna.kalam.AppVersion
import com.krishna.kalam.core.Logger
import com.krishna.kalam.data.Prefs
import com.krishna.kalam.update.GitHubUpdates
import com.krishna.kalam.update.UpdateChecks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun UpdatesControls(f: Fonts, expanded: Boolean, onToggle: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by GitHubUpdates.status.collectAsState()
    val includeBeta by Prefs.includeBetaFlow(ctx).collectAsState(initial = false)
    val lastCheck by UpdateChecks.lastCheck.collectAsState()
    var dailyChecks by remember { mutableStateOf(UpdateChecks.dailyEnabled(ctx)) }
    var error by remember { mutableStateOf<String?>(null) }
    var verifying by remember { mutableStateOf(false) }   // double taps / Download during verify
    GroupBox("App updates", f, expanded, onToggle) {
        Text("Installed version: ${AppVersion.NAME}", fontSize = f.data)
        RowToggle("Check for updates every day", dailyChecks, f) { enabled -> scope.launch {
            try { UpdateChecks.setDailyEnabled(ctx, enabled); dailyChecks = enabled }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                Logger.e("Updates", "daily preference failed", e); error = "Could not save the daily update preference."
            }
        } }
        RowToggle("Include beta versions", includeBeta, f) { enabled -> scope.launch {
            try { Prefs.setIncludeBeta(ctx, enabled); UpdateChecks.check(ctx, enabled) }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                Logger.e("Updates", "beta preference failed", e); error = "Could not save the beta preference."
            }
        } }
        Text("Beta versions are optional and may have unfinished features.", fontSize = f.data,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(enabled = !status.busy, onClick = { scope.launch {
            try { UpdateChecks.check(ctx, includeBeta) }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                Logger.e("Updates", "manual check failed", e); error = "Could not save the update check result."
            }
        } }) {
            Text("Check GitHub for updates", fontSize = f.data)
        }
        Text(status.message, fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant)
        lastCheck?.takeIf { it.includeBeta == includeBeta }?.let { checked ->
            val whenChecked = remember(checked.at) {
                DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(checked.at))
            }
            Text("Last checked: $whenChecked. ${checked.message}", fontSize = f.data,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (status.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        status.release?.takeIf { includeBeta || !it.beta }?.let { release ->
            Button(enabled = !status.busy && !verifying, onClick = { scope.launch { GitHubUpdates.download(ctx, release) } }) {
                Text("Download v${release.version}", fontSize = f.data)
            }
            status.apk?.let { apk ->
                Button(enabled = !status.busy && !verifying, onClick = { scope.launch {
                    verifying = true
                    try {
                        // Parsing and hashing a ~10 MB APK on the main thread risked an ANR.
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            GitHubUpdates.verify(ctx, apk, release.version)
                        }
                        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
                            ctx.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:${ctx.packageName}")))
                            error = "Allow Kalam to install updates, then return here and tap Install again."
                        } else {
                            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", apk)
                            ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            Logger.e("Updates", "opened Android installer ${release.version}")
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Logger.e("Updates", "installer could not open", e)
                        error = e.message ?: "Could not open Android's installer."
                    } finally { verifying = false }
                } }) { Text("Install v${release.version}", fontSize = f.data) }
            }
            TextButton(onClick = {
                try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.url))) }
                catch (e: Exception) { Logger.e("Updates", "release page failed", e); error = "Could not open the release page." }
            }) { Text("Release notes on GitHub", fontSize = f.data) }
        }
        Text("Checks automatically when Kalam starts. Daily background checks use an internet connection; Android may delay them to save battery. Downloads are verified before installation. Android asks you to confirm the update; your clocks and settings stay in place when the signing key matches.",
            fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, text = { Text(message) },
        confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } }) }
}
