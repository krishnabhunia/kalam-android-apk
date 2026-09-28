package com.krishna.kalam.ui

/** K18 — four-tier font ladder, Model A: a change moves neighbours only when the
 *  >=1pt invariant would break; a change whose cascade would exceed any range is rejected. */
object Ladder {
    data class L(val display: Int, val header: Int, val sub: Int, val data: Int)
    data class Range(val min: Int, val max: Int)
    val RANGES = mapOf(0 to Range(16, 28), 1 to Range(13, 24), 2 to Range(12, 22), 3 to Range(10, 20))
    val DEFAULT = L(20, 16, 14, 13)

    data class Outcome(val value: L, val cascadeNote: String?)

    private fun L.toList() = listOf(display, header, sub, data)
    private fun fromList(v: List<Int>) = L(v[0], v[1], v[2], v[3])
    private val NAMES = listOf("Display", "Header", "Sub-header", "Data")

    /** tier: 0..3. delta: +1/-1. Returns null when the move is impossible (clamp). */
    fun change(cur: L, tier: Int, delta: Int): Outcome? {
        val v = cur.toList().toMutableList()
        val target = v[tier] + delta
        val r = RANGES.getValue(tier)
        if (target < r.min || target > r.max) return null
        v[tier] = target
        val moved = mutableListOf<Pair<Int, Pair<Int, Int>>>()
        if (delta > 0) {
            // push upper tiers up to keep each >= lower+1
            for (t in tier - 1 downTo 0) {
                if (v[t] < v[t + 1] + 1) {
                    val nv = v[t + 1] + 1
                    if (nv > RANGES.getValue(t).max) return null
                    moved.add(t to (v[t] to nv)); v[t] = nv
                }
            }
        } else {
            // push lower tiers down
            for (t in tier + 1..3) {
                if (v[t] > v[t - 1] - 1) {
                    val nv = v[t - 1] - 1
                    if (nv < RANGES.getValue(t).min) return null
                    moved.add(t to (v[t] to nv)); v[t] = nv
                }
            }
        }
        val note = moved.firstOrNull()?.let { (t, p) ->
            "${NAMES[tier]} ${cur.toList()[tier]} \u2192 ${target} pushed ${NAMES[t]} ${p.first} \u2192 ${p.second}"
        }
        return Outcome(fromList(v), note)
    }

    fun canChange(cur: L, tier: Int, delta: Int): Boolean = change(cur, tier, delta) != null
}
