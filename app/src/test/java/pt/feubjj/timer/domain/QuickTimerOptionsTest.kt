package pt.feubjj.timer.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import pt.feubjj.timer.domain.model.QuickTimerOptions
import pt.feubjj.timer.domain.model.RoundConfig

class QuickTimerOptionsTest {

    @Test
    fun `single repeat runs one plain countdown and ignores rest`() {
        val cfg = QuickTimerOptions(repeat = 1, restSeconds = 90L).toRoundConfig(180L)
        assertEquals(RoundConfig.single(180L), cfg)
    }

    @Test
    fun `repeat above one builds rounds with the chosen rest`() {
        val cfg = QuickTimerOptions(repeat = 4, restSeconds = 45L).toRoundConfig(180L)
        assertEquals(RoundConfig(repeat = 4, workSeconds = 180L, restSeconds = 45L), cfg)
        assertEquals("4 × 3m / 45s", cfg.summaryBadge())
    }

    @Test
    fun `rest can be zero and steps by 5s under 30s and 10s above`() {
        var o = QuickTimerOptions(repeat = 2, restSeconds = 20L)
        o = o.adjustRest(true)
        assertEquals(25L, o.restSeconds)
        o = o.adjustRest(true).adjustRest(true)
        assertEquals(40L, o.restSeconds)
        repeat(20) { o = o.adjustRest(false) }
        assertEquals(0L, o.restSeconds)
        assertEquals(RoundConfig(2, 60L, 0L), o.toRoundConfig(60L))
    }

    @Test
    fun `repeat is clamped between 1 and 99`() {
        var o = QuickTimerOptions()
        o = o.adjustRepeat(false)
        assertEquals(1, o.repeat)
        repeat(120) { o = o.adjustRepeat(true) }
        assertEquals(99, o.repeat)
    }
}
