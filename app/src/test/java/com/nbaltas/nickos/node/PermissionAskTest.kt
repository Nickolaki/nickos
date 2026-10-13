package com.nbaltas.nickos.node

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the Node's own "let the parent tablet change brightness?" card shows, and what Allow walks through. */
class PermissionAskTest {
    private fun show(
        owner: Boolean = false, brightnessOk: Boolean = false, screenOffOk: Boolean = false,
        declined: Boolean = false, playing: Boolean = false, masterAsked: Boolean = false,
    ) = PermissionAsk.showCard(owner, brightnessOk, screenOffOk, declined, playing, masterAsked)

    @Test fun showsWhenSomethingIsMissing() {
        assertTrue(show())
        assertTrue(show(brightnessOk = true))
        assertTrue(show(screenOffOk = true))
    }

    @Test fun neverAsksADeviceOwner() {
        assertFalse(show(owner = true))
        assertFalse("not even when the parent asks", show(owner = true, masterAsked = true))
        assertNull(PermissionAsk.nextStep(true, false, false, emptySet()))
    }

    @Test fun silentWhenAlreadyAllowed() {
        assertFalse(show(brightnessOk = true, screenOffOk = true))
        assertFalse(show(brightnessOk = true, screenOffOk = true, masterAsked = true))
    }

    @Test fun notNowHidesItUntilTheParentAsks() {
        assertFalse(show(declined = true))
        assertTrue(show(declined = true, masterAsked = true))
    }

    @Test fun neverCoversAVideoPlayingInKidMode() {
        assertFalse(show(playing = true))
        assertFalse("even when the parent asked: it waits for the next time home shows", show(playing = true, masterAsked = true))
        assertTrue(show(playing = false, masterAsked = true))
    }

    @Test fun walkOpensBrightnessThenScreenOffThenStops() {
        val tried = mutableSetOf<PermStep>()
        assertEquals(PermStep.BRIGHTNESS, PermissionAsk.nextStep(false, false, false, tried)); tried += PermStep.BRIGHTNESS
        assertEquals(PermStep.SCREEN_OFF, PermissionAsk.nextStep(false, false, false, tried)); tried += PermStep.SCREEN_OFF
        assertNull("both were shown once: a declined screen is not shown again", PermissionAsk.nextStep(false, false, false, tried))
    }

    @Test fun walkSkipsWhatIsAlreadyAllowed() {
        assertEquals(PermStep.SCREEN_OFF, PermissionAsk.nextStep(false, true, false, emptySet()))
        assertEquals(PermStep.BRIGHTNESS, PermissionAsk.nextStep(false, false, true, emptySet()))
        assertNull(PermissionAsk.nextStep(false, true, true, emptySet()))
    }

    @Test fun stateRemembersNotNowAndTheMastersRequest() {
        val s = PermissionAskState()
        assertFalse(s.declined); assertFalse(s.masterAsked.value)
        s.decline()
        assertTrue(s.declined)
        s.masterAsks()
        assertTrue(s.masterAsked.value)
        assertTrue("a request overrides Not now", show(declined = s.declined, masterAsked = s.masterAsked.value))
        s.begin()
        assertFalse("Allow clears the request, and the card does not return by itself", s.masterAsked.value)
        assertFalse(show(declined = s.declined, masterAsked = s.masterAsked.value))
    }

    @Test fun cardCopyHasNoLongDashes() {
        for (t in listOf(PermissionAsk.TITLE, PermissionAsk.BODY, PermissionAsk.ALLOW, PermissionAsk.NOT_NOW, ControlAccess.askedLine("Hailey's Tablet"))) {
            assertFalse(t, t.contains('—') || t.contains('–'))
        }
    }

    @Test fun masterLineShowsForTwoMinutesAfterAsking() {
        assertEquals("Asked on Hailey's Tablet. Tap Allow on that tablet.", ControlAccess.askedLine("Hailey's Tablet"))
        assertTrue(ControlAccess.askedActive(1000L, 1000L + 119_000L))
        assertFalse(ControlAccess.askedActive(1000L, 1000L + 121_000L))
        assertFalse(ControlAccess.askedActive(null, 5L))
    }
}
