package pt.feubjj.timer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.feubjj.timer.R
import pt.feubjj.timer.ui.input.HomeShortcut
import pt.feubjj.timer.ui.theme.FeuColors
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private data class WheelWedge(
    val shortcut: HomeShortcut,
    val centerAngle: Float,
    val icon: ImageVector,
    val labelRes: Int
)

// Angles follow the canvas convention: 0 = right (3 o'clock), growing clockwise
private val wedges = listOf(
    WheelWedge(HomeShortcut.UP, -90f, Icons.Rounded.HourglassBottom, R.string.nav_quick_timers),
    WheelWedge(HomeShortcut.RIGHT, 0f, Icons.Rounded.EmojiEvents, R.string.nav_scoreboard),
    WheelWedge(HomeShortcut.DOWN, 90f, Icons.Rounded.Settings, R.string.nav_settings),
    WheelWedge(HomeShortcut.LEFT, 180f, Icons.Rounded.FitnessCenter, R.string.nav_rounds)
)

private val DISC_SIZE = 148.dp

// Proportions of the disc radius
private const val OUTER_RATIO = 106f / 110f
private const val INNER_RATIO = 47f / 110f
private const val CENTER_RATIO = 40f / 110f
private const val ICON_RATIO = 68f / 110f
private const val WEDGE_GAP_DEG = 3f

/**
 * Home remote wheel: one unified control shaped like the remote's D-pad. The disc carries icons only;
 * [HomeWheelLegend] names what each direction (and OK) does. [pressed] lights up the direction currently held.
 */
@Composable
fun HomeWheelDisc(
    pressed: HomeShortcut?,
    onShortcut: (HomeShortcut) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) { WheelDisc(pressed = pressed, onShortcut = onShortcut) }
}

@Composable
fun HomeWheelLegend(
    pressed: HomeShortcut?,
    onShortcut: (HomeShortcut) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        wedges.forEach { wedge ->
            LegendKey(
                arrow = arrowGlyph(wedge.shortcut),
                icon = wedge.icon,
                text = stringResource(id = wedge.labelRes),
                highlighted = pressed == wedge.shortcut,
                onTap = { onShortcut(wedge.shortcut) }
            )
        }
        LegendKey(
            arrow = "OK",
            icon = Icons.Rounded.Timer,
            // Full name, e.g. "Cronómetro Progressivo"
            text = stopwatchLegendLabel(stringResource(id = R.string.phase_stopwatch)),
            highlighted = pressed == HomeShortcut.OK,
            onTap = { onShortcut(HomeShortcut.OK) }
        )
    }
}

private fun stopwatchLegendLabel(full: String): String =
    full.lowercase().split(" ").joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }

private fun arrowGlyph(shortcut: HomeShortcut): String = when (shortcut) {
    HomeShortcut.UP -> "▲"
    HomeShortcut.RIGHT -> "▶"
    HomeShortcut.DOWN -> "▼"
    HomeShortcut.LEFT -> "◀"
    HomeShortcut.OK -> "OK"
}

@Composable
private fun LegendKey(arrow: String, icon: ImageVector, text: String, highlighted: Boolean, onTap: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.pointerInput(Unit) { detectTapGestures { onTap() } }
    ) {
        Text(
            text = arrow,
            maxLines = 1,
            softWrap = false,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = FeuColors.CrownGold
            )
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (highlighted) FeuColors.CrownGold else FeuColors.GiWhite,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            maxLines = 1,
            softWrap = false,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.2.sp,
                color = if (highlighted) FeuColors.CrownGold else FeuColors.GiWhite
            )
        )
    }
}

@Composable
private fun WheelDisc(pressed: HomeShortcut?, onShortcut: (HomeShortcut) -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(DISC_SIZE)
            // Touch / air-mouse: a tap on a wedge or on the centre triggers that shortcut
            .pointerInput(Unit) {
                detectTapGestures { tap ->
                    val dx = tap.x - size.width / 2f
                    val dy = tap.y - size.height / 2f
                    val radius = size.width / 2f
                    val distance = hypot(dx, dy)
                    when {
                        distance <= radius * INNER_RATIO -> onShortcut(HomeShortcut.OK)
                        distance <= radius -> {
                            val angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                            onShortcut(
                                when {
                                    angle >= -135f && angle < -45f -> HomeShortcut.UP
                                    angle >= -45f && angle < 45f -> HomeShortcut.RIGHT
                                    angle >= 45f && angle < 135f -> HomeShortcut.DOWN
                                    else -> HomeShortcut.LEFT
                                }
                            )
                        }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val outer = radius * OUTER_RATIO
            val inner = radius * INNER_RATIO
            fun polar(angleDeg: Float, r: Float): Offset {
                val rad = Math.toRadians(angleDeg.toDouble())
                return Offset(center.x + r * cos(rad).toFloat(), center.y + r * sin(rad).toFloat())
            }

            wedges.forEach { wedge ->
                val start = wedge.centerAngle - 45f + WEDGE_GAP_DEG
                val sweep = 90f - 2 * WEDGE_GAP_DEG
                val path = Path().apply {
                    arcTo(Rect(center, outer), start, sweep, forceMoveTo = true)
                    arcTo(Rect(center, inner), start + sweep, -sweep, forceMoveTo = false)
                    close()
                }
                val isPressed = pressed == wedge.shortcut
                drawPath(path, color = if (isPressed) FeuColors.FeuRed else Color(0x12FFFFFF))
                drawPath(
                    path,
                    color = if (isPressed) Color.White else Color(0x57FFFFFF),
                    style = Stroke(width = 1.5.dp.toPx())
                )
                // Small arrow at the rim: this wedge IS that arrow on the remote
                val chevron = Path().apply {
                    val tip = polar(wedge.centerAngle, radius * 102f / 110f)
                    val baseA = polar(wedge.centerAngle - 6.5f, radius * 89f / 110f)
                    val baseB = polar(wedge.centerAngle + 6.5f, radius * 89f / 110f)
                    moveTo(tip.x, tip.y)
                    lineTo(baseA.x, baseA.y)
                    lineTo(baseB.x, baseB.y)
                    close()
                }
                drawPath(chevron, color = FeuColors.CrownGold)
            }
        }

        val iconDistance = DISC_SIZE / 2 * ICON_RATIO
        wedges.forEach { wedge ->
            val rad = Math.toRadians(wedge.centerAngle.toDouble())
            Icon(
                imageVector = wedge.icon,
                contentDescription = null,
                tint = FeuColors.GiWhite,
                modifier = Modifier
                    .offset(x = iconDistance * cos(rad).toFloat(), y = iconDistance * sin(rad).toFloat())
                    .size(21.dp)
            )
        }

        // Centre = the OK button of the remote (a shaped surface, not a canvas circle)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .size(DISC_SIZE * CENTER_RATIO)
                .background(if (pressed == HomeShortcut.OK) FeuColors.FeuRed else Color(0xFF151212), CircleShape)
                .border(3.dp, FeuColors.FeuRed, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Rounded.Timer,
                contentDescription = null,
                tint = FeuColors.GiWhite,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "OK",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = FeuColors.GiWhite
                )
            )
        }
    }
}
