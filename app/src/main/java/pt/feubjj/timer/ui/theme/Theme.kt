package pt.feubjj.timer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val FeuDarkScheme = darkColorScheme(
    primary = FeuColors.FeuRed,
    onPrimary = FeuColors.GiWhite,
    secondary = FeuColors.CrownGold,
    onSecondary = FeuColors.TatamiBlack,
    tertiary = FeuColors.RestEmerald,
    background = FeuColors.TatamiBlack,
    onBackground = FeuColors.GiWhite,
    surface = FeuColors.TatamiElevated,
    onSurface = FeuColors.GiWhite,
    surfaceVariant = FeuColors.CardSurface,
    onSurfaceVariant = FeuColors.TextMuted,
    outline = FeuColors.HairlineStrong
)

@Composable
fun FeuTimerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FeuDarkScheme,
        typography = FeuTypography.Material,
        content = content
    )
}
