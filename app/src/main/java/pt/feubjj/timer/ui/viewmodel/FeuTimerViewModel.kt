package pt.feubjj.timer.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import pt.feubjj.timer.audio.TimerSoundPlayer
import pt.feubjj.timer.data.SharedPreferencesTimerStorage
import pt.feubjj.timer.network.ScoreboardLanServer

/**
 * Lifecycle-aware Android [AndroidViewModel] wrapping [FeuTimerSessionController]
 * and hosting the local Wi-Fi [ScoreboardLanServer] (`:7777`) for mobile referee control.
 */
class FeuTimerViewModel(application: Application) : AndroidViewModel(application) {

    val controller = FeuTimerSessionController(
        storage = SharedPreferencesTimerStorage(application),
        soundPlayer = TimerSoundPlayer(application),
        scope = viewModelScope
    )

    private val refereeHtmlBytes: ByteArray by lazy {
        application.assets.open("referee/index.html").use { it.readBytes() }
    }

    private val lanServer = ScoreboardLanServer(
        controller = controller,
        refereeHtmlProvider = { refereeHtmlBytes },
        scope = viewModelScope
    )

    val uiState: StateFlow<FeuTimerUiState> = controller.uiState

    init {
        lanServer.start()
    }

    override fun onCleared() {
        lanServer.stop()
        controller.dispose()
        super.onCleared()
    }
}
