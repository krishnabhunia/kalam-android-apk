package com.krishna.kalam.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.floor

/**
 * K67/K68/K69 — Bengali (West Bengal, sankranti-based) calendar + tithi.
 * Sidereal solar longitude = Lunar.sunAppLong − Lahiri ayanamsa (linear approx; ±1–2′,
 * disclosed: a sankranti within minutes of local midnight could shift a month start by
 * one day — golden vectors + device drikpanchang checks guard the dates that matter).
 * WB rule: month k begins the day AFTER the day containing sankranti into sign k.
 * Tithi: index = floor(((moonLon − sunLon) mod 360) / 12); the tithi at SUNRISE governs
 * the day; end instant found by bisection on the elongation boundary.
 */
object Bangla {

    private fun Double.mod360(): Double = ((this % 360.0) + 360.0) % 360.0

    /** Lahiri ayanamsa, linear around J2000 (23.853° + 0.013972°/yr). */
    fun ayanamsa(jdv: Double): Double = 23.853 + 0.013972 * ((jdv - 2451545.0) / 365.2425)

    fun siderealSunLong(jdv: Double): Double = (Lunar.sunAppLong(jdv) - ayanamsa(jdv)).mod360()

    private fun signAtStartOfDay(d: LocalDate, zone: ZoneId): Int {
        val jdv = Lunar.jd(d.atStartOfDay(zone).withZoneSameInstant(ZoneId.of("UTC")))
        return floor(siderealSunLong(jdv) / 30.0).toInt()
    }

    /** True if the sun enters a new sidereal sign during local day d. */
    fun isSankrantiDay(d: LocalDate, zone: ZoneId): Boolean =
        signAtStartOfDay(d, zone) != signAtStartOfDay(d.plusDays(1), zone)

    /** Most recent day S <= from with a sankranti; walk-back bounded. */
    private fun lastSankrantiOnOrBefore(from: LocalDate, zone: ZoneId): LocalDate? {
        var d = from
        repeat(35) { if (isSankrantiDay(d, zone)) return d; d = d.minusDays(1) }
        return null
    }

    // Month order: index = sidereal sign after the sankranti (0 = Mesha = Boishakh).
    val MONTHS_BN = listOf("বৈশাখ","জ্যৈষ্ঠ","আষাঢ়","শ্রাবণ","ভাদ্র","আশ্বিন","কার্তিক","অগ্রহায়ণ","পৌষ","মাঘ","ফাল্গুন","চৈত্র")
    val MONTHS_EN = listOf("Boishakh","Jyoishtho","Asharh","Srabon","Bhadro","Ashshin","Kartik","Ogrohayon","Poush","Magh","Falgun","Choitro")
    val WEEKDAYS_BN = listOf("সোমবার","মঙ্গলবার","বুধবার","বৃহস্পতিবার","শুক্রবার","শনিবার","রবিবার") // Mon..Sun (ISO)

    private val BN_DIGITS = charArrayOf('০','১','২','৩','৪','৫','৬','৭','৮','৯')
    fun bnNum(n: Int): String = n.toString().map { c -> if (c in '0'..'9') BN_DIGITS[c - '0'] else c }.joinToString("")

    data class BDate(val day: Int, val monthIdx: Int, val year: Int) {
        val monthBn get() = MONTHS_BN[monthIdx]
        val monthEn get() = MONTHS_EN[monthIdx]
    }

    /** 1 Boishakh of Bengali year (greg year y): day after the Mesha sankranti (~mid-April). */
    fun boishakh1(gregYear: Int, zone: ZoneId): LocalDate? {
        var d = LocalDate.of(gregYear, 4, 7)
        repeat(16) {
            if (isSankrantiDay(d, zone) && signAtStartOfDay(d.plusDays(1), zone) == 0) return d.plusDays(1)
            d = d.plusDays(1)
        }
        return null
    }

    /** Bengali date for local day d (WB rule). Null only if the scan fails (logged by caller). */
    fun bengaliDate(d: LocalDate, zone: ZoneId): BDate? {
        val s = lastSankrantiOnOrBefore(d.minusDays(1), zone) ?: return null
        val monthStart = s.plusDays(1)
        val monthIdx = signAtStartOfDay(monthStart, zone)
        val day = (monthStart.until(d, java.time.temporal.ChronoUnit.DAYS) + 1).toInt()
        val b1 = boishakh1(d.year, zone) ?: return null
        val year = if (!d.isBefore(b1)) d.year - 593 else d.year - 594
        return BDate(day, monthIdx, year)
    }

    // ---------- Tithi ----------
    val TITHI_BN = listOf("প্রতিপদ","দ্বিতীয়া","তৃতীয়া","চতুর্থী","পঞ্চমী","ষষ্ঠী","সপ্তমী","অষ্টমী","নবমী","দশমী","একাদশী","দ্বাদশী","ত্রয়োদশী","চতুর্দশী")
    val TITHI_EN = listOf("Pratipada","Dwitiya","Tritiya","Chaturthi","Panchami","Shashthi","Saptami","Ashtami","Navami","Dashami","Ekadashi","Dwadashi","Trayodashi","Chaturdashi")

    fun elongation(jdv: Double): Double = (Lunar.moonEcliptic(jdv).lon - Lunar.sunAppLong(jdv)).mod360()
    fun tithiIndex(jdv: Double): Int = floor(elongation(jdv) / 12.0).toInt().coerceIn(0, 29)

    data class TithiInfo(val idx: Int, val nameBn: String, val nameEn: String,
                         val pakshaBn: String, val pakshaEn: String, val endLocal: ZonedDateTime?)

    fun names(idx: Int): Pair<String, String> = when {
        idx == 14 -> "পূর্ণিমা" to "Purnima"
        idx == 29 -> "অমাবস্যা" to "Amavasya"
        idx < 14  -> TITHI_BN[idx] to TITHI_EN[idx]
        else      -> TITHI_BN[idx - 15] to TITHI_EN[idx - 15]
    }

    /** Tithi governing local day d = tithi at (observational) sunrise; end by bisection. */
    fun tithiForDay(d: LocalDate, sunriseLocal: ZonedDateTime?, zone: ZoneId): TithiInfo? {
        return try {
            val rise = sunriseLocal ?: d.atTime(LocalTime.of(6, 0)).atZone(zone)
            val jdRise = Lunar.jd(rise.withZoneSameInstant(ZoneId.of("UTC")))
            val idx = tithiIndex(jdRise)
            val target = ((idx + 1) * 12).toDouble()
            // Bisect for elongation crossing target within 48h (relative motion ~12°/day).
            var lo = jdRise; var hi = jdRise
            var end: Double? = null
            var step = 2.0 / 24.0
            var t = jdRise
            while (t < jdRise + 2.0) {
                t += step
                val e = ((elongation(t) - target).mod360())
                if (e < 180.0) { hi = t; lo = t - step; end = t; break }
            }
            var endJd: Double? = null
            if (end != null) {
                repeat(40) {
                    val mid = (lo + hi) / 2.0
                    val e = ((elongation(mid) - target).mod360())
                    if (e < 180.0) hi = mid else lo = mid
                }
                endJd = (lo + hi) / 2.0
            }
            val (bn, en) = names(idx)
            val pakshaBn = if (idx < 15) "শুক্লপক্ষ" else "কৃষ্ণপক্ষ"
            val pakshaEn = if (idx < 15) "Shukla" else "Krishna"
            val endZ = endJd?.let { jdToLocal(it, zone) }
            // If the tithi runs past next sunrise, drop the upto (Day-tab rule).
            val nextRise = rise.plusDays(1)
            val shownEnd = if (endZ != null && endZ.isBefore(nextRise)) endZ else null
            TithiInfo(idx, bn, en, pakshaBn, pakshaEn, shownEnd)
        } catch (t: Throwable) { null }
    }

    private fun jdToLocal(jdv: Double, zone: ZoneId): ZonedDateTime {
        val epochSec = ((jdv - 2440587.5) * 86400.0).toLong()
        return java.time.Instant.ofEpochSecond(epochSec).atZone(zone)
    }
}
