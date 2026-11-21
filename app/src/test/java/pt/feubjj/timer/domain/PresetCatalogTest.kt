package pt.feubjj.timer.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import pt.feubjj.timer.domain.presets.PresetCatalog

class PresetCatalogTest {

    @Test
    fun `provides all 15 exact quick single timer durations from JJTimer`() {
        val labels = PresetCatalog.quickTimers.map { it.label }
        assertEquals(
            listOf(
                "00:30", "01:00", "02:00", "03:00", "04:00",
                "05:00", "06:00", "07:00", "08:00", "09:00",
                "10:00", "12:00", "15:00", "20:00", "30:00"
            ),
            labels
        )
        assertEquals(30L, PresetCatalog.quickTimers.first().seconds)
        assertEquals(1800L, PresetCatalog.quickTimers.last().seconds)
    }

    @Test
    fun `provides all 4 exact core multi-round BJJ presets from JJTimer`() {
        val core = PresetCatalog.coreRoundPresets
        assertEquals(4, core.size)

        assertEquals(5, core[0].config.repeat)
        assertEquals(300L, core[0].config.workSeconds)
        assertEquals(60L, core[0].config.restSeconds)

        assertEquals(6, core[1].config.repeat)
        assertEquals(360L, core[1].config.workSeconds)
        assertEquals(30L, core[1].config.restSeconds)

        assertEquals(10, core[2].config.repeat)
        assertEquals(120L, core[2].config.workSeconds)
        assertEquals(180L, core[2].config.restSeconds)

        assertEquals(3, core[3].config.repeat)
        assertEquals(180L, core[3].config.workSeconds)
        assertEquals(30L, core[3].config.restSeconds)
    }
}
