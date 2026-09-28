package com.krishna.kalam

/** K63 — single source for the About line. version_gate.py fails the build if this ever
 *  disagrees with app/build.gradle versionName. */
object AppVersion {
    const val NAME = "1.21"
    const val RELEASE_DATE = "17-Sep-2026"
    /** Contact for API providers' abuse desks (Open-Meteo, Nominatim ask for one). */
    const val USER_AGENT = "Kalam/$NAME (kri.subsc@gmail.com)"
}
