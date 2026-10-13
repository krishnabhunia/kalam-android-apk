package com.krishna.kalam.ui

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishna.kalam.alarm.AlarmScheduler
import com.krishna.kalam.alarm.SoundService
import com.krishna.kalam.core.Logger
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * K79 — the alarm screen. Shows OVER the lock screen, turns the screen on, and always presents a
 * large STOP. Volume/power keys and back also stop it — the alarm can never be unstoppable in public.
 */
class AlarmActivity : ComponentActivity() {
    private var slot = 0
    private var seq = 0
    private var title = "Alarm"
    private var body = ""

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        readExtras(intent)
        setContent { AlarmScreen() }
    }

    override fun onNewIntent(i: Intent) { super.onNewIntent(i); readExtras(i); setContent { AlarmScreen() } }

    private fun readExtras(i: Intent?) {
        slot = i?.getIntExtra("id", 0) ?: 0
        seq = i?.getIntExtra("seq", 0) ?: 0
        title = i?.getStringExtra("title") ?: "Alarm"
        body = i?.getStringExtra("body") ?: ""
    }

    /** Any volume or power key silences immediately — before the screen is even read. */
    override fun onKeyDown(code: Int, ev: KeyEvent?): Boolean =
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN ||
            code == KeyEvent.KEYCODE_VOLUME_MUTE || code == KeyEvent.KEYCODE_POWER) { stop(); true }
        else super.onKeyDown(code, ev)

    override fun onBackPressed() { stop() }

    private fun stop() {
        SoundService.stop(this)
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            for (c in AlarmScheduler.SKY_CODES + AlarmScheduler.SNOOZE_CODES) nm.cancel(200 + c)
        } catch (t: Throwable) { Logger.e("AlarmActivity", "notification cancel failed", t) }
        finish()
    }

    private fun snooze(mins: Int) {
        val next = seq + 1
        if (next > AlarmScheduler.MAX_SNOOZE) {
            Logger.e("AlarmActivity", "snooze limit reached — stopping for today"); stop(); return
        }
        AlarmScheduler.snooze(this, slot, mins, next, title, body)
        stop()
    }

    @Composable
    private fun AlarmScreen() {
        val now = remember { LocalDateTime.now() }
        val hm = remember { DateTimeFormatter.ofPattern("HH:mm") }
        val dt = remember { DateTimeFormatter.ofPattern("EEE, dd MMM") }
        var snoozeOpen by remember { mutableStateOf(false) }
        MaterialTheme {
            Box(Modifier.fillMaxSize().background(Color(0xFF10162B)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(now.format(hm), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
                    Text(now.format(dt), color = Color(0xCCFFFFFF), fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 18.dp))
                    Surface(shape = MaterialTheme.shapes.large, color = Color.White, modifier = Modifier.fillMaxWidth()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(18.dp, 20.dp)) {
                            Text(if (slot == AlarmScheduler.CODE_SUNRISE || slot == AlarmScheduler.CODE_SUNRISE + 4) "\u2600"
                                 else if (slot == AlarmScheduler.CODE_SUNSET || slot == AlarmScheduler.CODE_SUNSET + 4) "\uD83C\uDF07"
                                 else "\uD83C\uDF19", fontSize = 30.sp)
                            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                                color = Palette.blueText, textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp))
                            if (body.isNotBlank()) Text(body, fontSize = 14.sp, textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp))
                            if (seq > 0) Text("Snoozed ${seq}/${AlarmScheduler.MAX_SNOOZE}", fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { stop() }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(54.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Palette.red)) {
                                Text("STOP", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            if (seq < AlarmScheduler.MAX_SNOOZE) {
                                OutlinedButton(onClick = { snoozeOpen = true },
                                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(48.dp)) {
                                    Text("Snooze ${AlarmScheduler.SNOOZE_MINUTES.first()} min", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                }
                                DropdownMenu(expanded = snoozeOpen, onDismissRequest = { snoozeOpen = false }) {
                                    for (m in AlarmScheduler.SNOOZE_MINUTES) DropdownMenuItem(
                                        text = { Text("Snooze $m min") },
                                        onClick = { snoozeOpen = false; snooze(m) })
                                }
                            }
                        }
                    }
                    Text("Volume or power key also stops it", color = Color(0x99FFFFFF), fontSize = 12.sp,
                        modifier = Modifier.padding(top = 14.dp))
                }
            }
        }
    }
}
