package pt.feubjj.timer.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import pt.feubjj.timer.R
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Preloads both studio-mixed PT-PT vocal + tatami cues and pure non-verbal SFX cues into Android's
 * native [SoundPool] at startup so every second tick, warning, and horn plays with zero HDMI latency.
 */
class TimerSoundPlayer(context: Context) : SoundPlayerPort {

    private data class SampleKey(
        val canonicalName: String
    )

    private val soundPool: SoundPool
    private val sampleIds = ConcurrentHashMap<SampleKey, Int>()
    private val loadedIds: MutableSet<Int> =
        Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())
    private val pendingPlayIds: MutableSet<Int> =
        Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())
    private var lastStreamId: Int = 0

    init {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attributes)
            .build()

        soundPool.setOnLoadCompleteListener { pool, sampleId, status ->
            if (status == 0) {
                loadedIds.add(sampleId)
                if (pendingPlayIds.remove(sampleId)) {
                    lastStreamId = pool.play(sampleId, 1.0f, 1.0f, 1, 0, 1.0f)
                }
            }
        }

        val voicePcm = loadRawVoicePcmMap(context)
        val cacheDir = File(context.cacheDir, "feu_timer_vocal_v3").apply { mkdirs() }

        fun registerWav(keyName: String, generator: () -> ByteArray) {
            val wavFile = File(cacheDir, "$keyName.wav")
            if (!wavFile.exists() || wavFile.length() <= 44L) {
                wavFile.writeBytes(generator())
            }
            val id = soundPool.load(wavFile.absolutePath, 1)
            sampleIds[SampleKey(keyName)] = id
        }

        // 1. Pack-independent vocal cues (countdown 10..1, mid-round warnings, prep)
        val sharedVocalCues = listOf(
            SoundCue.COUNT_10, SoundCue.COUNT_9, SoundCue.COUNT_8, SoundCue.COUNT_7, SoundCue.COUNT_6,
            SoundCue.COUNT_5, SoundCue.COUNT_4, SoundCue.COUNT_3, SoundCue.COUNT_2, SoundCue.COUNT_1,
            SoundCue.WARN_HALFWAY, SoundCue.WARN_2M, SoundCue.WARN_1M, SoundCue.WARN_50S, SoundCue.WARN_30S,
            SoundCue.PREP_READY
        )
        for (cue in sharedVocalCues) {
            registerWav("vocal_${cue.name.lowercase()}") {
                WavSynthesizer.generateVocalCompositeWav(cue, SoundPack.CLASSIC_HORN, voicePcm)
            }
        }

        // 2. Pack-dependent vocal transition cues (FIGHT_HORN, ROUND_END_TO_REST, ROUND_END_ALARM)
        for (pack in SoundPack.entries) {
            for (cue in listOf(SoundCue.FIGHT_HORN, SoundCue.ROUND_END_TO_REST, SoundCue.MATCH_END, SoundCue.ROUND_END_ALARM)) {
                registerWav("vocal_${pack.name.lowercase()}_${cue.name.lowercase()}") {
                    WavSynthesizer.generateVocalCompositeWav(cue, pack, voicePcm)
                }
            }
        }

        // 3. Pure non-verbal SFX fallbacks when `voiceEnabled == false`
        registerWav("sfx_pip") { WavSynthesizer.generateCueWav(SoundCue.COUNTDOWN_PIP) }
        registerWav("sfx_clap") { WavSynthesizer.generateCueWav(SoundCue.WARNING_CLAP) }
        for (pack in SoundPack.entries) {
            registerWav("sfx_${pack.name.lowercase()}_start") {
                WavSynthesizer.generateCueWav(SoundCue.FIGHT_HORN, pack)
            }
            registerWav("sfx_${pack.name.lowercase()}_end") {
                WavSynthesizer.generateCueWav(SoundCue.ROUND_END_ALARM, pack)
            }
        }
    }

    override fun play(cue: SoundCue, pack: SoundPack, voiceEnabled: Boolean) {
        val keyName = resolveSampleKeyName(cue, pack, voiceEnabled)
        val sampleId = sampleIds[SampleKey(keyName)] ?: return
        if (loadedIds.contains(sampleId)) {
            if (lastStreamId != 0) {
                soundPool.stop(lastStreamId)
            }
            lastStreamId = soundPool.play(sampleId, 1.0f, 1.0f, 1, 0, 1.0f)
        } else {
            pendingPlayIds.add(sampleId)
        }
    }

    override fun stopAll() {
        pendingPlayIds.clear()
        if (lastStreamId != 0) {
            soundPool.stop(lastStreamId)
            lastStreamId = 0
        }
        soundPool.autoPause()
    }

    override fun release() {
        pendingPlayIds.clear()
        soundPool.release()
    }

    private fun resolveSampleKeyName(cue: SoundCue, pack: SoundPack, voiceEnabled: Boolean): String {
        if (!voiceEnabled) {
            return when (cue) {
                SoundCue.FIGHT_HORN -> "sfx_${pack.name.lowercase()}_start"
                SoundCue.ROUND_END_TO_REST,
                SoundCue.MATCH_END,
                SoundCue.ROUND_END_ALARM -> "sfx_${pack.name.lowercase()}_end"
                SoundCue.WARN_HALFWAY,
                SoundCue.WARN_2M,
                SoundCue.WARN_1M,
                SoundCue.WARN_50S,
                SoundCue.WARN_30S,
                SoundCue.WARNING_CLAP -> "sfx_clap"
                else -> "sfx_pip"
            }
        }

        return when (cue) {
            SoundCue.FIGHT_HORN,
            SoundCue.ROUND_END_TO_REST,
            SoundCue.MATCH_END,
            SoundCue.ROUND_END_ALARM -> "vocal_${pack.name.lowercase()}_${cue.name.lowercase()}"
            SoundCue.COUNTDOWN_PIP -> "sfx_pip"
            SoundCue.WARNING_CLAP -> "sfx_clap"
            else -> "vocal_${cue.name.lowercase()}"
        }
    }

    private fun loadRawVoicePcmMap(context: Context): Map<String, ShortArray> {
        val rawMap = mapOf(
            "count_01" to R.raw.voice_count_01,
            "count_02" to R.raw.voice_count_02,
            "count_03" to R.raw.voice_count_03,
            "count_04" to R.raw.voice_count_04,
            "count_05" to R.raw.voice_count_05,
            "count_06" to R.raw.voice_count_06,
            "count_07" to R.raw.voice_count_07,
            "count_08" to R.raw.voice_count_08,
            "count_09" to R.raw.voice_count_09,
            "count_10" to R.raw.voice_count_10,
            "warn_halfway" to R.raw.voice_warn_halfway,
            "warn_2m" to R.raw.voice_warn_2m,
            "warn_1m" to R.raw.voice_warn_1m,
            "warn_50s" to R.raw.voice_warn_50s,
            "warn_30s" to R.raw.voice_warn_30s,
            "warn_10s" to R.raw.voice_warn_10s,
            "prep_ready" to R.raw.voice_prep_ready,
            "fight_start" to R.raw.voice_fight_start,
            "round_end" to R.raw.voice_round_end,
            "rest_start" to R.raw.voice_rest_start,
            "session_end" to R.raw.voice_session_end
        )
        return rawMap.mapValues { (_, resId) ->
            context.resources.openRawResource(resId).use { stream ->
                WavSynthesizer.decodePcm16MonoWav(stream.readBytes())
            }
        }
    }
}
