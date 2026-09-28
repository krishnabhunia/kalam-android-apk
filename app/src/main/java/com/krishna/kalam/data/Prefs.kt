package com.krishna.kalam.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.krishna.kalam.core.Convention
import com.krishna.kalam.core.Kalam
import com.krishna.kalam.core.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

data class Place(val id: String, val name: String, val lat: Double, val lon: Double,
                 val tz: String, val saved: Boolean, val frozenAt: String = "")

object Cities {
    val BUNDLED = listOf(
        Place("c_bengaluru", "Bengaluru", 12.9716, 77.5946, "Asia/Kolkata", false),
        Place("c_chennai", "Chennai", 13.0827, 80.2707, "Asia/Kolkata", false),
        Place("c_delhi", "Delhi", 28.6139, 77.2090, "Asia/Kolkata", false),
        Place("c_hyderabad", "Hyderabad", 17.3850, 78.4867, "Asia/Kolkata", false),
        Place("c_kalyan", "Kalyan", 19.2403, 73.1305, "Asia/Kolkata", false),
        Place("c_kolkata", "Kolkata", 22.5726, 88.3639, "Asia/Kolkata", false),
        Place("c_mumbai", "Mumbai", 19.0760, 72.8777, "Asia/Kolkata", false),
        Place("c_pune", "Pune", 18.5204, 73.8567, "Asia/Kolkata", false)
    )
    fun byId(id: String): Place? = BUNDLED.find { it.id == id }
}

data class SlotSetting(val enabled: Boolean, val offsetMin: Int)

data class Settings(
    val activePlaceId: String = "c_kolkata",
    val defaultPlaceId: String = "c_kolkata",
    val showBoth: Boolean = false,
    val alarmConvention: Convention = Convention.GEOMETRIC,
    val weekConvention: Convention = Convention.GEOMETRIC,
    val visible: Map<Kalam, Boolean> = Kalam.entries.associateWith { true },
    val startSlots: Map<Kalam, SlotSetting> = Kalam.entries.associateWith { SlotSetting(true, 15) },
    val endSlots: Map<Kalam, Boolean> = Kalam.entries.associateWith { true },
    val startStyleSound: Boolean = true,
    val endStyleSound: Boolean = true,
    val ringSeconds: Int = 5,
    val boyDob: String = "",
    val boyTob: String = "",
    val boyPob: String = "",
    val boyLat: Double = Double.NaN,
    val boyLon: Double = Double.NaN,
    val apiClientId: String = "",
    val apiClientSecret: String = "",
    val girlDob: String = "", val girlTob: String = "", val girlTobUnknown: Boolean = false,
    val girlPob: String = "", val girlLat: Double = Double.NaN, val girlLon: Double = Double.NaN,
    val lastKalamTab: Kalam = Kalam.RK,
    // v1.1 (K18, K21)
    val fontDisplay: Int = 20, val fontHeader: Int = 16, val fontSub: Int = 14, val fontData: Int = 13,
    val boyName: String = "Krishna", val girlName: String = "",
    // v1.2 (K37)
    val gunaDescMask: Int = 0, val moreDetailsOpen: Boolean = false,
    // v1.3 (K38)
    val durationHours: Boolean = false,
    // v1.4 (K45, K47)
    val girlMatrimonyId: String = "", val girlMobile: String = "", val lastMatchKey: String = "",
    // v1.5 (K48)
    val girlPobResolved: String = "",
    // v1.6 (K50)
    val boyPobResolved: String = "",
    val clockSort: String = "ADDED",     // K65 — ADDED|NAME|TIME|COUNTRY
    val clockGroup: String = "NONE",     // K66 — NONE|COUNTRY|CONTINENT
    val groupStages: String = "",        // K71 — path\u0002pos pairs joined by \u0001
    val sortDir: String = "0",           // K75 — "0" natural, "1" reversed (re-tap flip)
    val sunriseAlarm: Boolean = false,   // K76 — fields 43–48
    val sunriseLead: Int = 0,
    val sunriseDays: String = "1111111",
    val sunsetAlarm: Boolean = false,
    val sunsetLead: Int = 0,
    val sunsetDays: String = "1111111",
    val moonriseAlarm: Boolean = false,  // K77 — fields 49–54
    val moonriseLead: Int = 0,
    val moonriseDays: String = "1111111",
    val moonsetAlarm: Boolean = false,
    val moonsetLead: Int = 0,
    val moonsetDays: String = "1111111"
)

// K51 — a clock is a frozen SNAPSHOT (deleting a saved place never breaks it)
/** K70 — per-clock weather snapshot (v2 row; hum/wind/precip/aqi optional). */
data class Wx(val temp: Double, val epochMin: Long, val hum: Double? = null,
              val wind: Double? = null, val precip: Double? = null, val aqi: Int? = null)

data class ClockPlace(val name: String, val region: String, val tz: String,
                      val lat: Double, val lon: Double,
                      val expanded: Boolean = false,   // K53 — per-clock, persisted
                      val alias: String = "") {        // K72 — user rename; "" = original
    fun shown(): String = alias.ifBlank { name }
}

data class MatchRecord(val label: String, val score: Double, val approx: Boolean,
                       val date: String, val json: String,
                       val girlDob: String = "", val cacheKey: String = "",
                       val matrimonyId: String = "", val mobile: String = "")

private val Context.store by preferencesDataStore(name = "kalam_prefs")

object Prefs {
    private val K_SETTINGS = stringPreferencesKey("settings_v1")
    private val K_SAVED = stringPreferencesKey("saved_places_v1")
    private val K_HISTORY = stringPreferencesKey("match_history_v1")
    private val K_CLOCKS = stringPreferencesKey("clocks_v1")
    private val K_TEMPS = stringPreferencesKey("clock_temps_v1")
    const val MAX_SAVED = 20

    private const val FS = "\u001F"; private const val RS = "\u001E"

    fun encodeSettings(s: Settings): String = listOf(
        s.activePlaceId, s.defaultPlaceId, s.showBoth, s.alarmConvention.name, s.weekConvention.name,
        Kalam.entries.joinToString(",") { "${it.name}:${s.visible[it]}" },
        Kalam.entries.joinToString(",") { "${it.name}:${s.startSlots[it]!!.enabled}:${s.startSlots[it]!!.offsetMin}" },
        Kalam.entries.joinToString(",") { "${it.name}:${s.endSlots[it]}" },
        s.startStyleSound, s.endStyleSound, s.ringSeconds,
        s.boyDob, s.boyTob, s.boyPob, s.boyLat, s.boyLon,
        s.apiClientId, s.apiClientSecret,
        s.girlDob, s.girlTob, s.girlTobUnknown, s.girlPob, s.girlLat, s.girlLon,
        s.lastKalamTab.name,
        s.fontDisplay, s.fontHeader, s.fontSub, s.fontData, s.boyName, s.girlName,
        s.gunaDescMask, s.moreDetailsOpen, s.durationHours,
        s.girlMatrimonyId, s.girlMobile, s.lastMatchKey, s.girlPobResolved, s.boyPobResolved,
        s.clockSort, s.clockGroup, s.groupStages, s.sortDir,
        s.sunriseAlarm.toString(), s.sunriseLead.toString(), s.sunriseDays,
        s.sunsetAlarm.toString(), s.sunsetLead.toString(), s.sunsetDays,
        s.moonriseAlarm.toString(), s.moonriseLead.toString(), s.moonriseDays,
        s.moonsetAlarm.toString(), s.moonsetLead.toString(), s.moonsetDays
    ).joinToString(FS)

    fun decodeSettings(raw: String?): Settings {
        if (raw.isNullOrEmpty()) return Settings()
        return try {
            val p = raw.split(FS)
            fun kmapB(s: String) = s.split(",").associate { e ->
                val (k, v) = e.split(":"); Kalam.valueOf(k) to v.toBoolean() }
            fun kmapS(s: String) = s.split(",").associate { e ->
                val t = e.split(":"); Kalam.valueOf(t[0]) to SlotSetting(t[1].toBoolean(), t[2].toInt()) }
            // v1.0 wrote 25 fields; v1.1 writes 31 — read positionally with defaults so
            // an in-place upgrade keeps every existing setting.
            Settings(p[0], p[1], p[2].toBoolean(), Convention.valueOf(p[3]), Convention.valueOf(p[4]),
                kmapB(p[5]), kmapS(p[6]), kmapB(p[7]), p[8].toBoolean(), p[9].toBoolean(), p[10].toInt(),
                p[11], p[12], p[13], p[14].toDouble(), p[15].toDouble(), p[16], p[17],
                p[18], p[19], p[20].toBoolean(), p[21], p[22].toDouble(), p[23].toDouble(),
                Kalam.valueOf(p[24]),
                p.getOrElse(25) { "20" }.toInt(), p.getOrElse(26) { "16" }.toInt(),
                p.getOrElse(27) { "14" }.toInt(), p.getOrElse(28) { "13" }.toInt(),
                p.getOrElse(29) { "Krishna" }, p.getOrElse(30) { "" },
                p.getOrElse(31) { "0" }.toInt(), p.getOrElse(32) { "false" }.toBoolean(),
                p.getOrElse(33) { "false" }.toBoolean(),
                p.getOrElse(34) { "" }, p.getOrElse(35) { "" }, p.getOrElse(36) { "" },
                p.getOrElse(37) { "" }, p.getOrElse(38) { "" },
                p.getOrElse(39) { "ADDED" }, p.getOrElse(40) { "NONE" }, p.getOrElse(41) { "" },
                p.getOrElse(42) { "0" },
                p.getOrElse(43) { "false" }.toBoolean(), p.getOrElse(44) { "0" }.toIntOrNull() ?: 0,
                p.getOrElse(45) { "1111111" }.let { if (com.krishna.kalam.core.SunAlarm.validMask(it)) it else "1111111" },
                p.getOrElse(46) { "false" }.toBoolean(), p.getOrElse(47) { "0" }.toIntOrNull() ?: 0,
                p.getOrElse(48) { "1111111" }.let { if (com.krishna.kalam.core.SunAlarm.validMask(it)) it else "1111111" },
                p.getOrElse(49) { "false" }.toBoolean(), p.getOrElse(50) { "0" }.toIntOrNull() ?: 0,
                p.getOrElse(51) { "1111111" }.let { if (com.krishna.kalam.core.SunAlarm.validMask(it)) it else "1111111" },
                p.getOrElse(52) { "false" }.toBoolean(), p.getOrElse(53) { "0" }.toIntOrNull() ?: 0,
                p.getOrElse(54) { "1111111" }.let { if (com.krishna.kalam.core.SunAlarm.validMask(it)) it else "1111111" })
        } catch (t: Throwable) {
            Logger.e("Prefs", "settings decode failed — resetting to defaults", t)
            Settings()
        }
    }

    private fun encodePlaces(l: List<Place>): String = l.joinToString(RS) {
        listOf(it.id, it.name, it.lat, it.lon, it.tz, it.frozenAt).joinToString(FS) }

    private fun decodePlaces(raw: String?): List<Place> {
        if (raw.isNullOrEmpty()) return emptyList()
        return try {
            raw.split(RS).filter { it.isNotEmpty() }.map {
                val p = it.split(FS)
                Place(p[0], p[1], p[2].toDouble(), p[3].toDouble(), p[4], true, p[5])
            }
        } catch (t: Throwable) {
            Logger.e("Prefs", "saved places decode failed — resetting", t); emptyList()
        }
    }

    private fun encodeHistory(l: List<MatchRecord>): String = l.joinToString(RS) {
        listOf(it.label, it.score, it.approx, it.date,
            it.json.replace(FS, " ").replace(RS, " "), it.girlDob, it.cacheKey,
            it.matrimonyId, it.mobile).joinToString(FS) }

    private fun decodeHistory(raw: String?): List<MatchRecord> {
        if (raw.isNullOrEmpty()) return emptyList()
        return try {
            raw.split(RS).filter { it.isNotEmpty() }.map {
                val p = it.split(FS)
                MatchRecord(p[0], p[1].toDouble(), p[2].toBoolean(), p[3], p.getOrElse(4) { "" },
                    p.getOrElse(5) { "" }, p.getOrElse(6) { "" },
                    p.getOrElse(7) { "" }, p.getOrElse(8) { "" })   // 5/7-field legacy rows -> defaults
            }
        } catch (t: Throwable) {
            Logger.e("Prefs", "history decode failed — resetting", t); emptyList()
        }
    }

    fun settingsFlow(ctx: Context): Flow<Settings> =
        ctx.store.data.map { decodeSettings(it[K_SETTINGS]) }

    suspend fun readSettings(ctx: Context): Settings {
        var out = Settings()
        ctx.store.edit { out = decodeSettings(it[K_SETTINGS]) }
        return out
    }

    suspend fun writeSettings(ctx: Context, s: Settings) {
        try { ctx.store.edit { it[K_SETTINGS] = encodeSettings(s) } }
        catch (t: Throwable) { Logger.e("Prefs", "settings write failed", t); throw t }
    }

    fun savedPlacesFlow(ctx: Context): Flow<List<Place>> =
        ctx.store.data.map { decodePlaces(it[K_SAVED]) }

    suspend fun readSaved(ctx: Context): List<Place> {
        var out: List<Place> = emptyList()
        ctx.store.edit { out = decodePlaces(it[K_SAVED]) }
        return out
    }

    suspend fun addSaved(ctx: Context, p: Place): Boolean {
        val cur = readSaved(ctx)
        if (cur.size >= MAX_SAVED) { Logger.e("Prefs", "saved-place cap hit"); return false }
        ctx.store.edit { it[K_SAVED] = encodePlaces(cur + p) }
        return true
    }

    suspend fun updateSaved(ctx: Context, list: List<Place>) {
        ctx.store.edit { it[K_SAVED] = encodePlaces(list) }
    }

fun clocksFlow(ctx: Context) = ctx.store.data.map { p -> decodeClocks(p[K_CLOCKS] ?: "") }

    suspend fun readClocks(ctx: Context): List<ClockPlace> =
        decodeClocks(ctx.store.data.first()[K_CLOCKS] ?: "")

    suspend fun writeClocks(ctx: Context, list: List<ClockPlace>) {
        ctx.store.edit { p -> p[K_CLOCKS] = list.joinToString(RS) {
            listOf(it.name.replace(FS, " ").replace(RS, " "),
                   it.region.replace(FS, " ").replace(RS, " "),
                   it.tz, it.lat.toString(), it.lon.toString(),
                   it.expanded.toString(), it.alias).joinToString(FS) } }
    }

    fun tempsFlow(ctx: Context) = ctx.store.data.map { decodeTemps(it[K_TEMPS] ?: "") }


    suspend fun readTemps(ctx: Context): Map<String, Wx> =
        decodeTemps(ctx.store.data.first()[K_TEMPS] ?: "")

    suspend fun writeTemps(ctx: Context, m: Map<String, Wx>) {
        ctx.store.edit { p -> p[K_TEMPS] = m.entries.joinToString(RS) { (k, w) ->
            listOf(k, w.temp.toString(), w.epochMin.toString(),
                w.hum?.toString() ?: "", w.wind?.toString() ?: "",
                w.precip?.toString() ?: "", w.aqi?.toString() ?: "").joinToString(FS) } }
    }

    /** K54/K70 — row-safe; v1 rows (3 fields) upgrade with null extras, v2 rows carry 7. */
    fun decodeTemps(raw: String): Map<String, Wx> =
        if (raw.isBlank()) emptyMap() else raw.split(RS).mapNotNull { row ->
            try {
                val p = row.split(FS)
                p[0] to Wx(p[1].toDouble(), p[2].toLong(),
                    p.getOrNull(3)?.toDoubleOrNull(), p.getOrNull(4)?.toDoubleOrNull(),
                    p.getOrNull(5)?.toDoubleOrNull(), p.getOrNull(6)?.toIntOrNull())
            } catch (t: Throwable) { null }
        }.toMap()

    /** One bad row never kills the list. */
    fun decodeClocks(raw: String): List<ClockPlace> =
        if (raw.isBlank()) emptyList() else raw.split(RS).mapNotNull { row ->
            try {
                val p = row.split(FS)
                ClockPlace(p[0], p.getOrElse(1) { "" }, p[2], p[3].toDouble(), p[4].toDouble(),
                    p.getOrElse(5) { "false" }.toBoolean(), p.getOrElse(6) { "" })
            } catch (t: Throwable) { null }
        }

        fun historyFlow(ctx: Context): Flow<List<MatchRecord>> =
        ctx.store.data.map { decodeHistory(it[K_HISTORY]) }

    suspend fun writeHistory(ctx: Context, l: List<MatchRecord>) {
        ctx.store.edit { it[K_HISTORY] = encodeHistory(l) }
    }

    suspend fun readHistory(ctx: Context): List<MatchRecord> {
        var out: List<MatchRecord> = emptyList()
        ctx.store.edit { out = decodeHistory(it[K_HISTORY]) }
        return out
    }

    suspend fun cacheMatch(ctx: Context, key: String, json: String) {
        ctx.store.edit { it[stringPreferencesKey("mc_$key")] = json }
    }

    suspend fun readMatchCache(ctx: Context, key: String): String? {
        var out: String? = null
        ctx.store.edit { out = it[stringPreferencesKey("mc_$key")] }
        return out
    }

    suspend fun resolvePlace(ctx: Context, id: String): Place? =
        Cities.byId(id) ?: readSaved(ctx).find { it.id == id }
}
