package pt.feubjj.timer.ui.input

import android.view.KeyEvent

/**
 * Direct shortcuts of the home remote wheel: on the idle clock each D-pad direction and OK opens one destination,
 * so the on-screen wheel reads exactly like the remote in the coach's hand.
 */
enum class HomeShortcut {
    UP,
    RIGHT,
    DOWN,
    LEFT,
    OK;

    companion object {
        fun forKeyCode(keyCode: Int): HomeShortcut? = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> UP
            KeyEvent.KEYCODE_DPAD_RIGHT -> RIGHT
            KeyEvent.KEYCODE_DPAD_DOWN -> DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> LEFT
            else -> if (RemoteKeyClassifier.isConfirmKeyCode(keyCode)) OK else null
        }
    }
}
