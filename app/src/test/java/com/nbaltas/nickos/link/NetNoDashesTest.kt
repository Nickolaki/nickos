package com.nbaltas.nickos.link

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** No em dash (U+2014) or en dash (U+2013) in the hands-off join files, source or tests. */
class NetNoDashesTest {
    private val main = File("src/main/java/com/nbaltas/nickos")
    private val test = File("src/test/java/com/nbaltas/nickos")

    private val files = listOf(
        File(main, "link/Subnet.kt"), File(main, "link/NetMessages.kt"), File(main, "link/NetWatch.kt"),
        File(main, "node/WifiJoin.kt"), File(main, "node/NodeRuntime.kt"),
        File(main, "master/Sweep.kt"), File(main, "master/OnlinePolicy.kt"), File(main, "master/MasterRuntime.kt"),
        File(main, "data/Store.kt"), File(main, "link/Wire.kt"), File("src/main/AndroidManifest.xml"),
        File(test, "link/SubnetTest.kt"), File(test, "link/NetMessagesTest.kt"), File(test, "link/ChangeGateTest.kt"),
        File(test, "link/NetNoDashesTest.kt"), File(test, "master/SweepTest.kt"), File(test, "master/OnlinePolicyTest.kt"),
        File(test, "master/LinkStateTest.kt"), File(test, "master/StoreHotspotTest.kt"),
        File(test, "node/AdvertiserTest.kt"), File(test, "node/WifiJoinTest.kt"),
    )

    @Test
    fun `no em or en dashes`() {
        val em = 0x2014.toChar()
        val en = 0x2013.toChar()
        for (f in files) {
            assertTrue("missing ${f.path}", f.exists())
            val t = f.readText()
            assertTrue("em dash in ${f.path}", em !in t)
            assertTrue("en dash in ${f.path}", en !in t)
        }
    }
}
