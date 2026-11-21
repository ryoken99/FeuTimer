package pt.feubjj.timer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.ui.draw.alpha
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
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import pt.feubjj.timer.domain.presets.PresetCatalog
import pt.feubjj.timer.ui.components.TvFocusableSurface
import pt.feubjj.timer.ui.theme.FeuColors
import pt.feubjj.timer.ui.theme.FeuTypography

/**
 * High-contrast 3x5 Grid of the 15 instant countdown durations (`00:30`..`30:00`)
 * plus a 1-click Progressive Stopwatch (`00:00 ↑`) pill at the top.
 * Focused cells invert to crisp Gi-White with dark numerals for instant legibility across the mat.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun QuickTimersScreen(
    quickRepeat: Int,
    quickRestSeconds: Long,
    onAdjustRepeat: (Boolean) -> Unit,
    onAdjustRest: (Boolean) -> Unit,
    onSelectQuickTimer: (String) -> Unit,
    onStartStopwatch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val options = PresetCatalog.quickTimers
    val focusRequesters = remember { List(options.size) { FocusRequester() } }
    // Explicit hops: stopwatch -> Repetir, end of the options row -> first time (reading order, never Back)
    val repeatMinusFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { focusRequesters.first().requestFocus() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 44.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.quick_timers_title),
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp)
            )
            TvFocusableSurface(
                onClick = onStartStopwatch,
                shape = RoundedCornerShape(999.dp),
                containerColor = FeuColors.CardSurfaceHover,
                focusedContainerColor = FeuColors.GiWhite,
                borderColor = FeuColors.CrownGold.copy(alpha = 0.70f),
                focusedBorderColor = FeuColors.GiWhite,
                glowColor = FeuColors.CrownGold,
                modifier = Modifier
                    .height(48.dp)
                    .focusProperties { right = repeatMinusFocus }
            ) { isFocused ->
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Timer,
                        contentDescription = null,
                        tint = if (isFocused) FeuColors.TatamiBlack else FeuColors.CrownGold,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = stringResource(id = R.string.quick_stopwatch_btn),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 17.sp,
                            color = if (isFocused) FeuColors.TatamiBlack else FeuColors.GiWhite,
                            fontFeatureSettings = FeuTypography.TABULAR_FEATURES
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // How many times the chosen time runs, and the rest in between (applies to every time below)
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuickOptionStepper(
                label = stringResource(id = R.string.quick_repeat_label),
                valueText = "×$quickRepeat",
                onMinus = { onAdjustRepeat(false) },
                onPlus = { onAdjustRepeat(true) },
                minusFocus = repeatMinusFocus
            )
            QuickOptionStepper(
                label = stringResource(id = R.string.quick_rest_label),
                valueText = if (quickRestSeconds == 0L) stringResource(id = R.string.quick_rest_none) else TimeFormatter.formatMmSs(quickRestSeconds),
                onMinus = { onAdjustRest(false) },
                onPlus = { onAdjustRest(true) },
                plusRightFocus = focusRequesters.first(),
                modifier = Modifier.alpha(if (quickRepeat <= 1) 0.5f else 1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        val rows = options.chunked(5)
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            rows.forEachIndexed { rowIdx, rowItems ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rowItems.forEachIndexed { colIdx, item ->
                        val flatIndex = rowIdx * 5 + colIdx
                        val prevIndex = (flatIndex - 1 + options.size) % options.size
                        val nextIndex = (flatIndex + 1) % options.size

                        TvFocusableSurface(
                            onClick = { onSelectQuickTimer(item.label) },
                            shape = RoundedCornerShape(10.dp),
                            containerColor = FeuColors.CardSurface,
                            focusedContainerColor = FeuColors.GiWhite,
                            borderColor = Color.White.copy(alpha = 0.52f),
                            focusedBorderColor = FeuColors.FeuRed,
                            glowColor = FeuColors.FeuRed,
                            modifier = Modifier
                                .weight(1f)
                                .height(66.dp)
                                .focusRequester(focusRequesters[flatIndex])
                                .focusProperties {
                                    if (colIdx == 0) left = focusRequesters[prevIndex]
                                    if (colIdx == rowItems.lastIndex) right = focusRequesters[nextIndex]
                                }
                        ) { isFocused ->
                            Text(
                                text = item.label,
                                style = TextStyle(
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 34.sp,
                                    fontFeatureSettings = FeuTypography.TABULAR_FEATURES,
                                    color = if (isFocused) FeuColors.TatamiBlack else FeuColors.GiWhite
                                )
                            )
                        }
                    }
                }
            }
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
    minusFocus: FocusRequester? = null,
    plusRightFocus: FocusRequester? = null
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
        QuickStepBtn(
            icon = Icons.Rounded.Remove,
            onClick = onMinus,
            modifier = if (minusFocus != null) Modifier.focusRequester(minusFocus) else Modifier
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
            modifier = if (plusRightFocus != null) Modifier.focusProperties { right = plusRightFocus } else Modifier
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
