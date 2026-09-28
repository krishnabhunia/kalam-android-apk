package com.krishna.kalam.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.krishna.kalam.alarm.AlarmScheduler
import com.krishna.kalam.core.DayResult
import com.krishna.kalam.core.Engine
import com.krishna.kalam.core.SunAlarm
import com.krishna.kalam.data.Place
import com.krishna.kalam.data.Prefs
import com.krishna.kalam.data.Settings
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/** K76/K77 — the four sky events an alarm can follow. */
enum class SkyEvent(val label: String) { SUNRISE("Sunrise"), SUNSET("Sunset"), MOONRISE("Moonrise"), MOONSET("Moonset") }
fun Settings.evOn(e: SkyEvent) = when (e) { SkyEvent.SUNRISE -> sunriseAlarm; SkyEvent.SUNSET -> sunsetAlarm; SkyEvent.MOONRISE -> moonriseAlarm; SkyEvent.MOONSET -> moonsetAlarm }
fun Settings.evLead(e: SkyEvent) = when (e) { SkyEvent.SUNRISE -> sunriseLead; SkyEvent.SUNSET -> sunsetLead; SkyEvent.MOONRISE -> moonriseLead; SkyEvent.MOONSET -> moonsetLead }
fun Settings.evDays(e: SkyEvent) = when (e) { SkyEvent.SUNRISE -> sunriseDays; SkyEvent.SUNSET -> sunsetDays; SkyEvent.MOONRISE -> moonriseDays; SkyEvent.MOONSET -> moonsetDays }
fun Settings.withEv(e: SkyEvent, on: Boolean, lead: Int, days: String) = when (e) {
    SkyEvent.SUNRISE -> copy(sunriseAlarm = on, sunriseLead = lead, sunriseDays = days)
    SkyEvent.SUNSET -> copy(sunsetAlarm = on, sunsetLead = lead, sunsetDays = days)
    SkyEvent.MOONRISE -> copy(moonriseAlarm = on, moonriseLead = lead, moonriseDays = days)
    SkyEvent.MOONSET -> copy(moonsetAlarm = on, moonsetLead = lead, moonsetDays = days)
}
fun com.krishna.kalam.core.SkyDay.eventTime(e: SkyEvent): ZonedDateTime? = when (e) {
    SkyEvent.SUNRISE -> sunrise; SkyEvent.SUNSET -> sunset; SkyEvent.MOONRISE -> moonRise; SkyEvent.MOONSET -> moonSet
}

/** K76 — bell + summary pill under a Rise/Set cell. Disabled on non-today pages (alarms are for today). */
@Composable
fun SunBell(s: Settings, f: Fonts, isToday: Boolean, ev: SkyEvent, onClick: () -> Unit) {
    val on = s.evOn(ev); val lead = s.evLead(ev); val mask = s.evDays(ev)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.then(if (isToday) Modifier.clickable { onClick() } else Modifier).padding(top = 2.dp)) {
        Text(if (on) "\uD83D\uDD14" else "\uD83D\uDD15", fontSize = f.data,
            color = if (isToday) MaterialTheme.colorScheme.onSurface else muted.copy(alpha = .5f))
        if (on) Text(SunAlarm.leadSummary(lead, ev.label.lowercase()) + " \u00b7 " + SunAlarm.daysSummary(mask),
            fontSize = f.data, color = if (isToday) Palette.blueText else muted, textAlign = TextAlign.Center)
        if (!isToday) Text("alarms are for today", fontSize = f.data, color = muted, textAlign = TextAlign.Center)
    }
}

/** K76 — the alarm sheet (AlertDialog): on/off, lead presets + custom, 7-day mask + presets, next-ring preview, Save gated. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SunAlarmDialog(s: Settings, f: Fonts, place: Place, zone: ZoneId, ev: SkyEvent, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val name = ev.label
    var on by remember { mutableStateOf(s.evOn(ev)) }
    var lead by remember { mutableStateOf(s.evLead(ev)) }
    var custom by remember { mutableStateOf(lead !in SunAlarm.LEAD_PRESETS) }
    var customTxt by remember { mutableStateOf(lead.toString()) }
    var mask by remember { mutableStateOf(s.evDays(ev)) }
    val valid = SunAlarm.validMask(mask)
    val HM = remember { DateTimeFormatter.ofPattern("EEE d MMM \u00b7 HH:mm") }
    val eventFor: (LocalDate) -> ZonedDateTime? = remember(place, zone, s.alarmConvention) {
        { d -> (Engine.computeDay(d, place.lat, place.lon, zone, s.alarmConvention) as? DayResult.Ok)?.sky?.eventTime(ev) }
    }
    val next = remember(on, lead, mask) { SunAlarm.nextRing(ZonedDateTime.now(zone), on, lead, mask, eventFor) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("$name alarm"); Switch(checked = on, onCheckedChange = { on = it }) } },
        text = {
            Column {
                Text("Ring before ${name.lowercase()}:", fontSize = f.data)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (p in SunAlarm.LEAD_PRESETS) FilterChip(selected = !custom && lead == p,
                        onClick = { custom = false; lead = p },
                        label = { Text(if (p == 0) "At ${name.lowercase()}" else "$p", fontSize = f.data) })
                    FilterChip(selected = custom, onClick = { custom = true }, label = { Text("Custom\u2026", fontSize = f.data) })
                }
                if (custom) OutlinedTextField(value = customTxt, singleLine = true,
                    onValueChange = { t -> customTxt = t.filter { it.isDigit() }.take(3)
                        lead = (customTxt.toIntOrNull() ?: 0).coerceIn(0, SunAlarm.LEAD_MAX) },
                    label = { Text("minutes (0\u2013${SunAlarm.LEAD_MAX})", fontSize = f.data) }, modifier = Modifier.padding(top = 4.dp))
                Text("On these days:", fontSize = f.data, modifier = Modifier.padding(top = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (d in DayOfWeek.entries) FilterChip(selected = mask[d.value - 1] == '1',
                        onClick = { mask = SunAlarm.toggle(mask, d) },
                        label = { Text(d.name.take(1), fontSize = f.data) }, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    AssistChip(onClick = { mask = SunAlarm.EVERY }, label = { Text("Every day", fontSize = f.data) })
                    AssistChip(onClick = { mask = SunAlarm.WEEKDAYS }, label = { Text("Weekdays", fontSize = f.data) })
                    AssistChip(onClick = { mask = SunAlarm.WEEKENDS }, label = { Text("Weekends", fontSize = f.data) })
                }
                if (!valid) Text("Pick at least one day", fontSize = f.data, color = Palette.red, modifier = Modifier.padding(top = 6.dp))
                Text(when {
                    !on -> "Alarm off"
                    next != null -> "Next ring: ${next.format(HM)} \u00b7 recomputed daily" +
                        (if (ev == SkyEvent.MOONRISE || ev == SkyEvent.MOONSET) " \u00b7 days without a ${name.lowercase()} are skipped" else ", drifts with the sun")
                    else -> "No ring in the next week"
                }, fontSize = f.data, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = {
            scope.launch {
                val ns = s.withEv(ev, on, lead, mask)
                Prefs.writeSettings(ctx, ns)
                AlarmScheduler.rearmToday(ctx)
                onDismiss()
            }
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
