package com.krishna.kalam
import com.krishna.kalam.ui.NavRules
import com.krishna.kalam.ui.Screen
import org.junit.Assert.assertEquals
import org.junit.Test

class NavRulesTest {   // K33
    @Test fun matchOriginReturnsHome() = assertEquals(Screen.MAIN, NavRules.historyBack(Screen.MAIN))
    @Test fun settingsOriginReturnsSettings() = assertEquals(Screen.SETTINGS, NavRules.historyBack(Screen.SETTINGS))
    @Test fun unknownOriginDefaultsSettings() = assertEquals(Screen.SETTINGS, NavRules.historyBack(Screen.ERROR_LOGS))   // K58: ABOUT retired; any non-Match origin still defaults to Settings
}
