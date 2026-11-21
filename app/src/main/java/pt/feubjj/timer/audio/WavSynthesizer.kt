package pt.feubjj.timer.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Deterministic 16-bit 44.1 kHz mono PCM WAV synthesizer & vocal mixer for tatami audio cues.
 * Supports both pure non-verbal tatami sound effects and sample-accurate composite vocal + SFX cues.
 */
object WavSynthesizer {

    const val SAMPLE_RATE = 44100

    /** Pure non-verbal SFX fallback when `voiceEnabled == false` (or when voice PCM is unavailable). */
    fun generateCueWav(cue: SoundCue, pack: SoundPack = SoundPack.CLASSIC_HORN): ByteArray {
        val pcmSamples = renderNonVerbalSamples(cue, pack)
        return encodePcm16MonoWav(pcmSamples)
    }

    /**
     * Mixes tatami sound effects with pre-trimmed `44.1 kHz 16-bit mono` PT-PT voice clips
     * using soft-knee limiting so horn/clapper transients never clip or mask spoken syllables.
     */
    fun generateVocalCompositeWav(
        cue: SoundCue,
        pack: SoundPack,
        voicePcmByKey: Map<String, ShortArray>
    ): ByteArray {
        val countVoiceKey = when (cue) {
            SoundCue.COUNT_10 -> "count_10"
            SoundCue.COUNT_9 -> "count_09"
            SoundCue.COUNT_8 -> "count_08"
            SoundCue.COUNT_7 -> "count_07"
            SoundCue.COUNT_6 -> "count_06"
            SoundCue.COUNT_5 -> "count_05"
            SoundCue.COUNT_4 -> "count_04"
            SoundCue.COUNT_3 -> "count_03"
            SoundCue.COUNT_2 -> "count_02"
            SoundCue.COUNT_1 -> "count_01"
            else -> null
        }

        if (countVoiceKey != null) {
            val v = voicePcmByKey[countVoiceKey] ?: return generateCueWav(cue, pack)
            val pip = renderCountdownPip()
            return encodePcm16MonoWav(
                mixLayers(
                    Layer(pip, offsetSec = 0.0, gain = 0.22),
                    Layer(v, offsetSec = 0.0, gain = 1.0)
                )
            )
        }

        val warnKey = when (cue) {
            SoundCue.WARN_HALFWAY -> "warn_halfway"
            SoundCue.WARN_2M -> "warn_2m"
            SoundCue.WARN_1M -> "warn_1m"
            SoundCue.WARN_50S -> "warn_50s"
            SoundCue.WARN_30S -> "warn_30s"
            else -> null
        }

        if (warnKey != null) {
            val v = voicePcmByKey[warnKey] ?: return generateCueWav(cue, pack)
            val singleClap = renderWoodClapperSingle()
            return encodePcm16MonoWav(
                mixLayers(
                    Layer(singleClap, offsetSec = 0.0, gain = 0.52),
                    Layer(v, offsetSec = 0.13, gain = 1.0)
                )
            )
        }

        return when (cue) {
            SoundCue.PREP_READY -> {
                val v = voicePcmByKey["prep_ready"] ?: return generateCueWav(cue, pack)
                encodePcm16MonoWav(mixLayers(Layer(v, offsetSec = 0.0, gain = 1.0)))
            }

            SoundCue.FIGHT_HORN -> {
                val v = voicePcmByKey["fight_start"] ?: return generateCueWav(cue, pack)
                val horn = renderStartEffect(pack)
                encodePcm16MonoWav(
                    mixLayers(
                        Layer(horn, offsetSec = 0.0, gain = 0.68),
                        Layer(v, offsetSec = 0.24, gain = 1.0)
                    )
                )
            }

            SoundCue.ROUND_END_TO_REST -> {
                val tempo = voicePcmByKey["round_end"] ?: return generateCueWav(cue, pack)
                val descanso = voicePcmByKey["rest_start"]
                val endFx = renderShortEndEffect(pack)
                val layers = mutableListOf(
                    Layer(endFx, offsetSec = 0.0, gain = 0.66),
                    Layer(tempo, offsetSec = 0.25, gain = 1.0)
                )
                if (descanso != null) {
                    val restOffset = 0.25 + (tempo.size.toDouble() / SAMPLE_RATE) + 0.10
                    layers += Layer(descanso, offsetSec = restOffset, gain = 1.0)
                }
                encodePcm16MonoWav(mixLayers(*layers.toTypedArray()))
            }

            SoundCue.MATCH_END -> {
                val tempo = voicePcmByKey["round_end"] ?: return generateCueWav(cue, pack)
                val endFx = renderShortEndEffect(pack)
                encodePcm16MonoWav(
                    mixLayers(
                        Layer(endFx, offsetSec = 0.0, gain = 0.68),
                        Layer(tempo, offsetSec = 0.25, gain = 1.0)
                    )
                )
            }

            SoundCue.ROUND_END_ALARM -> {
                val tempo = voicePcmByKey["round_end"] ?: return generateCueWav(cue, pack)
                val fim = voicePcmByKey["session_end"]
                val endFx = renderShortEndEffect(pack)
                val layers = mutableListOf(
                    Layer(endFx, offsetSec = 0.0, gain = 0.68),
                    Layer(tempo, offsetSec = 0.25, gain = 1.0)
                )
                if (fim != null) {
                    val endOffset = 0.25 + (tempo.size.toDouble() / SAMPLE_RATE) + 0.10
                    layers += Layer(fim, offsetSec = endOffset, gain = 1.0)
                }
                encodePcm16MonoWav(mixLayers(*layers.toTypedArray()))
            }

            else -> generateCueWav(cue, pack)
        }
    }

    /** Decodes a 16-bit mono PCM RIFF/WAVE byte array into raw [ShortArray] samples. */
    fun decodePcm16MonoWav(wavBytes: ByteArray): ShortArray {
        if (wavBytes.size <= 44) return ShortArray(0)
        val bb = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 12
        while (pos + 8 <= wavBytes.size) {
            val chunkId = String(wavBytes, pos, 4, Charsets.US_ASCII)
            val chunkSize = bb.getInt(pos + 4)
            val dataStart = pos + 8
            if (chunkId == "data") {
                val safeBytes = chunkSize.coerceAtMost(wavBytes.size - dataStart)
                val sampleCount = safeBytes / 2
                val out = ShortArray(sampleCount)
                for (i in 0 until sampleCount) {
                    out[i] = bb.getShort(dataStart + i * 2)
                }
                return out
            }
            pos = dataStart + chunkSize + (chunkSize and 1)
        }
        return ShortArray(0)
    }

    private data class Layer(
        val samples: ShortArray,
        val offsetSec: Double,
        val gain: Double
    )

    private fun mixLayers(vararg layers: Layer): ShortArray {
        var maxSampleIndex = 0
        for (layer in layers) {
            val end = (layer.offsetSec * SAMPLE_RATE).toInt() + layer.samples.size
            if (end > maxSampleIndex) maxSampleIndex = end
        }
        val mix = DoubleArray(maxSampleIndex.coerceAtLeast(1))
        for (layer in layers) {
            val start = (layer.offsetSec * SAMPLE_RATE).toInt().coerceAtLeast(0)
            for (i in layer.samples.indices) {
                val idx = start + i
                if (idx >= mix.size) break
                mix[idx] += (layer.samples[i].toDouble() / Short.MAX_VALUE) * layer.gain
            }
        }
        // Soft-knee tanh limiter prevents harsh digital clipping where horn tail and voice overlap
        return ShortArray(mix.size) { i ->
            val v = mix[i]
            val limited = if (kotlin.math.abs(v) > 0.85) tanh(v) * 0.98 else v
            toSample(limited)
        }
    }

    private fun renderNonVerbalSamples(cue: SoundCue, pack: SoundPack): ShortArray = when (cue) {
        SoundCue.COUNT_10,
        SoundCue.COUNT_9,
        SoundCue.COUNT_8,
        SoundCue.COUNT_7,
        SoundCue.COUNT_6,
        SoundCue.COUNT_5,
        SoundCue.COUNT_4,
        SoundCue.COUNT_3,
        SoundCue.COUNT_2,
        SoundCue.COUNT_1,
        SoundCue.COUNTDOWN_PIP,
        SoundCue.PREP_READY -> renderCountdownPip()

        SoundCue.FIGHT_HORN -> renderStartEffect(pack)

        SoundCue.WARN_HALFWAY,
        SoundCue.WARN_2M,
        SoundCue.WARN_1M,
        SoundCue.WARN_50S,
        SoundCue.WARN_30S,
        SoundCue.WARNING_CLAP -> renderWoodClapperTriple()

        SoundCue.ROUND_END_TO_REST,
        SoundCue.MATCH_END,
        SoundCue.ROUND_END_ALARM -> when (pack) {
            SoundPack.CLASSIC_HORN -> renderRoundEndAlarm()
            SoundPack.BELL_GONG -> renderChampionshipBell(strikes = 3)
            SoundPack.WHISTLE -> renderRefereeWhistle(longBlast = false)
        }
    }

    private fun renderStartEffect(pack: SoundPack): ShortArray = when (pack) {
        SoundPack.CLASSIC_HORN -> renderFightHorn()
        SoundPack.BELL_GONG -> renderChampionshipBell(strikes = 1)
        SoundPack.WHISTLE -> renderRefereeWhistle(longBlast = true)
    }

    private fun renderShortEndEffect(pack: SoundPack): ShortArray = when (pack) {
        SoundPack.CLASSIC_HORN -> renderFightHorn(durationSec = 0.36)
        SoundPack.BELL_GONG -> renderChampionshipBell(strikes = 2)
        SoundPack.WHISTLE -> renderRefereeWhistle(longBlast = true)
    }

    private fun renderCountdownPip(): ShortArray {
        val durationSec = 0.20
        val total = (SAMPLE_RATE * durationSec).toInt()
        val out = ShortArray(total)
        for (i in 0 until total) {
            val t = i.toDouble() / SAMPLE_RATE
            val attack = (t / 0.005).coerceAtMost(1.0)
            val decay = exp(-t * 9.5)
            val wave = 0.82 * sin(2.0 * PI * 950.0 * t) + 0.18 * sin(2.0 * PI * 1900.0 * t)
            out[i] = toSample(wave * attack * decay * 0.78)
        }
        return out
    }

    private fun renderFightHorn(durationSec: Double = 0.52): ShortArray {
        val total = (SAMPLE_RATE * durationSec).toInt()
        val out = ShortArray(total)
        for (i in 0 until total) {
            val t = i.toDouble() / SAMPLE_RATE
            val attack = (t / 0.012).coerceAtMost(1.0)
            val release = ((durationSec - t) / 0.08).coerceIn(0.0, 1.0)
            val env = attack * release
            val fundamental = 0.56 * sin(2.0 * PI * 985.0 * t)
            val sub = 0.26 * sin(2.0 * PI * 492.5 * t)
            val fifth = 0.18 * sin(2.0 * PI * 1477.5 * t)
            out[i] = toSample((fundamental + sub + fifth) * env * 0.88)
        }
        return out
    }

    private fun renderChampionshipBell(strikes: Int): ShortArray {
        val durationSec = when (strikes) {
            1 -> 1.15
            2 -> 1.35
            else -> 1.95
        }
        val total = (SAMPLE_RATE * durationSec).toInt()
        val mix = DoubleArray(total)
        val offsets = when (strikes) {
            1 -> doubleArrayOf(0.0)
            2 -> doubleArrayOf(0.0, 0.26)
            else -> doubleArrayOf(0.0, 0.34, 0.68)
        }

        for (off in offsets) {
            val start = (off * SAMPLE_RATE).toInt()
            for (k in 0 until total - start) {
                val t = k.toDouble() / SAMPLE_RATE
                val attack = (t / 0.002).coerceAtMost(1.0)
                val p1 = 0.45 * sin(2.0 * PI * 820.0 * t) * exp(-t * 3.4)
                val p2 = 0.30 * sin(2.0 * PI * 1685.0 * t) * exp(-t * 5.6)
                val p3 = 0.16 * sin(2.0 * PI * 2590.0 * t) * exp(-t * 8.8)
                val p4 = 0.09 * sin(2.0 * PI * 3710.0 * t) * exp(-t * 13.5)
                mix[start + k] += (p1 + p2 + p3 + p4) * attack * 0.78
            }
        }
        return ShortArray(total) { i -> toSample(mix[i]) }
    }

    private fun renderRefereeWhistle(longBlast: Boolean): ShortArray {
        val durationSec = if (longBlast) 0.48 else 0.90
        val total = (SAMPLE_RATE * durationSec).toInt()
        val mix = DoubleArray(total)
        val blasts = if (longBlast) {
            arrayOf(0.0 to 0.44)
        } else {
            arrayOf(0.0 to 0.22, 0.30 to 0.22, 0.60 to 0.28)
        }

        for ((startSec, lenSec) in blasts) {
            val sIdx = (startSec * SAMPLE_RATE).toInt()
            val bLen = (lenSec * SAMPLE_RATE).toInt()
            for (k in 0 until bLen) {
                val idx = sIdx + k
                if (idx >= total) break
                val t = k.toDouble() / SAMPLE_RATE
                val env = (t / 0.012).coerceAtMost(1.0) * (((lenSec - t) / 0.025).coerceIn(0.0, 1.0))
                val trill = 0.72 + 0.28 * sin(2.0 * PI * 38.0 * t)
                val carrier = 0.55 * sin(2.0 * PI * 2820.0 * t) + 0.45 * sin(2.0 * PI * 2945.0 * t)
                mix[idx] += carrier * trill * env * 0.76
            }
        }
        return ShortArray(total) { i -> toSample(mix[i]) }
    }

    private fun renderWoodClapperSingle(): ShortArray {
        val durationSec = 0.22
        val total = (SAMPLE_RATE * durationSec).toInt()
        val out = ShortArray(total)
        val rng = Random(0xFE0B11)
        for (k in 0 until total) {
            val t = k.toDouble() / SAMPLE_RATE
            val clickTransient = rng.nextDouble(-1.0, 1.0) * exp(-t * 140.0) * 0.55
            val woodMode1 = sin(2.0 * PI * 2150.0 * t) * exp(-t * 32.0) * 0.48
            val woodMode2 = sin(2.0 * PI * 3980.0 * t) * exp(-t * 45.0) * 0.36
            val woodBody = sin(2.0 * PI * 1120.0 * t) * exp(-t * 24.0) * 0.25
            val attack = (t / 0.0015).coerceAtMost(1.0)
            out[k] = toSample((clickTransient + woodMode1 + woodMode2 + woodBody) * attack * 0.85)
        }
        return out
    }

    private fun renderWoodClapperTriple(): ShortArray {
        val durationSec = 1.20
        val total = (SAMPLE_RATE * durationSec).toInt()
        val mix = DoubleArray(total)
        val rng = Random(0xFE0B11)
        val strikeOffsetsSec = doubleArrayOf(0.01, 0.42, 0.83)

        for (offsetSec in strikeOffsetsSec) {
            val startIdx = (offsetSec * SAMPLE_RATE).toInt()
            val strikeSamples = (0.26 * SAMPLE_RATE).toInt()
            for (k in 0 until strikeSamples) {
                val idx = startIdx + k
                if (idx >= total) break
                val t = k.toDouble() / SAMPLE_RATE
                val clickTransient = rng.nextDouble(-1.0, 1.0) * exp(-t * 140.0) * 0.55
                val woodMode1 = sin(2.0 * PI * 2150.0 * t) * exp(-t * 32.0) * 0.48
                val woodMode2 = sin(2.0 * PI * 3980.0 * t) * exp(-t * 45.0) * 0.36
                val woodBody = sin(2.0 * PI * 1120.0 * t) * exp(-t * 24.0) * 0.25
                val attack = (t / 0.0015).coerceAtMost(1.0)
                mix[idx] += (clickTransient + woodMode1 + woodMode2 + woodBody) * attack
            }
        }
        return ShortArray(total) { i -> toSample(mix[i] * 0.85) }
    }

    private fun renderRoundEndAlarm(): ShortArray {
        val durationSec = 2.25
        val total = (SAMPLE_RATE * durationSec).toInt()
        val mix = DoubleArray(total)
        val groupStarts = doubleArrayOf(0.0, 0.76, 1.52)
        val subPulses = doubleArrayOf(0.0, 0.18)

        for (group in groupStarts) {
            for (sub in subPulses) {
                val startIdx = ((group + sub) * SAMPLE_RATE).toInt()
                val pulseLen = (0.14 * SAMPLE_RATE).toInt()
                for (k in 0 until pulseLen) {
                    val idx = startIdx + k
                    if (idx >= total) break
                    val t = k.toDouble() / SAMPLE_RATE
                    val env = (t / 0.004).coerceAtMost(1.0) * (((0.14 - t) / 0.015).coerceIn(0.0, 1.0))
                    val tone = 0.68 * sin(2.0 * PI * 3200.0 * t) + 0.32 * sin(2.0 * PI * 1600.0 * t)
                    mix[idx] += tone * env
                }
            }
        }
        return ShortArray(total) { i -> toSample(mix[i] * 0.84) }
    }

    private fun toSample(normalized: Double): Short {
        val clamped = normalized.coerceIn(-0.98, 0.98)
        return (clamped * Short.MAX_VALUE).toInt().toShort()
    }

    fun encodePcm16MonoWav(samples: ShortArray): ByteArray {
        val dataBytes = samples.size * 2
        val buffer = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + dataBytes)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(1)
        buffer.putInt(SAMPLE_RATE)
        buffer.putInt(SAMPLE_RATE * 2)
        buffer.putShort(2)
        buffer.putShort(16)
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataBytes)
        for (sample in samples) {
            buffer.putShort(sample)
        }
        return buffer.array()
    }
}
