package pt.feubjj.timer.domain.presets

import pt.feubjj.timer.domain.engine.TimeFormatter
import pt.feubjj.timer.domain.model.RoundConfig

data class QuickTimerOption(
    val label: String,
    val seconds: Long
) {
    fun toRoundConfig(): RoundConfig = RoundConfig.single(seconds)
}

data class RoundPresetOption(
    val id: String,
    val titleKey: String,
    val config: RoundConfig,
    val isCustomized: Boolean = false
)

/**
 * Canonical preset definitions combining all 15 quick countdowns (`00:30`..`30:00`),
 * the 4 editable Academy Preset Slots, and a rich BJJ Preset Library (including IBJJF,
 * BJJ OUT endurance/sprint formats, Tabata, and Open-Mat Endless rounds).
 */
object PresetCatalog {

    private val quickTimerLabels = listOf(
        "00:30", "01:00", "02:00", "03:00", "04:00",
        "05:00", "06:00", "07:00", "08:00", "09:00",
        "10:00", "12:00", "15:00", "20:00", "30:00"
    )

    val quickTimers: List<QuickTimerOption> = quickTimerLabels.map { label ->
        QuickTimerOption(
            label = label,
            seconds = TimeFormatter.parseMmSs(label)
        )
    }

    val coreRoundPresets: List<RoundPresetOption> = listOf(
        RoundPresetOption(
            id = "preset_adult_5x5",
            titleKey = "preset_adult",
            config = RoundConfig(repeat = 5, workSeconds = 300L, restSeconds = 60L)
        ),
        RoundPresetOption(
            id = "preset_advanced_6x6",
            titleKey = "preset_comp",
            config = RoundConfig(repeat = 6, workSeconds = 360L, restSeconds = 30L)
        ),
        RoundPresetOption(
            id = "preset_positional_10x2",
            titleKey = "preset_specific",
            config = RoundConfig(repeat = 10, workSeconds = 120L, restSeconds = 180L)
        ),
        RoundPresetOption(
            id = "preset_drills_3x3",
            titleKey = "preset_kids_drills",
            config = RoundConfig(repeat = 3, workSeconds = 180L, restSeconds = 30L)
        )
    )

    /**
     * Built-in BJJ template library (including `BJJ OUT` templates, IBJJF match times, Tabata, and Endless Open Mat)
     * that coaches can load into the Custom Builder or save into any of the 4 Academy Slots.
     */
    val templateLibrary: List<RoundPresetOption> = listOf(
        RoundPresetOption("tpl_open_5x1", "tpl_open_mat", RoundConfig.endless(300L, 60L)),
        RoundPresetOption("tpl_endurance_10x5", "tpl_endurance", RoundConfig(10, 300L, 90L)),
        RoundPresetOption("tpl_pro_4x10", "tpl_black_belt", RoundConfig(4, 600L, 120L)),
        RoundPresetOption("tpl_pace_10x3", "tpl_high_pace", RoundConfig(10, 180L, 40L)),
        RoundPresetOption("tpl_shark_4x1", "tpl_shark_tank", RoundConfig(4, 60L, 60L)),
        RoundPresetOption("tpl_tabata_8x20", "tpl_tabata", RoundConfig(8, 20L, 10L))
    )

    /** Official IBJJF match durations by belt category for Competition / Scoreboard mode. */
    val ibjjfBeltDurationsSeconds: List<Pair<String, Long>> = listOf(
        "2:00" to 120L,  // Kids / short drills
        "3:00" to 180L,  // Kids / short fights
        "4:00" to 240L,  // Juvenile / short fights
        "5:00" to 300L,  // White / Masters
        "6:00" to 360L,  // Blue
        "7:00" to 420L,  // Purple
        "8:00" to 480L,  // Brown
        "10:00" to 600L  // Black
    )
}
