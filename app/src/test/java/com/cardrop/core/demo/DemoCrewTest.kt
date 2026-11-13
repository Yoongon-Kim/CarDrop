package com.cardrop.core.demo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoCrewTest {
    @Test fun withoutAPickLocalPropertiesDecides() {
        assertTrue(DemoCrew.resolve(picked = null, defaultWhenPicked = null, buildDefault = true, hasFirebase = true))
        assertFalse(DemoCrew.resolve(picked = null, defaultWhenPicked = null, buildDefault = false, hasFirebase = true))
    }

    @Test fun thePanelsPickWinsWhileLocalPropertiesIsUnchanged() {
        // local.properties says real crew, the panel switched this device to the bots.
        assertFalse(DemoCrew.resolve(picked = false, defaultWhenPicked = true, buildDefault = true, hasFirebase = true))
        // local.properties says bots, the panel switched this device to the real crew.
        assertTrue(DemoCrew.resolve(picked = true, defaultWhenPicked = false, buildDefault = false, hasFirebase = true))
    }

    @Test fun changingLocalPropertiesEndsThePick() {
        // Switched to the bots and back to the real crew while local.properties said true; then it was set to false.
        assertFalse(DemoCrew.resolve(picked = true, defaultWhenPicked = true, buildDefault = false, hasFirebase = true))
    }

    @Test fun withoutFirebaseSettingsThereAreOnlyTheBots() {
        assertFalse(DemoCrew.resolve(picked = true, defaultWhenPicked = true, buildDefault = true, hasFirebase = false))
        assertFalse(DemoCrew.resolve(picked = null, defaultWhenPicked = null, buildDefault = true, hasFirebase = false))
    }
}
