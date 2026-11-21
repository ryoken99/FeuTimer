package pt.feubjj.timer

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import pt.feubjj.timer.ui.FeuTimerAppRoot
import pt.feubjj.timer.ui.input.RemoteGlobalAction
import pt.feubjj.timer.ui.input.RemoteKeyClassifier
import pt.feubjj.timer.ui.viewmodel.FeuTimerViewModel
import pt.feubjj.timer.ui.viewmodel.TvScreen

/**
 * Main Android TV & Android Box Activity hosting the FEU BJJ Tatami Timer.
 * Keeps the television display awake (`FLAG_KEEP_SCREEN_ON`) and normalizes D-pad, HDMI-CEC,
 * 2.4 GHz USB / Air-Mouse, Bluetooth media, and gamepad remote keys.
 */
class MainActivity : ComponentActivity() {

    private val timerViewModel: FeuTimerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (!timerViewModel.controller.onBackPressed()) {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        )

        setContent {
            FeuTimerAppRoot(controller = timerViewModel.controller)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (event?.isCanceled == true) return super.onKeyUp(keyCode, event)

        when (RemoteKeyClassifier.classifyGlobalAction(keyCode)) {
            RemoteGlobalAction.BACK -> {
                if (keyCode != KeyEvent.KEYCODE_BACK) {
                    // ESCAPE (USB / CEC remotes) and BUTTON_B (gamepads) route through Back navigation
                    onBackPressedDispatcher.onBackPressed()
                    return true
                }
            }
            RemoteGlobalAction.TOGGLE_PLAY_PAUSE -> {
                timerViewModel.controller.togglePlayPause()
                return true
            }
            RemoteGlobalAction.PAUSE -> {
                timerViewModel.controller.pauseTimer()
                return true
            }
            RemoteGlobalAction.RESUME -> {
                timerViewModel.controller.resumeTimer()
                return true
            }
            RemoteGlobalAction.STOP_TO_CLOCK -> {
                timerViewModel.controller.returnToClock()
                return true
            }
            RemoteGlobalAction.SKIP_NEXT -> {
                timerViewModel.controller.skipActivePhase()
                return true
            }
            RemoteGlobalAction.SKIP_PREVIOUS -> {
                timerViewModel.controller.previousActiveRound()
                return true
            }
            RemoteGlobalAction.FAST_FORWARD_10S -> {
                timerViewModel.controller.adjustActiveTime(+10L)
                return true
            }
            RemoteGlobalAction.REWIND_10S -> {
                timerViewModel.controller.adjustActiveTime(-10L)
                return true
            }
            RemoteGlobalAction.NONE -> Unit
        }

        // Fallback: if on Active Timer and focus was cleared (e.g. after moving a USB Air-Mouse cursor),
        // pressing OK / Enter / Space still pauses or resumes immediately.
        if (timerViewModel.uiState.value.screen == TvScreen.ACTIVE_TIMER &&
            RemoteKeyClassifier.isConfirmKeyCode(keyCode)
        ) {
            timerViewModel.controller.togglePlayPause()
            return true
        }

        return super.onKeyUp(keyCode, event)
    }
}
