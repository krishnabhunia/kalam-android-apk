package com.krishna.kalam.core

import java.time.LocalDate
import java.util.Locale

/**
 * Tolerant reading of stored birth details. A blank (fresh install, K84) or legacy value must
 * open the picker at a sensible default instead of crashing, and every time sent to Prokerala
 * must use ASCII digits whatever the phone's language (Bengali/Marathi digits break ISO-8601).
 */
object BirthInput {
    val DEFAULT_DATE: LocalDate = LocalDate.of(1990, 1, 1)
    val DEFAULT_TIME = 12 to 0

    fun dateOr(iso: String, fallback: LocalDate = DEFAULT_DATE): LocalDate =
        try { LocalDate.parse(iso.trim()) } catch (_: Exception) { fallback }

    /** "HH:mm" (any Unicode digits) → hour/minute, or null when blank/out of range. */
    fun parseTime(value: String): Pair<Int, Int>? {
        val parts = value.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].trim().toIntOrNull() ?: return null
        val m = parts[1].trim().toIntOrNull() ?: return null
        return if (h in 0..23 && m in 0..59) h to m else null
    }

    fun timeOr(value: String, fallback: Pair<Int, Int> = DEFAULT_TIME): Pair<Int, Int> = parseTime(value) ?: fallback

    fun hhmm(h: Int, m: Int): String = String.format(Locale.ROOT, "%02d:%02d", h, m)

    /** Canonical ASCII "HH:mm", or null when the stored value is not a time. */
    fun normalizeTime(value: String): String? = parseTime(value)?.let { (h, m) -> hhmm(h, m) }
}
