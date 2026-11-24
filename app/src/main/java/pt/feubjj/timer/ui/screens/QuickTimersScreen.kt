package pt.feubjj.timer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.ui.draw.clip
import pt.feubjj.timer.domain.engine.TimeFormatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.feubjj.timer.R
import pt.feubjj.timer.ui.components.TvFocusableSurface
import pt.feubjj.timer.ui.theme.FeuColors
import pt.feubjj.timer.ui.theme.FeuTypography

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun QuickTimersScreen(
    onRepeat: (() -> Unit)?,
    workSeconds: Long,
    onFocusTime: (Boolean) -> Unit,
    onAdd: (Long) -> Unit,
    onSecond: () -> Unit,
    onMinute: (Boolean) -> Unit,
    onStart: (Boolean) -> Unit,
    endless: Boolean,
    onEndless: () -> Unit,
    onRestMinute: (Boolean) -> Unit,
    quickRepeat: Int,
    quickRestSeconds: Long,
    onAdjustRepeat: (Boolean) -> Unit,
    onAdjustRest: (Boolean) -> Unit,
    onStartStopwatch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val face = remember { FocusRequester() }
    val repeatFocus = remember { FocusRequester() }
    val toggleFocus = remember { FocusRequester() }
    val roundsPlus = remember { FocusRequester() }
    val firstStep = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var press by remember { mutableStateOf<Job?>(null) }
    var heldVertical by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    DisposableEffect(windowFocused) { onDispose { press?.cancel(); press = null } }
    LaunchedEffect(Unit) { face.requestFocus() }
    LaunchedEffect(expanded) { if (expanded) roundsPlus.requestFocus() }
    BackHandler(enabled = expanded) { expanded = false; toggleFocus.requestFocus() }

    Column(modifier.fillMaxSize().padding(horizontal = 36.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.nav_quick_timers), Modifier.weight(1f), fontSize = 28.sp)
            if (onRepeat != null) OptionPill(stringResource(R.string.quick_repeat_label), false, onRepeat, Modifier.focusRequester(repeatFocus).onPreviewKeyEvent { e ->
                if (e.key != Key.DirectionDown) false else {
                    if (e.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN && e.nativeKeyEvent.repeatCount == 0) {
                        if (expanded) roundsPlus.requestFocus() else expanded = true
                    }
                    true
                }
            })
            OptionPill(stringResource(R.string.quick_stopwatch_btn), false, onStartStopwatch)
        }
        Spacer(Modifier.height(8.dp))
        TvFocusableSurface(
            onClick = { onStart(false) }, onHold = { onStart(true) },
            onFocusChange = {
                onFocusTime(it)
                if (!it) { press?.cancel(); press = null }
            },
            modifier = Modifier.fillMaxWidth().height(82.dp).focusRequester(face)
                .focusProperties { down = firstStep }
                .onPreviewKeyEvent { event ->
                    val key = event.key
                    if (key != Key.DirectionLeft && key != Key.DirectionRight && key != Key.DirectionUp && key != Key.DirectionDown) return@onPreviewKeyEvent false
                    val native = event.nativeKeyEvent
                    if (native.action == android.view.KeyEvent.ACTION_DOWN && native.repeatCount == 0) {
                        press?.cancel()
                        heldVertical = false
                        if (key == Key.DirectionLeft || key == Key.DirectionRight) onMinute(key == Key.DirectionRight)
                        press = scope.launch {
                            delay(450L)
                            while (true) {
                                if (key == Key.DirectionDown) {
                                    heldVertical = true
                                    if (expanded) roundsPlus.requestFocus() else expanded = true
                                    break
                                }
                                if (key == Key.DirectionUp) { heldVertical = true; onSecond() } else onMinute(key == Key.DirectionRight)
                                delay(165L)
                            }
                        }
                    } else if (native.action == android.view.KeyEvent.ACTION_UP && press != null) {
                        press?.cancel(); press = null
                        if (!native.isCanceled && key == Key.DirectionDown && !heldVertical) firstStep.requestFocus()
                        if (!native.isCanceled && key == Key.DirectionUp && !heldVertical) {
                            if (onRepeat != null) repeatFocus.requestFocus() else firstStep.requestFocus()
                        }
                    }
                    true
                }
        ) { focused ->
            Text("▶  " + TimeFormatter.formatMmSs(workSeconds), fontSize = 64.sp, fontWeight = FontWeight.Black,
                color = if (focused) FeuColors.CrownGold else FeuColors.GiWhite)
        }
        Text(stringResource(R.string.time_guide), fontSize = 14.sp, color = FeuColors.GiWhite)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30L,60L,180L,300L,600L).forEach { step ->
                OptionPill(if (step == 30L) "+30 s" else "+${step / 60} min", false,
                    { onAdd(step); face.requestFocus() }, if (step == 30L) Modifier.focusRequester(firstStep) else Modifier)
            }
        }
        Spacer(Modifier.height(4.dp))
        val rounds = if (endless) "∞" else quickRepeat.toString()
        val total = if (endless) "∞" else TimeFormatter.formatCompactDuration(workSeconds * quickRepeat + quickRestSeconds * (quickRepeat - 1))
        val summary = if (endless || quickRepeat > 1) "   ·   " + stringResource(R.string.total_prefix, total) else ""
        OptionPill(stringResource(R.string.role_rounds) + " ×" + rounds + "   ·   " +
            stringResource(R.string.quick_rest_label) + " " + TimeFormatter.formatMmSs(quickRestSeconds) + summary + if (expanded) "   ▴" else "   ▾",
            expanded, { expanded = !expanded }, Modifier.focusRequester(toggleFocus))
        if (expanded) Row(modifier = Modifier.onPreviewKeyEvent { e ->
            if (e.key != Key.DirectionUp) false else {
                if (e.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) face.requestFocus()
                true
            }
        }, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OptionPill("∞", endless, onEndless)
            QuickOptionStepper(stringResource(R.string.role_rounds), "×$rounds", { onAdjustRepeat(false) },
                { onAdjustRepeat(true) }, plusModifier = Modifier.focusRequester(roundsPlus))
            QuickOptionStepper(stringResource(R.string.quick_rest_label), TimeFormatter.formatMmSs(quickRestSeconds),
                { onAdjustRest(false) }, { onAdjustRest(true) },
                leadingAction = { OptionPill("−1m", false, { onRestMinute(false) }) })
            OptionPill("+1m", false, { onRestMinute(true) })
        }
    }
}

@Composable
private fun QuickOptionStepper(
    label: String,
    valueText: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    plusModifier: Modifier = Modifier,
    leadingAction: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(FeuColors.CardSurface)
            .border(1.25.dp, Color.White.copy(alpha = 0.45f), shape)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = label.uppercase(),
            maxLines = 1,
            softWrap = false,
            style = FeuTypography.Eyebrow.copy(color = FeuColors.CrownGold, fontSize = 13.sp, letterSpacing = 1.0.sp)
        )
        leadingAction?.invoke()
        QuickStepBtn(
            icon = Icons.Rounded.Remove,
            onClick = onMinus
        )
        Text(
            text = valueText,
            maxLines = 1,
            softWrap = false,
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Black,
                fontSize = 26.sp,
                fontFeatureSettings = FeuTypography.TABULAR_FEATURES,
                color = FeuColors.GiWhite
            ),
            modifier = Modifier.widthIn(min = 72.dp)
        )
        QuickStepBtn(
            icon = Icons.Rounded.Add,
            onClick = onPlus,
            modifier = plusModifier
        )
    }
}

@Composable
private fun QuickStepBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TvFocusableSurface(
        repeatOnHold = true,
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        containerColor = FeuColors.CardSurfaceHover,
        focusedContainerColor = FeuColors.GiWhite,
        borderColor = FeuColors.HairlineStrong,
        focusedBorderColor = FeuColors.FeuRed,
        modifier = modifier.size(width = 44.dp, height = 38.dp)
    ) { isFocused ->
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isFocused) FeuColors.TatamiBlack else FeuColors.GiWhite,
            modifier = Modifier.size(22.dp)
        )
    }
}
