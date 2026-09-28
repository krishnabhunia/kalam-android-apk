package com.krishna.kalam.alarm

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.krishna.kalam.R
import com.krishna.kalam.core.*
import com.krishna.kalam.data.Prefs
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

const val CHANNEL_ID = "kalam_alerts"
const val ALARM_CHANNEL_ID = "kalam_alarms_v1"   // K78 — real alarms (sky events)
const val ACTION_DISMISS = "com.krishna.kalam.DISMISS_ALARM"

object Notifs {
    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(CHANNEL_ID, "Kalam alerts", NotificationManager.IMPORTANCE_HIGH)
            ch.setSound(null, null)              // D15: sound is app-managed for custom duration
            ch.enableVibration(true)
            nm.createNotificationChannel(ch)
        }
    }

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
        ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun post(ctx: Context, id: Int, title: String, body: String) {
        if (!canNotify(ctx)) { Logger.e("Notifs", "notification blocked — permission missing"); return }
        try {
            ensureChannel(ctx)
            val open = PendingIntent.getActivity(ctx, 0,
                ctx.packageManager.getLaunchIntentForPackage(ctx.packageName),
                PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(title).setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(open).setAutoCancel(true)
                .build()
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(id, n)
        } catch (t: Throwable) { Logger.e("Notifs", "post failed", t) }
    }

    /** K78 — real-alarm channel: high importance, no channel sound (app-managed alarm-stream audio). */
    fun ensureAlarmChannel(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(ALARM_CHANNEL_ID) == null) {
            val ch = NotificationChannel(ALARM_CHANNEL_ID, "Kalam alarms", NotificationManager.IMPORTANCE_HIGH)
            ch.setSound(null, null)
            ch.enableVibration(false)          // vibration is app-managed (repeating pattern)
            ch.setBypassDnd(false)
            nm.createNotificationChannel(ch)
        }
    }

    /** K78 — full-screen alarm notification that keeps ringing until Dismiss. */
    fun postAlarm(ctx: Context, id: Int, title: String, body: String, slot: Int = 0, seq: Int = 0) {
        if (!canNotify(ctx)) { Logger.e("Notifs", "alarm notification blocked — permission missing"); return }
        try {
            ensureAlarmChannel(ctx)
            val open = PendingIntent.getActivity(ctx, id,
                Intent(ctx, com.krishna.kalam.ui.AlarmActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra("title", title).putExtra("body", body).putExtra("id", slot).putExtra("seq", seq),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val dismiss = PendingIntent.getBroadcast(ctx, 900 + id,
                Intent(ctx, AlarmActionReceiver::class.java).setAction(ACTION_DISMISS),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(ctx, ALARM_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(title).setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true).setAutoCancel(false)
                .setContentIntent(open)
                .setFullScreenIntent(open, true)          // wakes the screen like a clock alarm
                .addAction(R.drawable.ic_notif, "STOP", dismiss)   // K79 — real icon so the action always renders
                .build()
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(id, n)
        } catch (t: Throwable) { Logger.e("Notifs", "alarm post failed", t) }
    }
}

/** K78 — Dismiss button: stops the ringing and clears the alarm notifications. */
class AlarmActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != ACTION_DISMISS) return
        SoundService.stop(ctx)
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            for (id in AlarmScheduler.SKY_CODES) nm.cancel(200 + id)
        } catch (t: Throwable) { Logger.e("Alarm", "dismiss cancel failed", t) }
    }
}

/** K64 — fired-slot ledger: "code@yyyy-MM-dd" markers. Sync SharedPreferences (receiver-safe). */
object FiredLedger {
    private const val NAME = "kalam_fired_v1"
    private fun sp(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    /** K79 — seq > 0 distinguishes repeat snooze fires of the same slot on the same day. */
    fun key(code: Int, day: LocalDate, seq: Int = 0) = if (seq > 0) "$code@$day#$seq" else "$code@$day"
    fun has(ctx: Context, code: Int, day: LocalDate): Boolean = try { sp(ctx).contains(key(code, day)) } catch (_: Throwable) { false }
    /** true = newly recorded (first fire); false = was already fired (duplicate). */
    fun markIfNew(ctx: Context, code: Int, day: LocalDate, seq: Int = 0): Boolean = try {
        val k = key(code, day, seq); val p = sp(ctx)
        if (p.contains(k)) false else { p.edit().putLong(k, System.currentTimeMillis()).apply(); true }
    } catch (_: Throwable) { true }   // ledger broken → fail OPEN (never silence a real alarm)
    /** Keep today + yesterday only. */
    fun prune(ctx: Context, today: LocalDate) = try {
        val keep = setOf(today.toString(), today.minusDays(1).toString())
        val p = sp(ctx); val ed = p.edit()
        p.all.keys.forEach { if (it.substringAfter('@', "").substringBefore('#') !in keep) ed.remove(it) }
        ed.apply()
    } catch (_: Throwable) {}
}

/** Six stable request codes: kalam ordinal * 2 (+1 for end). Today-only arming (D8). */
object AlarmScheduler {
    private val FMT = DateTimeFormatter.ofPattern("HH:mm")

    fun exactAllowed(ctx: Context): Boolean {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    fun rearmToday(ctx: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val s = Prefs.readSettings(ctx)
                val place = Prefs.resolvePlace(ctx, s.activePlaceId) ?: run {
                    Logger.e("Alarm", "active place unresolvable — skipping arm"); return@launch
                }
                val zone = ZoneId.of(place.tz)
                val today = LocalDate.now(zone)
                val day = Engine.computeDay(today, place.lat, place.lon, zone, s.alarmConvention)
                val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                for (k in Kalam.entries) for (end in listOf(false, true)) cancelSlot(ctx, am, k, end)
                for (c in listOf(CODE_SUNRISE, CODE_SUNSET, CODE_MOONRISE, CODE_MOONSET)) am.cancel(pendingCode(ctx, c, "", ""))   // K76/K77
                if (day !is DayResult.Ok) { Logger.e("Alarm", "day unavailable — nothing armed"); return@launch }
                FiredLedger.prune(ctx, today)
                // K76 — sun slots: displayed-convention sunrise/sunset − lead, today only, masked days only
                for ((code, on, lead, mask, evN, name) in listOf(
                    SunSlot(CODE_SUNRISE, s.sunriseAlarm, s.sunriseLead, s.sunriseDays, day.sky.sunrise, "Sunrise"),
                    SunSlot(CODE_SUNSET, s.sunsetAlarm, s.sunsetLead, s.sunsetDays, day.sky.sunset, "Sunset"),
                    SunSlot(CODE_MOONRISE, s.moonriseAlarm, s.moonriseLead, s.moonriseDays, day.sky.moonRise, "Moonrise"),   // K77
                    SunSlot(CODE_MOONSET, s.moonsetAlarm, s.moonsetLead, s.moonsetDays, day.sky.moonSet, "Moonset"))) {
                    if (!on || !SunAlarm.dayOk(mask, today.dayOfWeek)) continue
                    val ev = evN ?: run { Logger.e("Alarm", "$name: no event today — skipped"); null } ?: continue
                    if (FiredLedger.has(ctx, code, today)) continue
                    val fire = SunAlarm.ringAt(ev, lead)
                    val now = ZonedDateTime.now(zone).plusSeconds(1)
                    if (fire.isAfter(now)) {
                        armCode(ctx, am, code, fire, SunAlarm.title(name, lead, ev),
                            "$name \u00b7 ${ev.format(FMT)} \u00b7 ${place.name}", name.lowercase())
                        Logger.e("Alarm", "$name armed for ${fire.format(FMT)} (lead $lead, mask $mask)")
                    }
                }
                for (span in day.spans) {
                    if (s.visible[span.kalam] != true) continue      // hidden = fully suppressed (D6)
                    val startCfg = s.startSlots[span.kalam]!!
                    if (startCfg.enabled && !FiredLedger.has(ctx, code(span.kalam, false), today)) {   // K64
                        val fire = span.start.minusMinutes(startCfg.offsetMin.toLong())
                        val now = ZonedDateTime.now(zone).plusSeconds(1)   // K64: sampled per slot, 1s guard
                        if (fire.isAfter(now)) arm(ctx, am, span.kalam, false, fire,
                            "${span.kalam.code} starts at ${span.start.format(FMT)}",
                            "${span.kalam.full} \u00b7 ${span.start.format(FMT)} \u2013 ${span.end.format(FMT)} \u00b7 ${place.name}")
                    }
                    if (s.endSlots[span.kalam] == true && !FiredLedger.has(ctx, code(span.kalam, true), today)) {   // K64
                        val now = ZonedDateTime.now(zone).plusSeconds(1)
                        if (span.end.isAfter(now)) arm(ctx, am, span.kalam, true, span.end,
                            "${span.kalam.code} ended at ${span.end.format(FMT)}",
                            "${span.kalam.full} \u00b7 ${span.start.format(FMT)} \u2013 ${span.end.format(FMT)} \u00b7 ${place.name}")
                    }
                }
            } catch (t: Throwable) { Logger.e("Alarm", "rearm failed", t) }
        }
    }

    private fun code(k: Kalam, end: Boolean) = k.ordinal * 2 + (if (end) 1 else 0)

    // K76 — sun slots live beside the six kalam codes (0..5)
    const val CODE_SUNRISE = 6
    const val CODE_SUNSET = 7
    const val CODE_MOONRISE = 8   // K77
    const val CODE_MOONSET = 9
    /** K78 — slots that behave as real alarms (ring until dismissed). */
    val SKY_CODES = setOf(CODE_SUNRISE, CODE_SUNSET, CODE_MOONRISE, CODE_MOONSET)
    /** K79 — snooze re-arms on its own code (base + 4) so the original slot's ledger key stays intact. */
    fun snoozeCodeOf(code: Int) = code + 4          // 6..9 -> 10..13
    fun baseCodeOf(code: Int) = if (code in 10..13) code - 4 else code
    val SNOOZE_CODES = SKY_CODES.map { snoozeCodeOf(it) }.toSet()
    const val MAX_SNOOZE = 3
    val SNOOZE_MINUTES = listOf(5, 10, 15)

    /** K79 — arm a snooze for [code] at now + [mins]; seq distinguishes repeat snoozes in the ledger. */
    fun snooze(ctx: Context, code: Int, mins: Int, seq: Int, title: String, body: String) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val sc = snoozeCodeOf(baseCodeOf(code))
            val at = ZonedDateTime.now().plusMinutes(mins.toLong())
            if (at.toLocalDate() != ZonedDateTime.now().toLocalDate()) {
                Logger.e("Alarm", "snooze crosses midnight — dropped"); return
            }
            val i = Intent(ctx, AlarmReceiver::class.java)
                .putExtra("title", title).putExtra("body", body).putExtra("end", false)
                .putExtra("id", sc).putExtra("seq", seq)
            val pi = PendingIntent.getBroadcast(ctx, sc, i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), pi)
            Logger.e("Alarm", "snoozed $code for $mins min (seq $seq)")
        } catch (t: Throwable) { Logger.e("Alarm", "snooze failed", t) }
    }
    private data class SunSlot(val code: Int, val on: Boolean, val lead: Int, val mask: String,
                               val event: ZonedDateTime?, val name: String)

    private fun pendingCode(ctx: Context, code: Int, title: String, body: String): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java)
            .putExtra("title", title).putExtra("body", body).putExtra("end", false).putExtra("id", code)
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun armCode(ctx: Context, am: AlarmManager, code: Int, at: ZonedDateTime, title: String, body: String, label: String) {
        val pi = pendingCode(ctx, code, title, body)
        val t = at.toInstant().toEpochMilli()
        try {
            if (exactAllowed(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
            else { am.setWindow(AlarmManager.RTC_WAKEUP, t, 10 * 60 * 1000, pi); Logger.e("Alarm", "exact revoked — $label armed inexact") }
        } catch (se: SecurityException) {
            Logger.e("Alarm", "SecurityException arming $label — falling back inexact", se)
            try { am.setWindow(AlarmManager.RTC_WAKEUP, t, 10 * 60 * 1000, pi) }
            catch (t2: Throwable) { Logger.e("Alarm", "inexact arm also failed", t2) }
        }
    }

    private fun pending(ctx: Context, k: Kalam, end: Boolean, title: String, body: String, isEnd: Boolean): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java)
            .putExtra("title", title).putExtra("body", body)
            .putExtra("end", isEnd).putExtra("id", code(k, end))
        return PendingIntent.getBroadcast(ctx, code(k, end), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun cancelSlot(ctx: Context, am: AlarmManager, k: Kalam, end: Boolean) {
        am.cancel(pending(ctx, k, end, "", "", end))
    }

    private fun arm(ctx: Context, am: AlarmManager, k: Kalam, end: Boolean,
                    at: ZonedDateTime, title: String, body: String) {
        val pi = pending(ctx, k, end, title, body, end)
        val t = at.toInstant().toEpochMilli()
        try {
            if (exactAllowed(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
            else {
                am.setWindow(AlarmManager.RTC_WAKEUP, t, 10 * 60 * 1000, pi)
                Logger.e("Alarm", "exact revoked — ${k.code} ${if (end) "end" else "start"} armed inexact")
            }
        } catch (se: SecurityException) {
            Logger.e("Alarm", "SecurityException arming — falling back inexact", se)
            try { am.setWindow(AlarmManager.RTC_WAKEUP, t, 10 * 60 * 1000, pi) }
            catch (t2: Throwable) { Logger.e("Alarm", "inexact arm also failed", t2) }
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        try {
            val title = intent.getStringExtra("title") ?: return
            val body = intent.getStringExtra("body") ?: ""
            val isEnd = intent.getBooleanExtra("end", false)
            val id = intent.getIntExtra("id", 0)
            val seq = intent.getIntExtra("seq", 0)   // K79 — snooze sequence
            // K64 — idempotent: same slot, same day, delivered twice ⇒ second one is dropped
            val today = try {
                val s0 = kotlinx.coroutines.runBlocking { Prefs.readSettings(ctx) }
                val pl = kotlinx.coroutines.runBlocking { Prefs.resolvePlace(ctx, s0.activePlaceId) }
                LocalDate.now(ZoneId.of(pl?.tz ?: "UTC"))
            } catch (_: Throwable) { LocalDate.now() }
            if (!FiredLedger.markIfNew(ctx, id, today, seq)) {
                Logger.e("AlarmReceiver", "duplicate fire suppressed: slot $id $today"); return
            }
            val sky = id in AlarmScheduler.SKY_CODES || id in AlarmScheduler.SNOOZE_CODES   // K78/K79
            if (sky) {
                // K79 — the alarm SCREEN is the control surface; the notification is only a fallback
                Notifs.postAlarm(ctx, 200 + id, title, body, id, seq)
                try {
                    ctx.startActivity(Intent(ctx, com.krishna.kalam.ui.AlarmActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .putExtra("title", title).putExtra("body", body)
                        .putExtra("id", id).putExtra("seq", seq))
                } catch (t: Throwable) { Logger.e("Alarm", "alarm screen launch failed — notification fallback", t) }
            } else if (com.krishna.kalam.ui.AlertBus.appForeground) {
                com.krishna.kalam.ui.AlertBus.current.value = title to body   // D16: sheet, no duplicate notification
            } else {
                Notifs.post(ctx, 100 + id, title, body)
            }
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val s = Prefs.readSettings(ctx)
                    // K78 — sky alarms always ring (never gated by the kalam start/end style flags)
                    val wantSound = sky || (if (isEnd) s.endStyleSound else s.startStyleSound)
                    if (wantSound) SoundService.start(ctx, s.ringSeconds, title, alarmMode = sky)
                    AlarmScheduler.rearmToday(ctx)   // keep the day's remaining slots armed
                } catch (t: Throwable) { Logger.e("AlarmReceiver", "post-fire work failed", t) }
            }
        } catch (t: Throwable) { Logger.e("AlarmReceiver", "onReceive failed", t) }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val a = intent.action ?: return
        if (a == Intent.ACTION_BOOT_COMPLETED || a == Intent.ACTION_TIMEZONE_CHANGED ||
            a == Intent.ACTION_TIME_CHANGED || a == Intent.ACTION_DATE_CHANGED) {
            AlarmScheduler.rearmToday(ctx)
        }
    }
}

/** Plays the alert sound for the configured ring duration (D15). Dedupes overlapping requests (D20). */
class SoundService : Service() {
    private var player: MediaPlayer? = null
    private var job: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val secs = intent?.getIntExtra("secs", 5) ?: 5
        val title = intent?.getStringExtra("title") ?: "Kalam alert"
        val alarmMode = intent?.getBooleanExtra("alarm", false) ?: false   // K78 — real alarm vs short alert
        if (player?.isPlaying == true) { return START_NOT_STICKY }   // dedupe: one sound
        try {
            Notifs.ensureChannel(this)
            val n = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notif).setContentTitle(title)
                .setContentText("Ringing").setOngoing(true).build()
            startForeground(99, n)
            // K78 — alarm mode: alarm ringtone on the ALARM stream (loud, survives notification silencing)
            val uri = if (alarmMode)
                (RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(if (alarmMode) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(this@SoundService, uri)
                isLooping = true
                prepare(); start()
            }
            try {
                val vib = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (alarmMode) vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 600), 0))   // repeats until stopped
                else vib.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (t: Throwable) { Logger.e("Sound", "vibrate failed", t) }
            job = CoroutineScope(Dispatchers.Main).launch {
                // K78 — an alarm rings until dismissed; RING_CAP_SECS is only a runaway guard
                delay((if (alarmMode) RING_CAP_SECS else secs) * 1000L); stopSelf()
            }
        } catch (t: Throwable) {
            Logger.e("Sound", "playback failed — notification only", t)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        try { (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).cancel() } catch (_: Throwable) {}
        try { job?.cancel(); player?.stop(); player?.release() } catch (_: Throwable) {}
        player = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val RING_CAP_SECS = 60    // K79 — 60 s: an unattended alarm can never ring on and on

        fun start(ctx: Context, secs: Int, title: String, alarmMode: Boolean = false) {
            try {
                val i = Intent(ctx, SoundService::class.java)
                    .putExtra("secs", secs).putExtra("title", title).putExtra("alarm", alarmMode)
                ctx.startForegroundService(i)
            } catch (t: Throwable) { Logger.e("Sound", "FGS start denied — notification only", t) }
        }
        fun stop(ctx: Context) {
            try { ctx.stopService(Intent(ctx, SoundService::class.java)) } catch (_: Throwable) {}
        }
    }
}
