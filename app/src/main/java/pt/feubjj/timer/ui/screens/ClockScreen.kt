package pt.feubjj.timer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import pt.feubjj.timer.ui.components.formatCurrentTime
import pt.feubjj.timer.ui.theme.FeuColors
import pt.feubjj.timer.ui.theme.FeuTypography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * High-legibility Idle Academy Wall Clock screen (`24h` or `12h AM/PM`),
 * using bold header labels and colossal tabular digits readable across the entire gym.
 */
@Composable
fun ClockScreen(
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

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(id = R.string.brand_tatami_mode).uppercase(),
            style = FeuTypography.Eyebrow.copy(
                color = FeuColors.FeuRed,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.8.sp
            )
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = dateText,
            style = FeuTypography.Eyebrow.copy(
                color = FeuColors.GiRestIvory,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.0.sp
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = timeText,
            style = FeuTypography.GiantClock.copy(
                fontSize = if (use24HourClock) 166.sp else 128.sp
            ),
            maxLines = 1
        )
    }
}
