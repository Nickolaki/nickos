package com.nbaltas.nickos.node

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForgetGateTest {
    @Test
    fun `a load looked up before the delete is stale`() {
        val g = ForgetGate()
        val at = g.now()
        g.forget("f1")
        assertTrue(g.stale("f1", at))
    }

    @Test
    fun `a load looked up after the delete is not stale`() {
        val g = ForgetGate()
        g.forget("f1")
        assertFalse(g.stale("f1", g.now()))
    }

    @Test
    fun `other movies and never-deleted movies are untouched`() {
        val g = ForgetGate()
        val at = g.now()
        g.forget("f1")
        assertFalse(g.stale("f2", at))
        assertFalse(ForgetGate().stale("f1", 0))
    }

    @Test
    fun `a second delete makes lookups since the first stale again`() {
        val g = ForgetGate()
        g.forget("f1")
        val at = g.now()
        g.forget("f1")
        assertTrue(g.stale("f1", at))
    }
}
