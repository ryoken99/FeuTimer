package pt.feubjj.timer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import pt.feubjj.timer.data.SharedPreferencesTimerStorage
import pt.feubjj.timer.domain.model.MatchResultRecord
import pt.feubjj.timer.domain.model.RoundConfig

private fun RoundConfig.json() = JSONObject().put("repeat", repeat).put("workSeconds", workSeconds)
    .put("restSeconds", restSeconds).put("isStopwatch", isStopwatch)

internal fun legacyProfile(context: Context): String {
    val storage = SharedPreferencesTimerStorage(context)
    val slots = JSONObject()
    storage.loadPresetSlots().forEachIndexed { index, preset ->
        if (preset.isCustomized) slots.put(index.toString(), preset.config.json()
            .put("id", preset.id).put("titleKey", preset.titleKey).put("customName", preset.customName))
    }
    val sound = with(storage.loadSoundPreferences()) {
        JSONObject().put("voiceEnabled", voiceEnabled).put("warnHalfway", warnHalfway)
            .put("warn2m", warn2m).put("warn1m", warn1m).put("beep50s", beep50s)
            .put("warn30s", warn30s).put("beep10s", beep10s).put("prepSeconds", prepSeconds)
            .put("soundPack", soundPack.name).put("use24HourClock", use24HourClock)
    }
    val locale = when (storage.loadLocaleOverride()?.take(2)) {
        "en" -> "en"
        "es" -> "es"
        else -> "pt-PT"
    }
    return with(storage.loadQuickOptions()) {
        JSONObject().put("customizedSlots", slots).put("locale", locale)
            .put("quickSeconds", durationSeconds).put("quickRepeat", repeat)
            .put("quickRestSeconds", restSeconds).put("quickEndless", endless)
            .put("soundPreferences", sound).put("customRound", storage.loadCustomRound()?.json())
            .put("lastSessionConfig", storage.loadLastSession()?.json())
            .put("matchResults", JSONArray(MatchResultRecord.encodeList(storage.loadMatchResults()))).toString()
    }
}
