package pt.feubjj.timer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pt.feubjj.timer.R
import pt.feubjj.timer.audio.SoundPack
import pt.feubjj.timer.domain.model.SoundPreferences
import pt.feubjj.timer.ui.components.TvFocusableSurface
import pt.feubjj.timer.ui.theme.FeuColors

/**
 * Clean 4-Row Tatami Settings Screen (`960 × 540` overscan-safe):
 * 1. Initial Countdown (`Direto 0s`, `3s`, `5s`, `10s`)
 * 2. Mid-Round Alerts (`Metade`, `2m`, `1m`, `50s`, `30s` — independent toggles!)
 * 3. End Countdown, PT-PT Voice Coach & Sound Pack (`10s: ON`, `Voz: ON`, `Buzina`, `Gongo`, `Apito`, `Testar`)
 * 4. Wall Clock Format, Language & Preset Reset (`24h/12h`, `PT`, `EN`, `ES`, `Repor`)
 */
@Composable
fun SettingsScreen(
    preferences: SoundPreferences,
    localeOverride: String?,
    onSetPrepSeconds: (Int) -> Unit,
    onToggleHalfway: () -> Unit,
    onToggleWarn2m: () -> Unit,
    onToggleWarn1m: () -> Unit,
    onToggleBeep50s: () -> Unit,
    onToggleWarn30s: () -> Unit,
    onToggleFinal10s: () -> Unit,
    onToggleVoice: () -> Unit,
    onSelectSoundPack: (SoundPack) -> Unit,
    onTestSound: () -> Unit,
    onToggle24Hour: () -> Unit,
    onSelectLocale: (String) -> Unit,
    onResetPresets: () -> Unit,
    modifier: Modifier = Modifier
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { firstFocus.requestFocus() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 36.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(id = R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp)
        )
        Spacer(modifier = Modifier.height(10.dp))

        val panelShape = RoundedCornerShape(14.dp)
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(panelShape)
                .background(FeuColors.CardSurface)
                .border(1.5.dp, FeuColors.HairlineStrong, panelShape)
                .padding(18.dp)
        ) {
            SettingRow(label = stringResource(id = R.string.set_prep_label)) {
                listOf(0, 3, 5, 10).forEachIndexed { idx, sec ->
                    OptionPill(
                        label = if (sec == 0) stringResource(id = R.string.set_prep_off) else "${sec}s",
                        selected = preferences.prepSeconds == sec,
                        onClick = { onSetPrepSeconds(sec) },
                        modifier = if (idx == 0) Modifier.focusRequester(firstFocus) else Modifier
                    )
                }
            }

            SettingRow(label = stringResource(id = R.string.set_warn_label)) {
                OptionPill(
                    label = stringResource(id = R.string.warn_halfway_short),
                    selected = preferences.warnHalfway,
                    onClick = onToggleHalfway
                )
                OptionPill(label = "2m", selected = preferences.warn2m, onClick = onToggleWarn2m)
                OptionPill(label = "1m", selected = preferences.warn1m, onClick = onToggleWarn1m)
                OptionPill(label = "50s", selected = preferences.beep50s, onClick = onToggleBeep50s)
                OptionPill(label = "30s", selected = preferences.warn30s, onClick = onToggleWarn30s)
            }

            SettingRow(label = stringResource(id = R.string.set_pack_label)) {
                OptionPill(
                    label = "10s: " + stringResource(id = if (preferences.beep10s) R.string.feu_toggle_on else R.string.feu_toggle_off),
                    selected = preferences.beep10s,
                    onClick = onToggleFinal10s
                )
                OptionPill(
                    label = stringResource(id = R.string.nav_voice_short) + ": " +
                        stringResource(id = if (preferences.voiceEnabled) R.string.feu_toggle_on else R.string.feu_toggle_off),
                    selected = preferences.voiceEnabled,
                    onClick = onToggleVoice
                )
                OptionPill(
                    label = stringResource(id = R.string.pack_horn),
                    selected = preferences.soundPack == SoundPack.CLASSIC_HORN,
                    onClick = { onSelectSoundPack(SoundPack.CLASSIC_HORN) }
                )
                OptionPill(
                    label = stringResource(id = R.string.pack_bell),
                    selected = preferences.soundPack == SoundPack.BELL_GONG,
                    onClick = { onSelectSoundPack(SoundPack.BELL_GONG) }
                )
                OptionPill(
                    label = stringResource(id = R.string.pack_whistle),
                    selected = preferences.soundPack == SoundPack.WHISTLE,
                    onClick = { onSelectSoundPack(SoundPack.WHISTLE) }
                )
                OptionPill(
                    label = "🔊 " + stringResource(id = R.string.set_test_sound),
                    selected = false,
                    onClick = onTestSound
                )
            }

            SettingRow(label = stringResource(id = R.string.set_lang_label)) {
                OptionPill(
                    label = if (preferences.use24HourClock) "24h" else "12h AM/PM",
                    selected = true,
                    onClick = onToggle24Hour
                )
                val activeLang = localeOverride ?: "pt"
                listOf("pt" to "PT", "en" to "EN", "es" to "ES").forEach { (code, title) ->
                    OptionPill(
                        label = title,
                        selected = activeLang == code,
                        onClick = { onSelectLocale(code) }
                    )
                }
                OptionPill(
                    label = stringResource(id = R.string.set_reset_presets),
                    selected = false,
                    onClick = onResetPresets
                )
            }
        }
    }
}

@Composable
private fun SettingRow(
    label: String,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            maxLines = 1,
            softWrap = false,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = FeuColors.GiWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        )
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            content()
        }
    }
}

@Composable
private fun OptionPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TvFocusableSurface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        containerColor = if (selected) FeuColors.FeuRedPrimary else FeuColors.CardSurfaceHover,
        focusedContainerColor = FeuColors.GiWhite,
        borderColor = if (selected) Color.White.copy(alpha = 0.75f) else FeuColors.HairlineStrong,
        focusedBorderColor = FeuColors.FeuRed,
        modifier = modifier.height(40.dp)
    ) { isFocused ->
        Text(
            text = label,
            maxLines = 1,
            softWrap = false,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 14.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isFocused) FeuColors.TatamiBlack else FeuColors.GiWhite
            ),
            modifier = Modifier.padding(horizontal = 14.dp)
        )
    }
}
