package com.cardrop.core.demo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartModeTest {
    @Test fun aBuildWithFirebaseAsksAtStart() {
        assertTrue(StartMode.shouldAsk(hasFirebase = true, skip = false))
    }

    @Test fun theDemoPanelsRestartDoesNotAskAgain() {
        assertFalse(StartMode.shouldAsk(hasFirebase = true, skip = true))
    }

    @Test fun aBuildWithoutFirebaseHasOnlyTheBotsSoItDoesNotAsk() {
        assertFalse(StartMode.shouldAsk(hasFirebase = false, skip = false))
    }
}
