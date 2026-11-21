package pt.feubjj.timer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.feubjj.timer.R
import pt.feubjj.timer.domain.model.MatchScoreState
import pt.feubjj.timer.domain.model.TimerPhase
import pt.feubjj.timer.domain.model.TimerSnapshot
import pt.feubjj.timer.domain.presets.PresetCatalog
import pt.feubjj.timer.ui.components.TvFocusableSurface
import pt.feubjj.timer.ui.theme.FeuColors
import pt.feubjj.timer.ui.theme.FeuTypography
import pt.feubjj.timer.ui.viewmodel.ScoreMetricKind

/**
 * IBJJF Competition Scoreboard + Match Clock synced live with the Wi-Fi Mobile Portrait Referee Console.
 * Displays live custom athlete names, optional division pill, giant points, advantages, penalties, and TV controls.
 */
@Composable
fun ScoreboardScreen(
    score: MatchScoreState,
    timer: TimerSnapshot,
    selectedDurationSeconds: Long,
    onSelectDuration: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onResetMatch: () -> Unit,
    onAdjustTime: (Long) -> Unit,
    onAdjustScore: (isFighterA: Boolean, kind: ScoreMetricKind, delta: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { playFocus.requestFocus() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 36.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top IBJJF Belt Duration Picker + Optional Division Pill + Match Clock + Reset
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                PresetCatalog.ibjjfBeltDurationsSeconds.forEach { (label, secs) ->
                    val selected = secs == selectedDurationSeconds
                    TvFocusableSurface(
                        onClick = { onSelectDuration(secs) },
                        shape = RoundedCornerShape(999.dp),
                        containerColor = if (selected) FeuColors.FeuRedPrimary else FeuColors.CardSurfaceHover,
                        focusedContainerColor = FeuColors.GiWhite,
                        borderColor = if (selected) Color.White else FeuColors.HairlineStrong,
                        focusedBorderColor = FeuColors.FeuRed,
                        modifier = Modifier.height(38.dp)
                    ) { isFocused ->
                        Text(
                            text = label,
                            maxLines = 1,
                            softWrap = false,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 14.sp,
                                color = if (isFocused) FeuColors.TatamiBlack else FeuColors.GiWhite,
                                fontFeatureSettings = FeuTypography.TABULAR_FEATURES
                            ),
                            modifier = Modifier.padding(horizontal = 9.dp)
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ScoreStepBtn(label = "−") { onAdjustTime(-10L) }
                Text(
                    text = timer.formattedRemaining,
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Black,
                        fontSize = 46.sp,
                        fontFeatureSettings = FeuTypography.TABULAR_FEATURES,
                        color = if (timer.phase == TimerPhase.PAUSED) FeuColors.CrownGold else FeuColors.GiWhite
                    )
                )
                ScoreStepBtn(label = "+") { onAdjustTime(10L) }
                val paused = timer.phase == TimerPhase.PAUSED || timer.phase == TimerPhase.FINISHED
                TvFocusableSurface(
                    onClick = onTogglePlayPause,
                    shape = CircleShape,
                    containerColor = FeuColors.GiWhite,
                    focusedContainerColor = FeuColors.FeuRed,
                    borderColor = Color.White,
                    focusedBorderColor = FeuColors.GiWhite,
                    modifier = Modifier
                        .size(46.dp)
                        .focusRequester(playFocus)
                ) { isFocused ->
                    Icon(
                        imageVector = if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        contentDescription = null,
                        tint = if (isFocused) FeuColors.GiWhite else FeuColors.TatamiBlack,
                        modifier = Modifier.size(28.dp)
                    )
                }
                TvFocusableSurface(
                    onClick = onResetMatch,
                    shape = RoundedCornerShape(999.dp),
                    containerColor = FeuColors.CardSurfaceHover,
                    focusedContainerColor = FeuColors.GiWhite,
                    borderColor = FeuColors.HairlineStrong,
                    focusedBorderColor = FeuColors.FeuRed,
                    modifier = Modifier.height(42.dp)
                ) { isFocused ->
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = if (isFocused) FeuColors.TatamiBlack else FeuColors.GiWhite,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(id = R.string.score_reset),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 14.5.sp,
                                color = if (isFocused) FeuColors.TatamiBlack else FeuColors.GiWhite
                            )
                        )
                    }
                }
            }
        }

        if (score.categoryLabel.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(FeuColors.CrownGold.copy(alpha = 0.18f))
                    .border(1.dp, FeuColors.CrownGold.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Text(
                    text = score.categoryLabel.uppercase(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = FeuTypography.Eyebrow.copy(fontSize = 13.sp, color = FeuColors.CrownGold)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val displayNameA = score.fighterAName.ifBlank { stringResource(id = R.string.fighter_a_name) }
        val displayNameB = score.fighterBName.ifBlank { stringResource(id = R.string.fighter_b_name) }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                FighterScorePanel(
                    name = displayNameA,
                    accentColor = FeuColors.GiWhite,
                    points = score.fighterAPoints,
                    advantages = score.fighterAAdvantages,
                    penalties = score.fighterAPenalties,
                    onAdjust = { kind, delta -> onAdjustScore(true, kind, delta) },
                    modifier = Modifier.weight(1f)
                )
                FighterScorePanel(
                    name = displayNameB,
                    accentColor = FeuColors.FeuRed,
                    points = score.fighterBPoints,
                    advantages = score.fighterBAdvantages,
                    penalties = score.fighterBPenalties,
                    onAdjust = { kind, delta -> onAdjustScore(false, kind, delta) },
                    modifier = Modifier.weight(1f)
                )
            }
            if (timer.phase == TimerPhase.LEAD_IN) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xEB141108))
                        .border(2.dp, FeuColors.CrownGold, RoundedCornerShape(999.dp))
                        .padding(horizontal = 18.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (timer.leadInCount > 0) stringResource(id = R.string.phase_prep, timer.leadInCount) else stringResource(id = R.string.phase_fight_go),
                        maxLines = 1,
                        softWrap = false,
                        style = FeuTypography.Eyebrow.copy(fontSize = 20.sp, fontWeight = FontWeight.Black, color = FeuColors.CrownGold)
                    )
                }
            }
        }
    }
}

@Composable
private fun FighterScorePanel(
    name: String,
    accentColor: Color,
    points: Int,
    advantages: Int,
    penalties: Int,
    onAdjust: (ScoreMetricKind, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(shape)
            .background(FeuColors.CardSurface)
            .border(2.dp, accentColor.copy(alpha = 0.65f), shape)
            .padding(15.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name.uppercase(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = FeuTypography.Eyebrow.copy(
                    color = accentColor,
                    fontSize = 19.sp,
                    letterSpacing = 1.0.sp
                ),
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "${stringResource(id = R.string.score_advantages)}: $advantages",
                    maxLines = 1,
                    softWrap = false,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 18.sp,
                        color = FeuColors.CrownGold,
                        fontFeatureSettings = FeuTypography.TABULAR_FEATURES
                    )
                )
                Text(
                    text = "${stringResource(id = R.string.score_penalties)}: $penalties",
                    maxLines = 1,
                    softWrap = false,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 18.sp,
                        color = FeuColors.FeuRed,
                        fontFeatureSettings = FeuTypography.TABULAR_FEATURES
                    )
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = points.toString(),
                maxLines = 1,
                softWrap = false,
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Black,
                    fontSize = 104.sp,
                    lineHeight = 104.sp,
                    fontFeatureSettings = FeuTypography.TABULAR_FEATURES,
                    color = FeuColors.GiWhite
                )
            )

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ScoreQuickBtn("+2") { onAdjust(ScoreMetricKind.POINTS, +2) }
                    ScoreQuickBtn("+3") { onAdjust(ScoreMetricKind.POINTS, +3) }
                    ScoreQuickBtn("+4") { onAdjust(ScoreMetricKind.POINTS, +4) }
                    ScoreQuickBtn("−1") { onAdjust(ScoreMetricKind.POINTS, -1) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ScoreQuickBtn("+V") { onAdjust(ScoreMetricKind.ADVANTAGES, +1) }
                    ScoreQuickBtn("−V") { onAdjust(ScoreMetricKind.ADVANTAGES, -1) }
                    ScoreQuickBtn("+P") { onAdjust(ScoreMetricKind.PENALTIES, +1) }
                    ScoreQuickBtn("−P") { onAdjust(ScoreMetricKind.PENALTIES, -1) }
                }
            }
        }
    }
}

@Composable
private fun ScoreQuickBtn(
    label: String,
    onClick: () -> Unit
) {
    TvFocusableSurface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        containerColor = FeuColors.CardSurfaceHover,
        focusedContainerColor = FeuColors.GiWhite,
        borderColor = FeuColors.HairlineStrong,
        focusedBorderColor = FeuColors.FeuRed,
        modifier = Modifier.height(40.dp)
    ) { isFocused ->
        Text(
            text = label,
            maxLines = 1,
            softWrap = false,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isFocused) FeuColors.TatamiBlack else FeuColors.GiWhite,
                fontFeatureSettings = FeuTypography.TABULAR_FEATURES
            ),
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

@Composable
private fun ScoreStepBtn(label: String, onClick: () -> Unit) {
    TvFocusableSurface(
        onClick = onClick,
        shape = RoundedCornerShape(9.dp),
        containerColor = FeuColors.CardSurfaceHover,
        focusedContainerColor = FeuColors.GiWhite,
        borderColor = FeuColors.HairlineStrong,
        focusedBorderColor = FeuColors.FeuRed,
        modifier = Modifier.size(width = 36.dp, height = 38.dp)
    ) { isFocused ->
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = if (isFocused) FeuColors.TatamiBlack else FeuColors.GiWhite
            )
        )
    }
}
