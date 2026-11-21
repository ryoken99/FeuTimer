package pt.feubjj.timer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import pt.feubjj.timer.R
import pt.feubjj.timer.domain.model.TimerPhase
import pt.feubjj.timer.ui.theme.FeuColors
import pt.feubjj.timer.ui.viewmodel.TvScreen

/**
 * Full-screen tatami dark canvas with controlled header aura lighting, subtle gi-cotton weave,
 * and discreet corner watermark calibrated for steep downward wall-TV viewing angles (preventing
 * LCD off-axis gamma haze while keeping deep ink-black contrast behind white numerals).
 */
@Composable
fun TatamiBackdrop(
    screen: TvScreen,
    timerPhase: TimerPhase,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val targetGlowColor = when {
        screen != TvScreen.ACTIVE_TIMER -> FeuColors.FeuRedPrimary.copy(alpha = 0.12f)
        timerPhase == TimerPhase.LEAD_IN -> FeuColors.CrownGold.copy(alpha = 0.14f)
        timerPhase == TimerPhase.RUNNING_WORK -> FeuColors.FeuRed.copy(alpha = 0.15f)
        timerPhase == TimerPhase.RUNNING_REST -> FeuColors.RestEmerald.copy(alpha = 0.14f)
        timerPhase == TimerPhase.PAUSED -> FeuColors.CrownGold.copy(alpha = 0.12f)
        timerPhase == TimerPhase.FINISHED -> FeuColors.CrownGold.copy(alpha = 0.15f)
        else -> FeuColors.FeuRedPrimary.copy(alpha = 0.12f)
    }

    val ambientGlow by animateColorAsState(
        targetValue = targetGlowColor,
        animationSpec = tween(durationMillis = 450),
        label = "tatamiAmbientGlow"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(FeuColors.TatamiBlack)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Subtle diagonal gi-cotton weave
            val step = 24.dp.toPx()
            val weaveColor = Color.White.copy(alpha = 0.010f)
            var x = -size.height
            while (x < size.width + size.height) {
                drawLine(
                    color = weaveColor,
                    start = Offset(x, 0f),
                    end = Offset(x + size.height, size.height),
                    strokeWidth = 1f
                )
                x += step
            }

            // Focused aura behind the top-center FEU BJJ crest so the center/lower mat stays pure ink black
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ambientGlow, Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.08f),
                    radius = size.maxDimension * 0.42f
                ),
                center = Offset(size.width * 0.5f, size.height * 0.08f),
                radius = size.maxDimension * 0.42f
            )
        }

        // Discreet reversed FEU mark watermark in bottom-right corner
        Image(
            painter = painterResource(id = R.drawable.feu_logo_on_dark),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .height(300.dp)
                .padding(end = 22.dp, bottom = 14.dp)
                .alpha(0.16f)
        )

        content()
    }
}
