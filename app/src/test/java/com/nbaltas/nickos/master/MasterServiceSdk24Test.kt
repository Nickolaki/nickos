package com.nbaltas.nickos.master

import androidx.test.core.app.ApplicationProvider
import com.nbaltas.nickos.App
import com.nbaltas.nickos.data.Role
import com.nbaltas.nickos.node.NodeService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Every other test runs at SDK 34, where NotificationChannel exists. On the Galaxy Tab S2 (Android 7, API 24) it does
 * not, and the first MasterService.start crashed there. These run the real service against the API 24 framework.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class MasterServiceSdk24Test {
    private val app: App get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `the master service goes foreground on android 7`() {
        app.store.setRole(Role.MASTER)
        val service = Robolectric.buildService(MasterService::class.java).create().get()
        val shadow = shadowOf(service)
        assertFalse(shadow.isForegroundStopped)
        assertNotNull(shadow.lastForegroundNotification)
        assertEquals(2, shadow.lastForegroundNotificationId)
    }

    @Test
    fun `the node service refuses to run the node on android 7`() {
        app.store.setRole(Role.NODE)
        NodeService.start(app)
        val controller = Robolectric.buildService(NodeService::class.java).create()
        val shadow = shadowOf(controller.get())
        assertTrue(shadow.isStoppedBySelf)
        assertNull(shadow.lastForegroundNotification)
        assertFalse(app.node.listening)
        app.node.start()
        assertFalse(app.node.listening)
        assertNull(shadowOf(app).nextStartedService)
    }
}
