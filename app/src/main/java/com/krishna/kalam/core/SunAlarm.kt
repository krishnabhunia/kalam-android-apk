package com.krishna.kalam.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** K76 — pure sunrise/sunset alarm rules: day mask, lead, next ring, labels. */
object SunAlarm {
    const val EVERY = "1111111"; const val WEEKDAYS = "1111100"; const val WEEKENDS = "0000011"
    val LEAD_PRESETS = listOf(0, 5, 10, 15, 30, 45, 60)
    const val LEAD_MAX = 180
    private val HM = DateTimeFormatter.ofPattern("HH:mm")
    private val DOW = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    /** 7 chars Mon→Sun of '0'/'1' with at least one '1'. */
    fun validMask(m: String): Boolean = m.length == 7 && m.all { it == '0' || it == '1' } && m.contains('1')
    fun dayOk(mask: String, dow: DayOfWeek): Boolean = validMask(mask) && mask[dow.value - 1] == '1'
    fun toggle(mask: String, dow: DayOfWeek): String {
        val m = (if (mask.length == 7) mask else EVERY).toCharArray()
        m[dow.value - 1] = if (m[dow.value - 1] == '1') '0' else '1'
        return String(m)
    }

    fun ringAt(event: ZonedDateTime, leadMin: Int): ZonedDateTime = event.minusMinutes(leadMin.coerceIn(0, LEAD_MAX).toLong())

    /** Next ring strictly after `now`, scanning today + 7 days; eventFor(date) = that day's sunrise/sunset (null = no event). */
    fun nextRing(now: ZonedDateTime, on: Boolean, leadMin: Int, mask: String,
                 eventFor: (LocalDate) -> ZonedDateTime?): ZonedDateTime? {
        if (!on || !validMask(mask)) return null
        for (i in 0..7) {
            val d = now.toLocalDate().plusDays(i.toLong())
            if (!dayOk(mask, d.dayOfWeek)) continue
            val ev = eventFor(d) ?: continue
            val r = ringAt(ev, leadMin)
            if (r.isAfter(now)) return r
        }
        return null
    }

    fun daysSummary(mask: String): String = when {
        !validMask(mask) -> "no days"
        mask == EVERY -> "Every day"; mask == WEEKDAYS -> "Weekdays"; mask == WEEKENDS -> "Weekends"
        else -> { val on = DOW.filterIndexed { i, _ -> mask[i] == '1' }; if (on.size >= 5) "${on.size} days" else on.joinToString(" ") }
    }

    fun leadSummary(leadMin: Int, event: String): String = if (leadMin <= 0) "at $event" else "\u2212$leadMin min"

    /** Alert title per 1.17.D1 blocker 3. */
    fun title(eventName: String, leadMin: Int, at: ZonedDateTime): String =
        if (leadMin > 0) "$eventName in $leadMin min \u00b7 ${at.format(HM)}" else "$eventName now \u00b7 ${at.format(HM)}"
}
