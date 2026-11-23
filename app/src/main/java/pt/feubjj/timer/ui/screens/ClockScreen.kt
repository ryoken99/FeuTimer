package pt.feubjj.timer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import pt.feubjj.timer.R
import pt.feubjj.timer.ui.components.HomeWheelDisc
import pt.feubjj.timer.ui.components.formatCurrentTime
import pt.feubjj.timer.ui.input.HomeShortcut
import pt.feubjj.timer.ui.theme.FeuColors
import pt.feubjj.timer.ui.theme.FeuTypography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Idle Academy home screen: the remote wheel (each D-pad direction and OK is a direct shortcut) beside the
 * high-legibility wall clock (`24h` or `12h AM/PM`).
 */
@Composable
fun ClockScreen(
    pressedShortcut: HomeShortcut?,
    onShortcut: (HomeShortcut) -> Unit,
    use24HourClock: Boolean,
    modifier: Modifier = Modifier
) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(250L)
        }
    }

    val timeText = remember(now.time / 1000L, use24HourClock) {
        formatCurrentTime(use24HourClock)
    }
    val dateText = remember(now.time / 60_000L) {
        SimpleDateFormat("EEEE · d MMMM yyyy", Locale.getDefault())
            .format(now)
            .uppercase(Locale.getDefault())
    }

    // Clock stays big and centred on the screen; the remote wheel sits in the bottom-left corner (legend is in the bottom bar)
    Box(
        modifier = modifier.fillMaxSize().padding(horizontal = 36.dp),
        contentAlignment = Alignment.Center
    ) {
        HomeWheelDisc(
            pressed = pressedShortcut,
            onShortcut = onShortcut,
            modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 6.dp)
        )
        // Digits are sized to leave the wheel clear on both sides of the centred clock (24h ~4.45 em wide, 12h ~5.4 em)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val available = maxWidth.value
            val digitSize = if (use24HourClock) minOf(134f, available / 6.6f) else minOf(104f, available / 8.5f)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(id = R.string.brand_tatami_mode).uppercase(),
                    maxLines = 1,
                    softWrap = false,
                    style = FeuTypography.Eyebrow.copy(
                        color = FeuColors.FeuRed,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.8.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dateText,
                    maxLines = 1,
                    softWrap = false,
                    style = FeuTypography.Eyebrow.copy(
                        color = FeuColors.GiRestIvory,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.6.sp
                    )
                )
                Text(
                    text = timeText,
                    // Tight line height (as on the web TV preview) keeps the digits compact
                    style = FeuTypography.GiantClock.copy(
                        fontSize = digitSize.sp,
                        lineHeight = (digitSize * 0.96f).sp
                    ),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
