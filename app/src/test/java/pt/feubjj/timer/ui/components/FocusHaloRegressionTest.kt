package pt.feubjj.timer.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Regression guard for the v1.0.0 TV bug: a huge red CIRCLE was drawn under every focused option
 * (`drawCircle(radius = size.maxDimension * 0.62f)`). The focus halo must follow each control's own
 * shape (pill / card / circle), so focusable surfaces may never draw a bare circle behind themselves.
 */
class FocusHaloRegressionTest {

    private val uiDir = File("src/main/java/pt/feubjj/timer/ui")

    @Test
    fun `focusable surface halo follows the control shape and never draws a circle`() {
        val src = File(uiDir, "components/TvFocusableSurface.kt").readText()
        assertFalse("TvFocusableSurface must not call drawCircle", src.contains("drawCircle"))
        assertTrue("Halo must be built from the control's own outline", src.contains("shape.createOutline"))
    }

    @Test
    fun `no UI component or screen draws a stray circle glow`() {
        val offenders = uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("drawCircle") }
            .map { it.name }
            .toList()
        // TatamiBackdrop draws the single ambient background aura on purpose; nothing else may.
        assertTrue("Unexpected drawCircle in: $offenders", offenders.all { it == "TatamiBackdrop.kt" })
    }
}
