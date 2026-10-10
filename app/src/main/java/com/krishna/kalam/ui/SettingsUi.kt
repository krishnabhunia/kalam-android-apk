package com.krishna.kalam.ui

import android.Manifest
import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.krishna.kalam.alarm.AlarmScheduler
import com.krishna.kalam.api.Prokerala
import com.krishna.kalam.core.Convention
import com.krishna.kalam.core.Kalam
import com.krishna.kalam.core.Logger
import com.krishna.kalam.data.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID


@Composable
fun BackBar(title: String, f: Fonts, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(6.dp, 8.dp, 14.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {   // K19/K31 — real back affordance everywhere
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(title, fontSize = f.header, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun SettingsScreen(committed: Settings, f0: Fonts, saved: List<Place>, onBack: () -> Unit,
                   onSavedManager: () -> Unit, onErrorLogs: () -> Unit,
                   onHistory: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // Clock restoration is immediate; retain unrelated unsaved edits.
    val draftKey = committed.copy(clockSort = "ADDED", clockGroup = "NONE", groupStages = "", sortDir = "0")
    var draft by remember(draftKey) { mutableStateOf(committed) }
    LaunchedEffect(committed.clockSort, committed.clockGroup, committed.groupStages, committed.sortDir) {
        draft = draft.copy(clockSort = committed.clockSort, clockGroup = committed.clockGroup,
            groupStages = committed.groupStages, sortDir = committed.sortDir)
    }
    val dirty = draft != committed
    var askExit by remember { mutableStateOf(false) }
    var openGroup by remember { mutableStateOf<String?>(null) }   // K62 — all collapsed on entry
    fun groupToggle(name: String) { openGroup = if (openGroup == name) null else name }
    val openDeck = rememberSaveable { mutableStateOf(mutableMapOf<Int, String?>()) }
    var cascadeNote by remember { mutableStateOf<String?>(null) }
    var editingCreds by remember(committed) { mutableStateOf(false) }
    var askCredEdit by remember { mutableStateOf(false) }
    var askClearMatch by remember { mutableStateOf(false) }
    val f = fontsOf(draft)   // live preview: the whole settings screen follows the draft ladder

    fun save() = scope.launch {
        try {
            Prefs.writeSettings(ctx, draft)
            AlarmScheduler.rearmToday(ctx)
            onBack()
        } catch (t: Throwable) { Logger.e("Settings", "commit failed — draft kept", t) }
    }
    fun exit() { if (dirty) askExit = true else onBack() }
    BackHandler { exit() }

    fun deckOpen(name: String) = openDeck.value[0] == name   // K58: one open across the page
    fun toggleDeck(name: String) {
        val m = openDeck.value.toMutableMap()
        m[0] = if (m[0] == name) null else name
        openDeck.value = m
    }

    Column(Modifier.fillMaxSize()) {
        BackBar("Settings", f, onBack = { exit() }) {
            OutlinedButton(enabled = dirty, onClick = { draft = committed; editingCreds = false }) {
                Text("Discard", fontSize = f.data) }
            Spacer(Modifier.width(8.dp))
            Button(enabled = dirty, onClick = { save() }) { Text("Save", fontSize = f.data) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp, 4.dp, 12.dp, 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GroupBox("Display", f, openGroup == "Display", { groupToggle("Display") }) {   // K58
                    Deck("Text size", f, deckOpen("text"), { toggleDeck("text") },
                        hd = MaterialTheme.colorScheme.primary.copy(alpha = .12f),
                        hdText = MaterialTheme.colorScheme.primary) {
                        val cur = Ladder.L(draft.fontDisplay, draft.fontHeader, draft.fontSub, draft.fontData)
                        val names = listOf("Display" to "kalam time ranges", "Header" to "titles, tabs, kalam names",
                            "Sub-header" to "section & table headers", "Data" to "body, cells, notes")
                        names.forEachIndexed { tier, (n, hint) ->
                            val v = listOf(cur.display, cur.header, cur.sub, cur.data)[tier]
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(n, fontSize = f.data)
                                    Text(hint, fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Stepper("\u2212", f, Ladder.canChange(cur, tier, -1)) {
                                        Ladder.change(cur, tier, -1)?.let { o ->
                                            draft = draft.copy(fontDisplay = o.value.display, fontHeader = o.value.header,
                                                fontSub = o.value.sub, fontData = o.value.data)
                                            cascadeNote = o.cascadeNote
                                        }
                                    }
                                    Text("$v", fontSize = f.data, fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.width(26.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    Stepper("+", f, Ladder.canChange(cur, tier, +1)) {
                                        Ladder.change(cur, tier, +1)?.let { o ->
                                            draft = draft.copy(fontDisplay = o.value.display, fontHeader = o.value.header,
                                                fontSub = o.value.sub, fontData = o.value.data)
                                            cascadeNote = o.cascadeNote
                                        }
                                    }
                                }
                            }
                        }
                        cascadeNote?.let {
                            Text(it, fontSize = f.data, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = .10f), RoundedCornerShape(8.dp))
                                    .padding(8.dp))
                        }
                        Column(Modifier.fillMaxWidth().padding(top = 6.dp)
                            .background(zebra(), RoundedCornerShape(10.dp)).padding(10.dp)) {
                            Text("Header preview", fontSize = f.header, fontWeight = FontWeight.SemiBold)
                            Text("Sub-header preview", fontSize = f.sub, fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Data text preview \u2014 10:05 \u2013 11:41", fontSize = f.data)
                        }
                    }
                    Deck("Conventions", f, deckOpen("conv"), { toggleDeck("conv") }) {
                        RowToggle("Show both conventions", draft.showBoth, f) { draft = draft.copy(showBoth = it) }
                        Label("Alarm rings at", f)
                        Segmented(draft.alarmConvention, f) { draft = draft.copy(alarmConvention = it) }
                        Label("Week view time", f)
                        Segmented(draft.weekConvention, f) { draft = draft.copy(weekConvention = it) }
                        Label("Kalam duration", f)
                        // K38 — option labels are live examples: today's real duration in both formats
                        val todayMins = remember(draft.activePlaceId, saved) {
                            try {
                                val pl = Cities.byId(draft.activePlaceId)
                                    ?: saved.find { it.id == draft.activePlaceId }
                                    ?: Cities.byId(draft.defaultPlaceId) ?: Cities.BUNDLED[5]
                                val res = com.krishna.kalam.core.Engine.computeDay(LocalDate.now(),
                                    pl.lat, pl.lon, java.time.ZoneId.of(pl.tz), draft.alarmConvention)
                                (res as? com.krishna.kalam.core.DayResult.Ok)?.spans?.firstOrNull()
                                    ?.let { java.time.Duration.between(it.start, it.end).toMinutes() }
                            } catch (t: Throwable) { Logger.e("Settings", "duration example failed", t); null }
                        }
                        SegmentedBool(
                            offLabel = todayMins?.let { fmtDuration(it, false) } ?: "Mins",
                            onLabel = todayMins?.let { fmtDuration(it, true) } ?: "Hours + mins",
                            value = draft.durationHours, f = f) { draft = draft.copy(durationHours = it) }
                    }
                    Deck("Show kalams", f, deckOpen("vis"), { toggleDeck("vis") }) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (k in Kalam.entries) {
                                val on = draft.visible[k] == true
                                Text(k.full, fontSize = f.data,
                                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else Color.Transparent, RoundedCornerShape(999.dp))
                                        .border(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp))
                                        .clickable { draft = draft.copy(visible = draft.visible + (k to !on)) }
                                        .padding(9.dp, 4.dp))
                            }
                        }
                    }
            }
            GroupBox("Alerts", f, openGroup == "Alerts", { groupToggle("Alerts") }) {
                    for (k in Kalam.entries) {
                        val hidden = draft.visible[k] != true
                        Deck(k.full + if (hidden) " \u00b7 hidden, no alerts" else "", f,
                            deckOpen(k.name), { toggleDeck(k.name) },
                            hd = if (hidden) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .4f) else Palette.bg(k),
                            hdText = if (hidden) MaterialTheme.colorScheme.onSurfaceVariant else Palette.text(k),
                            border = if (hidden) MaterialTheme.colorScheme.outlineVariant else Palette.border(k).copy(alpha = .5f)) {
                            if (hidden) {
                                Text("Unhide in Display \u2192 Show kalams to configure alerts.",
                                    fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp))
                            } else {
                                val slot = draft.startSlots[k]!!
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Start alert", fontSize = f.data)
                                    Switch(checked = slot.enabled, onCheckedChange = {
                                        draft = draft.copy(startSlots = draft.startSlots + (k to slot.copy(enabled = it)))
                                    })
                                }
                                OffsetDropdown(slot.offsetMin, f, enabled = slot.enabled) { v ->
                                    draft = draft.copy(startSlots = draft.startSlots + (k to slot.copy(offsetMin = v)))
                                }
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("End alert \u00b7 at end time", fontSize = f.data)
                                    Switch(checked = draft.endSlots[k] == true, onCheckedChange = {
                                        draft = draft.copy(endSlots = draft.endSlots + (k to it))
                                    })
                                }
                            }
                        }
                    }
                    Deck("Sound & ring", f, deckOpen("sound"), { toggleDeck("sound") }) {
                        RowToggle("Start alerts: sound + vibrate", draft.startStyleSound, f) { draft = draft.copy(startStyleSound = it) }
                        RowToggle("End alerts: sound + vibrate", draft.endStyleSound, f) { draft = draft.copy(endStyleSound = it) }
                        Label("Ring duration", f)
                        SimpleDropdown(listOf(3, 5, 7, 10, 15, 20, 30).map { "$it s" },
                            "${draft.ringSeconds} s", f) { sel ->
                            draft = draft.copy(ringSeconds = sel.removeSuffix(" s").toInt())
                        }
                    }
            }
            GroupBox("Location", f, openGroup == "Location", { groupToggle("Location") }) {
                    Deck("Default location", f, deckOpen("def"), { toggleDeck("def") },
                        hd = MaterialTheme.colorScheme.primary.copy(alpha = .12f),
                        hdText = MaterialTheme.colorScheme.primary) {
                        PlaceDropdown(draft.defaultPlaceId, saved, f) { id -> draft = draft.copy(defaultPlaceId = id) }
                    }
                    Deck("Saved locations", f, deckOpen("saved"), { toggleDeck("saved") }) {
                        RowNav("Manage saved locations", "${saved.size} of ${Prefs.MAX_SAVED}", f) { onSavedManager() }
                    }
            }
            ClockBackupControls(f, openGroup == "Clock backup") { groupToggle("Clock backup") }
            GroupBox("Match", f, openGroup == "Match", { groupToggle("Match") }) {
                    Deck("My details", f, deckOpen("my"), { toggleDeck("my") }) {
                        var bname by remember(draft.boyName) { mutableStateOf(draft.boyName) }
                        OutlinedTextField(value = bname, onValueChange = { bname = it; draft = draft.copy(boyName = it) },
                            label = { Text("Name", fontSize = f.data) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        RowNav("DOB", fmtDate(draft.boyDob), f) {
                            val base = LocalDate.parse(draft.boyDob)
                            android.app.DatePickerDialog(ctx, { _, y, m, d ->
                                draft = draft.copy(boyDob = LocalDate.of(y, m + 1, d).toString())
                            }, base.year, base.monthValue - 1, base.dayOfMonth).show()
                        }
                        RowNav("TOB \u00b7 IST", draft.boyTob, f) {
                            val parts = draft.boyTob.split(":")
                            android.app.TimePickerDialog(ctx, { _, h, m ->
                                draft = draft.copy(boyTob = "%02d:%02d".format(h, m))
                            }, parts[0].toInt(), parts[1].toInt(), true).show()
                        }
                        var pob by remember(draft.boyPob) { mutableStateOf(draft.boyPob) }
                        OutlinedTextField(value = pob, onValueChange = { pob = it; draft = draft.copy(boyPob = it) },
                            label = { Text("POB", fontSize = f.data) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        // K50 — same resolver as the Girl form; staged into the draft
                        var boyGeoMsg by remember { mutableStateOf("") }
                        var boyCand by remember { mutableStateOf<List<GeoCandidate>?>(null) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when {
                                    boyGeoMsg.isNotBlank() -> boyGeoMsg
                                    draft.boyPobResolved.isNotBlank() ->
                                        "Resolved: ${draft.boyPobResolved} \u00b7 ${"%.4f".format(draft.boyLat)}, ${"%.4f".format(draft.boyLon)}"
                                    else -> "Coordinates ${"%.4f".format(draft.boyLat)}, ${"%.4f".format(draft.boyLon)}"
                                }, fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                boyGeoMsg = "Looking up\u2026"
                                scope.launch {
                                    val (srcTag, c) = resolvePlaces(ctx, pob)
                                    Logger.e("Geo", "resolve boy '$pob' via $srcTag \u00d7${c.size}")
                                    when {
                                        c.isEmpty() -> boyGeoMsg = "Lookup failed \u2014 enter coordinates manually"
                                        c.size == 1 -> {
                                            draft = draft.copy(boyPob = pob, boyLat = c[0].lat, boyLon = c[0].lon,
                                                boyPobResolved = c[0].label)
                                            boyGeoMsg = ""
                                        }
                                        else -> { boyCand = c; boyGeoMsg = "" }
                                    }
                                }
                            }) { Text("Resolve", fontSize = f.data) }
                        }
                        var blat by remember { mutableStateOf("") }
                        var blon by remember { mutableStateOf("") }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = blat, onValueChange = { blat = it },
                                label = { Text("Lat", fontSize = f.data) }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(value = blon, onValueChange = { blon = it },
                                label = { Text("Lon", fontSize = f.data) }, modifier = Modifier.weight(1f), singleLine = true)
                        }
                        TextButton(onClick = {
                            val la = blat.toDoubleOrNull(); val lo = blon.toDoubleOrNull()
                            if (la == null || lo == null || la < -90 || la > 90 || lo < -180 || lo > 180) {
                                Logger.e("Settings", "boy manual coordinates rejected: $blat,$blon"); return@TextButton
                            }
                            draft = draft.copy(boyPob = pob, boyLat = la, boyLon = lo, boyPobResolved = "")
                        }) { Text("Use manual coordinates", fontSize = f.data) }
                        boyCand?.let { list ->
                            PlacePickerDialog(pob, list, f,
                                onPick = { c ->
                                    draft = draft.copy(boyPob = pob, boyLat = c.lat, boyLon = c.lon,
                                        boyPobResolved = c.label)
                                    boyCand = null
                                },
                                onCancel = { boyCand = null })
                        }
                    }
                    Deck("Prokerala API", f, deckOpen("api"), { toggleDeck("api") }) {
                        val configured = committed.apiClientId.isNotBlank() && committed.apiClientSecret.isNotBlank()
                        if (configured && !editingCreds) {   // K26 lock
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Status", fontSize = f.data)
                                Text("Configured \u2713", fontSize = f.data, fontWeight = FontWeight.SemiBold,
                                    color = Palette.green,
                                    modifier = Modifier.background(Palette.greenSoft, RoundedCornerShape(999.dp))
                                        .border(1.dp, Palette.green, RoundedCornerShape(999.dp)).padding(10.dp, 3.dp))
                            }
                            KV("Client ID", committed.apiClientId.take(4) + "\u2026" + committed.apiClientId.takeLast(4), f)
                            KV("Client secret", "hidden", f)
                            OutlinedButton(onClick = { askCredEdit = true },
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                                Text("Edit credentials", fontSize = f.data) }
                        } else {
                            var idField by remember(draft.apiClientId) { mutableStateOf(draft.apiClientId) }
                            var secField by remember(draft.apiClientSecret) { mutableStateOf(draft.apiClientSecret) }
                            OutlinedTextField(value = idField, onValueChange = { idField = it; draft = draft.copy(apiClientId = it.trim()) },
                                label = { Text("Client ID", fontSize = f.data) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(value = secField, onValueChange = { secField = it; draft = draft.copy(apiClientSecret = it.trim()) },
                                label = { Text("Client secret", fontSize = f.data) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                            Text("Stored on this device only. Save at the top to apply.",
                                fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    Deck("Match data", f, deckOpen("md"), { toggleDeck("md") }) {
                        RowNav("Match history", "", f) { onHistory() }
                        RowNav("Clear Match Details", "", f) { askClearMatch = true }   // K30
                    }
            }
            GroupBox("Logs", f, openGroup == "Logs", { groupToggle("Logs") }) {
                RowNav("Error logs", "", f) { onErrorLogs() }
            }
            // K58 + K62 — About: last box, ALWAYS expanded (no toggle)
            Column(Modifier.fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))) {
                Text("About", fontSize = f.sub, fontWeight = FontWeight.SemiBold, color = Palette.blueText,
                    modifier = Modifier.fillMaxWidth()
                        .background(Palette.blueBand, RoundedCornerShape(14.dp, 14.dp, 0.dp, 0.dp))
                        .padding(12.dp, 9.dp))
                AboutBody(f)
            }
        }
    }

    if (askExit) AlertDialog(onDismissRequest = { askExit = false },
        title = { Text("Unsaved changes") },
        text = { Text("Save your changes before leaving?") },
        confirmButton = { TextButton(onClick = { askExit = false; save() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { askExit = false; draft = committed; onBack() }) { Text("Discard") } })

    if (askCredEdit) AlertDialog(onDismissRequest = { askCredEdit = false },
        title = { Text("Edit API credentials?") },
        text = { Text("Match stops working until valid keys are saved again. Continue?") },
        confirmButton = { TextButton(onClick = { editingCreds = true; askCredEdit = false }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { askCredEdit = false }) { Text("Cancel") } })

    if (askClearMatch) AlertDialog(onDismissRequest = { askClearMatch = false },
        title = { Text("Clear match details?") },
        text = { Text("The girl's saved entries \u2014 name, DOB, time, place \u2014 will be removed from the Match form. History is not affected.") },
        confirmButton = { TextButton(onClick = {
            draft = draft.copy(girlName = "", girlDob = "", girlTob = "", girlTobUnknown = false,
                girlPob = "", girlLat = Double.NaN, girlLon = Double.NaN,
                girlMatrimonyId = "", girlMobile = "", lastMatchKey = "", girlPobResolved = "")
            askClearMatch = false
        }) { Text("Clear", color = Palette.red) } },
        dismissButton = { TextButton(onClick = { askClearMatch = false }) { Text("Cancel") } })
}

@Composable
fun Stepper(sym: String, f: Fonts, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(30.dp)
        .border(1.dp, if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
        .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center) {
        Text(sym, fontSize = f.data,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun GroupBox(title: String, f: Fonts, open: Boolean, onToggle: () -> Unit,
             content: @Composable ColumnScope.() -> Unit) {   // K58 + K62 accordion member
    Column(Modifier.fillMaxWidth()
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))) {
        Row(Modifier.fillMaxWidth()
            .background(Palette.blueBand, if (open) RoundedCornerShape(14.dp, 14.dp, 0.dp, 0.dp)
                                          else RoundedCornerShape(14.dp))
            .clickable { onToggle() }
            .padding(12.dp, 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, fontSize = f.sub, fontWeight = FontWeight.SemiBold, color = Palette.blueText)
            Text(if (open) "\u25BE" else "\u25B8", fontSize = f.sub,
                color = MaterialTheme.colorScheme.primary)
        }
        if (open) Column(Modifier.padding(10.dp, 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun AboutBody(f: Fonts) {   // K58 — inline About (screen retired)
    val ctx = LocalContext.current
    fun open(url: String) {
        try { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
        catch (t: Throwable) { Logger.e("About", "open failed: $url", t) }
    }
    Column(Modifier.padding(12.dp, 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Krishna Dipayan Bhunia", fontSize = f.sub, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Suggestion / Feedback / Bugs / Error", fontSize = f.data,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("kri.subsc@gmail.com", fontSize = f.data, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { open("mailto:kri.subsc@gmail.com") })
        Spacer(Modifier.height(6.dp))
        Text("LinkedIn", fontSize = f.data, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { open("https://www.linkedin.com/in/krishnabhunia/") })
        Text("Facebook", fontSize = f.data, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { open("https://www.facebook.com/kdbhunia/") })
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))
        Text("Version ${com.krishna.kalam.AppVersion.NAME} · ${com.krishna.kalam.AppVersion.RELEASE_DATE}",
            fontSize = f.data)   // K63 — single source: AppVersion (gated vs gradle)
        Spacer(Modifier.height(8.dp))
        Text("Kalam times use geometric sunrise (Sun's centre at the horizon), matching standard panchang convention. Moonrise and moonset use standard refraction-included times. Kundli matching uses Lahiri ayanamsa via Prokerala. Place lookup uses OpenStreetMap Nominatim.",
            fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Deck(title: String, f: Fonts, open: Boolean, onToggle: () -> Unit,
         hd: Color = Color.Transparent, hdText: Color = MaterialTheme.colorScheme.onSurface,
         border: Color = MaterialTheme.colorScheme.outlineVariant,
         content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().border(1.dp, border, RoundedCornerShape(12.dp))) {
        Row(Modifier.fillMaxWidth().clickable { onToggle() }
            .background(hd, RoundedCornerShape(if (open) 12.dp else 12.dp, if (open) 12.dp else 12.dp,
                if (open) 0.dp else 12.dp, if (open) 0.dp else 12.dp))
            .padding(12.dp, 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = f.sub, fontWeight = FontWeight.SemiBold, color = hdText)
            Text(if (open) "\u25B4" else "\u25BE", color = hdText)
        }
        if (open) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.padding(12.dp, 6.dp, 12.dp, 12.dp), content = content)
        }
    }
}

@Composable
fun OffsetDropdown(current: Int, f: Fonts, enabled: Boolean, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val opts = listOf(0, 15, 30, 45, 60, 75, 90, 105, 120)
    fun lbl(v: Int) = if (v == 0) "On time" else "$v min before"
    Box {
        Row(Modifier.fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(9.dp))
            .clickable(enabled = enabled) { open = true }.padding(10.dp, 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(lbl(current), fontSize = f.data,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Text("\u25BE", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (v in opts) DropdownMenuItem(
                text = { Text(lbl(v), fontSize = f.data,
                    fontWeight = if (v == current) FontWeight.SemiBold else FontWeight.Normal) },
                trailingIcon = { if (v == current) Text("\u2713", color = MaterialTheme.colorScheme.primary) },
                onClick = { onPick(v); open = false })
        }
    }
}

@Composable
fun SimpleDropdown(options: List<String>, current: String, f: Fonts, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(9.dp))
            .clickable { open = true }.padding(10.dp, 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(current, fontSize = f.data)
            Text("\u25BE", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (o in options) DropdownMenuItem(
                text = { Text(o, fontSize = f.data,
                    fontWeight = if (o == current) FontWeight.SemiBold else FontWeight.Normal) },
                trailingIcon = { if (o == current) Text("\u2713", color = MaterialTheme.colorScheme.primary) },
                onClick = { onPick(o); open = false })
        }
    }
}

@Composable
fun PlaceDropdown(currentId: String, saved: List<Place>, f: Fonts, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val cur = Cities.byId(currentId) ?: saved.find { it.id == currentId }
    Box {
        Row(Modifier.fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(9.dp))
            .clickable { open = true }.padding(10.dp, 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(cur?.name ?: "Kolkata", fontSize = f.data)
            Text("\u25BE", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false },
            modifier = Modifier.heightIn(max = 320.dp)) {
            DropdownMenuItem(text = { Text("CITIES", fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                enabled = false, onClick = {})
            for (p in Cities.BUNDLED) DropdownMenuItem(
                text = { Text(p.name, fontSize = f.data,
                    fontWeight = if (p.id == currentId) FontWeight.SemiBold else FontWeight.Normal) },
                trailingIcon = { if (p.id == currentId) Text("\u2713", color = MaterialTheme.colorScheme.primary) },
                onClick = { onPick(p.id); open = false })
            if (saved.isNotEmpty()) {
                DropdownMenuItem(text = { Text("SAVED", fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    enabled = false, onClick = {})
                for (p in saved.sortedBy { it.name.lowercase() }) DropdownMenuItem(
                    text = { Text(p.name, fontSize = f.data,
                        fontWeight = if (p.id == currentId) FontWeight.SemiBold else FontWeight.Normal) },
                    trailingIcon = { if (p.id == currentId) Text("\u2713", color = MaterialTheme.colorScheme.primary) },
                    onClick = { onPick(p.id); open = false })
            }
        }
    }
}

@Composable
fun RowNav(k: String, v: String, f: Fonts, onClick: () -> Unit) =
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, fontSize = f.data)
        Text(if (v.isBlank()) "\u203A" else "$v \u203A", fontSize = f.data,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

@Composable
fun RowToggle(k: String, v: Boolean, f: Fonts, onChange: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(k, fontSize = f.data, modifier = Modifier.weight(1f))
        Switch(checked = v, onCheckedChange = onChange)
    }

@Composable fun Label(t: String, f: Fonts) =
    Text(t, fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))

@Composable
fun Segmented(v: Convention, f: Fonts, onChange: (Convention) -> Unit) {
    Row(Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(9.dp))) {
        for (c in Convention.entries) {
            val on = c == v
            Box(Modifier.weight(1f).clickable { onChange(c) }
                .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else Color.Transparent)
                .padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                Text(if (c == Convention.GEOMETRIC) "Geometric" else "Observational",
                    fontSize = f.data,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SegmentedBool(offLabel: String, onLabel: String, value: Boolean, f: Fonts, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(9.dp))) {
        listOf(false to offLabel, true to onLabel).forEach { (v, lbl) ->
            val on = v == value
            Box(Modifier.weight(1f).clickable { onChange(v) }
                .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else Color.Transparent)
                .padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                Text(lbl, fontSize = f.data,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun LocationPicker(s: Settings, f: Fonts, saved: List<Place>, onBack: () -> Unit,
                   onPick: (String) -> Unit, onGps: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        BackBar("Choose location", f, onBack)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(14.dp, 8.dp)) {
            Row(Modifier.fillMaxWidth().clickable { onGps() }
                .background(MaterialTheme.colorScheme.primary.copy(alpha = .10f), RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)).padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("\uD83D\uDEF0 Use current GPS location", fontSize = f.data,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Text("\u203A", color = MaterialTheme.colorScheme.primary)
            }
            Label("Cities", f)
            for (p in Cities.BUNDLED) PlaceRow(p, f, p.id == s.activePlaceId) { onPick(p.id) }
            Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant))
            Label("Saved \u00b7 ${saved.size} of ${Prefs.MAX_SAVED}", f)
            if (saved.isEmpty()) Text("Nothing saved yet.", fontSize = f.data,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp))
            saved.sortedBy { it.name.lowercase() }.forEachIndexed { i, p ->
                Box(Modifier.background(if (i % 2 == 1) zebra() else Color.Transparent)) {
                    PlaceRow(p, f, p.id == s.activePlaceId) { onPick(p.id) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun PlaceRow(p: Place, f: Fonts, active: Boolean, onClick: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(2.dp, 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(p.name, fontSize = f.data, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                if (p.saved) Text("${"%.4f".format(p.lat)}, ${"%.4f".format(p.lon)}",
                    fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (active) Text("\u2713", color = MaterialTheme.colorScheme.primary)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@SuppressLint("MissingPermission")
@Composable
fun GpsSaveScreen(f: Fonts, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(false) }
    var fix by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var acc by remember { mutableStateOf(0f) }
    var name by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Waiting for permission\u2026") }
    var capMsg by remember { mutableStateOf<String?>(null) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { res ->
        granted = res.values.any { it }
        if (!granted) { status = "Location permission denied \u2014 GPS unavailable"; Logger.e("Gps", "permission denied") }
    }

    fun fetch() {
        status = "Fetching\u2026"
        try {
            LocationServices.getFusedLocationProviderClient(ctx)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                .addOnSuccessListener { loc ->
                    if (loc == null) { status = "No fix \u2014 retry"; Logger.e("Gps", "null location"); return@addOnSuccessListener }
                    fix = loc.latitude to loc.longitude; acc = loc.accuracy
                    status = ""
                    scope.launch {
                        try {
                            @Suppress("DEPRECATION")
                            val g = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                android.location.Geocoder(ctx, Locale.ENGLISH)
                                    .getFromLocation(loc.latitude, loc.longitude, 1)
                            }
                            name = g?.firstOrNull()?.locality ?: g?.firstOrNull()?.subAdminArea ?: "Current Location"
                        } catch (t: Throwable) {
                            Logger.e("Gps", "reverse geocode failed — fallback name", t)
                            name = "Current Location"
                        }
                    }
                }
                .addOnFailureListener { t -> status = "Fetch failed \u2014 retry"; Logger.e("Gps", "getCurrentLocation failed", t) }
        } catch (t: Throwable) { status = "Fetch failed \u2014 retry"; Logger.e("Gps", "fetch threw", t) }
    }

    LaunchedEffect(Unit) {
        permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    LaunchedEffect(granted) { if (granted && fix == null) fetch() }

    Column(Modifier.fillMaxSize()) {
        BackBar("Save current location", f, onDone)
        Column(Modifier.padding(16.dp, 4.dp)) {
            Column(Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .4f), RoundedCornerShape(12.dp))
                .padding(12.dp)) {
                Text(fix?.let { "\uD83D\uDEF0 ${"%.4f".format(it.first)}, ${"%.4f".format(it.second)}" } ?: "\uD83D\uDEF0 \u2014",
                    fontSize = f.data)
                Text(if (fix != null) "Accuracy \u00b1${"%.0f".format(acc)} m" else status,
                    fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = name, onValueChange = { name = it },
                label = { Text("Name", fontSize = f.data) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("Suggested from GPS. If lookup fails, this reads \"Current Location\".",
                fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { if (granted) fetch() }, modifier = Modifier.weight(1f)) {
                    Text("\u21BB Re-fetch", fontSize = f.data) }
                Button(enabled = fix != null && name.isNotBlank(), onClick = {
                    val fx = fix ?: return@Button
                    scope.launch {
                        val ok = Prefs.addSaved(ctx, Place("s_" + UUID.randomUUID().toString().take(8),
                            name.trim(), fx.first, fx.second, java.util.TimeZone.getDefault().id, true,
                            LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM"))))
                        if (ok) onDone() else capMsg = "Limit of ${Prefs.MAX_SAVED} saved locations reached. Delete one first."
                    }
                }, modifier = Modifier.weight(1f)) { Text("Save", fontSize = f.data) }
            }
            Text("Coordinates freeze after saving \u2014 rename or delete only.",
                fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp))
            capMsg?.let { Text(it, fontSize = f.data, color = Palette.red, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}

@Composable
fun SavedManager(saved: List<Place>, s: Settings, f: Fonts, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var undo by remember { mutableStateOf<Place?>(null) }
    var renaming by remember { mutableStateOf<Place?>(null) }
    Column(Modifier.fillMaxSize()) {
        BackBar("Saved locations", f, onBack) {
            Text("${saved.size} of ${Prefs.MAX_SAVED}", fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(14.dp, 4.dp)) {
            if (saved.isEmpty()) Text("Nothing saved yet.", fontSize = f.data,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
            saved.sortedBy { it.name.lowercase() }.forEachIndexed { i, p ->
                Row(Modifier.fillMaxWidth().background(if (i % 2 == 1) zebra() else Color.Transparent)
                    .padding(2.dp, 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontSize = f.data)
                        Text("${"%.4f".format(p.lat)}, ${"%.4f".format(p.lon)} \u00b7 frozen ${p.frozenAt}",
                            fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("\u270E", fontSize = f.data, modifier = Modifier.clickable { renaming = p }.padding(6.dp))
                    Text("\uD83D\uDDD1", fontSize = f.data, color = Palette.red,
                        modifier = Modifier.clickable {
                            scope.launch {
                                Prefs.updateSaved(ctx, saved - p)
                                undo = p
                                if (s.activePlaceId == p.id) {
                                    Prefs.writeSettings(ctx, Prefs.readSettings(ctx).copy(activePlaceId = s.defaultPlaceId))
                                    AlarmScheduler.rearmToday(ctx)
                                    Logger.e("Saved", "active location deleted — fell back to default")
                                }
                            }
                        }.padding(6.dp))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        undo?.let { p ->
            Row(Modifier.fillMaxWidth().padding(14.dp, 8.dp)
                .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(9.dp)).padding(12.dp, 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Deleted \"${p.name}\"", fontSize = f.data, color = MaterialTheme.colorScheme.inverseOnSurface)
                Text("UNDO", fontSize = f.data, fontWeight = FontWeight.Bold, color = Color(0xFFFFD27A),
                    modifier = Modifier.clickable {
                        scope.launch { Prefs.addSaved(ctx, p); undo = null }
                    })
            }
        }
    }
    renaming?.let { p ->
        var newName by remember(p) { mutableStateOf(p.name) }
        AlertDialog(onDismissRequest = { renaming = null },
            title = { Text("Rename") },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        Prefs.updateSaved(ctx, saved.map { if (it.id == p.id) it.copy(name = newName.trim()) else it })
                        renaming = null
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } })
    }
}

@Composable
fun ErrorLogsScreen(f: Fonts, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var logs by remember { mutableStateOf(Logger.readAll()) }
    var askClear by remember { mutableStateOf(false) }   // K61
    Column(Modifier.fillMaxSize()) {
        BackBar("Error logs", f, onBack) {
            Text("${logs.size} entries", fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            OutlinedButton(enabled = logs.isNotEmpty(), onClick = { askClear = true }) {
                Text("Clear", fontSize = f.data, color = if (logs.isNotEmpty()) Palette.red
                    else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(14.dp, 4.dp)) {
            if (logs.isEmpty()) Text("No errors logged.", fontSize = f.data,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
            logs.forEachIndexed { i, l ->
                Text(l, fontSize = f.data, modifier = Modifier.fillMaxWidth()
                    .background(if (i % 2 == 1) zebra() else Color.Transparent).padding(4.dp, 6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        OutlinedButton(onClick = {
            try {
                val i = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                    data = android.net.Uri.parse("mailto:kri.subsc@gmail.com")
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Kalam bug/crash report")
                    putExtra(android.content.Intent.EXTRA_TEXT, logs.take(60).joinToString("\n"))
                }
                ctx.startActivity(i)
            } catch (t: Throwable) { Logger.e("Logs", "mail intent failed", t) }
        }, modifier = Modifier.fillMaxWidth().padding(14.dp)) { Text("Report bug / crash \u2709", fontSize = f.data) }
    }
    if (askClear) AlertDialog(onDismissRequest = { askClear = false },
        title = { Text("Clear all error logs?") },
        text = { Text("This cannot be undone.", fontSize = f.data) },
        confirmButton = {
            TextButton(onClick = {
                if (Logger.clear()) logs = emptyList()
                else Logger.e("Logs", "clear failed \u2014 logs kept")
                logs = Logger.readAll()
                askClear = false
            }) { Text("Clear", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { askClear = false }) { Text("Cancel") } })
}


@Composable
fun HistoryScreen(f: Fonts, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val history by Prefs.historyFlow(ctx).collectAsState(initial = emptyList())
    val settings by Prefs.settingsFlow(ctx).collectAsState(initial = Settings())
    var undo by remember { mutableStateOf<MatchRecord?>(null) }
    var detail by remember { mutableStateOf<Pair<MatchRecord, Prokerala.MatchResult>?>(null) }
    Column(Modifier.fillMaxSize()) {
        BackBar("Match history", f, onBack) {
            Text("${history.size} saved", fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(14.dp, 4.dp)) {
            if (history.isEmpty()) Text("Nothing saved yet.", fontSize = f.data,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
            history.forEachIndexed { i, r ->
                Row(Modifier.fillMaxWidth().background(if (i % 2 == 1) zebra() else Color.Transparent)
                    .clickable {
                        scope.launch {
                            val json = if (r.cacheKey.isNotBlank()) Prefs.readMatchCache(ctx, r.cacheKey) else r.json
                            val parsed = json?.let { Prokerala.parse(it, true) }
                            if (parsed != null) detail = r to parsed
                            else Logger.e("History", "no cached payload for '${r.label}' — legacy record")
                        }
                    }
                    .padding(2.dp, 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${r.label} \u00b7 ${fmtScore(r.score)}/36" +
                            if (r.approx) " \u00b7 approx" else "", fontSize = f.data, modifier = Modifier.weight(1f))
                    Text(r.date, fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(" \uD83D\uDDD1", fontSize = f.data, color = Palette.red,
                        modifier = Modifier.clickable {
                            scope.launch { Prefs.writeHistory(ctx, history - r); undo = r }
                        }.padding(4.dp))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        undo?.let { r ->
            Row(Modifier.fillMaxWidth().padding(14.dp, 8.dp)
                .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(9.dp)).padding(12.dp, 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Deleted \"${r.label}\"", fontSize = f.data, color = MaterialTheme.colorScheme.inverseOnSurface)
                Text("UNDO", fontSize = f.data, fontWeight = FontWeight.Bold, color = Color(0xFFFFD27A),
                    modifier = Modifier.clickable {
                        scope.launch { Prefs.writeHistory(ctx, Prefs.readHistory(ctx) + r); undo = null }
                    })
            }
        }
    }
    detail?.let { (rec, parsed) ->
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())) {
            BackHandler { detail = null }
            BackBar(rec.label, f, onBack = { detail = null }) {
                Text(rec.date, fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.padding(14.dp, 4.dp)) {
                AdvResult(parsed, settings, f, girlNameOverride = rec.label)
                // K46 — contact details ("-" when absent, incl. every legacy row)
                Spacer(Modifier.height(8.dp))
                Column(Modifier.fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))) {
                    Text("Contact details", fontSize = f.sub, fontWeight = FontWeight.SemiBold,
                        color = Palette.blueText,
                        modifier = Modifier.fillMaxWidth()
                            .background(Palette.blueBand, RoundedCornerShape(12.dp, 12.dp, 0.dp, 0.dp))
                            .padding(8.dp, 7.dp))
                    Row(Modifier.fillMaxWidth().padding(8.dp, 6.dp)) {
                        Text("Matrimony ID", Modifier.width(110.dp), fontSize = f.data, fontWeight = FontWeight.SemiBold)
                        Text(rec.matrimonyId.ifBlank { "-" }, fontSize = f.data)
                    }
                    Row(Modifier.fillMaxWidth().background(zebra()).padding(8.dp, 6.dp)) {
                        Text("Contact Number", Modifier.width(110.dp), fontSize = f.data, fontWeight = FontWeight.SemiBold)
                        Text(rec.mobile.ifBlank { "-" }, fontSize = f.data)
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
