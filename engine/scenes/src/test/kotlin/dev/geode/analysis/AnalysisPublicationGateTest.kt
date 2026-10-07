package dev.geode.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisPublicationGateTest {
    @Test
    fun `rate changed while analysis runs rejects old features`() {
        val gate = AnalysisPublicationGate()
        var rate = 44100
        val old = gate.snapshot { generation -> generation to rate }
        gate.invalidate { rate = 48000 }
        assertEquals(44100, old.second)
        assertFalse(gate.publish(old.first) { error("obsolete analysis was published") })
        val current = gate.snapshot { generation -> generation to rate }
        assertEquals(48000, current.second)
        assertTrue(gate.publish(current.first) {})
    }

    @Test
    fun `configuration change and restoration still invalidate pending analysis`() {
        val gate = AnalysisPublicationGate()
        var rate = 44100
        val generation = gate.snapshot { it }
        gate.invalidate { rate = 48000 }
        gate.invalidate { rate = 44100 }
        assertEquals(44100, rate)
        assertFalse(gate.isCurrent(generation))
        assertFalse(gate.publish(generation) { error("ABA rate change was ignored") })
    }

    @Test
    fun `reset cannot be overwritten by a previously analyzed frame`() {
        val gate = AnalysisPublicationGate()
        var energy = 1f
        val generation = gate.snapshot { it }
        gate.invalidate { energy = 0f }
        assertFalse(gate.publish(generation) { energy = 1f })
        assertEquals(0f, energy, 0f)
    }
}
