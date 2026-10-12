package com.nbaltas.nickos

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The only launcher icon used to be adaptive (API 26+), so Android 7 had none. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class LauncherIconSdk24Test {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Suppress("DEPRECATION")
    @Test
    fun `the launcher icon resolves on android 7`() {
        assertNotNull(ctx.resources.getDrawable(R.mipmap.ic_launcher, null))
    }
}
