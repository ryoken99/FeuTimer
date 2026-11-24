package pt.feubjj.timer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.graphics.lerp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.delay
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pt.feubjj.timer.ui.input.RemoteKeyClassifier
import pt.feubjj.timer.ui.input.SurfaceKeyPressTracker
import pt.feubjj.timer.ui.theme.FeuColors

/**
 * Universal TV + Android Box focusable surface:
 * - Uses a SINGLE [focusable] node (no duplicate `.clickable()` FocusTargetNode) so every D-pad arrow moves cleanly in 1 press,
 *   and remains focusable even when a 2.4 GHz USB Air-Mouse dongle places Android into Touch Mode.
 * - Accepts `DPAD_CENTER`, `ENTER`, `NUMPAD_ENTER`, `SPACE`, and `BUTTON_A` via [SurfaceKeyPressTracker]
 *   plus pointer/Air-Mouse/touch taps via [detectTapGestures].
 */
@Composable
fun TvFocusableSurface(
    onClick: () -> Unit,
    onHold: (() -> Unit)? = null,
    repeatOnHold: Boolean = false,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    containerColor: Color = FeuColors.CardSurface,
    focusedContainerColor: Color = FeuColors.CardSurfaceHover,
    borderColor: Color = FeuColors.Hairline,
    focusedBorderColor: Color = FeuColors.FeuRed,
    glowColor: Color = FeuColors.FeuRed,
    focusScale: Float = 1.035f,
    contentAlignment: Alignment = Alignment.Center,
    onFocusChange: ((Boolean) -> Unit)? = null,
    pulse: Boolean = false,
    pulseColor: Color = FeuColors.CrownGold,
    content: @Composable BoxScope.(isFocused: Boolean) -> Unit
) {
    val latestHold by rememberUpdatedState(onHold)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnFocusChange by rememberUpdatedState(onFocusChange)


    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val keyTracker = remember { SurfaceKeyPressTracker() }
    var holding by remember { mutableStateOf(false) }
    var repeated by remember { mutableStateOf(false) }

    LaunchedEffect(holding, isFocused) {
        if (holding && isFocused) {
            delay(android.view.ViewConfiguration.getLongPressTimeout().toLong())
            while (holding) {
                repeated = true
                currentOnClick()
                delay(200L)
            }
        }
    }
    LaunchedEffect(isFocused) {
        if (!isFocused) { keyTracker.reset(); holding = false }
        currentOnFocusChange?.invoke(isFocused)
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) focusScale else 1.0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "tvFocusScale"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (isFocused) 0.60f else 0.0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "tvFocusGlow"
    )
    val bg by animateColorAsState(
        targetValue = if (isFocused) focusedContainerColor else containerColor,
        animationSpec = tween(durationMillis = 180),
        label = "tvFocusBg"
    )
    val strokeColor by animateColorAsState(
        targetValue = if (isFocused) focusedBorderColor else borderColor,
        animationSpec = tween(durationMillis = 180),
        label = "tvFocusStroke"
    )

    // Optional attention pulse (e.g. the preset slot a Guardar button is about to overwrite): grows, glows, breathes
    val pulseT = if (pulse) {
        rememberInfiniteTransition(label = "tvPulse").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "tvPulseT"
        ).value
    } else {
        0f
    }
    val shownScale = if (pulse) 1.05f + 0.04f * pulseT else scale
    val shownGlowAlpha = if (pulse) 0.45f + 0.35f * pulseT else glowAlpha
    val shownGlowColor = if (pulse) lerp(pulseColor, glowColor, pulseT) else glowColor
    val shownStroke = if (pulse) pulseColor else strokeColor
    val shownBg = if (pulse) pulseColor.copy(alpha = 0.14f) else bg

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = shownScale
                scaleY = shownScale
            }
            .drawBehind {
                // Soft halo that follows the control's OWN shape (pill, card, circle) - never a big circle behind it
                if (shownGlowAlpha > 0.005f) {
                    val outline = shape.createOutline(size, layoutDirection, this)
                    for (ring in 1..4) {
                        drawOutline(
                            outline = outline,
                            color = shownGlowColor.copy(alpha = shownGlowAlpha * (0.55f / ring)),
                            style = Stroke(width = (ring * 5).dp.toPx())
                        )
                    }
                }
            }
            .onKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (RemoteKeyClassifier.isConfirmKeyCode(native.keyCode)) {
                    if (native.action == android.view.KeyEvent.ACTION_DOWN && native.repeatCount == 0) {
                        repeated = false
                        holding = repeatOnHold
                    }
                    if (native.action == android.view.KeyEvent.ACTION_UP) holding = false
                    if (keyTracker.onKeyEvent(native.keyCode, native.action, native.repeatCount) && !repeated && !native.isCanceled) {
                        if (onHold != null && native.eventTime - native.downTime >= android.view.ViewConfiguration.getLongPressTimeout()) onHold()
                        else currentOnClick()
                    }
                    true
                } else {
                    false
                }
            }
            .focusable(interactionSource = interactionSource)
            .pointerInput(onHold != null) {
                detectTapGestures(onLongPress = if (onHold != null) ({ _ -> latestHold?.invoke() }) else null) { currentOnClick() }
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                onClick {
                    currentOnClick()
                    true
                }
            },
        shape = shape,
        color = shownBg,
        border = BorderStroke(if (isFocused || pulse) 2.dp else 1.dp, shownStroke)
    ) {
        Box(contentAlignment = contentAlignment) {
            content(isFocused)
        }
    }
}
