package pt.feubjj.timer.ui

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.feubjj.timer.ui.input.RemoteGlobalAction
import pt.feubjj.timer.ui.input.RemoteKeyClassifier
import pt.feubjj.timer.ui.input.SurfaceKeyPressTracker

class RemoteInputCompatibilityTest {

    @Test
    fun `recognizes confirm keys across Android TV, HDMI-CEC, USB Air-Mouse, and gamepad remotes`() {
        assertTrue(RemoteKeyClassifier.isConfirmKeyCode(KeyEvent.KEYCODE_DPAD_CENTER))
        assertTrue(RemoteKeyClassifier.isConfirmKeyCode(KeyEvent.KEYCODE_ENTER))
        assertTrue(RemoteKeyClassifier.isConfirmKeyCode(KeyEvent.KEYCODE_NUMPAD_ENTER))
        assertTrue(RemoteKeyClassifier.isConfirmKeyCode(KeyEvent.KEYCODE_SPACE))
        assertTrue(RemoteKeyClassifier.isConfirmKeyCode(KeyEvent.KEYCODE_BUTTON_A))

        assertFalse(RemoteKeyClassifier.isConfirmKeyCode(KeyEvent.KEYCODE_DPAD_LEFT))
        assertFalse(RemoteKeyClassifier.isConfirmKeyCode(KeyEvent.KEYCODE_VOLUME_UP))
    }

    @Test
    fun `maps global back and media transport keys from TV and universal remotes`() {
        assertEquals(RemoteGlobalAction.BACK, RemoteKeyClassifier.classifyGlobalAction(KeyEvent.KEYCODE_BACK))
        assertEquals(RemoteGlobalAction.BACK, RemoteKeyClassifier.classifyGlobalAction(KeyEvent.KEYCODE_ESCAPE))
        assertEquals(RemoteGlobalAction.BACK, RemoteKeyClassifier.classifyGlobalAction(KeyEvent.KEYCODE_BUTTON_B))

        assertEquals(
            RemoteGlobalAction.TOGGLE_PLAY_PAUSE,
            RemoteKeyClassifier.classifyGlobalAction(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        )
        assertEquals(
            RemoteGlobalAction.TOGGLE_PLAY_PAUSE,
            RemoteKeyClassifier.classifyGlobalAction(KeyEvent.KEYCODE_BUTTON_START)
        )
        assertEquals(
            RemoteGlobalAction.PAUSE,
            RemoteKeyClassifier.classifyGlobalAction(KeyEvent.KEYCODE_MEDIA_PAUSE)
        )
        assertEquals(
            RemoteGlobalAction.RESUME,
            RemoteKeyClassifier.classifyGlobalAction(KeyEvent.KEYCODE_MEDIA_PLAY)
        )
        assertEquals(
            RemoteGlobalAction.STOP_TO_CLOCK,
            RemoteKeyClassifier.classifyGlobalAction(KeyEvent.KEYCODE_MEDIA_STOP)
        )
        // Volume keys must NEVER be intercepted so TV hardware volume works normally
        assertEquals(
            RemoteGlobalAction.NONE,
            RemoteKeyClassifier.classifyGlobalAction(KeyEvent.KEYCODE_VOLUME_UP)
        )
        assertEquals(
            RemoteGlobalAction.NONE,
            RemoteKeyClassifier.classifyGlobalAction(KeyEvent.KEYCODE_VOLUME_DOWN)
        )
        assertEquals(
            RemoteGlobalAction.NONE,
            RemoteKeyClassifier.classifyGlobalAction(KeyEvent.KEYCODE_VOLUME_MUTE)
        )
    }

    @Test
    fun `surface key tracker requires matching KeyDown before firing click on KeyUp to prevent cross-screen misfire`() {
        val tracker = SurfaceKeyPressTracker()

        // Stray KeyUp (e.g. left over from pressing OK on previous screen) must NOT fire click
        assertFalse(
            tracker.onKeyEvent(
                keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
                action = KeyEvent.ACTION_UP,
                repeatCount = 0
            )
        )

        // Armed KeyDown followed by KeyUp fires click exactly once
        assertFalse(
            tracker.onKeyEvent(
                keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0
            )
        )
        // Held key repeat events do not double-arm or fire early
        assertFalse(
            tracker.onKeyEvent(
                keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 3
            )
        )
        assertTrue(
            tracker.onKeyEvent(
                keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
                action = KeyEvent.ACTION_UP,
                repeatCount = 0
            )
        )
        // Subsequent stray KeyUp without new KeyDown does not fire again
        assertFalse(
            tracker.onKeyEvent(
                keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
                action = KeyEvent.ACTION_UP,
                repeatCount = 0
            )
        )
    }
}
