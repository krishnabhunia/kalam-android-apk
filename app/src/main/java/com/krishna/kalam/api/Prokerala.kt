package com.krishna.kalam.api

import android.content.Context
import com.krishna.kalam.core.Logger
import com.krishna.kalam.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/** Prokerala v2 advanced kundli-matching. Parser v3 (K32): array-driven, brace-matched,
 *  wire-verified against a real payload on 10-Aug-2026. Every absent field degrades, never crashes. */
object Prokerala {
    private var token: String? = null
    private var tokenExpiry: Long = 0

    data class Koota(val name: String, val girl: String, val boy: String,
                     val points: Double, val max: Double, val desc: String)
    enum class DoshaColor { RED, YELLOW, GREEN }
    data class Dosha(val text: String, val color: DoshaColor)
    data class PersonDetail(val rasi: String, val rasiLord: String, val rasiLordVedic: String,
                            val nakshatra: String, val nakshatraLord: String,
                            val nakshatraLordVedic: String, val pada: String)
    data class MatchResult(
        val total: Double, val max: Double, val union: String, val unionColor: DoshaColor,
        val kootas: List<Koota>, val extraNone: List<String>,
        val girl: PersonDetail, val boy: PersonDetail,
        val girlDosha: Dosha, val boyDosha: Dosha,
        val raw: String, val fromCache: Boolean)
    sealed class Outcome {
        data class Ok(val r: MatchResult) : Outcome()
        data class Err(val message: String) : Outcome()
    }

    private suspend fun fetchToken(id: String, secret: String): String? = withContext(Dispatchers.IO) {
        try {
            val c = URL("https://api.prokerala.com/token").openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            val body = "grant_type=client_credentials&client_id=" +
                    URLEncoder.encode(id, "UTF-8") + "&client_secret=" + URLEncoder.encode(secret, "UTF-8")
            c.outputStream.use { it.write(body.toByteArray()) }
            if (c.responseCode != 200) { Logger.e("Prokerala", "token HTTP ${c.responseCode}"); return@withContext null }
            val text = c.inputStream.bufferedReader().readText()
            val tok = Regex("\"access_token\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)
            val exp = Regex("\"expires_in\"\\s*:\\s*(\\d+)").find(text)?.groupValues?.get(1)?.toLong() ?: 3600
            tokenExpiry = System.currentTimeMillis() + (exp - 60) * 1000
            tok
        } catch (t: Throwable) { Logger.e("Prokerala", "token fetch failed", t); null }
    }

    fun cacheKey(bDob: String, bTob: String, bLat: Double, bLon: Double,
                 gDob: String, gTob: String, gLat: Double, gLon: Double): String {
        val s = "$bDob|$bTob|$bLat|$bLon|$gDob|$gTob|$gLat|$gLon|2"
        return MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(20)
    }

    suspend fun match(ctx: Context, clientId: String, clientSecret: String,
                      bDob: String, bTob: String, bLat: Double, bLon: Double,
                      gDob: String, gTob: String, gLat: Double, gLon: Double): Outcome {
        val key = cacheKey(bDob, bTob, bLat, bLon, gDob, gTob, gLat, gLon)
        Prefs.readMatchCache(ctx, key)?.let { cached ->
            parse(cached, true)?.let { return Outcome.Ok(it) }
        }
        if (clientId.isBlank() || clientSecret.isBlank())
            return Outcome.Err("Add Prokerala credentials in settings first.")
        if (token == null || System.currentTimeMillis() > tokenExpiry)
            token = fetchToken(clientId, clientSecret)
                ?: return Outcome.Err("Could not authenticate with Prokerala. Check credentials and network.")
        return withContext(Dispatchers.IO) {
            try {
                fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
                val gdt = enc("${gDob}T${gTob}:00+05:30")
                val bdt = enc("${bDob}T${bTob}:00+05:30")
                val url = "https://api.prokerala.com/v2/astrology/kundli-matching/advanced?ayanamsa=1" +
                        "&girl_coordinates=$gLat,$gLon&girl_dob=$gdt" +
                        "&boy_coordinates=$bLat,$bLon&boy_dob=$bdt"
                val c = URL(url).openConnection() as HttpURLConnection
                c.setRequestProperty("Authorization", "Bearer $token")
                if (c.responseCode == 401) {
                    token = null; Logger.e("Prokerala", "401 — token invalidated")
                    return@withContext Outcome.Err("Authentication rejected (401). Re-check credentials.")
                }
                if (c.responseCode == 404) {
                    Logger.e("Prokerala", "advanced endpoint 404 — plan may not include it")
                    return@withContext Outcome.Err("Advanced matching not available (404). Check plan/endpoint.")
                }
                if (c.responseCode != 200) {
                    val err = c.errorStream?.bufferedReader()?.readText()?.take(200) ?: ""
                    Logger.e("Prokerala", "match HTTP ${c.responseCode}: $err")
                    return@withContext Outcome.Err("Prokerala returned HTTP ${c.responseCode}.")
                }
                val text = c.inputStream.bufferedReader().readText()
                val parsed = parse(text, false)
                    ?: return@withContext Outcome.Err("Response format not recognised.")
                Prefs.cacheMatch(ctx, key, text)
                Outcome.Ok(parsed)
            } catch (t: Throwable) {
                Logger.e("Prokerala", "match call failed", t)
                Outcome.Err("Network error — check connection and retry.")
            }
        }
    }

    // ---- v3 structural helpers (quote-aware) ----

    private fun skipString(s: String, i: Int): Int {   // i at opening quote -> index after closing quote
        var j = i + 1
        while (j < s.length) {
            when (s[j]) { '\\' -> j += 2; '"' -> return j + 1; else -> j++ }
        }
        return s.length
    }

    /** Balanced block starting at s[open]==openCh; null if unbalanced within cap. */
    fun blockAt(s: String, open: Int, openCh: Char, closeCh: Char, cap: Int = 8000): String? {
        if (open < 0 || open >= s.length || s[open] != openCh) return null
        var depth = 0; var i = open
        while (i < s.length && i - open < cap) {
            val c = s[i]
            if (c == '"') { i = skipString(s, i); continue }
            if (c == openCh) depth++
            else if (c == closeCh) { depth--; if (depth == 0) return s.substring(open, i + 1) }
            i++
        }
        return null
    }

    private fun objectAfter(s: String, key: String): String {
        val k = s.indexOf(key); if (k < 0) return ""
        val ob = s.indexOf('{', k + key.length); if (ob < 0) return ""
        // reject if a value terminator appears before the brace (e.g. "dosha_type": null,)
        val between = s.substring(k + key.length, ob)
        if (between.contains(',') || between.contains('}')) return ""
        return blockAt(s, ob, '{', '}') ?: ""
    }

    private fun str(src: String, key: String): String =
        Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"").find(src)?.groupValues?.get(1) ?: "NONE"

    private fun strOrNull(src: String, key: String): String? =
        Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"").find(src)?.groupValues?.get(1)

    private fun num(src: String, key: String): Double? =
        Regex("\"$key\"\\s*:\\s*(-?[0-9.]+)").find(src)?.groupValues?.get(1)?.toDoubleOrNull()

    private fun bool(src: String, key: String): Boolean? =
        Regex("\"$key\"\\s*:\\s*(true|false)").find(src)?.groupValues?.get(1)?.toBoolean()

    private val KOOTA_FALLBACK = listOf("Varna" to 1.0, "Vasya" to 2.0, "Tara" to 3.0, "Yoni" to 4.0,
        "Graha Maitri" to 5.0, "Gana" to 6.0, "Bhakoot" to 7.0, "Nadi" to 8.0)

    fun parse(json: String, fromCache: Boolean): MatchResult? {
        return try {
            val total = Regex("\"total_points\"\\s*:\\s*([0-9.]+)").find(json)?.groupValues?.get(1)?.toDouble()
                ?: return null
            val max = Regex("\"maximum_points\"\\s*:\\s*([0-9.]+)").find(json)?.groupValues?.get(1)?.toDouble() ?: 36.0

            // union: colour from message.type (authoritative), keyword sniff only as fallback
            val msg = objectAfter(json, "\"message\"")
            val mtype = strOrNull(msg, "type")?.lowercase()
            val union = strOrNull(msg, "description")
                ?: strOrNull(json, "description")
                ?: if (total >= 18) "Acceptable match" else "Below threshold"
            val unionColor = when (mtype) {
                "good" -> DoshaColor.GREEN
                "bad" -> DoshaColor.RED
                "average" -> DoshaColor.YELLOW
                else -> {
                    val lc = union.lowercase()
                    when {
                        listOf("very good", "excellent", "good").any { lc.contains(it) } -> DoshaColor.GREEN
                        listOf("poor", "not recommend", "bad", "inauspicious").any { lc.contains(it) } -> DoshaColor.RED
                        else -> DoshaColor.YELLOW
                    }
                }
            }

            fun person(who: String): PersonDetail {
                val info = objectAfter(json, "\"${who}_info\"")
                val rasi = objectAfter(info, "\"rasi\"")
                val rLord = objectAfter(rasi, "\"lord\"")
                val nak = objectAfter(info, "\"nakshatra\"")
                val nLord = objectAfter(nak, "\"lord\"")
                return PersonDetail(
                    str(rasi, "name"), str(rLord, "name"), str(rLord, "vedic_name"),
                    str(nak, "name"), str(nLord, "name"), str(nLord, "vedic_name"),
                    num(nak, "pada")?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() } ?: "NONE")
            }
            val girl = person("girl"); val boy = person("boy")

            fun dosha(who: String): Dosha {
                var d = objectAfter(json, "\"${who}_mangal_dosha_details\"")
                if (d.isEmpty()) d = objectAfter(objectAfter(json, "\"${who}_info\""), "\"mangal_dosha\"")
                val has = bool(d, "has_dosha")
                val exc = bool(d, "has_exception") ?: false
                val type = strOrNull(d, "dosha_type")?.takeIf { it.isNotBlank() && it.lowercase() != "null" }
                val base = strOrNull(d, "description")
                    ?: when (has) { true -> "Is Manglik"; false -> "Is Not Manglik"; null -> "NONE" }
                val color = when {
                    has == false -> DoshaColor.GREEN
                    has == true && exc -> DoshaColor.YELLOW
                    has == true -> DoshaColor.RED
                    else -> DoshaColor.YELLOW
                }
                return Dosha(if (type != null) "$base \u00b7 $type" else base, color)   // K36
            }

            // guna array — brace-matched items, wire names displayed as-is
            val kootas = mutableListOf<Koota>()
            var itemFails = 0
            var arrayFound = false
            val gk = json.indexOf("\"guna\"")
            if (gk >= 0) {
                val lb = json.indexOf('[', gk)
                if (lb >= 0) blockAt(json, lb, '[', ']', 30000)?.let { arr ->
                    arrayFound = true
                    var i = 1; var n = 0
                    while (n < 16) {
                        val ob = arr.indexOf('{', i); if (ob < 0) break
                        val item = blockAt(arr, ob, '{', '}', 4000) ?: break
                        val nm = strOrNull(item, "name") ?: "Koota ${n + 1}"
                        val g = strOrNull(item, "girl_koot")
                            ?: Regex("\"girl[a-z_]*\"\\s*:\\s*\"([^\"]+)\"").find(item)?.groupValues?.get(1) ?: "\u2014"
                        val b = strOrNull(item, "boy_koot")
                            ?: Regex("\"boy[a-z_]*\"\\s*:\\s*\"([^\"]+)\"").find(item)?.groupValues?.get(1) ?: "\u2014"
                        val pts = num(item, "obtained_points") ?: num(item, "points")
                            ?: objectAfter(item, "\"points\"").let { num(it, "obtained") }
                        val mx = num(item, "maximum_points")
                            ?: KOOTA_FALLBACK.getOrNull(n)?.second ?: 0.0
                        val desc = strOrNull(item, "description") ?: ""
                        if (pts != null) kootas.add(Koota(nm, g, b, pts, mx, desc))
                        else { itemFails++; Logger.e("Prokerala", "guna item unparsed: $nm — no points key") }
                        i = ob + item.length; n++
                    }
                }
            }
            val extraNone: List<String>
            if (!arrayFound) {   // legacy fallback — name-anchored windows (Graha Maitri fixed)
                for ((n, mx) in KOOTA_FALLBACK) {
                    val nameM = Regex("\"name\"\\s*:\\s*\"$n\"", RegexOption.IGNORE_CASE).find(json) ?: continue
                    val ob = json.lastIndexOf('{', nameM.range.first)
                    val item = if (ob >= 0) blockAt(json, ob, '{', '}', 4000) ?: "" else ""
                    val pts = num(item, "obtained_points") ?: num(item, "points") ?: continue
                    kootas.add(Koota(n,
                        strOrNull(item, "girl_koot") ?: "\u2014", strOrNull(item, "boy_koot") ?: "\u2014",
                        pts, num(item, "maximum_points") ?: mx, strOrNull(item, "description") ?: ""))
                }
                extraNone = KOOTA_FALLBACK.map { it.first }.filter { f -> kootas.none { it.name.equals(f, true) } }
            } else extraNone = emptyList()

            // fingerprint on any degrade — makes the next miss diagnosable from Error logs
            if (!arrayFound || itemFails > 0 || kootas.size < 8 || girl.rasi == "NONE") {
                val keys = Regex("\"([a-z_]+)\"\\s*:").findAll(json)
                    .map { it.groupValues[1] }.distinct().take(14).joinToString(",")
                Logger.e("Prokerala", "shape: array=$arrayFound guna=${kootas.size} fails=$itemFails " +
                        "info=${json.contains("_info")} doshaDet=${json.contains("_mangal_dosha_details")} keys=[$keys]")
            }

            MatchResult(total, max, union, unionColor, kootas, extraNone,
                girl, boy, dosha("girl"), dosha("boy"), json, fromCache)
        } catch (t: Throwable) { Logger.e("Prokerala", "parse failed", t); null }
    }
}
