package pt.feubjj.timer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import pt.feubjj.timer.domain.presets.RoundPresetOption
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
    firstPreset: RoundPresetOption,
    voiceEnabled: Boolean,
    onFive: () -> Unit, onVoice: () -> Unit, onQuick: () -> Unit,
    onPreset: () -> Unit, onWatch: () -> Unit, onCompetition: () -> Unit,
    onIntervals: () -> Unit, onRepeat: (() -> Unit)?, onSave: () -> Unit,
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
                fontSize = if (use24HourClock) 96.sp else 80.sp
            ),
            maxLines = 1
        )
        val watchFocus = remember { FocusRequester() }
        val rows = listOf(
            listOf("5:00" to onFive, (stringResource(R.string.nav_voice_short) + if (voiceEnabled) " ON" else " OFF") to onVoice, stringResource(R.string.nav_quick_timers) to onQuick),
            listOf(presetTitleText(firstPreset) to onPreset, stringResource(R.string.phase_stopwatch) to onWatch, stringResource(R.string.nav_scoreboard) to onCompetition),
            listOf("8 × 3:00 / 2:00" to onIntervals, stringResource(R.string.quick_repeat_label) to onRepeat, stringResource(R.string.save_ok) to onSave)
        )
        rows.forEachIndexed { row, items ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items.forEachIndexed { col, (label, action) ->
                    val cell = Modifier.width(230.dp)
                    if (action == null) Spacer(cell) else OptionPill(label, false, action, if (row == 1 && col == 1) cell.focusRequester(watchFocus) else cell)
                }
            }
            Spacer(Modifier.height(3.dp))
        }
        LaunchedEffect(Unit) { watchFocus.requestFocus() }
    }
}
