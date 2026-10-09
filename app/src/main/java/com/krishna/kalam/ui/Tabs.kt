package com.krishna.kalam.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.krishna.kalam.api.Prokerala
import com.krishna.kalam.core.*
import com.krishna.kalam.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeekTab(s: Settings, f: Fonts, place: Place, today: LocalDate) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val zone = remember(place.tz) { ZoneId.of(place.tz) }
    val visibleKalams = Kalam.entries.filter { s.visible[it] == true }
    var kalam by remember(visibleKalams) {
        mutableStateOf(if (s.lastKalamTab in visibleKalams) s.lastKalamTab else visibleKalams.firstOrNull() ?: Kalam.RK)
    }
    val monday = today.with(DayOfWeek.MONDAY)
    val pager = rememberPagerState(initialPage = 1) { 7 }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (k in visibleKalams) {
                val on = k == kalam
                Box(Modifier.weight(1f)
                    .clickable {
                        kalam = k
                        scope.launch { Prefs.writeSettings(ctx, Prefs.readSettings(ctx).copy(lastKalamTab = k)) }
                    }
                    .background(if (on) Palette.bg(k) else Color.Transparent, RoundedCornerShape(999.dp))
                    .border(1.dp, if (on) Palette.border(k) else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp))
                    .padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                    Text(k.code, fontSize = f.data,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (on) Palette.text(k) else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            val weekStart = monday.plusWeeks((page - 1).toLong())
            WeekPage(s, f, place, zone, weekStart, kalam, today)
        }
        Text("Swipe \u00b7 1 week back, 5 ahead", fontSize = f.data,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 10.dp))
    }
}

@Composable
fun WeekPage(s: Settings, f: Fonts, place: Place, zone: ZoneId, weekStart: LocalDate, k: Kalam, today: LocalDate) {
    val line = MaterialTheme.colorScheme.outlineVariant
    val fmtHdr = DateTimeFormatter.ofPattern("dd MMM yyyy")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp, 4.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${weekStart.format(DateTimeFormatter.ofPattern("dd MMM"))} \u2013 ${weekStart.plusDays(6).format(fmtHdr)}",
                fontSize = f.header, fontWeight = FontWeight.SemiBold)
            val label = when {
                weekStart == today.with(DayOfWeek.MONDAY) -> "This week"
                weekStart.isBefore(today) -> "Past week"
                else -> "Ahead"
            }
            Text("$label \u00b7 ${if (s.weekConvention == Convention.GEOMETRIC) "geometric" else "observational"}",
                fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().border(1.dp, line, RoundedCornerShape(12.dp))) {
            // K41 — selected-kalam banner: solid colour, full name
            Text(k.full, fontSize = f.sub, fontWeight = FontWeight.Bold, color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
                    .background(Palette.border(k), RoundedCornerShape(12.dp, 12.dp, 0.dp, 0.dp))
                    .padding(vertical = 7.dp))
            // K20 — header band tinted by the active kalam
            Row(Modifier.fillMaxWidth().background(Palette.bg(k))
                .padding(6.dp, 8.dp)) {   // K43 — content-proportional grid, headers centered
                Text("Date", Modifier.weight(.22f), fontSize = f.sub, fontWeight = FontWeight.SemiBold,
                    color = Palette.text(k), textAlign = TextAlign.Center)
                Text("Day", Modifier.weight(.13f), fontSize = f.sub, fontWeight = FontWeight.SemiBold,
                    color = Palette.text(k), textAlign = TextAlign.Center)
                Text("Time", Modifier.weight(.37f), fontSize = f.sub, fontWeight = FontWeight.SemiBold,
                    color = Palette.text(k), textAlign = TextAlign.Center)
                Text("Duration", Modifier.weight(.28f), fontSize = f.sub, fontWeight = FontWeight.SemiBold,
                    color = Palette.text(k), textAlign = TextAlign.Center)
            }
            for (i in 0..6) {
                val d = weekStart.plusDays(i.toLong())
                val isToday = d == today
                val past = d.isBefore(today)
                val res = Engine.computeDay(d, place.lat, place.lon, zone, s.weekConvention)
                val span = (res as? DayResult.Ok)?.spans?.find { it.kalam == k }
                val rowBg = when {
                    isToday -> MaterialTheme.colorScheme.primary.copy(alpha = .10f)
                    i % 2 == 1 -> zebra()          // K20 zebra (>6 rows)
                    else -> Color.Transparent
                }
                // K67/K69 — per-row Bengali date + full-width tithi sub-line (failures hide only this row's extras)
                val bnRow = remember(d, zone) { try { com.krishna.kalam.core.Bangla.bengaliDate(d, zone).also {
                    if (it == null) com.krishna.kalam.core.Logger.e("Bangla", "traditional date unavailable: $d")
                } } catch (t: Throwable) { com.krishna.kalam.core.Logger.e("Bangla", "date failed", t); null } }
                val tithRow = remember(d) {
                    try {
                        val sr = (com.krishna.kalam.core.Solar.sunTimes(d, place.lat, place.lon, zone,
                            com.krishna.kalam.core.Solar.ZENITH_OBSERVATIONAL) as? com.krishna.kalam.core.Solar.SunResult.Times)?.sunrise
                        com.krishna.kalam.core.Bangla.tithiForDay(d, sr, zone)
                    } catch (t: Throwable) { null }
                }
                Column(Modifier.fillMaxWidth().background(rowBg)) {
                Row(Modifier.fillMaxWidth().padding(6.dp, 9.dp, 6.dp, 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    val tc = when {
                        isToday -> MaterialTheme.colorScheme.primary
                        past -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    // K43 — centered cells on the header grid; wrap, never clip; today on its own line
                    Column(Modifier.weight(.22f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(d.format(DateTimeFormatter.ofPattern("dd MMM")), fontSize = f.data, color = tc,
                            textAlign = TextAlign.Center)
                        if (bnRow != null) {
                            val bd = com.krishna.kalam.core.Bangla
                            Text("${bd.bnNum(bnRow.day)} ${bnRow.monthBn}", fontSize = f.data, color = Palette.yellow,
                                textAlign = TextAlign.Center)
                            Text("${bnRow.day} ${bnRow.monthEn}", fontSize = f.data,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, textAlign = TextAlign.Center)
                        } else {
                            Text(com.krishna.kalam.core.Bangla.UNAVAILABLE_LABEL, fontSize = f.data,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                        if (isToday) Text("today", fontSize = f.data, color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    }
                    Text(d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH),
                        Modifier.weight(.13f), fontSize = f.data, textAlign = TextAlign.Center,
                        fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal, color = tc)
                    Text(span?.let { "${it.start.format(HM)} \u2013 ${it.end.format(HM)}" } ?: "\u2014",
                        Modifier.weight(.37f), fontSize = f.data, fontWeight = FontWeight.SemiBold, color = tc,
                        textAlign = TextAlign.Center)
                    Text(span?.let { fmtDuration(java.time.Duration.between(it.start, it.end).toMinutes(), s.durationHours) } ?: "\u2014",
                        Modifier.weight(.28f), fontSize = f.data, fontWeight = FontWeight.SemiBold,
                        color = tc, textAlign = TextAlign.Center)
                }
                if (tithRow != null) Row(Modifier.fillMaxWidth().padding(8.dp, 0.dp, 8.dp, 7.dp),
                    horizontalArrangement = Arrangement.Center) {
                    Text("${tithRow.pakshaBn} ${tithRow.nameBn}", fontSize = f.data,
                        color = Palette.blueText, fontWeight = FontWeight.SemiBold)
                    Text(" \u00b7 ${tithRow.pakshaEn} ${tithRow.nameEn}", fontSize = f.data,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                }
                }
                if (i < 6) HorizontalDivider(color = line)
            }
        }
    }
}

@Composable
fun MatchTab(s: Settings, f: Fonts, onHistory: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val history by Prefs.historyFlow(ctx).collectAsState(initial = emptyList())
    var result by remember { mutableStateOf<Prokerala.Outcome?>(null) }
    var busy by remember { mutableStateOf(false) }
    var snack by remember { mutableStateOf<String?>(null) }
    var dupAsk by remember { mutableStateOf<Pair<MatchRecord, MatchRecord>?>(null) }  // existing, new
    val line = MaterialTheme.colorScheme.outlineVariant
    LaunchedEffect(snack) { if (snack != null) { delay(3000); snack = null } }
    // K47 — restore the whole last match from cache (tab switches AND app restarts), zero credits
    LaunchedEffect(s.lastMatchKey) {
        if (result == null && s.lastMatchKey.isNotBlank()) {
            val cached = Prefs.readMatchCache(ctx, s.lastMatchKey)
            val parsed = cached?.let { Prokerala.parse(it, true) }
            if (parsed != null) result = Prokerala.Outcome.Ok(parsed)
            else Logger.e("Match", "K47 restore miss for key ${s.lastMatchKey.take(8)}\u2026")
        }
    }
    // K47 — Saved state derives from history: a record with this cache key exists ⇒ Saved ✓
    val savedInHistory = s.lastMatchKey.isNotBlank() && history.any { it.cacheKey == s.lastMatchKey }

    fun doSave(newRec: MatchRecord, replaceOf: MatchRecord?) {
        scope.launch {
            try {
                val h = Prefs.readHistory(ctx)
                val updated = if (replaceOf != null)
                    h.map { if (it === replaceOf || (it.label.equals(replaceOf.label, true) && it.girlDob == replaceOf.girlDob)) newRec else it }
                else h + newRec
                Prefs.writeHistory(ctx, updated)
                snack = "Saved \u2713 ${newRec.label} — ${fmtScore(newRec.score)}/36"
            } catch (t: Throwable) {
                Logger.e("Match", "history save failed", t)
                snack = "Save failed — see Error logs"
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.End) {
            Text("History \u203A", fontSize = f.data, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onHistory() }.padding(4.dp))
        }
        Column(Modifier.fillMaxWidth().border(1.dp, line, RoundedCornerShape(12.dp)).padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Boy \u2014 ${s.boyName}", fontSize = f.sub, fontWeight = FontWeight.SemiBold)
                Text("edit in settings", fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            KV("DOB", if (s.boyDob.isBlank()) "\u2014 set in Settings" else fmtDate(s.boyDob), f); KV("TOB", if (s.boyTob.isBlank()) "\u2014" else "${s.boyTob} IST", f)
            KV("POB", if (s.boyLat.isNaN()) (s.boyPob.ifBlank { "\u2014 set in Settings" }) else "${s.boyPob} \u00b7 ${"%.4f".format(s.boyLat)}, ${"%.4f".format(s.boyLon)}", f)
        }
        Spacer(Modifier.height(10.dp))
        GirlForm(s, f, onClear = {
            scope.launch {
                Prefs.writeSettings(ctx, Prefs.readSettings(ctx).copy(
                    girlName = "", girlDob = "", girlTob = "", girlTobUnknown = false,
                    girlPob = "", girlLat = Double.NaN, girlLon = Double.NaN,
                    girlMatrimonyId = "", girlMobile = "", lastMatchKey = "", girlPobResolved = ""))
                result = null
            }
        })
        Spacer(Modifier.height(10.dp))
        // K84 — boy defaults are blank in a fresh install (no baked PII); gate both sides
        Button(enabled = !busy && s.girlDob.isNotBlank() && !s.girlLat.isNaN()
                && s.boyDob.isNotBlank() && s.boyTob.isNotBlank() && !s.boyLat.isNaN(),
            onClick = {
                busy = true; result = null
                scope.launch {
                    val cur = Prefs.readSettings(ctx)
                    val tob = if (cur.girlTobUnknown || cur.girlTob.isBlank()) "12:00" else cur.girlTob
                    val out = Prokerala.match(ctx, cur.apiClientId, cur.apiClientSecret,
                        cur.boyDob, cur.boyTob, cur.boyLat, cur.boyLon,
                        cur.girlDob, tob, cur.girlLat, cur.girlLon)
                    if (out is Prokerala.Outcome.Ok) {   // K47 — remember for restore
                        val key = Prokerala.cacheKey(cur.boyDob, cur.boyTob, cur.boyLat, cur.boyLon,
                            cur.girlDob, tob, cur.girlLat, cur.girlLon)
                        Prefs.writeSettings(ctx, Prefs.readSettings(ctx).copy(lastMatchKey = key))
                    }
                    result = out
                    busy = false
                }
            }, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Matching\u2026" else "Match", fontSize = f.data)
        }
        Text("Birth times are IST. Details are sent to Prokerala to compute the score.",
            fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp))
        Spacer(Modifier.height(12.dp))
        when (val r = result) {
            is Prokerala.Outcome.Err -> Column(Modifier.fillMaxWidth()
                .border(1.dp, Palette.red, RoundedCornerShape(12.dp)).padding(12.dp)) {
                Text(r.message, fontSize = f.data, color = Palette.red)
            }
            is Prokerala.Outcome.Ok -> ResultCard(r.r, s, f, savedInHistory,
                onSave = { label, matId, mobile ->
                scope.launch {
                    val cur = Prefs.readSettings(ctx)
                    val key = Prokerala.cacheKey(cur.boyDob, cur.boyTob, cur.boyLat, cur.boyLon,
                        cur.girlDob, if (cur.girlTobUnknown || cur.girlTob.isBlank()) "12:00" else cur.girlTob,
                        cur.girlLat, cur.girlLon)
                    val newRec = MatchRecord(label.trim(), r.r.total, cur.girlTobUnknown,
                        LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM")), "",
                        cur.girlDob, key, matId.trim(), mobile.trim())
                    val existing = Prefs.readHistory(ctx).find {
                        it.girlDob.isNotBlank() && it.girlDob == newRec.girlDob &&
                        it.label.trim().equals(newRec.label, ignoreCase = true) }
                    if (existing != null) dupAsk = existing to newRec else doSave(newRec, null)
                }
            })
            null -> {}
        }
        snack?.let {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp)
                .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(9.dp))
                .padding(12.dp, 9.dp)) {
                Text(it, fontSize = f.data, color = MaterialTheme.colorScheme.inverseOnSurface)
            }
        }
    }
    dupAsk?.let { (old, new) ->
        AlertDialog(onDismissRequest = { dupAsk = null },
            title = { Text("Already saved") },
            text = { Text("\"${old.label}\" with DOB ${fmtDate(old.girlDob)} exists in history " +
                    "(${fmtScore(old.score)}/36, saved ${old.date}). Replace it with this result?") },
            confirmButton = { TextButton(onClick = { doSave(new, old); dupAsk = null }) { Text("Replace") } },
            dismissButton = { TextButton(onClick = { dupAsk = null }) { Text("Cancel") } })
    }
}

fun fmtDate(iso: String): String = try {
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
} catch (t: Throwable) { iso }

fun fmtScore(v: Double): String = "%.1f".format(v).removeSuffix(".0")

/** K38/K40 — one formatter drives Day cards and the Week Duration column. */
fun fmtDuration(mins: Long, hoursForm: Boolean): String {
    fun unit(n: Long, u: String) = "$n $u" + if (n == 1L) "" else "s"
    if (!hoursForm || mins < 60) return unit(mins, "min")
    val h = mins / 60; val m = mins % 60
    return if (m == 0L) unit(h, "hour") else unit(h, "hour") + " " + unit(m, "min")
}

/** K39 — sky durations, fixed h+m form. */
fun fmtHM(mins: Long): String = "%dh %02dm".format(mins / 60, mins % 60)

@Composable
fun KV(k: String, v: String, f: Fonts) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, fontSize = f.data)
    }
}

@Composable
fun GirlForm(s: Settings, f: Fonts, onClear: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val line = MaterialTheme.colorScheme.outlineVariant
    var geoState by remember { mutableStateOf("") }
    var candAsk by remember { mutableStateOf<List<GeoCandidate>?>(null) }
    var pob by remember(s.girlPob) { mutableStateOf(s.girlPob) }
    Column(Modifier.fillMaxWidth().border(1.dp, line, RoundedCornerShape(12.dp)).padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Girl", fontSize = f.sub, fontWeight = FontWeight.SemiBold)
            // K47 — Clear wipes the whole slate: form, save-block fields, last result
            Text("Clear", fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { onClear() })
        }
        FieldRow("Date of birth", if (s.girlDob.isBlank()) "Select" else fmtDate(s.girlDob), f) {
            val base = try { LocalDate.parse(s.girlDob) } catch (t: Throwable) { LocalDate.of(1995, 1, 1) }
            DatePickerDialog(ctx, { _, y, m, d ->
                val picked = LocalDate.of(y, m + 1, d)
                if (picked.isAfter(LocalDate.now())) { Logger.e("Match", "future DOB rejected"); return@DatePickerDialog }
                scope.launch { Prefs.writeSettings(ctx, Prefs.readSettings(ctx).copy(girlDob = picked.toString())) }
            }, base.year, base.monthValue - 1, base.dayOfMonth).show()
        }
        FieldRow("Time of birth \u00b7 IST",
            if (s.girlTobUnknown) "Unknown \u2014 12:00 assumed" else s.girlTob.ifBlank { "Select" }, f) {
            if (s.girlTobUnknown) return@FieldRow
            TimePickerDialog(ctx, { _, h, m ->
                scope.launch { Prefs.writeSettings(ctx, Prefs.readSettings(ctx).copy(girlTob = "%02d:%02d".format(h, m))) }
            }, 12, 0, true).show()
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = s.girlTobUnknown, onCheckedChange = { v ->
                scope.launch { Prefs.writeSettings(ctx, Prefs.readSettings(ctx).copy(girlTobUnknown = v)) }
            })
            Text("Time of birth unknown", fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(value = pob, onValueChange = { pob = it },
            label = { Text("Place of birth", fontSize = f.data) },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    geoState.isNotBlank() -> geoState
                    !s.girlLat.isNaN() && s.girlPobResolved.isNotBlank() ->
                        "Resolved: ${s.girlPobResolved} \u00b7 ${"%.4f".format(s.girlLat)}, ${"%.4f".format(s.girlLon)}"
                    !s.girlLat.isNaN() -> "Resolved: ${"%.4f".format(s.girlLat)}, ${"%.4f".format(s.girlLon)}"
                    else -> "Not resolved yet"
                }, fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                geoState = "Looking up\u2026"
                scope.launch {
                    val (srcTag, c) = resolvePlaces(ctx, pob)
                    Logger.e("Geo", "resolve '$pob' via $srcTag \u00d7${c.size}")
                    when {
                        c.isEmpty() -> {
                            geoState = "Lookup failed \u2014 enter coordinates manually"
                            Prefs.writeSettings(ctx, Prefs.readSettings(ctx).copy(girlPob = pob))
                        }
                        c.size == 1 -> {
                            Prefs.writeSettings(ctx, Prefs.readSettings(ctx).copy(
                                girlPob = pob, girlLat = c[0].lat, girlLon = c[0].lon,
                                girlPobResolved = c[0].label))
                            geoState = ""
                        }
                        else -> {   // K48 — same-name places: user picks
                            Logger.e("Geo", "multi-candidate: '$pob' \u00d7${c.size}")
                            candAsk = c; geoState = ""
                        }
                    }
                }
            }) { Text("Resolve", fontSize = f.data) }
        }
        var latText by remember(s.girlLat) { mutableStateOf(if (s.girlLat.isNaN()) "" else s.girlLat.toString()) }
        var lonText by remember(s.girlLon) { mutableStateOf(if (s.girlLon.isNaN()) "" else s.girlLon.toString()) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = latText, onValueChange = { latText = it },
                label = { Text("Lat", fontSize = f.data) }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(value = lonText, onValueChange = { lonText = it },
                label = { Text("Lon", fontSize = f.data) }, modifier = Modifier.weight(1f), singleLine = true)
        }
        TextButton(onClick = {
            val la = latText.toDoubleOrNull(); val lo = lonText.toDoubleOrNull()
            if (la == null || lo == null || la < -90 || la > 90 || lo < -180 || lo > 180) {
                Logger.e("Match", "manual coordinates rejected: $latText,$lonText"); return@TextButton
            }
            scope.launch { Prefs.writeSettings(ctx, Prefs.readSettings(ctx).copy(
                girlPob = pob, girlLat = la, girlLon = lo, girlPobResolved = "")) }
        }) { Text("Use manual coordinates", fontSize = f.data) }
    }
    candAsk?.let { list ->
        PlacePickerDialog(pob, list, f,
            onPick = { c ->
                scope.launch {
                    Prefs.writeSettings(ctx, Prefs.readSettings(ctx).copy(
                        girlPob = pob, girlLat = c.lat, girlLon = c.lon,
                        girlPobResolved = c.label))
                }
                candAsk = null
            },
            onCancel = { candAsk = null })
    }
}

data class GeoCandidate(val label: String, val lat: Double, val lon: Double)

private suspend fun nominatimFetch(q: String, settlements: Boolean): String? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val url = java.net.URL("https://nominatim.openstreetmap.org/search?q=" +
                    java.net.URLEncoder.encode(q, "UTF-8") +
                    "&format=jsonv2&addressdetails=1&limit=8&accept-language=en" +
                    if (settlements) "&featureType=settlement" else "")
            val c = url.openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 5000; c.readTimeout = 5000
            c.setRequestProperty("User-Agent", com.krishna.kalam.AppVersion.USER_AGENT)
            if (c.responseCode != 200) { Logger.e("Geo", "nominatim HTTP ${c.responseCode}"); null }
            else c.inputStream.bufferedReader().readText()
        } catch (t: Throwable) { Logger.e("Geo", "nominatim fetch failed", t); null }
    }

/** K49 — Nominatim first (settlement pass, one unfiltered retry), ordered by the K49 law,
 *  4-decimal deduped; total fallback to the v1.5 platform geocoder; failures degrade to empty. */
suspend fun resolvePlaces(ctx: android.content.Context, name: String): Pair<String, List<GeoCandidate>> {
    if (name.isBlank()) return "none" to emptyList()
    nominatimFetch(name, true)?.let { first ->
        var cands = Geo.parseNominatim(first, true)
        if (cands.isEmpty())
            nominatimFetch(name, false)?.let { cands = Geo.parseNominatim(it, false) }
        if (cands.isNotEmpty()) {
            val seen = HashSet<String>()
            val ordered = Geo.order(cands).filter { seen.add(Geo.key(it.lat, it.lon)) }
            return "nominatim" to ordered.map {
                GeoCandidate(Geo.label(it.name, it.state, it.country), it.lat, it.lon) }
        }
    }
    return "geocoder" to kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            @Suppress("DEPRECATION")
            val list = android.location.Geocoder(ctx, Locale.ENGLISH).getFromLocationName(name, 5)
                ?: return@withContext emptyList()
            val seen = HashSet<String>()
            list.mapNotNull { a ->
                val la = a.latitude; val lo = a.longitude
                if (!seen.add(Geo.key(la, lo))) null
                else GeoCandidate(
                    Geo.label(a.locality ?: a.subAdminArea ?: a.featureName, a.adminArea, a.countryCode),
                    la, lo)
            }
        } catch (t: Throwable) { Logger.e("Geo", "forward geocode failed for '$name'", t); emptyList() }
    }
}

@Composable
fun FieldRow(label: String, value: String, f: Fonts, onClick: () -> Unit) {
    Column(Modifier.padding(top = 6.dp)) {
        Text(label, fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().clickable { onClick() }
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(9.dp))
            .padding(10.dp, 9.dp)) { Text(value, fontSize = f.data) }
    }
}

@Composable
fun DoshaChip(d: Prokerala.Dosha, f: Fonts) {
    val (bg, fg) = when (d.color) {
        Prokerala.DoshaColor.RED -> Palette.redSoft to Palette.red
        Prokerala.DoshaColor.GREEN -> Palette.greenSoft to Palette.green
        Prokerala.DoshaColor.YELLOW -> Palette.yellowSoft to Palette.yellow
    }
    Text(d.text, fontSize = f.data, fontWeight = FontWeight.SemiBold, color = fg,
        modifier = Modifier.background(bg, RoundedCornerShape(999.dp))
            .border(1.dp, fg, RoundedCornerShape(999.dp)).padding(10.dp, 3.dp))
}

/** K21 result: details table + full guna + summary + union band. Reused by history detail. */
@Composable
fun AdvResult(r: Prokerala.MatchResult, s: Settings, f: Fonts, girlNameOverride: String? = null) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val line = MaterialTheme.colorScheme.outlineVariant
    val gName = (girlNameOverride ?: s.girlName).ifBlank { "Not Given" }
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().border(1.dp, line, RoundedCornerShape(12.dp))) {
            Row(Modifier.fillMaxWidth().background(Palette.blueBand, RoundedCornerShape(12.dp, 12.dp, 0.dp, 0.dp))
                .padding(8.dp, 7.dp)) {
                Text("", Modifier.width(86.dp), fontSize = f.sub)
                Text("Girl", Modifier.weight(1f), fontSize = f.sub, fontWeight = FontWeight.SemiBold, color = Palette.blueText)
                Text("Boy", Modifier.weight(1f), fontSize = f.sub, fontWeight = FontWeight.SemiBold, color = Palette.blueText)
            }
            fun withVedic(name: String, vedic: String) =
                if (name == "NONE") "NONE" else if (vedic == "NONE" || vedic.isBlank()) name else "$name ($vedic)"
            val rows = listOf(
                Triple("Name", gName, s.boyName.ifBlank { "Not Given" }),
                Triple("DOB", if (s.girlDob.isBlank()) "\u2014" else fmtDate(s.girlDob), fmtDate(s.boyDob)),
                Triple("TOB", if (s.girlTobUnknown) "12:00 (assumed)" else s.girlTob.ifBlank { "\u2014" }, s.boyTob),
                Triple("Place", s.girlPob.ifBlank { "\u2014" }, s.boyPob),
                Triple("Janma Rasi", r.girl.rasi, r.boy.rasi),
                Triple("Rasi Lord", withVedic(r.girl.rasiLord, r.girl.rasiLordVedic),
                    withVedic(r.boy.rasiLord, r.boy.rasiLordVedic)),
                Triple("Janma Nakshatra", r.girl.nakshatra, r.boy.nakshatra))
            rows.forEachIndexed { i, (k, g, b) ->
                Row(Modifier.fillMaxWidth().background(if (i % 2 == 0) zebra() else Color.Transparent)
                    .padding(8.dp, 6.dp)) {
                    Text(k, Modifier.width(86.dp), fontSize = f.data, fontWeight = FontWeight.SemiBold)
                    Text(g, Modifier.weight(1f), fontSize = f.data)
                    Text(b, Modifier.weight(1f), fontSize = f.data)
                }
            }
            // K35 — More birth details, persisted (K37)
            Row(Modifier.fillMaxWidth().clickable {
                    scope.launch { Prefs.writeSettings(ctx, Prefs.readSettings(ctx).copy(moreDetailsOpen = !s.moreDetailsOpen)) }
                }.background(Color(0xFFF3F6FB)).padding(8.dp, 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("More birth details", fontSize = f.data, fontWeight = FontWeight.SemiBold, color = Palette.blueText)
                Text(if (s.moreDetailsOpen) "\u25B4" else "\u25BE", color = Palette.blueText)
            }
            if (s.moreDetailsOpen) {
                Row(Modifier.fillMaxWidth().padding(8.dp, 6.dp)) {
                    Text("Pada", Modifier.width(86.dp), fontSize = f.data, fontWeight = FontWeight.SemiBold)
                    Text(r.girl.pada, Modifier.weight(1f), fontSize = f.data)
                    Text(r.boy.pada, Modifier.weight(1f), fontSize = f.data)
                }
                Row(Modifier.fillMaxWidth().background(zebra()).padding(8.dp, 6.dp)) {
                    Text("Nakshatra Lord", Modifier.width(86.dp), fontSize = f.data, fontWeight = FontWeight.SemiBold)
                    Text(withVedic(r.girl.nakshatraLord, r.girl.nakshatraLordVedic), Modifier.weight(1f), fontSize = f.data)
                    Text(withVedic(r.boy.nakshatraLord, r.boy.nakshatraLordVedic), Modifier.weight(1f), fontSize = f.data)
                }
            }
            Row(Modifier.fillMaxWidth().padding(8.dp, 6.dp)) {
                Text("System Used", Modifier.width(86.dp), fontSize = f.data, fontWeight = FontWeight.SemiBold)
                Text("Ashtakoot (Guna Milan)", fontSize = f.data)
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().border(1.dp, line, RoundedCornerShape(12.dp))) {
            Row(Modifier.fillMaxWidth().background(Palette.blueBand, RoundedCornerShape(12.dp, 12.dp, 0.dp, 0.dp))
                .padding(6.dp, 7.dp)) {
                Text("#", Modifier.width(22.dp), fontSize = f.sub, fontWeight = FontWeight.SemiBold, color = Palette.blueText)
                Text("Guna", Modifier.weight(1f), fontSize = f.sub, fontWeight = FontWeight.SemiBold, color = Palette.blueText)
                Text("Girl", Modifier.weight(1f), fontSize = f.sub, fontWeight = FontWeight.SemiBold, color = Palette.blueText)
                Text("Boy", Modifier.weight(1f), fontSize = f.sub, fontWeight = FontWeight.SemiBold, color = Palette.blueText)
                Text("Points", Modifier.width(52.dp), fontSize = f.sub, fontWeight = FontWeight.SemiBold, color = Palette.blueText)
                Text("", Modifier.width(16.dp), fontSize = f.sub)
            }
            r.kootas.forEachIndexed { i, k ->
                val open = (s.gunaDescMask shr i) and 1 == 1 && k.desc.isNotBlank()
                Row(Modifier.fillMaxWidth().background(if (i % 2 == 0) zebra() else Color.Transparent)
                    .clickable(enabled = k.desc.isNotBlank()) {
                        scope.launch { Prefs.writeSettings(ctx,
                            Prefs.readSettings(ctx).let { cur -> cur.copy(gunaDescMask = cur.gunaDescMask xor (1 shl i)) }) }
                    }
                    .padding(6.dp, 6.dp)) {
                    Text("${i + 1}", Modifier.width(22.dp), fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(k.name, Modifier.weight(1f), fontSize = f.data)
                    Text(k.girl, Modifier.weight(1f), fontSize = f.data, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(k.boy, Modifier.weight(1f), fontSize = f.data, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text("${fmtScore(k.points)} / ${fmtScore(k.max)}", Modifier.width(52.dp), fontSize = f.data,
                        fontWeight = FontWeight.SemiBold, softWrap = false,
                        color = if (k.points == 0.0) Palette.red else MaterialTheme.colorScheme.onSurface)
                    Text(if (k.desc.isBlank()) " " else if (open) "\u25B4" else "\u25BE",
                        Modifier.width(16.dp), fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (open) {
                    Text(k.desc, fontSize = f.data, color = Color(0xFF333333), lineHeight = f.data * 1.35,
                        modifier = Modifier.fillMaxWidth().background(Color(0xFFF7F9FC))
                            .padding(start = 28.dp, top = 8.dp, end = 10.dp, bottom = 10.dp))
                }
            }
            r.extraNone.forEachIndexed { i, n ->
                Row(Modifier.fillMaxWidth().padding(6.dp, 6.dp)) {
                    Text("${r.kootas.size + i + 1}", Modifier.width(22.dp), fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(n, Modifier.weight(3f), fontSize = f.data)
                    Text("NONE", Modifier.width(52.dp), fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(" ", Modifier.width(16.dp), fontSize = f.data)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().border(1.dp, line, RoundedCornerShape(12.dp)).padding(10.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Girl Mangal Dosha", fontSize = f.data)
                DoshaChip(r.girlDosha, f)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Boy Mangal Dosha", fontSize = f.data)
                DoshaChip(r.boyDosha, f)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total Guna Milan Points", fontSize = f.data)
                Text("${fmtScore(r.total)} / ${fmtScore(r.max)}", fontSize = f.data, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        val (ubg, ufg) = when (r.unionColor) {
            Prokerala.DoshaColor.GREEN -> Palette.greenSoft to Palette.green
            Prokerala.DoshaColor.RED -> Palette.redSoft to Palette.red
            Prokerala.DoshaColor.YELLOW -> Palette.yellowSoft to Palette.yellow
        }
        Text(r.union, fontSize = f.sub, fontWeight = FontWeight.Bold, color = ufg,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().background(ubg, RoundedCornerShape(10.dp))
                .border(1.dp, ufg, RoundedCornerShape(10.dp)).padding(10.dp))
    }
}

@Composable
fun ResultCard(r: Prokerala.MatchResult, s: Settings, f: Fonts, savedInHistory: Boolean,
               onSave: (String, String, String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth()) {
        if (s.girlTobUnknown)
            Text("TOB assumed 12:00 \u2014 score approximate", fontSize = f.data, color = Palette.yellow,
                modifier = Modifier.fillMaxWidth()
                    .background(Palette.yellowSoft, RoundedCornerShape(8.dp))
                    .border(1.dp, Palette.yellow, RoundedCornerShape(8.dp)).padding(8.dp),
                textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        AdvResult(r, s, f)
        if (r.fromCache) Text("cached \u00b7 restored", fontSize = f.data,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp))
        // K44 + K45 — save block: Name · Matrimony ID · Mobile Contact · Save (draft-persistent)
        var name by remember(s.girlName) { mutableStateOf(s.girlName) }
        var matId by remember(s.girlMatrimonyId) { mutableStateOf(s.girlMatrimonyId) }
        var mobile by remember(s.girlMobile) { mutableStateOf(s.girlMobile) }
        OutlinedTextField(value = name, onValueChange = { name = it
                scope.launch { Prefs.writeSettings(ctx, Prefs.readSettings(ctx).copy(girlName = it)) } },
            label = { Text("Girl's Name", fontSize = f.data) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true)
        OutlinedTextField(value = matId, onValueChange = { matId = it
                scope.launch { Prefs.writeSettings(ctx, Prefs.readSettings(ctx).copy(girlMatrimonyId = it)) } },
            label = { Text("Matrimony ID (optional)", fontSize = f.data) },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp), singleLine = true)
        OutlinedTextField(value = mobile, onValueChange = { mobile = it
                scope.launch { Prefs.writeSettings(ctx, Prefs.readSettings(ctx).copy(girlMobile = it)) } },
            label = { Text("Mobile Contact (optional)", fontSize = f.data) },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp), singleLine = true)
        val canSave = name.isNotBlank() && !savedInHistory
        OutlinedButton(enabled = canSave,
            onClick = { onSave(name, matId, mobile) },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(if (savedInHistory) "Saved \u2713" else "Save to history", fontSize = f.data)
        }
        if (name.isBlank() && !savedInHistory)
            Text("Enter the girl's name to save", fontSize = f.data, color = Palette.yellow,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp))
        Text("Lahiri ayanamsa \u00b7 via Prokerala", fontSize = f.data,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp))
    }
}


/** K48/K49/K50 — shared same-name place picker. */
@Composable
fun PlacePickerDialog(query: String, list: List<GeoCandidate>, f: Fonts,
                      onPick: (GeoCandidate) -> Unit, onCancel: () -> Unit) {
    AlertDialog(onDismissRequest = onCancel,
        title = { Text("Choose place of birth") },
        text = {
            Column {
                Text("\"$query\" matches ${list.size} places", fontSize = f.data,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                list.forEach { c ->
                    Column(Modifier.fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                        .clickable { onPick(c) }
                        .padding(10.dp, 8.dp)) {
                        Text(c.label, fontSize = f.data, fontWeight = FontWeight.SemiBold)
                        Text("${"%.4f".format(c.lat)}, ${"%.4f".format(c.lon)}",
                            fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } })
}
