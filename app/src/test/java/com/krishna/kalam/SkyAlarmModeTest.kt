package com.krishna.kalam

import com.krishna.kalam.alarm.AlarmScheduler
import com.krishna.kalam.alarm.SoundService
import org.junit.Assert.*
import org.junit.Test

/** K78 — sky slots must take the real-alarm path; kalam slots keep the short-alert path. */
class SkyAlarmModeTest {
    @Test fun skyCodesAreTheFourSkyEvents() {
        assertEquals(setOf(6, 7, 8, 9), AlarmScheduler.SKY_CODES)
    }

    @Test fun kalamSlotsAreNotSky() {
        for (code in 0..5) assertFalse("kalam slot $code", code in AlarmScheduler.SKY_CODES)
    }

    @Test fun ringCapIsShortAfterK79() {
        assertEquals(60, SoundService.RING_CAP_SECS)           // K79: cut from 300 s — never rings on in public
        assertTrue(SoundService.RING_CAP_SECS <= 60)
    }
}
