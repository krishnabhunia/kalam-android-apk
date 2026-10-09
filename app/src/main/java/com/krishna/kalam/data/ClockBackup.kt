package com.krishna.kalam.data

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.ZoneId

/** Portable clock-only backup; never includes credentials, match history or birth details. */
data class ClockArchive(val clocks: List<ClockPlace>, val sort: String, val group: String,
                        val stages: String, val direction: String) {
    fun applyTo(settings: Settings) = settings.copy(clockSort = sort, clockGroup = group,
        groupStages = stages, sortDir = direction)
}

object ClockBackup {
    const val MAX_BYTES = 1024 * 1024
    private const val FORMAT = "kalam-clocks"
    private val sorts = setOf("ADDED", "NAME", "TIME", "COUNTRY", "TEMP", "AQI", "HUM")
    private val groups = setOf("NONE", "COUNTRY", "CONTINENT", "COUNTRY_STATE",
        "CONTINENT_COUNTRY", "CONTINENT_COUNTRY_STATE")

    fun snapshot(clocks: List<ClockPlace>, s: Settings) =
        ClockArchive(clocks, s.clockSort, s.clockGroup, s.groupStages, s.sortDir)

    private fun validate(archive: ClockArchive) {
        require(archive.clocks.size <= 10000) { "Too many clocks" }
        require(archive.sort in sorts && archive.group in groups && archive.direction in setOf("0", "1"))
        require(archive.stages.length <= 100000)
        for (clock in archive.clocks) {
            require(clock.name.isNotBlank() && clock.name.length <= 2000)
            require(clock.region.length <= 2000 && clock.alias.length <= 2000)
            require(listOf(clock.name, clock.region, clock.alias).none { '\u001e' in it || '\u001f' in it })
            require(clock.lat.isFinite() && clock.lat in -90.0..90.0)
            require(clock.lon.isFinite() && clock.lon in -180.0..180.0)
            ZoneId.of(clock.tz)
        }
    }

    fun encode(archive: ClockArchive): String {
        validate(archive)
        val clocks = JSONArray()
        archive.clocks.forEach { c -> clocks.put(JSONObject().put("name", c.name)
            .put("region", c.region).put("timezone", c.tz).put("latitude", c.lat)
            .put("longitude", c.lon).put("expanded", c.expanded).put("alias", c.alias)) }
        val result = JSONObject().put("format", FORMAT).put("version", 1).put("clocks", clocks)
            .put("display", JSONObject().put("sort", archive.sort).put("group", archive.group)
                .put("stages", archive.stages).put("direction", archive.direction)).toString(2)
        require(result.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Backup too large" }
        return result
    }

    fun decode(raw: String): ClockArchive {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Backup too large" }
        val tokener = JSONTokener(raw)
        val value = tokener.nextValue()
        require(value is JSONObject && tokener.nextClean() == '\u0000') { "Invalid backup document" }
        val root = value
        require(root.get("format") == FORMAT && root.get("version") == 1) { "Unsupported backup" }
        fun text(obj: JSONObject, key: String): String = obj.get(key).let {
            require(it is String) { "Invalid $key" }; it
        }
        fun number(obj: JSONObject, key: String): Double = obj.get(key).let {
            require(it is Number) { "Invalid $key" }; it.toDouble()
        }
        val rows = root.getJSONArray("clocks")
        require(rows.length() <= 10000)
        val clocks = (0 until rows.length()).map { i ->
            val c = rows.getJSONObject(i)
            val expanded = c.get("expanded"); require(expanded is Boolean)
            ClockPlace(text(c, "name"), text(c, "region"), text(c, "timezone"),
                number(c, "latitude"), number(c, "longitude"), expanded, text(c, "alias"))
        }
        val d = root.getJSONObject("display")
        return ClockArchive(clocks, text(d, "sort"), text(d, "group"), text(d, "stages"),
            text(d, "direction")).also { validate(it) }
    }

    fun read(input: InputStream): ClockArchive {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            require(output.size() + count <= MAX_BYTES) { "Backup too large" }
            output.write(buffer, 0, count)
        }
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return decode(decoder.decode(ByteBuffer.wrap(output.toByteArray())).toString())
    }
}
