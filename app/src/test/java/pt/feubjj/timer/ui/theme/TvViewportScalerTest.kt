package pt.feubjj.timer.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class TvViewportScalerTest {

    @Test
    fun `normalizes 1080p panel to 2x density regardless of OEM DPI reporting`() {
        // Whether an Android TV Box reports 160dpi (1.0x) or 320dpi (2.0x), a 1920x1080 window
        // must map cleanly onto the canonical 960x540 10-foot reference grid (density = 2.0f).
        assertEquals(2.0f, TvViewportScaler.computeTargetDensity(1920, 1080, fallbackDensity = 1.0f), 0.001f)
        assertEquals(2.0f, TvViewportScaler.computeTargetDensity(1920, 1080, fallbackDensity = 2.0f), 0.001f)
    }

    @Test
    fun `normalizes 720p and 4K panels proportionally to identical physical screen fractions`() {
        assertEquals(1.3333f, TvViewportScaler.computeTargetDensity(1280, 720, fallbackDensity = 1.0f), 0.001f)
        assertEquals(4.0f, TvViewportScaler.computeTargetDensity(3840, 2160, fallbackDensity = 2.0f), 0.001f)
    }

    @Test
    fun `constrains by limiting axis on ultrawide phones so vertical UI never clips`() {
        // 2400x1080 (20:9 landscape phone): height limits density to 1080/540 = 2.0f, giving 1200x540dp
        assertEquals(2.0f, TvViewportScaler.computeTargetDensity(2400, 1080, fallbackDensity = 2.75f), 0.001f)
    }

    @Test
    fun `falls back safely when window pixel dimensions are zero or unmeasured`() {
        assertEquals(1.5f, TvViewportScaler.computeTargetDensity(0, 0, fallbackDensity = 1.5f), 0.001f)
    }
}
