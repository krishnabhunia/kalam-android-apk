package com.krishna.kalam

import android.content.Intent
import com.krishna.kalam.alarm.AlarmScheduler
import com.krishna.kalam.alarm.BootReceiver
import com.krishna.kalam.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class AlarmRulesTest {
    private val ist = ZoneId.of("Asia/Kolkata")

    @Test fun nextDayRearmLandsJustAfterThePlacesMidnight() {
        val evening = ZonedDateTime.of(LocalDateTime.of(2026, 10, 13, 21, 15), ist)
        assertEquals(ZonedDateTime.of(LocalDateTime.of(2026, 10, 14, 0, 0, 30), ist), AlarmScheduler.nextDayRearmAt(evening))
        val lastSecond = ZonedDateTime.of(LocalDateTime.of(2026, 12, 31, 23, 59, 59), ist)
        assertEquals(ZonedDateTime.of(LocalDateTime.of(2027, 1, 1, 0, 0, 30), ist), AlarmScheduler.nextDayRearmAt(lastSecond))
        // Midnight just after a fired alarm still targets the NEXT day, never "now".
        val justAfter = ZonedDateTime.of(LocalDateTime.of(2026, 10, 14, 0, 0, 40), ist)
        assertEquals(15, AlarmScheduler.nextDayRearmAt(justAfter).dayOfMonth)
    }

    @Test fun nextDayRearmSurvivesAMidnightDaylightSavingGap() {
        // Santiago skipped 00:00–01:00 on 6 Sep 2026: the first instant of that day is 01:00.
        val zone = ZoneId.of("America/Santiago")
        val before = ZonedDateTime.of(LocalDateTime.of(2026, 9, 5, 22, 0), zone)
        val at = AlarmScheduler.nextDayRearmAt(before)
        assertTrue(at.isAfter(before))
        assertEquals(6, at.dayOfMonth)
    }

    @Test fun requestCodesAndNotificationIdsDoNotCollide() {
        val kalam = 0..5
        val used = kalam.toSet() + AlarmScheduler.SKY_CODES + AlarmScheduler.SNOOZE_CODES
        assertFalse(AlarmScheduler.CODE_REARM in used)
        // STOP must clear the snoozed (210–213) cards as well as the originals (206–209).
        assertEquals((206..213).toSet(), AlarmScheduler.ALARM_NOTIFICATION_IDS)
    }

    @Test fun bootReceiverReArmsAfterAnUpdateAndAtMidnight() {
        assertTrue(Intent.ACTION_MY_PACKAGE_REPLACED in BootReceiver.REARM_ACTIONS)
        assertTrue(AlarmScheduler.ACTION_REARM in BootReceiver.REARM_ACTIONS)
        assertTrue(Intent.ACTION_BOOT_COMPLETED in BootReceiver.REARM_ACTIONS)
    }

    private val home = Place("g_home", "Home", 22.42, 87.32, "Asia/Kolkata", true)

    @Test fun deletingTheActiveAndDefaultSavedPlaceLeavesNoDanglingIds() {
        val s = Settings(activePlaceId = home.id, defaultPlaceId = home.id)
        val after = PlaceRules.afterDelete(s, home.id)
        assertEquals("c_kolkata", after.activePlaceId)
        assertEquals("c_kolkata", after.defaultPlaceId)
        // Only the active one deleted → fall back to the (still valid) default.
        val s2 = Settings(activePlaceId = home.id, defaultPlaceId = "c_pune")
        assertEquals("c_pune", PlaceRules.afterDelete(s2, home.id).activePlaceId)
        // Unrelated delete changes nothing.
        assertEquals(s2, PlaceRules.afterDelete(s2, "g_other"))
    }

    @Test fun undoOfADeleteRestoresTheReplacedIdsOnly() {
        val before = Settings(activePlaceId = home.id, defaultPlaceId = home.id)
        val after = PlaceRules.afterDelete(before, home.id)
        assertEquals(before, PlaceRules.afterUndo(after, before, after))
        // The user picked Pune after the delete: UNDO must not override that choice.
        val changed = after.copy(activePlaceId = "c_pune")
        val undone = PlaceRules.afterUndo(changed, before, after)
        assertEquals("c_pune", undone.activePlaceId)
        assertEquals(home.id, undone.defaultPlaceId)
    }

    @Test fun midnightWakeOnlyWhenSomeAlertCanRing() {
        val none = Settings(startSlots = com.krishna.kalam.core.Kalam.entries.associateWith { SlotSetting(false, 15) },
            endSlots = com.krishna.kalam.core.Kalam.entries.associateWith { false })
        assertFalse(AlarmScheduler.anyAlarmEnabled(none))
        assertTrue(AlarmScheduler.anyAlarmEnabled(none.copy(moonsetAlarm = true)))
        assertTrue(AlarmScheduler.anyAlarmEnabled(Settings()))   // defaults: kalam start/end alerts on
        // A hidden kalam never rings (D6), even with its slots on.
        assertFalse(AlarmScheduler.anyAlarmEnabled(Settings(visible = com.krishna.kalam.core.Kalam.entries.associateWith { false })))
    }

    @Test fun alarmsAndScreenResolveTheSamePlaceEvenWhenIdsDangle() {
        assertEquals(home, PlaceRules.resolve(Settings(activePlaceId = home.id), listOf(home)))
        assertEquals(home, PlaceRules.resolve(Settings(activePlaceId = "gone", defaultPlaceId = home.id), listOf(home)))
        assertEquals("c_pune", PlaceRules.resolve(Settings(activePlaceId = "gone", defaultPlaceId = "c_pune"), emptyList()).id)
        assertEquals(Cities.FALLBACK, PlaceRules.resolve(Settings(activePlaceId = "gone", defaultPlaceId = "gone2"), emptyList()))
    }
}
