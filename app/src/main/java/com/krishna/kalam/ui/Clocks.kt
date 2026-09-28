package com.krishna.kalam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import kotlin.math.roundToInt
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.krishna.kalam.core.*
import com.krishna.kalam.data.ClockPlace
import com.krishna.kalam.data.Prefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** K51 — one-time IANA timezone lookup at add: Open-Meteo, fallback timeapi.io; null = both failed. */
suspend fun fetchTimezone(lat: Double, lon: Double): String? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        fun hit(urlStr: String, tag: String): String? = try {
            val c = java.net.URL(urlStr).openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 5000; c.readTimeout = 5000
            c.setRequestProperty("User-Agent", com.krishna.kalam.AppVersion.USER_AGENT)
            if (c.responseCode != 200) { Logger.e("Clocks", "$tag HTTP ${c.responseCode}"); null }
            else Geo.parseTimezone(c.inputStream.bufferedReader().readText())
        } catch (t: Throwable) { Logger.e("Clocks", "$tag tz fetch failed", t); null }
        val ll = "latitude=$lat&longitude=$lon"
        val tz = hit("https://api.open-meteo.com/v1/forecast?$ll&timezone=auto&forecast_days=1", "open-meteo")
            ?: hit("https://timeapi.io/api/timezone/coordinate?$ll", "timeapi")
        tz?.takeIf { runCatching { ZoneId.of(it) }.isSuccess }
            .also { if (tz != null && it == null) Logger.e("Clocks", "tz rejected by ZoneId: $tz") }
    }

/** K54/K70 — current weather via Open-Meteo (one call); temp required, extras optional.
 *  Returns temp + hum/wind/precip; null only when even temperature is unavailable. */
suspend fun fetchWeather(lat: Double, lon: Double): DoubleArrayWx? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val c = java.net.URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,precipitation,wind_speed_10m")
                .openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 5000; c.readTimeout = 5000
            c.setRequestProperty("User-Agent", com.krishna.kalam.AppVersion.USER_AGENT)
            if (c.responseCode != 200) { Logger.e("Clocks", "wx HTTP ${c.responseCode}"); null }
            else {
                val j = c.inputStream.bufferedReader().readText()
                val t = Geo.parseTemperature(j) ?: return@withContext null
                DoubleArrayWx(t, Geo.parseNum(j, "relative_humidity_2m"),
                    Geo.parseNum(j, "wind_speed_10m"), Geo.parseNum(j, "precipitation"))
            }
        } catch (t: Throwable) { Logger.e("Clocks", "wx fetch failed", t); null }
    }

data class DoubleArrayWx(val temp: Double, val hum: Double?, val wind: Double?, val precip: Double?)

/** K70 — US AQI via Open-Meteo air-quality endpoint; failure never blocks weather. */
suspend fun fetchAqi(lat: Double, lon: Double): Int? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val c = java.net.URL("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon&current=us_aqi")
                .openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 5000; c.readTimeout = 5000
            c.setRequestProperty("User-Agent", com.krishna.kalam.AppVersion.USER_AGENT)
            if (c.responseCode != 200) { Logger.e("Clocks", "aqi HTTP ${c.responseCode}"); null }
            else Geo.parseNum(c.inputStream.bufferedReader().readText(), "us_aqi")?.toInt()
        } catch (t: Throwable) { Logger.e("Clocks", "aqi fetch failed", t); null }
    }

/** Sun/moon for a clock's LOCAL today — polar-safe: sun NoEvent leaves moon rows alive. */
data class ClockSky(val sunR: ZonedDateTime?, val sunS: ZonedDateTime?, val sunDur: Long?,
                    val moonR: ZonedDateTime?, val moonS: ZonedDateTime?, val moonDur: Long?)

fun clockSky(d: LocalDate, lat: Double, lon: Double, zone: ZoneId, conv: Convention): ClockSky {
    val zenith = if (conv == Convention.GEOMETRIC) Solar.ZENITH_GEOMETRIC else Solar.ZENITH_OBSERVATIONAL
    var sr: ZonedDateTime? = null; var ss: ZonedDateTime? = null; var sd: Long? = null
    try {
        val sun = Solar.sunTimes(d, lat, lon, zone, zenith)
        if (sun is Solar.SunResult.Times) {
            sr = sun.sunrise; ss = sun.sunset
            sd = java.time.Duration.between(sun.sunrise, sun.sunset).toMinutes()
        }
    } catch (t: Throwable) { Logger.e("Clocks", "sun compute failed", t) }
    var mr: ZonedDateTime? = null; var ms: ZonedDateTime? = null; var md: Long? = null
    try {
        val moon = Lunar.moonEvents(d, lat, lon, zone)
        mr = moon.rise; ms = moon.set
        val toMin = { t: ZonedDateTime? -> t?.let { it.hour * 60L + it.minute } }
        val upNoon = if (moon.rise == null && moon.set == null)
            Lunar.moonUpAt(d, 720.0, lat, lon, zone) else null
        md = Engine.moonPresence(toMin(moon.rise), toMin(moon.set), upNoon)
    } catch (t: Throwable) { Logger.e("Clocks", "moon compute failed", t) }
    return ClockSky(sr, ss, sd, mr, ms, md)
}

private fun offsetLabel(z: ZonedDateTime): String {
    val sec = z.offset.totalSeconds
    val h = sec / 3600; val m = kotlin.math.abs(sec % 3600) / 60
    return if (m == 0) "UTC%+d".format(h) else "UTC%+d:%02d".format(h, m)
}

/** K54/K56 — shared temp refresh; force=true (pull) bypasses the 30-min gate. */
suspend fun refreshTemps(ctx: android.content.Context,
                         clocks: List<ClockPlace>, force: Boolean) {
    if (clocks.isEmpty()) return
    val nowMin = System.currentTimeMillis() / 60000
    val cur = Prefs.readTemps(ctx)
    val due = if (force) clocks else clocks.filter { c ->
        cur[Geo.key(c.lat, c.lon)]?.epochMin?.let { nowMin - it <= 30 } != true }
    if (due.isEmpty()) return
    var ok = 0; var fail = 0
    val updated = cur.toMutableMap()
    for (c in due) {
        val w = fetchWeather(c.lat, c.lon)
        if (w != null) {
            val aqi = fetchAqi(c.lat, c.lon)   // optional — its failure never blocks weather
            updated[Geo.key(c.lat, c.lon)] =
                com.krishna.kalam.data.Wx(w.temp, nowMin, w.hum, w.wind, w.precip, aqi)
            ok++
        } else fail++
    }
    Logger.e("Clocks", "temps(${if (force) "manual" else "auto"}): $ok ok, $fail failed of ${due.size} due")
    if (ok > 0) try { Prefs.writeTemps(ctx, updated) }
        catch (t: Throwable) { Logger.e("Clocks", "temps write failed", t) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClocksTab(s: com.krishna.kalam.data.Settings, f: Fonts) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clocks by Prefs.clocksFlow(ctx).collectAsState(initial = emptyList())
    val saved by Prefs.savedPlacesFlow(ctx).collectAsState(initial = emptyList())
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    var addOpen by remember { mutableStateOf(false) }
    var delAsk by remember { mutableStateOf<ClockPlace?>(null) }   // K52
    var renameAsk by remember { mutableStateOf<ClockPlace?>(null) }   // K72
    val temps by Prefs.tempsFlow(ctx).collectAsState(initial = emptyMap())
    // K54 — auto refresh (stale-gated) on tab entry / clock-set change
    LaunchedEffect(clocks) { refreshTemps(ctx, clocks, force = false) }
    val line = MaterialTheme.colorScheme.outlineVariant
    LaunchedEffect(Unit) {
        while (true) {   // minute tick, aligned to :00
            now = ZonedDateTime.now()
            delay(60_000 - (System.currentTimeMillis() % 60_000))
        }
    }
    val HM = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val DFMT = remember { DateTimeFormatter.ofPattern("EEE, dd MMM") }

    // K65/K66 — sort + group, persisted in Settings (fields 39/40)
    val settings by Prefs.settingsFlow(ctx).collectAsState(initial = com.krishna.kalam.data.Settings())
    var sortOpen by remember { mutableStateOf(false) }
    var groupOpen by remember { mutableStateOf(false) }
    // K75 — metric lookup from the cached snapshot; order changes only when temps change
    val metricOf: (ClockPlace) -> Double? = { c ->
        val w = temps[Geo.key(c.lat, c.lon)]
        when (settings.clockSort) { "TEMP" -> w?.temp; "AQI" -> w?.aqi?.toDouble(); "HUM" -> w?.hum; else -> null }
    }
    val sortHasData = ClockOrder.hasData(clocks, settings.clockSort, metricOf)
    val effectiveSort = if (sortHasData) settings.clockSort else "ADDED"   // Added until data exists
    val ordered = remember(clocks, temps, settings.clockSort, settings.sortDir) {
        ClockOrder.sort(clocks, effectiveSort, settings.sortDir == "1", { it.shown() }, { it.region }, { it.tz }, metricOf)
    }
    val grouped = remember(ordered, settings.clockGroup) {
        ClockOrder.tree(ordered, settings.clockGroup, { it.region }, { it.tz })
    }

    val pullState = rememberPullToRefreshState()   // K56
    if (pullState.isRefreshing) {
        LaunchedEffect(true) {
            refreshTemps(ctx, clocks, force = true)
            pullState.endRefresh()
        }
    }
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()
        .nestedScroll(pullState.nestedScrollConnection)) {
    LazyColumn(Modifier.fillMaxSize().padding(14.dp, 8.dp)) {   // K55 — lazy: 100 cards scroll clean
        if (clocks.isNotEmpty()) item {   // K65/K66 — sort + group chips
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    val sortLabels = listOf("ADDED" to "Added", "NAME" to "Name", "TIME" to "Time", "COUNTRY" to "Country",
                        "TEMP" to "Temperature", "AQI" to "Air quality", "HUM" to "Humidity")
                    val arrow = if (settings.clockSort == "ADDED") "" else if (settings.sortDir == "1") " ↑" else " ↓"
                    OutlinedButton(onClick = { sortOpen = true }, contentPadding = PaddingValues(12.dp, 4.dp)) {
                        Text("Sort: " + (sortLabels.first { it.first == settings.clockSort }.second) + arrow +
                            (if (!sortHasData) " · waiting for data" else "") + " ▾", fontSize = f.data)
                    }
                    DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                        sortLabels.forEach { (v, label) ->
                            val gated = v in ClockOrder.METRIC_MODES && !ClockOrder.hasData(clocks, v) { c ->
                                val w = temps[Geo.key(c.lat, c.lon)]
                                when (v) { "TEMP" -> w?.temp; "AQI" -> w?.aqi?.toDouble(); else -> w?.hum } }
                            val active = settings.clockSort == v
                            DropdownMenuItem(enabled = !gated,
                                text = { Text(label + (if (active) arrow else "") + (if (gated) "  (waiting for data)" else ""),
                                    fontSize = f.data, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal) },
                                onClick = { sortOpen = false
                                    scope.launch {   // re-tap flips direction; a new mode resets to natural
                                        Prefs.writeSettings(ctx, if (active && v != "ADDED")
                                            settings.copy(sortDir = if (settings.sortDir == "1") "0" else "1")
                                        else settings.copy(clockSort = v, sortDir = "0"))
                                    } })
                        }
                    }
                }
                Box {
                    OutlinedButton(onClick = { groupOpen = true }, contentPadding = PaddingValues(12.dp, 4.dp)) {
                        Text("Group: " + when (settings.clockGroup) { "NONE" -> "None"; "COUNTRY" -> "Country"; "CONTINENT" -> "Continent"
                            "COUNTRY_STATE" -> "Country+State"; "CONTINENT_COUNTRY" -> "Continent+Countries"; else -> "Cont+Country+State" } + " ▾", fontSize = f.data)
                    }
                    DropdownMenu(expanded = groupOpen, onDismissRequest = { groupOpen = false }) {
                        listOf("NONE" to "None", "COUNTRY" to "Country", "CONTINENT" to "Continent",
                            "COUNTRY_STATE" to "Country + State", "CONTINENT_COUNTRY" to "Continent + Countries",
                            "CONTINENT_COUNTRY_STATE" to "Continent + Countries + State").forEach { (v, label) ->
                            DropdownMenuItem(text = { Text(label, fontSize = f.data,
                                fontWeight = if (settings.clockGroup == v) FontWeight.SemiBold else FontWeight.Normal) },
                                onClick = { groupOpen = false
                                    scope.launch { Prefs.writeSettings(ctx, settings.copy(clockGroup = v)) } })
                        }
                    }
                }
            }
        }
        if (clocks.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(top = 18.dp)
                .border(1.dp, line, RoundedCornerShape(14.dp)).padding(26.dp, 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No clocks yet.", fontSize = f.data)
                Text("Add a city to see its time and today's Sun & Moon.",
                    fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center)
            }
        }
        // K65/K66 — display rows: grouped headers or the flat ordered list
        val stages = GroupStage.decode(settings.groupStages)   // K71 (path-scoped keys; v1.16 name keys still resolve at level 0)
        fun stageOf(n: ClockOrder.Node<ClockPlace>): Int = stages[n.path] ?: (if (n.level == 0) stages[n.name] else null) ?: GroupStage.A
        // K74 — flatten the tree honouring each header's stage (▸ hides everything beneath)
        data class R(val node: ClockOrder.Node<ClockPlace>?, val clock: ClockPlace?, val depth: Int)
        val rows = ArrayList<R>()
        fun walk(nodes: List<ClockOrder.Node<ClockPlace>>) {
            for (n in nodes) {
                rows.add(R(n, null, n.level))
                if (GroupStage.visual(stageOf(n)) == 2) continue
                if (n.children.isNotEmpty()) walk(n.children) else n.items.forEach { rows.add(R(null, it, n.level + 1)) }
            }
        }
        if (grouped != null) walk(grouped) else ordered.forEach { rows.add(R(null, it, 0)) }
        items(rows, key = { r -> r.node?.path ?: Geo.key(r.clock!!.lat, r.clock.lon) }) { r ->
            val header = r.node
            if (header != null) {
                val pos = stageOf(header)
                val lvl = header.level
                val band = when (lvl) { 0 -> Palette.blueBand; 1 -> Palette.blueBand.copy(alpha = .55f); else -> Palette.blueBand.copy(alpha = .3f) }
                val weight = when (lvl) { 0 -> FontWeight.Bold; 1 -> FontWeight.SemiBold; else -> FontWeight.Medium }
                Text(GroupStage.glyph(pos) + " " + header.name + " (" + header.count + ")",
                    fontSize = f.data, fontWeight = weight, color = Palette.blueText,
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = (lvl * 14).dp, top = if (lvl == 0) 6.dp else 3.dp, bottom = 4.dp)
                        .background(band, RoundedCornerShape(9.dp))
                        .clickable {   // K71 — single tap: one stage per tap (A→B→C→B→A…), keyed by full path
                            scope.launch {
                                val next = GroupStage.advance(pos)
                                val members = header.all().map { Geo.key(it.lat, it.lon) }.toSet()
                                when (next) {
                                    GroupStage.B1, GroupStage.B2 -> Prefs.writeClocks(ctx,
                                        clocks.map { if (Geo.key(it.lat, it.lon) in members) it.copy(expanded = false) else it })
                                    GroupStage.A -> Prefs.writeClocks(ctx,
                                        clocks.map { if (Geo.key(it.lat, it.lon) in members) it.copy(expanded = true) else it })
                                    else -> {}
                                }
                                Prefs.writeSettings(ctx, settings.copy(
                                    groupStages = GroupStage.encode(stages + (header.path to next))))
                                com.krishna.kalam.core.Logger.e("Clocks", "group '" + header.path + "' stage " + pos + " -> " + next)
                            }
                        }
                        .padding(10.dp, 5.dp))
                return@items
            }
            val c = r.clock!!
            val cardIndent = (if (r.depth > 1) (r.depth - 1) * 8 else 0).dp
            val zone = runCatching { ZoneId.of(c.tz) }.getOrNull()
            // K53 \u00b7 tap toggles collapse; state persisted per clock
            fun toggle() = scope.launch {
                try {
                    Prefs.writeClocks(ctx, Prefs.readClocks(ctx).map {
                        if (Geo.key(it.lat, it.lon) == Geo.key(c.lat, c.lon))
                            it.copy(expanded = !it.expanded) else it })
                } catch (t: Throwable) { Logger.e("Clocks", "toggle failed", t) }
            }
            if (!c.expanded) {
                val local = zone?.let { now.withZoneSameInstant(it) }
                val sky = if (zone != null && local != null)
                    remember(local.toLocalDate(), c.tz, s.alarmConvention) {
                        clockSky(local.toLocalDate(), c.lat, c.lon, zone, s.alarmConvention)
                    } else null
                val HHMM = remember { DateTimeFormatter.ofPattern("HHmm") }
                Row(Modifier.fillMaxWidth().padding(start = cardIndent).padding(vertical = 4.dp)
                    .border(1.dp, line, RoundedCornerShape(14.dp))
                    .clickable { toggle() }
                    .padding(12.dp, 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("\u25B8 ", fontSize = f.data, color = MaterialTheme.colorScheme.primary)
                    Text(c.shown(), fontSize = f.data, fontWeight = FontWeight.Bold)
                    temps[Geo.key(c.lat, c.lon)]?.let { tp ->
                        val fresh = now.toInstant().toEpochMilli() / 60000 - tp.epochMin <= 60
                        Spacer(Modifier.width(6.dp))
                        Text("${tp.temp.roundToInt()}°", fontSize = f.data, fontWeight = FontWeight.Bold,
                            color = if (fresh) Palette.red else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(local?.format(HHMM)?.plus(" hrs") ?: "\u2014",
                        fontSize = f.data, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(Geo.sunSegment(sky?.sunR?.format(HHMM), sky?.sunS?.format(HHMM)),
                        fontSize = f.data, modifier = Modifier.weight(1f))
                    Text(sky?.sunDur?.let { fmtHM(it) } ?: "\u2014", fontSize = f.data)
                }
                return@items
            }
            Column(Modifier.fillMaxWidth().padding(start = cardIndent).padding(vertical = 4.dp)
                .border(1.dp, line, RoundedCornerShape(14.dp))
                .clickable { toggle() }
                .padding(12.dp, 10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("\u25BE ", fontSize = f.data, color = MaterialTheme.colorScheme.primary)
                            Text(c.shown(), fontSize = f.sub, fontWeight = FontWeight.SemiBold)
                            temps[Geo.key(c.lat, c.lon)]?.let { tp ->
                                val fresh = now.toInstant().toEpochMilli() / 60000 - tp.epochMin <= 60
                                Spacer(Modifier.width(6.dp))
                                Text("${tp.temp.roundToInt()}°", fontSize = f.sub, fontWeight = FontWeight.Bold,
                                    color = if (fresh) Palette.red else MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(6.dp))
                                Text(Geo.updatedCaption(tp.epochMin, java.time.ZoneId.systemDefault()),
                                    fontSize = f.data,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    color = Palette.tempSoft)   // K57 + K59 (variant B)
                            }
                        }
                        Text((if (c.alias.isBlank()) listOf(c.region, c.tz)
                              else listOf(c.name, c.region, c.tz)).filter { it.isNotBlank() }.joinToString(" \u00b7 "),
                            fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("\u270F\uFE0F", fontSize = f.data,
                        modifier = Modifier.clickable { renameAsk = c }.padding(end = 10.dp))   // K72
                    Text("\uD83D\uDDD1", fontSize = f.data,
                        modifier = Modifier.clickable { delAsk = c })   // K52 \u00b7 confirm first
                }
                if (zone == null) {
                    Text("Timezone unavailable \u2014 remove and re-add this clock",
                        fontSize = f.data, color = Palette.yellow,
                        modifier = Modifier.padding(top = 4.dp))
                } else {
                    val local = now.withZoneSameInstant(zone)
                    Row(verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)) {
                        Text(local.format(HM), fontSize = f.display, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("${local.format(DFMT)} \u00b7 ${offsetLabel(local)}",
                            fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp))
                    }
                    // K70 — weather metrics grid (variant A): expanded card only, per-metric "—"
                    temps[Geo.key(c.lat, c.lon)]?.let { wx ->
                        @Composable fun metric(label: String, value: String) {
                            Column(Modifier.weight(1f)) {
                                Text(label, fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(value, fontSize = f.data, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                            Row(Modifier.fillMaxWidth()) {
                                metric("Humidity", wx.hum?.let { "${it.roundToInt()}%" } ?: "—")
                                metric("Wind", wx.wind?.let { "${it.roundToInt()} km/h" } ?: "—")
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                metric("Precipitation", wx.precip?.let { "%.1f mm".format(it) } ?: "—")
                                Column(Modifier.weight(1f)) {
                                    Text("Air quality", fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (wx.aqi != null) {
                                        val band = Geo.aqiBand(wx.aqi)
                                        val (bg, fg) = when (band) {
                                            "Good" -> Palette.greenSoft to Palette.green
                                            "Moderate" -> Palette.yellowSoft to Palette.yellow
                                            "Sensitive" -> Palette.tempSoft.copy(alpha = .25f) to Palette.rkBorder
                                            else -> Palette.redSoft to Palette.red
                                        }
                                        Text("AQI ${wx.aqi} · $band", fontSize = f.data, fontWeight = FontWeight.SemiBold,
                                            color = fg, modifier = Modifier
                                                .background(bg, RoundedCornerShape(999.dp))
                                                .padding(8.dp, 1.dp))
                                    } else Text("—", fontSize = f.data, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    val sky = remember(local.toLocalDate(), c.tz, s.alarmConvention) {
                        clockSky(local.toLocalDate(), c.lat, c.lon, zone, s.alarmConvention)
                    }
                    val center = TextAlign.Center
                    Column(Modifier.fillMaxWidth().border(1.dp, line, RoundedCornerShape(10.dp))) {
                        Row(Modifier.fillMaxWidth().background(Palette.blueBand,
                            RoundedCornerShape(10.dp, 10.dp, 0.dp, 0.dp)).padding(4.dp, 5.dp)) {
                            Text("", Modifier.weight(.20f), fontSize = f.data)
                            Text("Rise", Modifier.weight(.25f), fontSize = f.data,
                                fontWeight = FontWeight.SemiBold, color = Palette.blueText, textAlign = center)
                            Text("Set", Modifier.weight(.25f), fontSize = f.data,
                                fontWeight = FontWeight.SemiBold, color = Palette.blueText, textAlign = center)
                            Text("Duration", Modifier.weight(.30f), fontSize = f.data,
                                fontWeight = FontWeight.SemiBold, color = Palette.blueText, textAlign = center)
                        }
                        HorizontalDivider(color = line)
                        Row(Modifier.fillMaxWidth().padding(4.dp, 5.dp)) {
                            Text("Sun", Modifier.weight(.20f), fontSize = f.data,
                                fontWeight = FontWeight.SemiBold, textAlign = center)
                            Text(sky.sunR?.format(HM) ?: "\u2014", Modifier.weight(.25f),
                                fontSize = f.data, textAlign = center)
                            Text(sky.sunS?.format(HM) ?: "\u2014", Modifier.weight(.25f),
                                fontSize = f.data, textAlign = center)
                            Text(sky.sunDur?.let { fmtHM(it) } ?: "\u2014", Modifier.weight(.30f),
                                fontSize = f.data, fontWeight = FontWeight.SemiBold, textAlign = center)
                        }
                        HorizontalDivider(color = line)
                        Row(Modifier.fillMaxWidth().background(zebra()).padding(4.dp, 5.dp)) {
                            Text("Moon", Modifier.weight(.20f), fontSize = f.data,
                                fontWeight = FontWeight.SemiBold, textAlign = center)
                            Text(sky.moonR?.format(HM) ?: "\u2014", Modifier.weight(.25f),
                                fontSize = f.data, textAlign = center)
                            Text(sky.moonS?.format(HM) ?: "\u2014", Modifier.weight(.25f),
                                fontSize = f.data, textAlign = center)
                            Text(sky.moonDur?.let { fmtHM(it) } ?: "\u2014", Modifier.weight(.30f),
                                fontSize = f.data, fontWeight = FontWeight.SemiBold, textAlign = center)
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { addOpen = true }, enabled = clocks.size < 100,   // K55
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("\uFF0B Add clock", fontSize = f.data)
                }
                Text("${clocks.size} of 100 clocks", fontSize = f.data,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp, bottom = 16.dp))
            }
        }
    }
    if (pullState.progress > 0f || pullState.isRefreshing)   // K60 — never at rest
        PullToRefreshContainer(pullState, Modifier.align(Alignment.TopCenter))
    }

    renameAsk?.let { c ->   // K72 — alias editor; blank restores the original name
        var txt by remember(c) { mutableStateOf(c.shown()) }
        AlertDialog(onDismissRequest = { renameAsk = null },
            title = { Text("Rename clock") },
            text = {
                Column {
                    OutlinedTextField(value = txt, onValueChange = { txt = it }, singleLine = true)
                    Text("Blank restores \u201c" + c.name + "\u201d", fontSize = f.data,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp))
                }
            },
            confirmButton = { TextButton(onClick = {
                scope.launch {
                    val a = txt.trim().let { if (it == c.name) "" else it }
                    Prefs.writeClocks(ctx, clocks.map {
                        if (Geo.key(it.lat, it.lon) == Geo.key(c.lat, c.lon)) it.copy(alias = a) else it })
                    renameAsk = null
                }
            }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renameAsk = null }) { Text("Cancel") } })
    }
    delAsk?.let { c ->
        AlertDialog(onDismissRequest = { delAsk = null },
            title = { Text("Remove clock?") },
            text = { Text("${c.name} \u2014 this only removes the clock, not any saved place.",
                fontSize = f.data) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try { Prefs.writeClocks(ctx, Prefs.readClocks(ctx).filterNot {
                            Geo.key(it.lat, it.lon) == Geo.key(c.lat, c.lon) })
                        } catch (t: Throwable) { Logger.e("Clocks", "delete failed", t) }
                    }
                    delAsk = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { delAsk = null }) { Text("Cancel") } })
    }
    if (addOpen) AddClockDialog(f, clocks, saved,
        onAdd = { cp ->
            scope.launch {
                try { Prefs.writeClocks(ctx, Prefs.readClocks(ctx) + cp) }
                catch (t: Throwable) { Logger.e("Clocks", "add failed", t) }
            }
            addOpen = false
        },
        onClose = { addOpen = false })
}

@Composable
private fun AddClockDialog(f: Fonts, existing: List<ClockPlace>,
                           saved: List<com.krishna.kalam.data.Place>,
                           onAdd: (ClockPlace) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GeoCandidate>>(emptyList()) }
    var busy by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    val line = MaterialTheme.colorScheme.outlineVariant
    fun already(lat: Double, lon: Double) = existing.any { Geo.key(it.lat, it.lon) == Geo.key(lat, lon) }
    AlertDialog(onDismissRequest = onClose,
        title = { Text("Add clock") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = q, onValueChange = { q = it },
                        label = { Text("Search any place", fontSize = f.data) },
                        modifier = Modifier.weight(1f), singleLine = true)
                    TextButton(onClick = {
                        busy = "Searching\u2026"; err = ""; results = emptyList()
                        scope.launch {
                            val (srcTag, c) = resolvePlaces(ctx, q)
                            Logger.e("Clocks", "search '$q' via $srcTag \u00d7${c.size}")
                            results = c
                            busy = ""
                            if (c.isEmpty()) err = "No places found \u2014 try a different spelling"
                        }
                    }, enabled = q.isNotBlank()) { Text("Search", fontSize = f.data) }
                }
                results.forEach { c ->
                    val dup = already(c.lat, c.lon)
                    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)
                        .border(1.dp, line, RoundedCornerShape(10.dp))
                        .clickable(enabled = !dup && busy.isBlank()) {
                            busy = "Determining timezone\u2026"; err = ""
                            scope.launch {
                                val tz = fetchTimezone(c.lat, c.lon)
                                busy = ""
                                if (tz == null)
                                    err = "Couldn't determine the timezone \u2014 clock not added. Check connection and retry."
                                else {
                                    val parts = c.label.split(" \u00b7 ")
                                    onAdd(ClockPlace(parts.first(),
                                        parts.drop(1).joinToString(" \u00b7 "), tz, c.lat, c.lon))
                                }
                            }
                        }
                        .padding(10.dp, 8.dp)) {
                        Text(c.label + if (dup) "  \u2014 added \u2713" else "", fontSize = f.data,
                            fontWeight = FontWeight.SemiBold,
                            color = if (dup) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface)
                        Text("${"%.4f".format(c.lat)}, ${"%.4f".format(c.lon)}",
                            fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (busy.isNotBlank()) Text(busy, fontSize = f.data,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp))
                if (err.isNotBlank()) Text(err, fontSize = f.data, color = Palette.yellow,
                    modifier = Modifier.padding(top = 6.dp))
                val savedRows = saved.filter { it.saved }
                if (savedRows.isNotEmpty()) {
                    Text("SAVED", fontSize = f.data, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                    savedRows.forEach { p ->
                        val dup = already(p.lat, p.lon)
                        Row(Modifier.fillMaxWidth()
                            .clickable(enabled = !dup) {
                                onAdd(ClockPlace(p.name, "Saved", p.tz, p.lat, p.lon))
                            }.padding(vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(p.name, fontSize = f.data,
                                color = if (dup) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.onSurface)
                            if (dup) Text("Added \u2713", fontSize = f.data,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}
