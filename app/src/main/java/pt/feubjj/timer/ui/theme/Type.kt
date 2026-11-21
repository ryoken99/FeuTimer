package pt.feubjj.timer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography system tuned for 10-foot to 25-foot Android TV legibility across a BJJ academy mat.
 * Uses `tnum` (tabular numerals) on all clocks, badges, and timers so digits never shift width.
 */
object FeuTypography {

    const val TABULAR_FEATURES = "tnum"

    val DisplayBrand = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        letterSpacing = 0.6.sp,
        color = FeuColors.GiWhite
    )

    val Eyebrow = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 14.sp,
        letterSpacing = 1.6.sp,
        color = FeuColors.TextMuted
    )

    val GiantClock = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 166.sp,
        letterSpacing = (-2.5).sp,
        fontFeatureSettings = TABULAR_FEATURES,
        color = FeuColors.GiWhite
    )

    val ColossalTimer = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 224.sp,
        lineHeight = 224.sp,
        letterSpacing = (-3.5).sp,
        fontFeatureSettings = TABULAR_FEATURES,
        color = FeuColors.GiWhite
    )

    val Material = Typography(
        headlineMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 28.sp,
            letterSpacing = (-0.3).sp,
            color = FeuColors.GiWhite
        ),
        titleMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = FeuColors.GiWhite
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = FeuColors.TextMuted
        ),
        labelLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp,
            letterSpacing = 0.4.sp,
            color = FeuColors.GiWhite
        )
    )
}
