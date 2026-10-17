package com.nbaltas.nickos.master

import com.nbaltas.nickos.data.Hotspot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CarNetStopTest {
    private val want = Hotspot("DIRECT-NK-NickOS-test", "testpassphrase01", direct = true)
    private val config = CarNet.Config(
        callTimeoutMs = 150, watchdogMs = 60,
        timing = CarNetPolicy.Timing(retryMs = 20, failedRetryMs = 200, maxFailures = 3, settleMs = 80),
    )

    /** The cleanup job is held back until the scope's children were cancelled: it must still remove the group. */
    @Test fun removalSurvivesACancelRightAfterStop() {
        val executor = Executors.newSingleThreadExecutor()
        val scope = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher())
        try {
            val p = FakeP2p()
            val net = CarNet(p, scope, wanted = { want }, manual = { false }, onUp = {}, sdk = 34, config = config)
            net.start()
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (net.state.value !is CarNetState.On) { check(System.nanoTime() < end) { "no group" }; Thread.sleep(2) }
            val held = CountDownLatch(1)
            val busy = CountDownLatch(1)
            executor.execute { busy.countDown(); held.await() }
            assertTrue(busy.await(5, TimeUnit.SECONDS))
            net.stop()
            scope.coroutineContext[Job]?.children?.forEach { it.cancel() }
            held.countDown()
            assertTrue(runBlocking { net.awaitStopped(5_000) })
            assertEquals(1, p.removes.get())
            assertTrue(p.events.contains("close"))
        } finally {
            scope.cancel(); executor.shutdownNow()
        }
    }
}
