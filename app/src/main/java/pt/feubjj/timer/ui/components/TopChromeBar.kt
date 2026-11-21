package pt.feubjj.timer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import pt.feubjj.timer.R
import pt.feubjj.timer.ui.theme.FeuColors
import pt.feubjj.timer.ui.theme.FeuTypography
import pt.feubjj.timer.ui.viewmodel.TvScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Heroic FEU BJJ Top Header Bar:
 * - Left: High-visibility active session summary (`⏱ 5 × 5m / 1m`, `∞ × 5m / 1m`, `05:00`)
 * - Center: Commanding FEU BJJ Crest (`92.dp` on Clock/Active Timer, `68.dp` on grid screens) +
 *           `FEU BJJ` display wordmark + `FEU BJJ PORTUGAL · PINHAL NOVO` + stitched Black-Belt rule
 * - Right: Large real-time digital wall clock (`24h` or `12h AM/PM`) legible across the entire mat
 */
@Composable
fun TopChromeBar(
    screen: TvScreen,
    sessionBadge: String?,
    use24HourClock: Boolean,
    modifier: Modifier = Modifier
) {
    var wallTime by remember(use24HourClock) { mutableStateOf(formatCurrentTime(use24HourClock)) }
    LaunchedEffect(use24HourClock) {
        while (true) {
            wallTime = formatCurrentTime(use24HourClock)
            delay(500L)
        }
    }

    val isHeroScreen = screen == TvScreen.CLOCK || screen == TvScreen.ACTIVE_TIMER
    val crestHeight by animateDpAsState(
        targetValue = if (isHeroScreen) 112.dp else 78.dp,
        animationSpec = tween(durationMillis = 220),
        label = "feuCrestHeight"
    )
    val verticalPad = if (isHeroScreen) 10.dp else 7.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = verticalPad)
    ) {
        // Left: Active Session Summary Pill
        AnimatedVisibility(
            visible = sessionBadge != null && screen == TvScreen.ACTIVE_TIMER,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(FeuColors.CardSurfaceHover)
                    .border(1.5.dp, FeuColors.HairlineStrong, RoundedCornerShape(999.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Timer,
                    contentDescription = null,
                    tint = FeuColors.FeuRed,
                    modifier = Modifier.size(26.dp)
                )
                Text(
                    text = sessionBadge.orEmpty(),
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 23.sp,
                        fontFeatureSettings = FeuTypography.TABULAR_FEATURES,
                        color = FeuColors.GiWhite
                    )
                )
            }
        }

        // Center: Heroic FEU BJJ Crest + Academy Lockup + Black-Belt Rule
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Image(
                painter = painterResource(id = R.drawable.feu_logo_on_black),
                contentDescription = stringResource(id = R.string.app_name),
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(crestHeight)
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "FEU BJJ",
                    style = FeuTypography.DisplayBrand.copy(
                        fontSize = if (isHeroScreen) 46.sp else 36.sp
                    )
                )
                Text(
                    text = stringResource(id = R.string.brand_academy_subtitle),
                    style = FeuTypography.Eyebrow.copy(
                        color = FeuColors.CrownGold,
                        fontSize = if (isHeroScreen) 17.sp else 14.sp,
                        letterSpacing = 1.8.sp
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                FeuSignatureBeltRule(width = if (isHeroScreen) 272.dp else 220.dp)
            }
        }

        // Right: Always-visible high-contrast live wall clock
        AnimatedVisibility(
            visible = screen != TvScreen.CLOCK,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = wallTime,
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 27.sp,
                        fontFeatureSettings = FeuTypography.TABULAR_FEATURES,
                        color = FeuColors.GiWhite
                    )
                )
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = FeuColors.CrownGold,
                    modifier = Modifier.size(25.dp)
                )
            }
        }
    }
}

/**
 * Signature FEU BJJ Black Belt bar with crimson rank sleeve (`ponteira`) and 3 white degree stripes (`graus`),
 * echoing the belt tied across the official FEU BJJ crest.
 */
@Composable
private fun FeuSignatureBeltRule(width: androidx.compose.ui.unit.Dp) {
    Row(
        modifier = Modifier
            .width(width)
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .border(0.75.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(3.dp))
    ) {
        Box(
            modifier = Modifier
                .weight(0.68f)
                .fillMaxHeight()
                .background(FeuColors.BeltBodyBlack)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(0.24f)
                .fillMaxHeight()
                .background(FeuColors.FeuRedPrimary)
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(FeuColors.GiWhite)
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(0.08f)
                .fillMaxHeight()
                .background(FeuColors.BeltBodyBlack)
        )
    }
}

fun formatCurrentTime(use24HourClock: Boolean): String {
    val pattern = if (use24HourClock) "HH:mm:ss" else "h:mm:ss a"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
}
