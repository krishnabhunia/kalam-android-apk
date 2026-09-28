package com.krishna.kalam.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.*

/** NOAA solar position. Line-for-line port of solar.py — the ICS pipeline reference.
 *  Python-semantics helpers only: no bare % or integer / in this file. */
object Solar {
    const val ZENITH_GEOMETRIC = 90.0
    const val ZENITH_OBSERVATIONAL = 90.833

    private fun Double.mod360(): Double = ((this % 360.0) + 360.0) % 360.0
    private fun floorDivL(a: Long, b: Long): Long = Math.floorDiv(a, b)

    sealed class SunResult {
        data class Times(val sunrise: ZonedDateTime, val sunset: ZonedDateTime) : SunResult()
        data class NoEvent(val reason: String) : SunResult()
    }

    private fun julianDay(d: LocalDate): Double {
        var y = d.year.toLong(); var m = d.monthValue.toLong()
        if (m <= 2L) { y -= 1L; m += 12L }
        val a = floorDivL(y, 100L)
        val b = 2L - a + floorDivL(a, 4L)
        return floor(365.25 * (y + 4716L)) + floor(30.6001 * (m + 1L)) + d.dayOfMonth + b - 1524.5
    }

    private fun solarTerms(jc: Double): Pair<Double, Double> {
        val geomMeanLong = (280.46646 + jc * (36000.76983 + jc * 0.0003032)).mod360()
        val geomMeanAnom = 357.52911 + jc * (35999.05029 - 0.0001537 * jc)
        val eccent = 0.016708634 - jc * (0.000042037 + 0.0000001267 * jc)
        val mRad = Math.toRadians(geomMeanAnom)
        val sunEqCtr = sin(mRad) * (1.914602 - jc * (0.004817 + 0.000014 * jc)) +
                sin(2 * mRad) * (0.019993 - 0.000101 * jc) + sin(3 * mRad) * 0.000289
        val trueLong = geomMeanLong + sunEqCtr
        val omega = 125.04 - 1934.136 * jc
        val appLong = trueLong - 0.00569 - 0.00478 * sin(Math.toRadians(omega))
        val meanObliq = 23.0 + (26.0 + ((21.448 - jc * (46.815 + jc * (0.00059 - jc * 0.001813)))) / 60.0) / 60.0
        val obliqCorr = meanObliq + 0.00256 * cos(Math.toRadians(omega))
        val declination = Math.toDegrees(asin(sin(Math.toRadians(obliqCorr)) * sin(Math.toRadians(appLong))))
        val varY = tan(Math.toRadians(obliqCorr / 2.0)).pow(2)
        val eqTime = 4.0 * Math.toDegrees(
            varY * sin(2 * Math.toRadians(geomMeanLong)) -
            2 * eccent * sin(mRad) +
            4 * eccent * varY * sin(mRad) * cos(2 * Math.toRadians(geomMeanLong)) -
            0.5 * varY * varY * sin(4 * Math.toRadians(geomMeanLong)) -
            1.25 * eccent * eccent * sin(2 * mRad))
        return declination to eqTime
    }

    fun sunTimes(d: LocalDate, latitude: Double, longitude: Double, zoneId: ZoneId,
                 zenith: Double = ZENITH_GEOMETRIC): SunResult {
        return try {
            val offsetHours = ZonedDateTime.of(d, java.time.LocalTime.NOON, zoneId)
                .offset.totalSeconds / 3600.0
            var noonFrac = 0.5
            var decl = 0.0; var eqTime = 0.0
            repeat(2) {
                val jd = julianDay(d) + (noonFrac - offsetHours / 24.0)
                val jc = (jd - 2451545.0) / 36525.0
                val (de, eq) = solarTerms(jc); decl = de; eqTime = eq
                val noonMin = 720.0 - 4.0 * longitude - eqTime + offsetHours * 60.0
                noonFrac = noonMin / 1440.0
            }
            val latR = Math.toRadians(latitude); val declR = Math.toRadians(decl)
            val cosHa = (cos(Math.toRadians(zenith)) - sin(latR) * sin(declR)) / (cos(latR) * cos(declR))
            if (cosHa > 1.0) return SunResult.NoEvent("Sun never rises on $d at latitude $latitude")
            if (cosHa < -1.0) return SunResult.NoEvent("Sun never sets on $d at latitude $latitude")
            val haMin = 4.0 * Math.toDegrees(acos(cosHa))
            val noonMin = 720.0 - 4.0 * longitude - eqTime + offsetHours * 60.0
            val midnight = ZonedDateTime.of(LocalDateTime.of(d, java.time.LocalTime.MIDNIGHT), zoneId)
            SunResult.Times(
                midnight.plusSeconds(((noonMin - haMin) * 60.0).toLong()),
                midnight.plusSeconds(((noonMin + haMin) * 60.0).toLong())
            )
        } catch (t: Throwable) {
            Logger.e("Solar", "sunTimes failed for $d", t)
            SunResult.NoEvent("calculation failed")
        }
    }
}
