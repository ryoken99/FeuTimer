package pt.feubjj.timer.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.feubjj.timer.R
import pt.feubjj.timer.domain.model.TimerPhase
import pt.feubjj.timer.domain.model.TimerSnapshot
import pt.feubjj.timer.ui.theme.FeuColors
import pt.feubjj.timer.ui.theme.FeuTypography

/**
 * Maximum-legibility Active Timer screen calibrated for across-the-mat wall TVs:
 * - Bold, high-contrast framed Round/Phase headline (`RONDA 2 / 5`, `DESCANSO · PRÓXIMA 3/5`, `PREPARA-TE · 3`)
 * - Colossal `224.sp` Black tabular `MM:SS` numerals filling the center of the 16:9 TV frame
 */
@Composable
fun ActiveTimerScreen(
    snapshot: TimerSnapshot,
    modifier: Modifier = Modifier
) {
    val phaseAccent by animateColorAsState(
        targetValue = when (snapshot.phase) {
            TimerPhase.LEAD_IN -> if (snapshot.leadInCount == 0) FeuColors.FeuRed else FeuColors.CrownGold
            TimerPhase.RUNNING_WORK -> FeuColors.GiWhite
            TimerPhase.RUNNING_STOPWATCH -> FeuColors.CrownGold
            TimerPhase.RUNNING_REST -> FeuColors.RestEmerald
            TimerPhase.PAUSED -> FeuColors.CrownGold
            TimerPhase.FINISHED -> FeuColors.CrownGold
            TimerPhase.IDLE -> FeuColors.GiWhite
        },
        animationSpec = tween(durationMillis = 280),
        label = "activePhaseAccent"
    )

    val phasePillBorder by animateColorAsState(
        targetValue = when (snapshot.phase) {
            TimerPhase.RUNNING_WORK -> FeuColors.FeuRed.copy(alpha = 0.85f)
            TimerPhase.RUNNING_REST -> FeuColors.RestEmerald.copy(alpha = 0.85f)
            else -> FeuColors.CrownGold.copy(alpha = 0.85f)
        },
        animationSpec = tween(durationMillis = 280),
        label = "activePhasePillBorder"
    )

    val timerTextColor by animateColorAsState(
        targetValue = when (snapshot.phase) {
            TimerPhase.RUNNING_REST -> FeuColors.RestEmerald
            TimerPhase.PAUSED -> FeuColors.CrownGold
            else -> FeuColors.GiWhite
        },
        animationSpec = tween(durationMillis = 280),
        label = "timerDigitsColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "timerBlinkTransition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "timerBlinkAlpha"
    )
    val effectiveAlpha = if (snapshot.isBlinking) pulseAlpha else 1.0f

    val isEndless = snapshot.config?.isEndless == true
    val bannerText = when (snapshot.phase) {
        TimerPhase.LEAD_IN -> if (snapshot.leadInCount > 0) {
            stringResource(id = R.string.phase_prep, snapshot.leadInCount)
        } else {
            stringResource(id = R.string.phase_fight_go)
        }
        TimerPhase.RUNNING_WORK -> when {
            isEndless -> stringResource(id = R.string.phase_round_endless, snapshot.currentRound)
            snapshot.totalRounds > 0 -> stringResource(
                id = R.string.phase_round_progress,
                snapshot.currentRound,
                snapshot.totalRounds
            )
            else -> stringResource(id = R.string.phase_single_time)
        }
        TimerPhase.RUNNING_STOPWATCH -> stringResource(id = R.string.phase_stopwatch)
        TimerPhase.RUNNING_REST -> when {
            isEndless -> stringResource(id = R.string.phase_rest_next_endless, snapshot.currentRound + 1)
            snapshot.totalRounds > 0 -> stringResource(
                id = R.string.phase_rest_next,
                snapshot.currentRound + 1,
                snapshot.totalRounds
            )
            else -> stringResource(id = R.string.phase_rest)
        }
        TimerPhase.PAUSED -> stringResource(id = R.string.phase_paused)
        TimerPhase.FINISHED -> stringResource(id = R.string.phase_finished)
        TimerPhase.IDLE -> ""
    }

    val bannerFontSize = if (bannerText.length > 16) 36.sp else 44.sp
    val pillShape = RoundedCornerShape(999.dp)

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (bannerText.isNotEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(pillShape)
                    .background(FeuColors.CardSurfaceHover)
                    .border(2.dp, phasePillBorder, pillShape)
                    .padding(horizontal = 28.dp, vertical = 6.dp)
            ) {
                Text(
                    text = bannerText.uppercase(),
                    maxLines = 1,
                    style = FeuTypography.Eyebrow.copy(
                        color = phaseAccent,
                        fontSize = bannerFontSize,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.4.sp,
                        fontFeatureSettings = FeuTypography.TABULAR_FEATURES
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = snapshot.formattedRemaining,
            style = FeuTypography.ColossalTimer.copy(
                fontSize = 224.sp,
                lineHeight = 224.sp,
                color = timerTextColor
            ),
            maxLines = 1,
            modifier = Modifier.alpha(effectiveAlpha)
        )
    }
}
