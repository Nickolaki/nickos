package com.nbaltas.nickos.power

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.nbaltas.nickos.data.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Like Android: when [failCommits] is set, commit() updates the memory and still returns false. */
private class FlakyPrefs(private val real: SharedPreferences) : SharedPreferences by real {
    var failCommits = false
    override fun edit(): SharedPreferences.Editor {
        val e = real.edit()
        return object : SharedPreferences.Editor by e {
            override fun putInt(k: String, v: Int) = apply { e.putInt(k, v) }
            override fun remove(k: String) = apply { e.remove(k) }
            override fun clear() = apply { e.clear() }
            override fun commit(): Boolean { val ok = e.commit(); return ok && !failCommits }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DurableBackupTest {
    private fun store(prefs: FlakyPrefs): Store {
        val app = ApplicationProvider.getApplicationContext<Context>()
        return Store(object : ContextWrapper(app) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = prefs
        })
    }

    private class Tablet(var timeout: Int = 60_000, val store: Store) : ScreenTimeoutPort {
        var writes = 0
        override fun canChange() = true
        override fun read() = timeout
        override fun write(ms: Int): Boolean { writes++; timeout = ms; return true }
        override fun saved() = store.savedScreenTimeout()
        override fun save(ms: Int?) = store.setSavedScreenTimeout(ms)
    }

    @Test fun `a failed backup commit is not durable and the timeout is never written`() {
        val prefs = FlakyPrefs(ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("flaky1", Context.MODE_PRIVATE))
        val s = store(prefs)
        val tablet = Tablet(store = s)
        val t = PowerScreenTimer(tablet)
        prefs.failCommits = true
        t.apply(false, 5, charging = false)
        t.apply(false, 5, charging = false)
        assertNull(s.savedScreenTimeout()); assertEquals(0, tablet.writes); assertEquals(60_000, tablet.timeout)
        prefs.failCommits = false
        t.apply(false, 5, charging = false)
        assertEquals(60_000, s.savedScreenTimeout()); assertEquals(300_000, tablet.timeout)
    }

    @Test fun `a failed removal keeps the copy present`() {
        val prefs = FlakyPrefs(ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("flaky2", Context.MODE_PRIVATE))
        val s = store(prefs)
        assertTrue(s.setSavedScreenTimeout(60_000))
        prefs.failCommits = true
        assertFalse(s.setSavedScreenTimeout(null))
        assertEquals(60_000, s.savedScreenTimeout())
        prefs.failCommits = false
        assertTrue(s.setSavedScreenTimeout(null))
        assertNull(s.savedScreenTimeout())
    }
}
