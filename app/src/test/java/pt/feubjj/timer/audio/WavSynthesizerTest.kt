package pt.feubjj.timer.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavSynthesizerTest {

    @Test
    fun `synthesizes valid 16-bit mono PCM RIFF WAVE buffers for all martial arts sound cues`() {
        for (cue in SoundCue.entries) {
            val wav = WavSynthesizer.generateCueWav(cue)
            assertTrue("WAV for $cue too short", wav.size > 44)

            val riff = String(wav, 0, 4, Charsets.US_ASCII)
            val wave = String(wav, 8, 4, Charsets.US_ASCII)
            val fmt = String(wav, 12, 4, Charsets.US_ASCII)
            val dataTag = String(wav, 36, 4, Charsets.US_ASCII)

            assertEquals("RIFF", riff)
            assertEquals("WAVE", wave)
            assertEquals("fmt ", fmt)
            assertEquals("data", dataTag)

            val header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(wav.size - 8, header.getInt(4))
            assertEquals(1, header.getShort(20).toInt()) // PCM = 1
            assertEquals(1, header.getShort(22).toInt()) // Mono = 1
            assertEquals(44100, header.getInt(24))       // Sample rate = 44.1 kHz
            assertEquals(16, header.getShort(34).toInt()) // 16-bit
            val dataBytes = header.getInt(40)
            assertEquals(wav.size - 44, dataBytes)

            // Ensure non-silent audio energy
            var peak = 0
            for (offset in 44 until wav.size step 2) {
                val sample = kotlin.math.abs(header.getShort(offset).toInt())
                if (sample > peak) peak = sample
            }
            assertTrue("Expected audible peak for $cue but got $peak", peak > 8000)
        }
    }
}
