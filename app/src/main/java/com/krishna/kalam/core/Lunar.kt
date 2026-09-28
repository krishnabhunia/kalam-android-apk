package com.krishna.kalam.core

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.*

/** Truncated Meeus lunar position, moonrise/moonset/phase.
 *  Line-for-line port of moon.py (validated vs ephem: 348 events, worst 24 s). */
object Lunar {
    private const val D2R = PI / 180.0

    private data class T6(val d: Int, val m: Int, val mp: Int, val f: Int, val sl: Long, val cr: Long)
    private data class T5(val d: Int, val m: Int, val mp: Int, val f: Int, val sb: Long)

    private val LR = listOf(
        T6(0,0,1,0, 6288774, -20905355), T6(2,0,-1,0, 1274027, -3699111), T6(2,0,0,0, 658314, -2955968),
        T6(0,0,2,0, 213618, -569925), T6(0,1,0,0, -185116, 48888), T6(0,0,0,2, -114332, -3149),
        T6(2,0,-2,0, 58793, 246158), T6(2,-1,-1,0, 57066, -152138), T6(2,0,1,0, 53322, -170733),
        T6(2,-1,0,0, 45758, -204586), T6(0,1,-1,0, -40923, -129620), T6(1,0,0,0, -34720, 108743),
        T6(0,1,1,0, -30383, 104755), T6(2,0,0,-2, 15327, 10321), T6(0,0,1,2, -12528, 0),
        T6(0,0,1,-2, 10980, 79661), T6(4,0,-1,0, 10675, -34782), T6(0,0,3,0, 10034, -23210),
        T6(4,0,-2,0, 8548, -21636), T6(2,1,-1,0, -7888, 24208), T6(2,1,0,0, -6766, 30824),
        T6(1,0,-1,0, -5163, -8379), T6(1,1,0,0, 4987, -16675), T6(2,-1,1,0, 4036, -12831),
        T6(2,0,2,0, 3994, -10445), T6(4,0,0,0, 3861, -11650), T6(2,0,-3,0, 3665, 14403),
        T6(0,1,-2,0, -2689, -7003), T6(2,0,-1,2, -2602, 0), T6(2,-1,-2,0, 2390, 10056)
    )
    private val B = listOf(
        T5(0,0,0,1, 5128122), T5(0,0,1,1, 280602), T5(0,0,1,-1, 277693), T5(2,0,0,-1, 173237),
        T5(2,0,-1,1, 55413), T5(2,0,-1,-1, 46271), T5(2,0,0,1, 32573), T5(0,0,2,1, 17198),
        T5(2,0,1,-1, 9266), T5(0,0,2,-1, 8822), T5(2,-1,0,-1, 8216), T5(2,0,-2,-1, 4324),
        T5(2,0,1,1, 4200), T5(2,1,0,-1, -3359), T5(2,-1,-1,1, 2463), T5(2,-1,0,1, 2211),
        T5(2,-1,-1,-1, 2065), T5(0,1,-1,-1, -1870), T5(4,0,-1,-1, 1828), T5(0,1,0,1, -1794)
    )

    private fun Double.mod360(): Double = ((this % 360.0) + 360.0) % 360.0

    fun jd(utc: ZonedDateTime): Double {
        var y = utc.year.toLong(); var m = utc.monthValue.toLong()
        val day = utc.dayOfMonth + (utc.hour + utc.minute / 60.0 + utc.second / 3600.0) / 24.0
        if (m <= 2L) { y -= 1L; m += 12L }
        val a = Math.floorDiv(y, 100L)
        val b = 2L - a + Math.floorDiv(a, 4L)
        return floor(365.25 * (y + 4716L)) + floor(30.6001 * (m + 1L)) + day + b - 1524.5
    }

    data class Ecl(val lon: Double, val lat: Double, val distKm: Double)

    fun moonEcliptic(jdv: Double): Ecl {
        val T = (jdv - 2451545.0) / 36525.0
        val Lp = (218.3164477 + 481267.88123421*T - 0.0015786*T*T + T*T*T/538841.0 - T*T*T*T/65194000.0).mod360()
        val D  = (297.8501921 + 445267.1114034*T - 0.0018819*T*T + T*T*T/545868.0 - T*T*T*T/113065000.0).mod360()
        val M  = (357.5291092 + 35999.0502909*T - 0.0001536*T*T + T*T*T/24490000.0).mod360()
        val Mp = (134.9633964 + 477198.8675055*T + 0.0087414*T*T + T*T*T/69699.0 - T*T*T*T/14712000.0).mod360()
        val F  = (93.2720950 + 483202.0175233*T - 0.0036539*T*T - T*T*T/3526000.0 + T*T*T*T/863310000.0).mod360()
        val A1 = (119.75 + 131.849*T).mod360()
        val A2 = (53.09 + 479264.290*T).mod360()
        val A3 = (313.45 + 481266.484*T).mod360()
        val E = 1.0 - 0.002516*T - 0.0000074*T*T
        var sl = 0.0; var sr = 0.0
        for (t in LR) {
            val arg = (t.d*D + t.m*M + t.mp*Mp + t.f*F) * D2R
            val e = E.pow(abs(t.m))
            sl += t.sl * e * sin(arg)
            sr += t.cr * e * cos(arg)
        }
        sl += 3958.0*sin(A1*D2R) + 1962.0*sin((Lp-F)*D2R) + 318.0*sin(A2*D2R)
        var sb = 0.0
        for (t in B) {
            val arg = (t.d*D + t.m*M + t.mp*Mp + t.f*F) * D2R
            sb += t.sb * E.pow(abs(t.m)) * sin(arg)
        }
        sb += -2235.0*sin(Lp*D2R) + 382.0*sin(A3*D2R) + 175.0*sin((A1-F)*D2R) +
              175.0*sin((A1+F)*D2R) + 127.0*sin((Lp-Mp)*D2R) - 115.0*sin((Lp+Mp)*D2R)
        return Ecl((Lp + sl/1e6).mod360(), sb/1e6, 385000.56 + sr/1e3)
    }

    private fun obliquity(jdv: Double): Double {
        val T = (jdv - 2451545.0) / 36525.0
        return 23.4392911 - 0.0130042*T - 1.64e-7*T*T
    }

    private fun gmst(jdv: Double): Double {
        val T = (jdv - 2451545.0) / 36525.0
        val g = 280.46061837 + 360.98564736629*(jdv - 2451545.0) + 0.000387933*T*T - T*T*T/38710000.0
        return g.mod360() * D2R
    }

    private fun altitude(jdv: Double, latDeg: Double, lonDeg: Double): Pair<Double, Double> {
        val e = moonEcliptic(jdv)
        val eps = obliquity(jdv) * D2R
        val l = e.lon * D2R; val b = e.lat * D2R
        val ra = atan2(sin(l)*cos(eps) - tan(b)*sin(eps), cos(l))
        val dec = asin(sin(b)*cos(eps) + cos(b)*sin(eps)*sin(l))
        val lst = gmst(jdv) + lonDeg * D2R
        val H = lst - ra
        val phi = latDeg * D2R
        val alt = asin(sin(phi)*sin(dec) + cos(phi)*cos(dec)*cos(H))
        val hp = asin(6378.14 / e.distKm)
        return (alt / D2R) to (hp / D2R)
    }

    private fun h0(hpDeg: Double): Double = 0.7275 * hpDeg - 34.0 / 60.0

    /** K39: is the refracted moon above the horizon at local civil minute-of-day? Null on failure. */
    fun moonUpAt(d: LocalDate, minutesOfDay: Double, lat: Double, lon: Double, zone: ZoneId): Boolean? {
        return try {
            val t0 = ZonedDateTime.of(d.atStartOfDay(), zone)
            val t = t0.plusSeconds((minutesOfDay * 60.0).toLong()).withZoneSameInstant(ZoneOffset.UTC)
            val (a, hp) = altitude(jd(t), lat, lon)
            a - h0(hp) >= 0
        } catch (t: Throwable) {
            Logger.e("Lunar", "moonUpAt failed for $d", t); null
        }
    }

    data class Events(val rise: ZonedDateTime?, val set: ZonedDateTime?)

    fun moonEvents(d: LocalDate, lat: Double, lon: Double, zone: ZoneId): Events {
        return try {
            val t0 = ZonedDateTime.of(d.atStartOfDay(), zone)
            fun altAt(minutes: Double): Double {
                val t = t0.plusSeconds((minutes * 60.0).toLong()).withZoneSameInstant(ZoneOffset.UTC)
                val (a, hp) = altitude(jd(t), lat, lon)
                return a - h0(hp)
            }
            var rise: ZonedDateTime? = null; var set: ZonedDateTime? = null
            val step = 10
            var prev = altAt(0.0)
            var m = step
            while (m <= 24 * 60) {
                val cur = altAt(m.toDouble())
                if ((prev < 0 && cur >= 0) || (prev >= 0 && cur < 0)) {
                    var lo = (m - step).toDouble(); var hi = m.toDouble()
                    repeat(22) {
                        val mid = (lo + hi) / 2.0
                        if ((altAt(lo) < 0) == (altAt(mid) < 0)) lo = mid else hi = mid
                    }
                    val t = t0.plusSeconds(((lo + hi) / 2.0 * 60.0).toLong())
                    if (prev < 0) { if (rise == null) rise = t } else { if (set == null) set = t }
                }
                prev = cur; m += step
            }
            Events(rise, set)
        } catch (t: Throwable) {
            Logger.e("Lunar", "moonEvents failed for $d", t)
            Events(null, null)
        }
    }

    fun sunAppLong(jdv: Double): Double {
        val T = (jdv - 2451545.0) / 36525.0
        val L0 = (280.46646 + 36000.76983*T).mod360()
        val M = 357.52911 + 35999.05029*T
        val C = (1.914602 - 0.004817*T)*sin(M*D2R) + (0.019993 - 0.000101*T)*sin(2*M*D2R) + 0.000289*sin(3*M*D2R)
        return (L0 + C).mod360()
    }

    data class Phase(val illumPct: Double, val waxing: Boolean, val name: String, val glyph: String)

    private val NAMES = listOf("New moon","Waxing crescent","First quarter","Waxing gibbous",
        "Full moon","Waning gibbous","Last quarter","Waning crescent")
    private val GLYPHS = listOf("\uD83C\uDF11","\uD83C\uDF12","\uD83C\uDF13","\uD83C\uDF14",
        "\uD83C\uDF15","\uD83C\uDF16","\uD83C\uDF17","\uD83C\uDF18")

    fun moonPhase(local: ZonedDateTime): Phase {
        return try {
            val jdv = jd(local.withZoneSameInstant(ZoneOffset.UTC))
            val mlon = moonEcliptic(jdv).lon
            val slon = sunAppLong(jdv)
            val elong = (mlon - slon).mod360()
            val illum = (1.0 - cos(elong * D2R)) / 2.0 * 100.0
            val idx = (((elong + 22.5).mod360()) / 45.0).toInt()
            Phase(illum, elong < 180.0, NAMES[idx], GLYPHS[idx])
        } catch (t: Throwable) {
            Logger.e("Lunar", "moonPhase failed", t)
            Phase(0.0, true, "Unavailable", "\uD83C\uDF11")
        }
    }
}
