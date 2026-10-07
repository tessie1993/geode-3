package dev.geode.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisEventsTest {
    @Test
    fun `early event survives four hop batch while latest continuous features win`() {
        val events = AnalysisEvents()
        val first = AudioFeatures.empty().copy(beat = true, beatStrength = 0.8f, transient = 0.9f, kick = 0.7f, drop = true)
        val latest = AudioFeatures.empty().copy(rms = 0.25f, bass = 0.3f)
        events.beginBatch()
        events.add(first)
        repeat(3) { events.add(latest) }

        val projected = events.apply(latest, 0)

        assertTrue(projected.beat)
        assertTrue(projected.drop)
        assertEquals(0.8f, projected.beatStrength, 0f)
        assertEquals(0.9f, projected.transient, 0f)
        assertEquals(0.7f, projected.kick, 0f)
        assertEquals(0.25f, projected.rms, 0f)
        assertEquals(0.3f, projected.bass, 0f)
    }

    @Test
    fun `waiting PCM expires event after wall time without extending hold`() {
        val events = AnalysisEvents()
        events.beginBatch()
        events.add(AudioFeatures.empty().copy(beat = true, transient = 1f))
        val first = events.apply(AudioFeatures.empty(), 0)
        assertTrue(first.beat)
        events.beginBatch()
        val waiting = events.apply(first, 16_000_000)
        assertTrue(waiting.beat)
        events.beginBatch()
        val expired = events.apply(waiting, 48_000_000)
        assertFalse(expired.beat)
        assertEquals(0f, expired.transient, 0f)
    }

    @Test
    fun `boundary clears all held event state immediately`() {
        val events = AnalysisEvents()
        events.add(AudioFeatures.empty().copy(downbeat = true, sectionBoundary = true, arrival = true, hat = 0.8f))
        assertTrue(events.apply(AudioFeatures.empty(), 0).arrival)
        events.reset()
        val projected = events.apply(AudioFeatures.empty(), 1)
        assertFalse(projected.downbeat)
        assertFalse(projected.sectionBoundary)
        assertFalse(projected.arrival)
        assertEquals(0f, projected.hat, 0f)
    }
}
