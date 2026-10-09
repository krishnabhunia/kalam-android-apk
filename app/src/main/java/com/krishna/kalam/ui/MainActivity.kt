package com.krishna.kalam.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings as SysSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import java.util.Locale
import androidx.compose.ui.unit.sp
import com.krishna.kalam.alarm.AlarmScheduler
import com.krishna.kalam.alarm.Notifs
import com.krishna.kalam.alarm.SoundService
import com.krishna.kalam.core.*
import com.krishna.kalam.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object AlertBus {
    val current = MutableStateFlow<Pair<String, String>?>(null)
    @Volatile var appForeground = false
}

object Palette {
    val rkBg = Color(0xFFFAECE7); val rkBorder = Color(0xFF993C1D); val rkText = Color(0xFF4A1B0C)
    val ygBg = Color(0xFFEEEDFE); val ygBorder = Color(0xFF534AB7); val ygText = Color(0xFF26215C)
    val gkBg = Color(0xFFE1F5EE); val gkBorder = Color(0xFF0F6E56); val gkText = Color(0xFF04342C)
    val blueBand = Color(0xFFE9EEF9); val blueText = Color(0xFF123472)
    val red = Color(0xFF993C1D); val redSoft = Color(0xFFFAECE7)
    val tempSoft = Color(0xFFC98F7A)   // K59 — "Updated" caption (variant B)
    val green = Color(0xFF0F6E56); val greenSoft = Color(0xFFE1F5EE)
    val yellow = Color(0xFF8A6D00); val yellowSoft = Color(0xFFFAF3D9)
    fun bg(k: Kalam) = when (k) { Kalam.RK -> rkBg; Kalam.YG -> ygBg; Kalam.GK -> gkBg }
    fun border(k: Kalam) = when (k) { Kalam.RK -> rkBorder; Kalam.YG -> ygBorder; Kalam.GK -> gkBorder }
    fun text(k: Kalam) = when (k) { Kalam.RK -> rkText; Kalam.YG -> ygText; Kalam.GK -> gkText }
}

/** K18 — the four active tiers, threaded through every screen. */
data class Fonts(val display: TextUnit, val header: TextUnit, val sub: TextUnit, val data: TextUnit)
fun fontsOf(s: Settings) = Fonts(s.fontDisplay.sp, s.fontHeader.sp, s.fontSub.sp, s.fontData.sp)

/** K20 — zebra stripe that works in both modes. */
@Composable fun zebra(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)

enum class Screen { MAIN, SETTINGS, LOCATION_PICK, GPS_SAVE, SAVED_MANAGER, ERROR_LOGS, HISTORY }

/** K33 — history back target depends on where it was opened from. */
object NavRules {
    fun historyBack(origin: Screen): Screen =
        if (origin == Screen.MAIN) Screen.MAIN else Screen.SETTINGS
}
enum class Tab { DAY, WEEK, MATCH, CLOCKS }

val HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

class MainActivity : ComponentActivity() {
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.init(this)
        Notifs.ensureChannel(this)
        if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        AlarmScheduler.rearmToday(this)
        setContent { KalamApp() }
    }
    override fun onResume() { super.onResume(); AlertBus.appForeground = true }
    override fun onPause() { super.onPause(); AlertBus.appForeground = false }
}

@Composable
fun KalamApp() {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme(primary = Color(0xFF8FB0E8))
                 else lightColorScheme(primary = Color(0xFF1A3E8C))
    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Root() }
    }
}

@Composable
fun Root() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by Prefs.settingsFlow(ctx).collectAsState(initial = Settings())
    val saved by Prefs.savedPlacesFlow(ctx).collectAsState(initial = emptyList())
    var screen by rememberSaveable { mutableStateOf(Screen.MAIN) }
    var tab by rememberSaveable { mutableStateOf(Tab.DAY) }
    var historyOrigin by rememberSaveable { mutableStateOf(Screen.SETTINGS.name) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    val alert by AlertBus.current.collectAsState()
    val f = fontsOf(settings)

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            val now = LocalDate.now()
            if (now != today) { today = now; AlarmScheduler.rearmToday(ctx) }
        }
    }

    val place = Cities.byId(settings.activePlaceId)
        ?: saved.find { it.id == settings.activePlaceId }
        ?: Cities.byId(settings.defaultPlaceId) ?: Cities.BUNDLED[5]

    // K31 — back walks the chain; only MAIN exits (system default handles that).
    when (screen) {
        Screen.MAIN -> MainScaffold(settings, f, place, tab, today,
            onTab = { tab = it }, onSettings = { screen = Screen.SETTINGS },
            onLocation = { screen = Screen.LOCATION_PICK },
            onHistory = { historyOrigin = Screen.MAIN.name; screen = Screen.HISTORY })
        Screen.SETTINGS -> SettingsScreen(settings, f, saved,
            onBack = { screen = Screen.MAIN },
            onSavedManager = { screen = Screen.SAVED_MANAGER },
            onErrorLogs = { screen = Screen.ERROR_LOGS },
            onHistory = { historyOrigin = Screen.SETTINGS.name; screen = Screen.HISTORY })
        Screen.LOCATION_PICK -> {
            BackHandler { screen = Screen.MAIN }
            LocationPicker(settings, f, saved,
                onBack = { screen = Screen.MAIN },
                onPick = { id ->
                    scope.launch {
                        Prefs.writeSettings(ctx, Prefs.readSettings(ctx).copy(activePlaceId = id))
                        AlarmScheduler.rearmToday(ctx)
                    }
                    screen = Screen.MAIN
                },
                onGps = { screen = Screen.GPS_SAVE })
        }
        Screen.GPS_SAVE -> {
            BackHandler { screen = Screen.LOCATION_PICK }
            GpsSaveScreen(f, onDone = { screen = Screen.LOCATION_PICK })
        }
        Screen.SAVED_MANAGER -> {
            BackHandler { screen = Screen.SETTINGS }
            SavedManager(saved, settings, f, onBack = { screen = Screen.SETTINGS })
        }
        Screen.ERROR_LOGS -> {
            BackHandler { screen = Screen.SETTINGS }
            ErrorLogsScreen(f, onBack = { screen = Screen.SETTINGS })
        }
        Screen.HISTORY -> {
            val backTo = NavRules.historyBack(Screen.valueOf(historyOrigin))
            BackHandler { screen = backTo }
            HistoryScreen(f, onBack = { screen = backTo })
        }
    }

    alert?.let { (title, body) ->
        AlertSheet(title, body, f) {   // K78 — one dismiss path: stops the ring AND clears the alarm notification
            ctx.sendBroadcast(android.content.Intent(ctx, com.krishna.kalam.alarm.AlarmActionReceiver::class.java)
                .setAction(com.krishna.kalam.alarm.ACTION_DISMISS))
            SoundService.stop(ctx); AlertBus.current.value = null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertSheet(title: String, body: String, f: Fonts, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp, 4.dp, 20.dp, 28.dp)) {
            Text(title, fontSize = f.header, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(body, fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Dismiss", fontSize = f.data) }
            Spacer(Modifier.height(6.dp))
            Text("Swipe down or tap outside to dismiss. This occurrence only.",
                fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
fun MainScaffold(s: Settings, f: Fonts, place: Place, tab: Tab, today: LocalDate,
                 onTab: (Tab) -> Unit, onSettings: () -> Unit, onLocation: () -> Unit,
                 onHistory: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        if (!AlarmScheduler.exactAllowed(ctx)) ExactAlarmBanner(f)
        Row(Modifier.fillMaxWidth().padding(16.dp, 10.dp, 8.dp, 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.clickable { onLocation() }, verticalAlignment = Alignment.CenterVertically) {
                Text("\uD83D\uDCCD ${place.name}", fontSize = f.header, fontWeight = FontWeight.SemiBold)
                Text(" \u25BE", fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onSettings) {   // K25
                Icon(Icons.Filled.Settings, contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth()) {           // K22 — justified thirds
            for (t in Tab.entries) {
                val on = t == tab
                Column(Modifier.weight(1f).clickable { onTab(t) },
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(t.name.lowercase().replaceFirstChar { it.uppercase() },
                        fontSize = f.header,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp))
                    Box(Modifier.fillMaxWidth().height(2.5.dp)
                        .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent))
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        when (tab) {
            Tab.DAY -> DayTab(s, f, place, today)
            Tab.WEEK -> WeekTab(s, f, place, today)
            Tab.MATCH -> MatchTab(s, f, onHistory)
            Tab.CLOCKS -> ClocksTab(s, f)   // K51
        }
    }
}

@Composable
fun ExactAlarmBanner(f: Fonts) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(14.dp, 8.dp)
        .background(Palette.yellowSoft, RoundedCornerShape(10.dp))
        .border(1.dp, Palette.yellow, RoundedCornerShape(10.dp)).padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("\u26A0 Exact alarms are off — timings may be approximate.",
            fontSize = f.data, color = Palette.yellow, modifier = Modifier.weight(1f))
        Text("Allow", fontSize = f.data, fontWeight = FontWeight.Bold, color = Palette.yellow,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable {
                if (Build.VERSION.SDK_INT >= 31) try {
                    ctx.startActivity(Intent(SysSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                } catch (t: Throwable) { Logger.e("Banner", "exact-alarm settings open failed", t) }
            })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DayTab(s: Settings, f: Fonts, place: Place, today: LocalDate) {
    val zone = remember(place.tz) { ZoneId.of(place.tz) }
    val pastDays = 30; val futureDays = 400
    val pager = rememberPagerState(initialPage = pastDays) { pastDays + futureDays + 1 }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        val viewDate = today.plusDays((pager.currentPage - pastDays).toLong())
        Row(Modifier.fillMaxWidth().padding(12.dp, 8.dp, 12.dp, 0.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("\u2039", fontSize = f.header, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } }.padding(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(viewDate.format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy")),
                    fontSize = f.header, fontWeight = FontWeight.SemiBold)
                // K67/K68 — Bengali date + tithi stack (both scripts); failure hides lines, never wrong values
                val bnDate = remember(viewDate, zone) { try { com.krishna.kalam.core.Bangla.bengaliDate(viewDate, zone).also {
                    if (it == null) com.krishna.kalam.core.Logger.e("Bangla", "traditional date unavailable: $viewDate")
                } } catch (t: Throwable) { com.krishna.kalam.core.Logger.e("Bangla", "date failed", t); null } }
                val tith = remember(viewDate) {
                    try {
                        val sr = (com.krishna.kalam.core.Solar.sunTimes(viewDate, place.lat, place.lon, zone,
                            com.krishna.kalam.core.Solar.ZENITH_OBSERVATIONAL) as? com.krishna.kalam.core.Solar.SunResult.Times)?.sunrise
                        com.krishna.kalam.core.Bangla.tithiForDay(viewDate, sr, zone)
                    } catch (t: Throwable) { com.krishna.kalam.core.Logger.e("Bangla", "tithi failed", t); null }
                }
                if (bnDate != null) {
                    val bd = com.krishna.kalam.core.Bangla
                    Text("${bd.WEEKDAYS_BN[viewDate.dayOfWeek.value - 1]}, ${bd.bnNum(bnDate.day)} ${bnDate.monthBn} ${bd.bnNum(bnDate.year)}",
                        fontSize = f.data, color = Palette.yellow, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Text("${viewDate.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)}, ${bnDate.day} ${bnDate.monthEn} ${bnDate.year}",
                        fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, textAlign = TextAlign.Center)
                    Text(bd.CALENDAR_LABEL, fontSize = f.data,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                } else {
                    Text(com.krishna.kalam.core.Bangla.UNAVAILABLE_LABEL, fontSize = f.data,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                if (tith != null) {
                    val bd = com.krishna.kalam.core.Bangla
                    val hm = DateTimeFormatter.ofPattern("HH:mm")
                    val upBn = tith.endLocal?.let { " \u00b7 ${bd.bnNum(it.hour)}:${bd.bnNum(it.minute).padStart(2, '\u09e6')} \u09aa\u09b0\u09cd\u09af\u09a8\u09cd\u09a4" } ?: ""
                    val upEn = tith.endLocal?.let { " \u00b7 upto ${it.format(hm)}" } ?: ""
                    Text("${tith.pakshaBn} ${tith.nameBn}$upBn", fontSize = f.data,
                        color = Palette.blueText, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Text("${tith.pakshaEn} ${tith.nameEn}$upEn", fontSize = f.data,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, textAlign = TextAlign.Center)
                }
                Text("Swipe for other days", fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("\u203A", fontSize = f.header, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(pager.pageCount - 1)) } }.padding(8.dp))
        }
        Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp, 16.dp, 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Today", f, pager.currentPage == pastDays) { scope.launch { pager.animateScrollToPage(pastDays) } }
            Pill("Tomorrow", f, pager.currentPage == pastDays + 1) { scope.launch { pager.animateScrollToPage(pastDays + 1) } }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            val d = today.plusDays((page - pastDays).toLong())
            DayPage(s, f, place, zone, d, isToday = page == pastDays)
        }
    }
}

@Composable
fun RowScope.Pill(label: String, f: Fonts, on: Boolean, onClick: () -> Unit) {
    Box(Modifier.weight(1f).clickable { onClick() }
        .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else Color.Transparent, RoundedCornerShape(999.dp))
        .border(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp))
        .padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
        Text(label, fontSize = f.data,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
            color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun DayPage(s: Settings, f: Fonts, place: Place, zone: ZoneId, d: LocalDate, isToday: Boolean) {
    val primary = remember(s.alarmConvention, place, d) {
        Engine.computeDay(d, place.lat, place.lon, zone, s.alarmConvention)
    }
    val other = remember(s.showBoth, s.alarmConvention, place, d) {
        if (s.showBoth) Engine.computeDay(d, place.lat, place.lon, zone,
            if (s.alarmConvention == Convention.GEOMETRIC) Convention.OBSERVATIONAL else Convention.GEOMETRIC)
        else null
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp, 4.dp, 12.dp, 16.dp)) {
        when (primary) {
            is DayResult.Unavailable -> Text(primary.reason, fontSize = f.data,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
            is DayResult.Ok -> {
                val now = ZonedDateTime.now(zone)
                for (span in primary.spans) {
                    if (s.visible[span.kalam] != true) continue
                    val sec = (other as? DayResult.Ok)?.spans?.find { it.kalam == span.kalam }
                    val anySlot = (s.startSlots[span.kalam]?.enabled == true) || (s.endSlots[span.kalam] == true)
                    KalamCard(span, sec, f, ended = isToday && span.end.isBefore(now),
                        inProgress = isToday && !span.start.isAfter(now) && span.end.isAfter(now),
                        bellOn = anySlot, showBoth = s.showBoth,
                        convLabel = if (s.alarmConvention == Convention.GEOMETRIC) "observational" else "geometric",
                        hoursForm = s.durationHours)
                    Spacer(Modifier.height(8.dp))
                }
                SkyTable(primary.sky, f, s.alarmConvention, s, place, zone, isToday)
            }
        }
    }
}

/** K28 — full name up top; third row is the secondary-convention time or absent. */
@Composable
fun KalamCard(span: KalamSpan, secondary: KalamSpan?, f: Fonts, ended: Boolean, inProgress: Boolean,
              bellOn: Boolean, showBoth: Boolean, convLabel: String, hoursForm: Boolean) {
    val bg = if (ended) MaterialTheme.colorScheme.surface else Palette.bg(span.kalam)
    val txt = if (ended) MaterialTheme.colorScheme.onSurface else Palette.text(span.kalam)
    val sub = if (ended) MaterialTheme.colorScheme.onSurfaceVariant else Palette.border(span.kalam)
    Column(Modifier.fillMaxWidth()
        .background(bg, RoundedCornerShape(14.dp))
        .border(1.dp, if (ended) MaterialTheme.colorScheme.outlineVariant else Palette.border(span.kalam).copy(alpha = .35f), RoundedCornerShape(14.dp))
        .padding(14.dp, 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f, fill = false)) {   // K38 — duration rides the title
                Text(span.kalam.full, fontSize = f.header, fontWeight = FontWeight.Bold,
                    color = txt.copy(alpha = if (ended) .55f else 1f))
                Text(" - " + fmtDuration(java.time.Duration.between(span.start, span.end).toMinutes(), hoursForm),
                    fontSize = f.header, color = txt.copy(alpha = if (ended) .55f else 1f))
            }
            Text(if (ended) "Ended" else if (inProgress) "In progress" else "Upcoming",
                fontSize = f.data, color = sub.copy(alpha = if (ended) .55f else 1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${span.start.format(HM)} \u2013 ${span.end.format(HM)}",
                fontSize = f.display, fontWeight = FontWeight.Bold,
                color = txt.copy(alpha = if (ended) .55f else 1f),
                textDecoration = if (ended) TextDecoration.LineThrough else TextDecoration.None)
            Spacer(Modifier.width(7.dp))
            Text(if (bellOn) "\uD83D\uDD14" else "\uD83D\uDD15", fontSize = f.data)
        }
        if (showBoth && secondary != null) {
            Text("${secondary.start.format(HM)} \u2013 ${secondary.end.format(HM)} $convLabel",
                fontSize = f.data, color = sub.copy(alpha = if (ended) .55f else 1f))
        }
    }
}

@Composable
fun SkyTable(sky: SkyDay, f: Fonts, conv: Convention,
             s: Settings? = null, place: Place? = null, zone: ZoneId? = null, isToday: Boolean = false) {
    val line = MaterialTheme.colorScheme.outlineVariant
    val center = androidx.compose.ui.text.style.TextAlign.Center
    // K42 \u2014 stacked labels, proportional centered grid (\u224830/22/22/26), wrap not clip
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)
        .border(1.dp, line, RoundedCornerShape(12.dp))) {
        Row(Modifier.fillMaxWidth().background(Palette.blueBand, RoundedCornerShape(12.dp, 12.dp, 0.dp, 0.dp))
            .padding(6.dp, 8.dp)) {
            Text("", Modifier.weight(.30f), fontSize = f.sub)
            Text("Rise", Modifier.weight(.22f), fontSize = f.sub, fontWeight = FontWeight.SemiBold,
                color = Palette.blueText, textAlign = center)
            Text("Set", Modifier.weight(.22f), fontSize = f.sub, fontWeight = FontWeight.SemiBold,
                color = Palette.blueText, textAlign = center)
            Text("Dur", Modifier.weight(.26f), fontSize = f.sub, fontWeight = FontWeight.SemiBold,
                color = Palette.blueText, textAlign = center)
        }
        HorizontalDivider(color = line)
        var sunSheet by remember { mutableStateOf<SkyEvent?>(null) }   // K76/K77
        Row(Modifier.fillMaxWidth().padding(6.dp, 8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(.30f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Sun", fontSize = f.data, fontWeight = FontWeight.SemiBold)
                Text(if (conv == Convention.GEOMETRIC) "geometric" else "observational",
                    fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = center)
            }
            // K76 — bells under Rise/Set (variant A); tap opens the alarm sheet; disabled on non-today pages
            Column(Modifier.weight(.22f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(sky.sunrise.format(HM), fontSize = f.data, textAlign = center)
                if (s != null) SunBell(s, f, isToday, SkyEvent.SUNRISE) { sunSheet = SkyEvent.SUNRISE }
            }
            Column(Modifier.weight(.22f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(sky.sunset.format(HM), fontSize = f.data, textAlign = center)
                if (s != null) SunBell(s, f, isToday, SkyEvent.SUNSET) { sunSheet = SkyEvent.SUNSET }
            }
            Text(fmtHM(java.time.Duration.between(sky.sunrise, sky.sunset).toMinutes()),
                Modifier.weight(.26f), fontSize = f.data, fontWeight = FontWeight.SemiBold, textAlign = center)
        }
        HorizontalDivider(color = line)
        Row(Modifier.fillMaxWidth().padding(6.dp, 8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(.30f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Moon", fontSize = f.data, fontWeight = FontWeight.SemiBold)
                Text("${sky.phase.glyph} ${sky.phase.name.lowercase()} ${"%.0f".format(sky.phase.illumPct)}%",
                    fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = center)
            }
            Column(Modifier.weight(.22f), horizontalAlignment = Alignment.CenterHorizontally) {   // K77
                Text(sky.moonRise?.format(HM) ?: "\u2014", fontSize = f.data, textAlign = center)
                if (s != null) SunBell(s, f, isToday, SkyEvent.MOONRISE) { sunSheet = SkyEvent.MOONRISE }
            }
            Column(Modifier.weight(.22f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(sky.moonSet?.format(HM) ?: "\u2014", fontSize = f.data, textAlign = center)
                if (s != null) SunBell(s, f, isToday, SkyEvent.MOONSET) { sunSheet = SkyEvent.MOONSET }
            }
            Text(sky.moonUpMin?.let { fmtHM(it) } ?: "\u2014",
                Modifier.weight(.26f), fontSize = f.data, fontWeight = FontWeight.SemiBold, textAlign = center)
        }
        if (s != null && place != null && zone != null) sunSheet?.let { SunAlarmDialog(s, f, place, zone, it) { sunSheet = null } }
    }
}
