package dev.geode.ui.world

import org.junit.Assert.assertEquals
import org.junit.Test

class WorldAudioMailboxTest {
    @Test
    fun `48 ms held event survives a cleared frame before 15 fps drain`() {
        val mailbox = WorldAudioMailbox()
        mailbox.offer(sample(1, 0))
        mailbox.offer(sample(2, 1, beat = true, beatStrength = 0.8f, transient = 0.7f))
        mailbox.offer(sample(3, 17, beat = true, beatStrength = 0.8f, transient = 0.7f))
        mailbox.offer(sample(4, 49))

        assertEquals(0.8f, mailbox.consume(nanos(67)), 0f)
        assertEquals(0f, mailbox.consume(nanos(134)), 0f)
    }

    @Test
    fun `held positive emissions and repeated snapshots fire once`() {
        val mailbox = WorldAudioMailbox()
        mailbox.offer(sample(1, 0))
        val strike = sample(2, 1, beat = true, beatStrength = 0.6f, transient = 0.4f)
        mailbox.offer(strike)
        assertEquals(0.6f, mailbox.consume(nanos(2)), 0f)
        mailbox.offer(strike)
        mailbox.offer(sample(3, 17, beat = true, beatStrength = 0.6f, transient = 0.4f))
        mailbox.offer(sample(4, 33, beat = true, beatStrength = 0.9f, transient = 0.8f))

        assertEquals(0f, mailbox.consume(nanos(67)), 0f)
    }

    @Test
    fun `continuous spectral onset does not mask independently rearmed transients`() {
        val mailbox = WorldAudioMailbox()
        mailbox.offer(sample(1, 0, onset = 0.8f))
        mailbox.offer(sample(2, 16, onset = 0.8f, transient = 0.7f))
        assertEquals(0.7f, mailbox.consume(nanos(17)), 0f)
        mailbox.offer(sample(3, 32, onset = 0.8f))
        mailbox.offer(sample(4, 48, onset = 0.8f, transient = 0.9f))

        assertEquals(0.9f, mailbox.consume(nanos(49)), 0f)
    }

    @Test
    fun `beat and transient independently rearm while the other remains held`() {
        val mailbox = WorldAudioMailbox()
        mailbox.offer(sample(1, 0))
        mailbox.offer(sample(2, 16, beat = true, beatStrength = 0.5f, transient = 0.6f))
        assertEquals(0.6f, mailbox.consume(nanos(17)), 0f)
        mailbox.offer(sample(3, 32, beat = true))
        mailbox.offer(sample(4, 48, beat = true, transient = 0.08f))
        assertEquals(0.08f, mailbox.consume(nanos(49)), 0f)
        mailbox.offer(sample(5, 64, transient = 0.08f))
        mailbox.offer(sample(6, 80, beat = true, beatStrength = 0.7f, transient = 0.08f))

        assertEquals(0.7f, mailbox.consume(nanos(81)), 0f)
    }

    @Test
    fun `expired accents and source gaps clear before a new cold to hot edge`() {
        val mailbox = WorldAudioMailbox()
        mailbox.offer(sample(1, 0))
        mailbox.offer(sample(2, 1, transient = 0.9f))
        assertEquals(0f, mailbox.consume(nanos(401)), 0f)
        mailbox.offer(sample(3, 500))
        mailbox.offer(sample(4, 516, transient = 0.4f))

        assertEquals(0.4f, mailbox.consume(nanos(517)), 0f)
    }

    @Test
    fun `yield reset clears pending and baselines a held replay without accent`() {
        val mailbox = WorldAudioMailbox()
        mailbox.offer(sample(1, 0))
        mailbox.offer(sample(2, 16, beat = true, transient = 0.8f))
        mailbox.clear()
        mailbox.offer(sample(3, 32, beat = true, transient = 0.8f))
        assertEquals(0f, mailbox.consume(nanos(33)), 0f)
        mailbox.offer(sample(4, 48))
        mailbox.offer(sample(5, 64, transient = 0.5f))

        assertEquals(0.5f, mailbox.consume(nanos(65)), 0f)
    }

    @Test
    fun `reduced motion discards pending and resumes from a held baseline`() {
        val mailbox = WorldAudioMailbox()
        mailbox.offer(sample(1, 0))
        mailbox.offer(sample(2, 16, transient = 0.8f))
        mailbox.setEnabled(false)
        mailbox.offer(sample(3, 32, transient = 0.8f))
        assertEquals(0f, mailbox.consume(nanos(33)), 0f)
        mailbox.setEnabled(true)
        mailbox.offer(sample(4, 48, transient = 0.8f))
        assertEquals(0f, mailbox.consume(nanos(49)), 0f)
        mailbox.offer(sample(5, 64))
        mailbox.offer(sample(6, 80, transient = 0.7f))

        assertEquals(0.7f, mailbox.consume(nanos(81)), 0f)
    }

    @Test
    fun `pending maximum is bounded and below-threshold transients stay quiet`() {
        val mailbox = WorldAudioMailbox()
        mailbox.offer(sample(1, 0))
        mailbox.offer(sample(2, 16, transient = 0.059f))
        assertEquals(0f, mailbox.consume(nanos(17)), 0f)
        mailbox.offer(sample(3, 32, transient = 2f))
        mailbox.offer(sample(4, 48))
        mailbox.offer(sample(5, 64, beat = true, beatStrength = Float.NaN))

        assertEquals(1f, mailbox.consume(nanos(67)), 0f)
    }

    @Test
    fun `weaker events do not rejuvenate an expired stronger peak during GL stall`() {
        val mailbox = WorldAudioMailbox()
        mailbox.offer(sample(1, 0))
        mailbox.offer(sample(2, 1, transient = 0.9f))
        mailbox.offer(sample(3, 150))
        mailbox.offer(sample(4, 200, transient = 0.2f))
        mailbox.offer(sample(5, 400))
        mailbox.offer(sample(6, 450, transient = 0.3f))

        assertEquals(0.3f, mailbox.consume(nanos(451)), 0f)
    }

    private fun sample(
        serial: Long,
        timeMs: Long,
        onset: Float = 0f,
        beat: Boolean = false,
        transient: Float = 0f,
        beatStrength: Float = 0f,
    ): WorldAudioSnapshot =
        WorldAudioSnapshot(
            serial = serial,
            receivedNanos = nanos(timeMs),
            onset = onset,
            beat = beat,
            transient = transient,
            beatStrength = beatStrength,
        )

    private fun nanos(milliseconds: Long): Long = milliseconds * 1_000_000L
}
