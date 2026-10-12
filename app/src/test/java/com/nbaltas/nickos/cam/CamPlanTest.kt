package com.nbaltas.nickos.cam

import com.nbaltas.nickos.cam.CamPlan.Want
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CamPlanTest {
    private val now = 100_000L
    private val live = NodeCam(NodeCam.LIVE, port = 47472, key = "abc", receivedAt = now)
    private val warming = NodeCam(NodeCam.WARMING, port = 47472, key = "abc", receivedAt = now)
    private fun off(reason: String?, receivedAt: Long = now) = NodeCam(NodeCam.OFF, reason = reason, receivedAt = receivedAt)

    private fun want(
        armed: Boolean = true, online: Boolean = true, supportsCam: Boolean = true, viewing: Boolean = true,
        cam: NodeCam? = null, hasClient: Boolean = false, ended: String? = null, endedAt: Long = 0, retries: Int = 0, mine: Boolean = true,
    ) = CamPlan.want(armed, online, supportsCam, viewing, cam, hasClient, now, ended, endedAt, retries, mine)

    // ---- want ----

    @Test
    fun `leaving the screen stops a camera that is on`() {
        assertEquals(Want.STOP, want(viewing = false, cam = live, hasClient = true))
        assertEquals(Want.STOP, want(viewing = false, cam = warming))
    }

    @Test
    fun `turning the toggle off stops a camera that is on`() {
        assertEquals(Want.STOP, want(armed = false, cam = live, hasClient = true))
    }

    @Test
    fun `not wanted and already off only drops a leftover stream`() {
        assertEquals(Want.DISCONNECT, want(armed = false, cam = off("stopped"), hasClient = true))
        assertEquals(Want.NONE, want(armed = false, cam = off("stopped")))
        assertEquals(Want.NONE, want(viewing = false, cam = null))
    }

    @Test
    fun `another parent's camera is not this Master's to stop`() {
        assertEquals(Want.NONE, want(viewing = false, cam = live, mine = false))
        assertEquals(Want.NONE, want(armed = false, cam = live, mine = false))
    }

    @Test
    fun `wanted, online, capable and off starts the camera`() {
        assertEquals(Want.START, want(cam = null))
        assertEquals(Want.START, want(cam = off(null)))
        assertEquals(Want.START, want(cam = off("stopped")))
        assertEquals(Want.START, want(cam = off("idle")))
        assertEquals(Want.START, want(cam = off("link")))
    }

    @Test
    fun `too hot waits for the cooldown, then starts`() {
        // The tablet said: retry at 50000 on my clock, and my clock reads 20000 now.
        val hot = NodeCam(NodeCam.OFF, reason = NodeCam.TOO_HOT, retryAt = 50_000, now = 20_000, receivedAt = now)
        assertEquals(now + 30_000, hot.retryAtLocal)
        assertEquals(Want.NONE, CamPlan.want(true, true, true, true, hot, false, now + 29_999))
        assertEquals(Want.START, CamPlan.want(true, true, true, true, hot, false, now + 30_000))
        assertTrue(CamPlan.coolingDown(hot, now))
        assertFalse(CamPlan.coolingDown(hot, now + 30_000))
        assertFalse(CamPlan.coolingDown(live, now))
    }

    @Test
    fun `what only the parent can fix is not asked for again`() {
        assertEquals(Want.NONE, want(cam = off(NodeCam.PERMISSION)))
        assertEquals(Want.NONE, want(cam = off(NodeCam.NO_CAMERA)))
        assertEquals(Want.NONE, want(cam = off(NodeCam.LOW_BATTERY)))
    }

    @Test
    fun `a camera that failed to start is tried again every 15 seconds`() {
        for (reason in listOf(NodeCam.BUSY, NodeCam.BACKGROUND, NodeCam.CAMERA)) {
            assertEquals(Want.NONE, want(cam = off(reason, receivedAt = now - 14_999)))
            assertEquals(Want.START, want(cam = off(reason, receivedAt = now - 15_000)))
        }
    }

    @Test
    fun `a camera that is on with a port and key gets a stream`() {
        assertEquals(Want.CONNECT, want(cam = live))
        assertEquals(Want.CONNECT, want(cam = warming))
        assertEquals(Want.NONE, want(cam = live, hasClient = true))
    }

    @Test
    fun `no port or key yet is nothing to connect to`() {
        assertEquals(Want.NONE, want(cam = NodeCam(NodeCam.WARMING)))
        assertEquals(Want.NONE, want(cam = NodeCam(NodeCam.WARMING, port = 47472)))
        assertEquals(Want.NONE, want(cam = NodeCam(NodeCam.WARMING, key = "abc")))
    }

    @Test
    fun `an offline tablet only loses its stream`() {
        assertEquals(Want.DISCONNECT, want(online = false, cam = live, hasClient = true))
        assertEquals(Want.NONE, want(online = false, cam = live))
        assertEquals(Want.NONE, want(online = false, armed = false, cam = live))
    }

    @Test
    fun `an old tablet is never asked for its camera`() {
        assertEquals(Want.NONE, want(supportsCam = false, cam = null))
        assertEquals(Want.NONE, want(supportsCam = false, cam = off(null)))
    }

    @Test
    fun `a refused key asks the tablet again`() {
        assertEquals(Want.START, want(cam = live, ended = CamPlan.ENDED_KEY, endedAt = now))
    }

    @Test
    fun `a dropped stream reconnects after two seconds`() {
        assertEquals(Want.NONE, want(cam = live, ended = CamPlan.ENDED_IO, endedAt = now - 1_999, retries = 1))
        assertEquals(Want.CONNECT, want(cam = live, ended = CamPlan.ENDED_IO, endedAt = now - 2_000, retries = 1))
        assertEquals(Want.CONNECT, want(cam = live, ended = CamPlan.ENDED_IO, endedAt = now - 2_000, retries = 2))
    }

    @Test
    fun `three dropped streams in a row start the camera fresh`() {
        assertEquals(Want.STOP, want(cam = live, ended = CamPlan.ENDED_IO, endedAt = now - 5_000, retries = 3))
        // The stop is answered with off, and off starts it again.
        assertEquals(Want.START, want(cam = off("stopped")))
    }

    // ---- display ----

    private fun display(armed: Boolean = true, online: Boolean = true, supportsCam: Boolean = true, cam: NodeCam? = null, hasFrame: Boolean = false) =
        CamPlan.display(armed, online, supportsCam, cam, hasFrame, now)

    @Test
    fun `offline beats everything`() {
        assertEquals(TileDisplay.OFFLINE, display(online = false, cam = live, hasFrame = true))
        assertEquals(TileDisplay.OFFLINE, display(online = false, supportsCam = false))
    }

    @Test
    fun `an old tablet says update needed whatever else is known`() {
        assertEquals(TileDisplay.UPDATE_NEEDED, display(supportsCam = false))
        assertEquals(TileDisplay.UPDATE_NEEDED, display(supportsCam = false, cam = live, hasFrame = true))
        assertEquals(TileDisplay.UPDATE_NEEDED, display(supportsCam = false, cam = off(NodeCam.PERMISSION)))
    }

    @Test
    fun `each reason for being off has its own tile`() {
        assertEquals(TileDisplay.PERMISSION, display(cam = off(NodeCam.PERMISSION)))
        assertEquals(TileDisplay.NO_CAMERA, display(cam = off(NodeCam.NO_CAMERA)))
        assertEquals(TileDisplay.BUSY, display(cam = off(NodeCam.BUSY)))
        assertEquals(TileDisplay.BACKGROUND, display(cam = off(NodeCam.BACKGROUND)))
        assertEquals(TileDisplay.CAMERA_ERROR, display(cam = off(NodeCam.CAMERA)))
        assertEquals(TileDisplay.LOW_BATTERY, display(cam = off(NodeCam.LOW_BATTERY)))
        assertEquals(TileDisplay.TOO_HOT, display(cam = off(NodeCam.TOO_HOT)))
        assertEquals(TileDisplay.AUTO_OFF, display(armed = false, cam = off(NodeCam.AUTO_OFF)))
    }

    @Test
    fun `starting, restarting and live with no picture yet all say warming`() {
        assertEquals(TileDisplay.WARMING, display(cam = null))
        assertEquals(TileDisplay.WARMING, display(cam = warming))
        assertEquals(TileDisplay.WARMING, display(cam = warming, hasFrame = true))
        assertEquals(TileDisplay.WARMING, display(cam = live, hasFrame = false))
        assertEquals(TileDisplay.WARMING, display(cam = off("stopped")))
        assertEquals(TileDisplay.WARMING, display(cam = off("idle")))
        assertEquals(TileDisplay.WARMING, display(cam = off("link")))
    }

    @Test
    fun `live with a picture shows it, and a warm tablet adds the badge`() {
        assertEquals(TileDisplay.LIVE, display(cam = live, hasFrame = true))
        assertEquals(TileDisplay.HOT, display(cam = live.copy(level = NodeCam.LEVEL_HOT), hasFrame = true))
    }

    @Test
    fun `only live and hot show the picture`() {
        assertEquals(setOf(TileDisplay.LIVE, TileDisplay.HOT), TileDisplay.values().filter { it.showsVideo }.toSet())
    }

    // ---- words ----

    @Test
    fun `every tile that hides the picture says why`() {
        for (d in TileDisplay.values()) {
            val text = CamText.tile(d, "Hailey's Tablet", 10)
            assertEquals("$d", d.showsVideo, text.isEmpty())
        }
        assertEquals("Turned off after 10 min to keep the tablet cool", CamText.tile(TileDisplay.AUTO_OFF, "Hailey's Tablet", 10))
        assertEquals("Can't start the camera right now. Open NickOS on Hailey's Tablet and try again", CamText.tile(TileDisplay.BACKGROUND, "Hailey's Tablet", 10))
        assertEquals("On Hailey's Tablet, hold the avatar, enter the PIN, then tap Allow camera", CamText.hint(TileDisplay.PERMISSION, "Hailey's Tablet"))
        assertNull(CamText.hint(TileDisplay.WARMING, "Hailey's Tablet"))
    }

    @Test
    fun `the countdown reads minutes and seconds`() {
        assertEquals("1:43", CamText.countdown(103_000))
        assertEquals("1:43", CamText.countdown(102_001))
        assertEquals("0:05", CamText.countdown(5_000))
        assertEquals("2:00", CamText.countdown(120_000))
        assertEquals("0:00", CamText.countdown(-50))
        assertEquals("Try again in 1:43", CamText.retryIn(103_000))
    }

    private fun chip(armed: Boolean = true, online: Boolean = true, supportsCam: Boolean = true, cam: NodeCam? = null, hasFrame: Boolean = false) =
        CamText.chip(armed, online, supportsCam, cam, display(armed, online, supportsCam, cam, hasFrame))

    @Test
    fun `chip words`() {
        assertEquals("", chip(cam = live, hasFrame = true))
        assertEquals("Starting", chip(cam = warming))
        assertEquals("Starting", chip(cam = null))
        assertEquals("Warm", chip(cam = live.copy(level = NodeCam.LEVEL_HOT), hasFrame = true))
        assertEquals("Too hot", chip(armed = false, cam = off(NodeCam.TOO_HOT)))
        assertEquals("Offline", chip(online = false, cam = live))
        assertEquals("Update needed", chip(supportsCam = false))
        assertEquals("No camera access", chip(cam = off(NodeCam.PERMISSION)))
        assertEquals("Turned off", chip(armed = false, cam = off(NodeCam.AUTO_OFF)))
        assertEquals("", chip(armed = false, cam = null))
        assertEquals("", chip(armed = false, cam = off("stopped")))
    }

    @Test
    fun `a chip is lit only while the camera is wanted and running or starting`() {
        assertTrue(CamText.chipLit(true, TileDisplay.LIVE))
        assertTrue(CamText.chipLit(true, TileDisplay.WARMING))
        assertTrue(CamText.chipLit(true, TileDisplay.HOT))
        assertFalse(CamText.chipLit(true, TileDisplay.PERMISSION))
        assertFalse(CamText.chipLit(true, TileDisplay.OFFLINE))
        assertFalse(CamText.chipLit(false, TileDisplay.WARMING))
    }

    @Test
    fun `toasts for a camera that turned itself off`() {
        assertEquals("Hailey's camera turned off after 10 min to keep it cool", CamText.toast(NodeCam.AUTO_OFF, "Hailey", 10))
        assertEquals("Hailey's tablet is too hot. Camera off for 2 minutes", CamText.toast(NodeCam.TOO_HOT, "Hailey", 10))
        assertEquals("Hailey's battery is too low for the camera", CamText.toast(NodeCam.LOW_BATTERY, "Hailey", 10))
        assertNull(CamText.toast(NodeCam.PERMISSION, "Hailey", 10))
        assertNull(CamText.toast(null, "Hailey", 10))
        assertEquals("Update Hailey's Tablet from Settings, About", CamText.updateHint("Hailey's Tablet"))
    }

    @Test
    fun `a tablet clock time reads on the Master's clock`() {
        val c = NodeCam(NodeCam.LIVE, maxMs = 600_000, now = 5_000, retryAt = 9_000, receivedAt = 70_000)
        assertEquals(74_000L, c.retryAtLocal)
        assertEquals(10, c.minutes)
        assertNull(live.retryAtLocal)
    }
}
