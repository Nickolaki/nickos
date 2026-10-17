package com.nbaltas.nickos.master

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nbaltas.nickos.App
import com.nbaltas.nickos.data.LinkedNode
import com.nbaltas.nickos.link.Link
import com.nbaltas.nickos.link.NetMessages
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A command lost with a stuck link must not undo what the parent chose: the Master sends its stored choices again on connect. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResendOnConnectTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val app = App.get(ctx)
    private val id = "aaaaaaaaaaaa"

    private fun claim(c: com.nbaltas.nickos.link.Conn) {
        val f = MasterRuntime::class.java.getDeclaredField("links").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (f.get(app.master) as LinkTable<com.nbaltas.nickos.link.Conn>).claim(id, c)
    }

    private fun readOrientation(link: Link): Boolean? {
        val m: JSONObject = link.node.read(deadlineMs = 3000) ?: return null
        return NetMessages.decodeOrientation(m)
    }

    @Test fun `the landscape choice is stored and sent again on every connect`() {
        app.store.upsertNode(LinkedNode(id, "Hailey", 0, "initial", "tok"))
        assertNull(app.store.nodes.value.single().landscape)
        Link().use { first ->
            claim(first.master)
            app.master.setLandscape(id, true)
            assertEquals(true, readOrientation(first))
        }
        // The stored choice survives a restart of the app's store.
        assertEquals(true, com.nbaltas.nickos.data.Store(ctx).nodes.value.single().landscape)
        // A new link (the old one was closed by the stalled write deadline): connect sends the choice again, unasked.
        Link().use { second ->
            claim(second.master)
            app.master.sendIntent(id)
            assertEquals(true, readOrientation(second))
        }
        Link().use { third ->
            claim(third.master)
            app.master.setLandscape(id, false)
            assertEquals(false, readOrientation(third))
        }
        Link().use { fourth ->
            claim(fourth.master)
            app.master.sendIntent(id)
            assertEquals(false, readOrientation(fourth))
        }
    }

    @Test fun `a tablet that never had a landscape choice gets nothing extra`() {
        app.store.upsertNode(LinkedNode("bbbbbbbbbbbb", "Leo", 0, "initial", "tok"))
        assertTrue(app.store.nodes.value.single { it.id == "bbbbbbbbbbbb" }.landscape == null)
        Link().use { l ->
            val f = MasterRuntime::class.java.getDeclaredField("links").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            (f.get(app.master) as LinkTable<com.nbaltas.nickos.link.Conn>).claim("bbbbbbbbbbbb", l.master)
            app.master.sendIntent("bbbbbbbbbbbb")
            Thread.sleep(300)
            l.master.close()
            assertNull("nothing was sent", l.node.read(deadlineMs = 1000))
        }
    }

    @Test fun `the power choice is stored and sent again on every connect`() {
        app.store.upsertNode(LinkedNode(id, "Mia", 0, "initial", "tok"))
        Link().use { first ->
            claim(first.master)
            app.master.setPower(id, true, 10)
            val c = NetMessages.decodePower(first.node.read(deadlineMs = 3000))!!
            assertEquals(true, c.wake); assertEquals(10, c.offMin)
        }
        assertEquals(10, com.nbaltas.nickos.data.Store(ctx).nodes.value.single { it.id == id }.powerOffMin)
        Link().use { second ->
            claim(second.master)
            app.master.sendIntent(id)
            val c = NetMessages.decodePower(second.node.read(deadlineMs = 3000))!!
            assertEquals(true, c.wake); assertEquals(10, c.offMin)
        }
    }

    @Test fun `old stored tablets without the field still load`() {
        val o = LinkedNode("c", "n", 1, "initial", "t").toJson().apply { remove("landscape") }
        assertNull(LinkedNode.from(o).landscape)
    }
}
