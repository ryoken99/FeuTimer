package pt.feubjj.timer.ui.input

import android.view.KeyEvent

enum class RemoteGlobalAction {
    NONE,
    BACK,
    TOGGLE_PLAY_PAUSE,
    PAUSE,
    RESUME,
    STOP_TO_CLOCK,
    SKIP_NEXT,
    SKIP_PREVIOUS,
    FAST_FORWARD_10S,
    REWIND_10S
}

/**
 * Universal remote keycode normalization across:
 * - Official Bluetooth / IR Android TV, Google TV, and Fire TV remotes (`DPAD_*`, `DPAD_CENTER`, `MEDIA_*`)
 * - Smart TV remotes over HDMI-CEC (Samsung Anynet+, LG Simplink, Sony Bravia Sync -> `ENTER`, `BACK`, `ESCAPE`, `MEDIA_*`)
 * - 2.4 GHz USB wireless / Air-Mouse dongles & mini keyboards (`ENTER`, `NUMPAD_ENTER`, `SPACE`, `ESCAPE`)
 * - Bluetooth gamepads (`BUTTON_A`, `BUTTON_B`, `BUTTON_START`)
 */
object RemoteKeyClassifier {

    fun isConfirmKeyCode(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_SPACE,
        KeyEvent.KEYCODE_BUTTON_A -> true
        else -> false
    }

    fun classifyGlobalAction(keyCode: Int): RemoteGlobalAction = when (keyCode) {
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_ESCAPE,
        KeyEvent.KEYCODE_BUTTON_B -> RemoteGlobalAction.BACK

        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_BUTTON_START -> RemoteGlobalAction.TOGGLE_PLAY_PAUSE

        KeyEvent.KEYCODE_MEDIA_PAUSE -> RemoteGlobalAction.PAUSE
        KeyEvent.KEYCODE_MEDIA_PLAY -> RemoteGlobalAction.RESUME
        KeyEvent.KEYCODE_MEDIA_STOP -> RemoteGlobalAction.STOP_TO_CLOCK
        KeyEvent.KEYCODE_MEDIA_NEXT,
        KeyEvent.KEYCODE_CHANNEL_UP -> RemoteGlobalAction.SKIP_NEXT
        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
        KeyEvent.KEYCODE_CHANNEL_DOWN -> RemoteGlobalAction.SKIP_PREVIOUS
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> RemoteGlobalAction.FAST_FORWARD_10S
        KeyEvent.KEYCODE_MEDIA_REWIND -> RemoteGlobalAction.REWIND_10S
        else -> RemoteGlobalAction.NONE
    }
}

/**
 * Tracks armed key-down state per focusable surface so `onClick` fires once on `ACTION_UP`
 * only if the matching `ACTION_DOWN` occurred on the same surface (preventing cross-screen `KeyUp` bleed).
 */
class SurfaceKeyPressTracker {

    private var armedKeyCode: Int = KeyEvent.KEYCODE_UNKNOWN

    fun onKeyEvent(keyCode: Int, action: Int, repeatCount: Int): Boolean {
        if (!RemoteKeyClassifier.isConfirmKeyCode(keyCode)) return false

        return when (action) {
            KeyEvent.ACTION_DOWN -> {
                if (repeatCount == 0) {
                    armedKeyCode = keyCode
                }
                false
            }
            KeyEvent.ACTION_UP -> {
                val shouldClick = (armedKeyCode == keyCode)
                armedKeyCode = KeyEvent.KEYCODE_UNKNOWN
                shouldClick
            }
            else -> {
                armedKeyCode = KeyEvent.KEYCODE_UNKNOWN
                false
            }
        }
    }

    fun reset() {
        armedKeyCode = KeyEvent.KEYCODE_UNKNOWN
    }
}
