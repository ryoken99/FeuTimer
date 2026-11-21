package pt.feubjj.timer.audio

/**
 * Abstraction over Android's [android.media.SoundPool] so [pt.feubjj.timer.ui.viewmodel.FeuTimerSessionController]
 * can be unit-tested deterministically on the JVM without Android audio hardware.
 */
interface SoundPlayerPort {
    fun play(
        cue: SoundCue,
        pack: SoundPack = SoundPack.CLASSIC_HORN,
        voiceEnabled: Boolean = true
    )
    fun stopAll()
    fun release()
}
