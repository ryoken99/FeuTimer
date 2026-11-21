package pt.feubjj.timer.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import pt.feubjj.timer.ui.screens.OptionPill
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.feubjj.timer.BuildConfig
import pt.feubjj.timer.R
import pt.feubjj.timer.domain.model.SoundPreferences
import pt.feubjj.timer.domain.model.TimerPhase
import pt.feubjj.timer.network.QrMatrixEncoder
import pt.feubjj.timer.ui.theme.FeuColors
import pt.feubjj.timer.ui.theme.FeuTypography
import pt.feubjj.timer.ui.viewmodel.TvScreen

/**
 * Overscan-safe, zero-cutoff 10-foot bottom TV dock (`960 × 540` reference grid):
 * - On `CLOCK`: Compact primary mode pills (`Tempos`, `Rondas`, `Placar`, `↻ <last>`) + `Voz` toggle + `⚙` Settings.
 * - On `SCOREBOARD`: `◀` Back + Prominent Scannable QR & Shortest Wi-Fi URL Banner (`📱 Controlo Telemóvel: 192.168.x.x:7777`).
 * - On `ACTIVE_TIMER`: Circular media controls (`◀`, `⏸/▶`, `↺`, `−10s`, `+10s`, `⏭`).
 */
@Composable
fun BottomControlBar(
    screen: TvScreen,
    timerPhase: TimerPhase,
    isMultiRoundActive: Boolean,
    soundPreferences: SoundPreferences,
    lanRefereeUrl: String?,
    lanConnectedClients: Int,
    savedResultsCount: Int,
    onOpenRounds: () -> Unit,
    onOpenScoreboard: () -> Unit,
    onOpenSettings: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onRestartPhase: () -> Unit,
    onAdjustTime: (Long) -> Unit,
    onSkipPhase: () -> Unit,
    onBackToClock: () -> Unit,
    onToggleVoice: () -> Unit,
    modifier: Modifier = Modifier
) {
    val defaultActionFocus = remember { FocusRequester() }

    LaunchedEffect(screen) {
        if (screen == TvScreen.CLOCK || screen == TvScreen.ACTIVE_TIMER) {
            runCatching { defaultActionFocus.requestFocus() }
        }
    }

    val isChildGrid = screen != TvScreen.CLOCK && screen != TvScreen.ACTIVE_TIMER
    val verticalPad = if (isChildGrid) 8.dp else 12.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp, vertical = verticalPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left & Center controls (constrained with weight so right side can NEVER clip off-screen)
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            when (screen) {
                TvScreen.CLOCK -> {
                    TvActionPill(
                        icon = Icons.Rounded.AllInclusive,
                        label = stringResource(id = R.string.nav_rounds),
                        isPrimary = true,
                        onClick = onOpenRounds,
                        modifier = Modifier.focusRequester(defaultActionFocus)
                    )
                    TvActionPill(
                        icon = Icons.Rounded.EmojiEvents,
                        label = stringResource(id = R.string.nav_scoreboard),
                        isPrimary = false,
                        onClick = onOpenScoreboard
                    )
                }

                TvScreen.ACTIVE_TIMER -> {
                    CircularMediaIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(id = R.string.nav_back_clock),
                        size = 48.dp,
                        isProminent = false,
                        onClick = onBackToClock
                    )
                    if (timerPhase != TimerPhase.FINISHED) {
                        val isPaused = timerPhase == TimerPhase.PAUSED
                        CircularMediaIconButton(
                            icon = if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = stringResource(
                                id = if (isPaused) R.string.nav_resume else R.string.nav_pause
                            ),
                            size = 58.dp,
                            isProminent = true,
                            prominentColor = if (isPaused) FeuColors.CrownGold else FeuColors.GiWhite,
                            iconTint = FeuColors.TatamiBlack,
                            onClick = onTogglePlayPause,
                            modifier = Modifier.focusRequester(defaultActionFocus)
                        )
                        CircularMediaIconButton(
                            icon = Icons.Rounded.RestartAlt,
                            contentDescription = "Restart",
                            size = 48.dp,
                            isProminent = false,
                            onClick = onRestartPhase
                        )
                        CompactDeltaButton(label = "−10s", onClick = { onAdjustTime(-10L) })
                        CompactDeltaButton(label = "+10s", onClick = { onAdjustTime(+10L) })
                        if (isMultiRoundActive) {
                            CircularMediaIconButton(
                                icon = Icons.Rounded.SkipNext,
                                contentDescription = "Next",
                                size = 48.dp,
                                isProminent = false,
                                onClick = onSkipPhase
                            )
                        }
                    }
                }

                TvScreen.SCOREBOARD -> {
                    CircularMediaIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(id = R.string.nav_back_clock),
                        size = 46.dp,
                        isProminent = false,
                        onClick = onBackToClock
                    )
                    LanRefereeUrlBanner(
                        lanUrl = lanRefereeUrl,
                        connectedClients = lanConnectedClients,
                        savedResultsCount = savedResultsCount
                    )
                }

                TvScreen.QUICK_TIMERS,
                TvScreen.ROUNDS,
                TvScreen.SETTINGS -> {
                    CircularMediaIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(id = R.string.nav_back_clock),
                        size = 46.dp,
                        isProminent = false,
                        onClick = onBackToClock
                    )
                }
            }
        }

        // Right controls: Voice toggle + Settings on Clock; crisp brand signature everywhere
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            if (screen == TvScreen.ROUNDS || screen == TvScreen.SCOREBOARD) {
                SoundToggleChip(
                    label = stringResource(id = R.string.nav_voice_short),
                    enabled = soundPreferences.voiceEnabled,
                    activeIcon = Icons.AutoMirrored.Rounded.VolumeUp,
                    inactiveIcon = Icons.AutoMirrored.Rounded.VolumeOff,
                    onClick = onToggleVoice
                )
                CircularMediaIconButton(
                    icon = Icons.Rounded.Settings,
                    contentDescription = stringResource(id = R.string.nav_settings),
                    size = 46.dp,
                    isProminent = false,
                    onClick = onOpenSettings
                )
            }

            if (screen == TvScreen.SETTINGS) {
                val context = LocalContext.current
                OptionPill(stringResource(R.string.nav_update), selected = false, onClick = {
                    val url = Uri.parse("https://github.com/ryoken99/FeuTimer/releases/latest/download/FeuTimer-AndroidTV.apk")
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, R.string.update_browser_required, Toast.LENGTH_LONG).show()
                    }
                })
            }
            val versionText = stringResource(id = R.string.version_label, BuildConfig.VERSION_NAME)
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = FeuColors.CrownGold, fontWeight = FontWeight.ExtraBold)) {
                        append("FEU BJJ")
                    }
                    withStyle(SpanStyle(color = FeuColors.TextMuted, fontWeight = FontWeight.Bold)) {
                        append(" · $versionText")
                    }
                },
                maxLines = 1,
                softWrap = false,
                style = FeuTypography.Eyebrow.copy(
                    fontSize = 13.5.sp,
                    letterSpacing = 1.2.sp
                ),
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun LanRefereeUrlBanner(
    lanUrl: String?,
    connectedClients: Int,
    savedResultsCount: Int
) {
    val pillShape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .clip(pillShape)
            .background(FeuColors.CardSurfaceHover)
            .border(1.5.dp, FeuColors.CrownGold.copy(alpha = 0.72f), pillShape)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        if (!lanUrl.isNullOrBlank()) {
            QrCodeBadge(url = "http://$lanUrl", size = 38.dp)
        }
        Text(
            text = stringResource(id = R.string.lan_remote_label),
            maxLines = 1,
            softWrap = false,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = FeuColors.GiRestIvory
            )
        )
        Text(
            text = lanUrl ?: stringResource(id = R.string.lan_remote_offline),
            maxLines = 1,
            softWrap = false,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = FeuColors.CrownGold,
                fontFeatureSettings = FeuTypography.TABULAR_FEATURES
            )
        )
        if (connectedClients > 0) {
            Text(
                text = stringResource(id = R.string.lan_connected_badge, connectedClients),
                maxLines = 1,
                softWrap = false,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FeuColors.RestEmerald
                )
            )
        }
        if (savedResultsCount > 0) {
            Text(
                text = stringResource(id = R.string.lan_saved_count, savedResultsCount),
                maxLines = 1,
                softWrap = false,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = FeuColors.GiWhite
                )
            )
        }
    }
}

@Composable
fun QrCodeBadge(
    url: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val matrix = remember(url) { QrMatrixEncoder.encodeUrl(url) }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White)
            .padding(2.5.dp)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val n = matrix.size
            if (n <= 0) return@Canvas
            val cell = this.size.minDimension / n.toFloat()
            for (r in 0 until n) {
                for (c in 0 until n) {
                    if (matrix[r][c]) {
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(c * cell, r * cell),
                            size = Size(cell + 0.35f, cell + 0.35f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CircularMediaIconButton(
    icon: ImageVector,
    contentDescription: String,
    size: Dp,
    isProminent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    prominentColor: Color = FeuColors.GiWhite,
    iconTint: Color = FeuColors.TatamiBlack
) {
    TvFocusableSurface(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = CircleShape,
        containerColor = if (isProminent) prominentColor else FeuColors.CardSurfaceHover,
        focusedContainerColor = if (isProminent) prominentColor else FeuColors.FeuRedPrimary,
        borderColor = if (isProminent) Color.White else FeuColors.HairlineStrong,
        focusedBorderColor = if (isProminent) FeuColors.FeuRed else FeuColors.GiWhite,
        glowColor = FeuColors.FeuRed,
        focusScale = 1.08f
    ) { _ ->
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isProminent) iconTint else FeuColors.GiWhite,
            modifier = Modifier.size(if (isProminent) 31.dp else 23.dp)
        )
    }
}

@Composable
private fun CompactDeltaButton(
    label: String,
    onClick: () -> Unit
) {
    TvFocusableSurface(
        onClick = onClick,
        modifier = Modifier.height(46.dp),
        shape = RoundedCornerShape(999.dp),
        containerColor = FeuColors.CardSurfaceHover,
        focusedContainerColor = FeuColors.FeuRedPrimary,
        borderColor = FeuColors.HairlineStrong,
        focusedBorderColor = FeuColors.GiWhite
    ) { _ ->
        Text(
            text = label,
            maxLines = 1,
            softWrap = false,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFeatureSettings = FeuTypography.TABULAR_FEATURES
            ),
            modifier = Modifier.padding(horizontal = 15.dp)
        )
    }
}

@Composable
private fun TvActionPill(
    icon: ImageVector,
    label: String,
    isPrimary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = FeuColors.FeuRedPrimary
) {
    TvFocusableSurface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(999.dp),
        containerColor = if (isPrimary) accentColor.copy(alpha = 0.90f) else FeuColors.CardSurfaceHover,
        focusedContainerColor = FeuColors.GiWhite,
        borderColor = if (isPrimary) Color.White.copy(alpha = 0.36f) else FeuColors.HairlineStrong,
        focusedBorderColor = FeuColors.FeuRed,
        glowColor = accentColor
    ) { isFocused ->
        val contentColor = if (isFocused) FeuColors.TatamiBlack else FeuColors.GiWhite
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 15.5.sp,
                    color = contentColor,
                    fontFeatureSettings = FeuTypography.TABULAR_FEATURES
                )
            )
        }
    }
}

@Composable
private fun SoundToggleChip(
    label: String,
    enabled: Boolean,
    activeIcon: ImageVector,
    inactiveIcon: ImageVector,
    onClick: () -> Unit
) {
    val dotColor by animateColorAsState(
        targetValue = if (enabled) FeuColors.FeuRed else FeuColors.TextFaint.copy(alpha = 0.55f),
        animationSpec = tween(durationMillis = 200),
        label = "soundDot"
    )
    val textColor by animateColorAsState(
        targetValue = if (enabled) FeuColors.GiWhite else FeuColors.TextMuted,
        animationSpec = tween(durationMillis = 200),
        label = "soundLabel"
    )

    TvFocusableSurface(
        onClick = onClick,
        modifier = Modifier.height(46.dp),
        shape = RoundedCornerShape(999.dp),
        containerColor = if (enabled) FeuColors.FeuRedPrimary.copy(alpha = 0.22f) else FeuColors.CardSurface,
        focusedContainerColor = FeuColors.GiWhite,
        borderColor = if (enabled) FeuColors.FeuRed.copy(alpha = 0.65f) else FeuColors.HairlineStrong,
        focusedBorderColor = FeuColors.CrownGold,
        glowColor = FeuColors.CrownGold
    ) { isFocused ->
        val fg = if (isFocused) FeuColors.TatamiBlack else textColor
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Icon(
                imageVector = if (enabled) activeIcon else inactiveIcon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                maxLines = 1,
                softWrap = false,
                style = MaterialTheme.typography.labelLarge.copy(
                    color = fg,
                    fontSize = 15.sp
                )
            )
        }
    }
}
