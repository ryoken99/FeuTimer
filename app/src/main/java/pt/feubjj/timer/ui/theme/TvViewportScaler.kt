package pt.feubjj.timer.ui.theme

import kotlin.math.min

/**
 * Normalizes physical window pixel dimensions onto a canonical `960 × 540` 16:9 Android TV grid.
 *
 * Many Smart TVs and HDMI Android boxes misreport their physical density (e.g., `160 dpi` / `1.0x`
 * on a `1920 × 1080` panel instead of Android TV's standard `320 dpi` / `2.0x`), shrinking fixed
 * `dp` and `sp` units to 50%–67% of their intended physical size. Deriving Compose density directly
 * from the measured window pixels guarantees identical proportional sizing across 720p, 1080p, and
 * 4K panels while preventing vertical clipping on ultrawide phones and tablets.
 */
object TvViewportScaler {

    const val REFERENCE_WIDTH_DP = 960f
    const val REFERENCE_HEIGHT_DP = 540f
    private const val MIN_DENSITY = 0.5f

    fun computeTargetDensity(
        widthPx: Int,
        heightPx: Int,
        fallbackDensity: Float
    ): Float {
        if (widthPx <= 0 || heightPx <= 0) {
            return fallbackDensity.coerceAtLeast(MIN_DENSITY)
        }
        val scaleX = widthPx.toFloat() / REFERENCE_WIDTH_DP
        val scaleY = heightPx.toFloat() / REFERENCE_HEIGHT_DP
        return min(scaleX, scaleY).coerceAtLeast(MIN_DENSITY)
    }
}
