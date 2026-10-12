package com.nbaltas.nickos.node

import org.junit.Assert.assertEquals
import org.junit.Test

class KioskWatchTest {
    private var homeVisible = false
    private var fg: String? = "com.some.game"
    private var bounces = 0
    private val logs = mutableListOf<String>()
    private val watch = KioskWatch(
        inputs = { KioskWatch.Inputs(false, false, homeVisible, fg, listOf("org.jellyfin"), "com.nbaltas.nickos") },
        goHome = { bounces++ },
        log = { logs += it },
    )

    @Test fun `bounces a stray app and respects the minimum gap`() {
        watch.tick(0); watch.tick(1000)
        assertEquals(1, bounces)
        watch.tick(1500)
        assertEquals(2, bounces)
    }

    @Test fun `three bounces in ten seconds log once and pause thirty seconds`() {
        watch.tick(0); watch.tick(2000); watch.tick(4000)
        assertEquals(3, bounces)
        assertEquals(listOf("cannot return home (display over other apps off?)"), logs)
        watch.tick(6000); watch.tick(20_000)
        assertEquals(3, bounces)
        watch.tick(34_001)
        assertEquals(4, bounces)
        assertEquals(1, logs.size)
    }

    @Test fun `home visible clears the count so slow bounces never pause`() {
        watch.tick(0); watch.tick(2000)
        homeVisible = true; watch.tick(3000)
        homeVisible = false; watch.tick(4000)
        assertEquals(3, bounces)
        assertEquals(emptyList<String>(), logs)
    }

    @Test fun `does nothing when the foreground app is allowed`() {
        fg = "org.jellyfin"
        watch.tick(0); watch.tick(5000)
        assertEquals(0, bounces)
    }
}
