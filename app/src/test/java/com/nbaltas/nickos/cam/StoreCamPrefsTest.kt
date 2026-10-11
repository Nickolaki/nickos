package com.nbaltas.nickos.cam

import androidx.test.core.app.ApplicationProvider
import com.nbaltas.nickos.App
import com.nbaltas.nickos.data.Store
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Store.camPrefs round trip and its fallback to defaults on junk in the key. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoreCamPrefsTest {
    private val store: Store get() = ApplicationProvider.getApplicationContext<App>().store

    @Test
    fun `defaults before anything is saved`() {
        assertEquals(CamPrefs(), store.camPrefs.value)
    }

    @Test
    fun `round trips a saved value`() {
        val v = CamPrefs(profile = "high", maxMin = 30, layoutMs = 450, fadeMs = 0)
        store.setCamPrefs(v)
        assertEquals(v, store.camPrefs.value)
        // a fresh Store reading the same prefs must see the same value
        val again = Store(ApplicationProvider.getApplicationContext())
        assertEquals(v, again.camPrefs.value)
    }

    @Test
    fun `unknown profile falls back to default`() {
        store.setCamPrefs(CamPrefs(profile = "normal"))
        val raw = JSONObject().put("profile", "ultra").put("maxMin", 10).put("layoutMs", 220).put("fadeMs", 250)
        writeRaw(raw)
        val again = Store(ApplicationProvider.getApplicationContext())
        assertEquals("normal", again.camPrefs.value.profile)
    }

    @Test
    fun `invalid maxMin falls back to default`() {
        val raw = JSONObject().put("profile", "normal").put("maxMin", 7).put("layoutMs", 220).put("fadeMs", 250)
        writeRaw(raw)
        val again = Store(ApplicationProvider.getApplicationContext())
        assertEquals(10, again.camPrefs.value.maxMin)
    }

    @Test
    fun `non JSON string falls back to all defaults`() {
        writeRawString("not json at all")
        val again = Store(ApplicationProvider.getApplicationContext())
        assertEquals(CamPrefs(), again.camPrefs.value)
    }

    @Test
    fun `resetAll clears camPrefs back to defaults`() {
        store.setCamPrefs(CamPrefs(profile = "low", maxMin = 5, layoutMs = 0, fadeMs = 0))
        store.resetAll()
        assertEquals(CamPrefs(), store.camPrefs.value)
    }

    private fun writeRaw(o: JSONObject) = writeRawString(o.toString())

    private fun writeRawString(s: String) {
        val ctx = ApplicationProvider.getApplicationContext<App>()
        val p = ctx.getSharedPreferences("nickos", android.content.Context.MODE_PRIVATE)
        p.edit().putString("camPrefs", s).apply()
    }
}
