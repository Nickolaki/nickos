package com.nbaltas.nickos.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPolicyTest {

    @Test
    fun `backoff is 5, 10, 20, 40, 60, 60 seconds`() {
        assertEquals(5_000L, DownloadPolicy.backoff(1))
        assertEquals(10_000L, DownloadPolicy.backoff(2))
        assertEquals(20_000L, DownloadPolicy.backoff(3))
        assertEquals(40_000L, DownloadPolicy.backoff(4))
        assertEquals(60_000L, DownloadPolicy.backoff(5))
        assertEquals(60_000L, DownloadPolicy.backoff(6))
    }

    @Test
    fun `queued moves to running on start`() {
        assertEquals(JobState.RUNNING, DownloadPolicy.onStart(JobState.QUEUED))
    }

    @Test
    fun `running moves to waiting on error`() {
        assertEquals(JobState.WAITING, DownloadPolicy.onError(JobState.RUNNING))
    }

    @Test
    fun `waiting moves back to running when retried`() {
        assertEquals(JobState.RUNNING, DownloadPolicy.onStart(JobState.WAITING))
    }

    @Test
    fun `running moves to verifying when the byte count matches`() {
        assertEquals(JobState.VERIFYING, DownloadPolicy.onBytesComplete(done = 100, size = 100))
        assertEquals(JobState.FAILED, DownloadPolicy.onBytesComplete(done = 90, size = 100))
    }

    @Test
    fun `verifying resolves to done or failed on the fingerprint check`() {
        assertEquals(JobState.DONE, DownloadPolicy.onVerify(fingerprintMatches = true))
        assertEquals(JobState.FAILED, DownloadPolicy.onVerify(fingerprintMatches = false))
    }

    @Test
    fun `cancel works from any non-terminal state`() {
        assertEquals(JobState.CANCELLED, DownloadPolicy.cancel(JobState.QUEUED))
        assertEquals(JobState.CANCELLED, DownloadPolicy.cancel(JobState.RUNNING))
        assertEquals(JobState.CANCELLED, DownloadPolicy.cancel(JobState.WAITING))
        assertEquals(JobState.CANCELLED, DownloadPolicy.cancel(JobState.VERIFYING))
    }

    @Test
    fun `cancel is a no-op on a terminal state`() {
        assertEquals(JobState.DONE, DownloadPolicy.cancel(JobState.DONE))
        assertEquals(JobState.FAILED, DownloadPolicy.cancel(JobState.FAILED))
        assertEquals(JobState.CANCELLED, DownloadPolicy.cancel(JobState.CANCELLED))
    }

    @Test
    fun `source is flagged after 30 tries`() {
        assertFalse(DownloadPolicy.shouldFlagSource(29))
        assertTrue(DownloadPolicy.shouldFlagSource(30))
        assertTrue(DownloadPolicy.shouldFlagSource(31))
    }

    @Test
    fun `a fresh fetch resets the retry counter`() {
        assertEquals(0, DownloadPolicy.onFreshFetch())
    }
}
