package com.krishna.kalam.core

import android.content.Context
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Logger.e writes logcat + a capped ring file surfaced in Error Logs / bug email. */
object Logger {
    @Volatile private var logFile: File? = null
    private const val MAX_BYTES = 64 * 1024

    fun init(ctx: Context) { logFile = File(ctx.filesDir, "error_log.txt") }

    @Synchronized
    fun e(tag: String, msg: String, t: Throwable? = null) {
        try { android.util.Log.e(tag, msg, t) } catch (_: Throwable) {}
        try {
            val f = logFile ?: return
            val line = "${ZonedDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM HH:mm"))} | $tag | $msg" +
                    (t?.let { " | ${it.javaClass.simpleName}: ${it.message}" } ?: "") + "\n"
            if (f.exists() && f.length() > MAX_BYTES) {
                val keep = f.readLines().takeLast(120).joinToString("\n")
                f.writeText(keep + "\n")
            }
            f.appendText(line)
        } catch (_: Throwable) { /* logging must never crash the app */ }
    }

    fun readAll(): List<String> = try {
        logFile?.takeIf { it.exists() }?.readLines()?.reversed() ?: emptyList()
    } catch (_: Throwable) { emptyList() }

    /** K61 — returns true on success; a failed wipe keeps the logs (caller logs it). */
    @Synchronized
    fun clear(): Boolean = try { logFile?.writeText(""); true } catch (t: Throwable) { false }
}

enum class Kalam(val code: String, val full: String) {
    RK("R.K", "Rahu Kalam"), YG("Y.G", "Yamagandam"), GK("G.K", "Gulika Kalam")
}

enum class Convention { GEOMETRIC, OBSERVATIONAL }

data class KalamSpan(val kalam: Kalam, val segment: Int,
                     val start: ZonedDateTime, val end: ZonedDateTime)

data class SkyDay(val sunrise: ZonedDateTime, val sunset: ZonedDateTime,
                  val moonRise: ZonedDateTime?, val moonSet: ZonedDateTime?,
                  val phase: Lunar.Phase, val moonUpMin: Long?)

sealed class DayResult {
    data class Ok(val spans: List<KalamSpan>, val sky: SkyDay) : DayResult()
    data class Unavailable(val reason: String) : DayResult()
}

/** Weekday -> eighth of daylight. Index 0 = Monday (ISO). */
object Engine {
    private val SEGMENT = mapOf(
        Kalam.RK to intArrayOf(2, 7, 5, 6, 4, 3, 8),
        Kalam.YG to intArrayOf(4, 3, 2, 1, 7, 6, 5),
        Kalam.GK to intArrayOf(6, 5, 4, 3, 2, 1, 7)
    )

    /** K39 — minutes the moon is above the horizon within the civil day.
     *  rise<set: set-rise · set<rise: (set-00:00)+(24:00-rise) · single event: edge to midnight ·
     *  neither: 1440 or 0 by upAtNoon; null when unknown. Pure & total. */
    fun moonPresence(riseMin: Long?, setMin: Long?, upAtNoon: Boolean?): Long? = when {
        riseMin != null && setMin != null ->
            if (setMin >= riseMin) setMin - riseMin else setMin + (1440 - riseMin)
        riseMin != null -> 1440 - riseMin
        setMin != null -> setMin
        upAtNoon == true -> 1440
        upAtNoon == false -> 0
        else -> null
    }

    fun segmentFor(k: Kalam, d: LocalDate): Int = SEGMENT.getValue(k)[d.dayOfWeek.value - 1]

    fun computeDay(d: LocalDate, lat: Double, lon: Double, zone: ZoneId,
                   convention: Convention): DayResult {
        val zenith = if (convention == Convention.GEOMETRIC) Solar.ZENITH_GEOMETRIC else Solar.ZENITH_OBSERVATIONAL
        val sun = Solar.sunTimes(d, lat, lon, zone, zenith)
        if (sun is Solar.SunResult.NoEvent) return DayResult.Unavailable(sun.reason)
        val (sr, ss) = (sun as Solar.SunResult.Times).let { it.sunrise to it.sunset }
        val eighthSec = java.time.Duration.between(sr, ss).seconds / 8.0
        val boundaries = (0..8).map { i -> sr.plusSeconds((eighthSec * i).toLong()) }
        val spans = Kalam.entries.map { k ->
            val idx = segmentFor(k, d)
            KalamSpan(k, idx, boundaries[idx - 1], boundaries[idx])
        }
        val moon = Lunar.moonEvents(d, lat, lon, zone)
        val phase = Lunar.moonPhase(ZonedDateTime.of(d.atTime(12, 0), zone))
        val toMin = { t: ZonedDateTime? -> t?.let { it.hour * 60L + it.minute } }
        val upNoon = if (moon.rise == null && moon.set == null)
            Lunar.moonUpAt(d, 720.0, lat, lon, zone) else null
        val moonUp = moonPresence(toMin(moon.rise), toMin(moon.set), upNoon)
        return DayResult.Ok(spans, SkyDay(sr, ss, moon.rise, moon.set, phase, moonUp))
    }
}
