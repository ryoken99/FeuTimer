package pt.feubjj.timer.data

import android.content.Context
import android.content.SharedPreferences
import pt.feubjj.timer.audio.SoundPack
import pt.feubjj.timer.domain.model.MatchResultRecord
import pt.feubjj.timer.domain.model.QuickTimerOptions
import pt.feubjj.timer.domain.model.RoundConfig
import pt.feubjj.timer.domain.model.SoundPreferences
import pt.feubjj.timer.domain.presets.PresetCatalog
import pt.feubjj.timer.domain.presets.PresetNames
import pt.feubjj.timer.domain.presets.RoundPresetOption

/**
 * Persistence contract for tatami preferences, editable academy preset slots, custom round builder,
 * app locale override, last session, and saved IBJJF competition match results.
 */
interface TimerStorage {
    fun loadSoundPreferences(): SoundPreferences
    fun saveSoundPreferences(preferences: SoundPreferences)

    fun loadCustomRound(): RoundConfig?
    fun saveCustomRound(config: RoundConfig)

    fun loadLastSession(): RoundConfig?
    fun saveLastSession(config: RoundConfig)

    fun loadPresetSlots(): List<RoundPresetOption>
    fun savePresetSlot(index: Int, config: RoundConfig, name: String? = null)
    fun resetPresetSlots()

    fun loadLocaleOverride(): String?
    fun saveLocaleOverride(localeTag: String?)

    fun loadMatchResults(): List<MatchResultRecord>
    fun saveMatchResults(results: List<MatchResultRecord>)

    fun loadQuickOptions(): QuickTimerOptions
    fun saveQuickOptions(options: QuickTimerOptions)
}

class InMemoryTimerStorage(
    private var soundPrefs: SoundPreferences = SoundPreferences(),
    private var customRound: RoundConfig? = null,
    private var lastSession: RoundConfig? = null,
    private var localeOverride: String? = null
) : TimerStorage {

    private val presetSlots: MutableList<RoundPresetOption> =
        PresetCatalog.coreRoundPresets.toMutableList()
    private val matchResults: MutableList<MatchResultRecord> = mutableListOf()

    override fun loadSoundPreferences(): SoundPreferences = soundPrefs

    override fun saveSoundPreferences(preferences: SoundPreferences) {
        soundPrefs = preferences
    }

    override fun loadCustomRound(): RoundConfig? = customRound

    override fun saveCustomRound(config: RoundConfig) {
        customRound = config
    }

    override fun loadLastSession(): RoundConfig? = lastSession

    override fun saveLastSession(config: RoundConfig) {
        lastSession = config
    }

    override fun loadPresetSlots(): List<RoundPresetOption> = presetSlots.toList()

    override fun savePresetSlot(index: Int, config: RoundConfig, name: String?) {
        if (index == presetSlots.size) {
            presetSlots.add(RoundPresetOption("workout_$index", "new_workout", config))
        }
        if (index !in presetSlots.indices) return
        presetSlots[index] = presetSlots[index].copy(
            config = config,
            isCustomized = true,
            customName = PresetNames.clean(name)
        )
    }

    override fun resetPresetSlots() {
        presetSlots.clear()
        presetSlots.addAll(PresetCatalog.coreRoundPresets)
    }

    override fun loadLocaleOverride(): String? = localeOverride

    override fun saveLocaleOverride(localeTag: String?) {
        localeOverride = localeTag
    }

    override fun loadMatchResults(): List<MatchResultRecord> = matchResults.toList()

    override fun saveMatchResults(results: List<MatchResultRecord>) {
        matchResults.clear()
        matchResults.addAll(results)
    }

    private var quickOptions = QuickTimerOptions()

    override fun loadQuickOptions(): QuickTimerOptions = quickOptions

    override fun saveQuickOptions(options: QuickTimerOptions) {
        quickOptions = options
    }
}

class SharedPreferencesTimerStorage(context: Context) : TimerStorage {

    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun loadSoundPreferences(): SoundPreferences {
        val packName = prefs.getString(KEY_SOUND_PACK, SoundPack.CLASSIC_HORN.name)
        val soundPack = SoundPack.entries.firstOrNull { it.name == packName } ?: SoundPack.CLASSIC_HORN
        return SoundPreferences(
            voiceEnabled = prefs.getBoolean(KEY_VOICE_ENABLED, true),
            warnHalfway = prefs.getBoolean(KEY_WARN_HALFWAY, true),
            warn2m = prefs.getBoolean(KEY_WARN_2M, false),
            warn1m = prefs.getBoolean(KEY_WARN_1M, false),
            beep50s = prefs.getBoolean(KEY_BEEP_50S, true),
            warn30s = prefs.getBoolean(KEY_WARN_30S, false),
            beep10s = prefs.getBoolean(KEY_BEEP_10S, true),
            prepSeconds = prefs.getInt(KEY_PREP_SECONDS, 3),
            soundPack = soundPack,
            use24HourClock = prefs.getBoolean(KEY_USE_24H, true)
        )
    }

    override fun saveSoundPreferences(preferences: SoundPreferences) {
        prefs.edit()
            .putBoolean(KEY_VOICE_ENABLED, preferences.voiceEnabled)
            .putBoolean(KEY_WARN_HALFWAY, preferences.warnHalfway)
            .putBoolean(KEY_WARN_2M, preferences.warn2m)
            .putBoolean(KEY_WARN_1M, preferences.warn1m)
            .putBoolean(KEY_BEEP_50S, preferences.beep50s)
            .putBoolean(KEY_WARN_30S, preferences.warn30s)
            .putBoolean(KEY_BEEP_10S, preferences.beep10s)
            .putInt(KEY_PREP_SECONDS, preferences.prepSeconds)
            .putString(KEY_SOUND_PACK, preferences.soundPack.name)
            .putBoolean(KEY_USE_24H, preferences.use24HourClock)
            .apply()
    }

    override fun loadCustomRound(): RoundConfig? {
        if (!prefs.contains(KEY_CUSTOM_WORK)) return null
        return RoundConfig(
            repeat = prefs.getInt(KEY_CUSTOM_REPEAT, 5),
            workSeconds = prefs.getLong(KEY_CUSTOM_WORK, 300L),
            restSeconds = prefs.getLong(KEY_CUSTOM_REST, 60L)
        )
    }

    override fun saveCustomRound(config: RoundConfig) {
        prefs.edit()
            .putInt(KEY_CUSTOM_REPEAT, config.repeat)
            .putLong(KEY_CUSTOM_WORK, config.workSeconds)
            .putLong(KEY_CUSTOM_REST, config.restSeconds)
            .apply()
    }

    override fun loadLastSession(): RoundConfig? {
        if (!prefs.contains(KEY_LAST_WORK)) return null
        return RoundConfig(
            repeat = prefs.getInt(KEY_LAST_REPEAT, 0),
            workSeconds = prefs.getLong(KEY_LAST_WORK, 300L),
            restSeconds = prefs.getLong(KEY_LAST_REST, 0L),
            isStopwatch = prefs.getBoolean(KEY_LAST_STOPWATCH, false)
        )
    }

    override fun saveLastSession(config: RoundConfig) {
        prefs.edit()
            .putInt(KEY_LAST_REPEAT, config.repeat)
            .putLong(KEY_LAST_WORK, config.workSeconds)
            .putLong(KEY_LAST_REST, config.restSeconds)
            .putBoolean(KEY_LAST_STOPWATCH, config.isStopwatch)
            .apply()
    }

    override fun loadPresetSlots(): List<RoundPresetOption> {
        val defaults = PresetCatalog.coreRoundPresets
        return List(prefs.getInt("workout_count", defaults.size)) { idx ->
            val defaultPreset = defaults.getOrNull(idx)
                ?: RoundPresetOption("workout_$idx", "new_workout", defaults.first().config)
            val workKey = "slot_${idx}_work"
            if (!prefs.contains(workKey)) {
                defaultPreset
            } else {
                defaultPreset.copy(
                    config = RoundConfig(
                        repeat = prefs.getInt("slot_${idx}_repeat", defaultPreset.config.repeat),
                        workSeconds = prefs.getLong(workKey, defaultPreset.config.workSeconds),
                        restSeconds = prefs.getLong("slot_${idx}_rest", defaultPreset.config.restSeconds)
                    ),
                    isCustomized = true,
                    customName = PresetNames.clean(prefs.getString("slot_${idx}_name", null))
                )
            }
        }
    }

    override fun savePresetSlot(index: Int, config: RoundConfig, name: String?) {
        val count = prefs.getInt("workout_count", PresetCatalog.coreRoundPresets.size)
        if (index !in 0..count) return
        val cleanName = PresetNames.clean(name)
        prefs.edit()
            .putInt("workout_count", maxOf(count, index + 1))
            .apply { if (cleanName == null) remove("slot_${index}_name") else putString("slot_${index}_name", cleanName) }
            .putInt("slot_${index}_repeat", config.repeat)
            .putLong("slot_${index}_work", config.workSeconds)
            .putLong("slot_${index}_rest", config.restSeconds)
            .apply()
    }

    override fun resetPresetSlots() {
        val editor = prefs.edit()
        editor.remove("workout_count")
        for (idx in 0 until prefs.getInt("workout_count", PresetCatalog.coreRoundPresets.size)) {
            editor.remove("slot_${idx}_repeat")
            editor.remove("slot_${idx}_work")
            editor.remove("slot_${idx}_rest")
            editor.remove("slot_${idx}_name")
        }
        editor.apply()
    }

    override fun loadLocaleOverride(): String? = prefs.getString(KEY_LOCALE, null)

    override fun saveLocaleOverride(localeTag: String?) {
        prefs.edit().apply {
            if (localeTag == null) remove(KEY_LOCALE) else putString(KEY_LOCALE, localeTag)
        }.apply()
    }

    override fun loadMatchResults(): List<MatchResultRecord> =
        MatchResultRecord.decodeList(prefs.getString(KEY_MATCH_RESULTS, null))

    override fun saveMatchResults(results: List<MatchResultRecord>) {
        prefs.edit()
            .putString(KEY_MATCH_RESULTS, MatchResultRecord.encodeList(results))
            .apply()
    }

    override fun loadQuickOptions(): QuickTimerOptions = QuickTimerOptions(
        repeat = prefs.getInt(KEY_QUICK_REPEAT, 1).coerceIn(QuickTimerOptions.MIN_REPEAT, QuickTimerOptions.MAX_REPEAT),
        restSeconds = prefs.getLong(KEY_QUICK_REST, 60L).coerceIn(0L, QuickTimerOptions.MAX_REST_SECONDS)
    )

    override fun saveQuickOptions(options: QuickTimerOptions) {
        prefs.edit()
            .putInt(KEY_QUICK_REPEAT, options.repeat)
            .putLong(KEY_QUICK_REST, options.restSeconds)
            .apply()
    }

    private companion object {
        const val KEY_QUICK_REPEAT = "quick_repeat"
        const val KEY_QUICK_REST = "quick_rest_sec"
        const val PREFS_NAME = "pt.feubjj.timer.prefs"
        const val KEY_VOICE_ENABLED = "voice_enabled"
        const val KEY_WARN_HALFWAY = "warn_halfway"
        const val KEY_WARN_2M = "warn_2m"
        const val KEY_WARN_1M = "warn_1m"
        const val KEY_BEEP_50S = "beep_50s"
        const val KEY_WARN_30S = "warn_30s"
        const val KEY_BEEP_10S = "beep_10s"
        const val KEY_PREP_SECONDS = "prep_seconds"
        const val KEY_SOUND_PACK = "sound_pack"
        const val KEY_USE_24H = "use_24h"
        const val KEY_LOCALE = "locale_override"
        const val KEY_CUSTOM_REPEAT = "custom_repeat"
        const val KEY_CUSTOM_WORK = "custom_work_sec"
        const val KEY_CUSTOM_REST = "custom_rest_sec"
        const val KEY_LAST_REPEAT = "last_repeat"
        const val KEY_LAST_WORK = "last_work_sec"
        const val KEY_LAST_REST = "last_rest_sec"
        const val KEY_LAST_STOPWATCH = "last_stopwatch"
        const val KEY_MATCH_RESULTS = "match_results_json"
    }
}
