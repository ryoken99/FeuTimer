@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package pt.feubjj.timer.ui.screens

import android.view.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import pt.feubjj.timer.domain.presets.PresetNames
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import pt.feubjj.timer.domain.engine.TimeFormatter
import pt.feubjj.timer.domain.engine.TimeUnitKind
import pt.feubjj.timer.domain.model.CustomRoundBuilderState
import pt.feubjj.timer.domain.model.RoundConfig
import pt.feubjj.timer.domain.presets.PresetCatalog
import pt.feubjj.timer.domain.presets.RoundPresetOption
import pt.feubjj.timer.ui.components.TvFocusableSurface
import pt.feubjj.timer.ui.theme.FeuColors
import pt.feubjj.timer.ui.theme.FeuTypography

private enum class RoundSection(val label: Int) {
    SAVED(R.string.round_section_saved),
    CUSTOM(R.string.round_section_custom),
    TEMPLATES(R.string.round_section_templates)
}

@Composable
fun RoundsScreen(
    presetSlots: List<RoundPresetOption>,
    customBuilder: CustomRoundBuilderState,
    onSelectPreset: (RoundConfig) -> Unit,
    onLoadTemplate: (RoundConfig) -> Unit,
    onToggleEndless: () -> Unit,
    onAdjustRepeat: (Boolean) -> Unit,
    onAdjustWorkSmart: (Boolean) -> Unit,
    onAdjustWorkMinute: (Boolean) -> Unit,
    onAdjustRestSmart: (Boolean) -> Unit,
    onAdjustRestMinute: (Boolean) -> Unit,
    onSaveToSlot: (Int, String) -> Unit,
    onStartCustom: () -> Unit,
    modifier: Modifier = Modifier
) {
    var section by remember { mutableStateOf(RoundSection.SAVED) }
    val tabFocus = remember { RoundSection.entries.associateWith { FocusRequester() } }
    val templateFocus = remember { FocusRequester() }
    var enterPanel by remember { mutableIntStateOf(0) }
    val firstPresetFocus = remember { FocusRequester() }
    // Explicit focus targets so +1m/-1m tags go DOWN to their own stepper, and the rightmost card goes RIGHT to Play
    val repeatMinusFocus = remember { FocusRequester() }
    val workMinusFocus = remember { FocusRequester() }
    val restMinusFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(enterPanel) {
        if (enterPanel == 0) tabFocus.getValue(section).requestFocus()
        else when (section) {
            RoundSection.SAVED -> firstPresetFocus.requestFocus()
            RoundSection.CUSTOM -> playFocus.requestFocus()
            RoundSection.TEMPLATES -> templateFocus.requestFocus()
        }
    }

    // Save-to-slot flow: focus on a Guardar number previews the overwrite, OK opens a confirm dialog, then a toast confirms
    var previewSlot by remember { mutableStateOf<Int?>(null) }
    var pendingSaveSlot by remember { mutableStateOf<Int?>(null) }
    var savedSlot by remember { mutableStateOf<Int?>(null) }
    var popupSlot by remember { mutableIntStateOf(0) }
    LaunchedEffect(previewSlot) { previewSlot?.let { popupSlot = it } }
    // Leaving the save flow (saved, cancelled, or Back on the popup/dialog) drops the preview popup and hands focus to Play.
    // Retried once because the closing dialog restores focus to the Guardar number a moment after it is dismissed.
    var leaveSaveFlowTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(leaveSaveFlowTick) {
        if (leaveSaveFlowTick > 0) {
            repeat(2) {
                previewSlot = null
                runCatching { playFocus.requestFocus() }
                delay(160L)
            }
        }
    }
    // Back while the preview popup is up only closes it (never exits to the clock); Back on the plain tab keeps its normal behaviour
    BackHandler(enabled = previewSlot != null && pendingSaveSlot == null) {
        leaveSaveFlowTick++
    }
    LaunchedEffect(savedSlot) {
        if (savedSlot != null) {
            delay(2200L)
            savedSlot = null
        }
    }
    val builderConfig = customBuilder.toRoundConfig()
    val showPopup = section == RoundSection.CUSTOM && previewSlot != null && pendingSaveSlot == null
    // Everything behind the popup softly blurs (Android 12+) and always darkens, so the popup is the only thing to read
    val backdropBlur by animateDpAsState(if (showPopup) 8.dp else 0.dp, tween(200), label = "popupBlur")
    val backdropDim by animateFloatAsState(if (showPopup) 0.62f else 0f, tween(200), label = "popupDim")

    Box(modifier = modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .blur(backdropBlur)
            .padding(horizontal = 36.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundSection.entries.forEach { tab ->
                OptionPill(
                    label = stringResource(tab.label),
                    selected = tab == section,
                    onClick = {
                        section = tab
                        enterPanel++
                    },
                    modifier = Modifier.focusRequester(tabFocus.getValue(tab))
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        when (section) {
            RoundSection.SAVED -> Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                presetSlots.forEachIndexed { index, preset ->
                    PresetRoundCard(
                        slotNumber = index + 1,
                        preset = preset,
                        previewConfig = if (previewSlot == index) builderConfig else null,
                        savedFlash = savedSlot == index,
                        onClick = { onSelectPreset(preset.config) },
                        modifier = Modifier
                            .weight(1f)
                            .then(if (index == 0) Modifier.focusRequester(firstPresetFocus) else Modifier)
                    )
                }
            }
            RoundSection.CUSTOM -> Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                CustomRepeatColumn(
                    customBuilder = customBuilder,
                    onToggleEndless = onToggleEndless,
                    onIncrease = { onAdjustRepeat(true) },
                    onDecrease = { onAdjustRepeat(false) },
                    minusFocus = repeatMinusFocus,
                    modifier = Modifier.weight(1f)
                )
                CustomDurationColumn(
                    title = stringResource(id = R.string.custom_col_work),
                    valueText = customBuilder.formattedWork,
                    unitText = unitLabelForSeconds(customBuilder.workSeconds),
                    onPlusStep = { onAdjustWorkSmart(true) },
                    onMinusStep = { onAdjustWorkSmart(false) },
                    onPlusMinute = { onAdjustWorkMinute(true) },
                    onMinusMinute = { onAdjustWorkMinute(false) },
                    minusFocus = workMinusFocus,
                    modifier = Modifier.weight(1f)
                )
                CustomDurationColumn(
                    title = stringResource(id = R.string.custom_col_rest),
                    valueText = customBuilder.formattedRest,
                    unitText = unitLabelForSeconds(customBuilder.restSeconds),
                    onPlusStep = { onAdjustRestSmart(true) },
                    onMinusStep = { onAdjustRestSmart(false) },
                    onPlusMinute = { onAdjustRestMinute(true) },
                    onMinusMinute = { onAdjustRestMinute(false) },
                    minusFocus = restMinusFocus,
                    rightFocus = playFocus,
                    modifier = Modifier.weight(1f)
                )
                CustomStartAndSaveColumn(
                    totalDurationText = customBuilder.formattedTotal,
                    onStart = onStartCustom,
                    onSaveSlot = { pendingSaveSlot = it },
                    onSlotFocusChange = { slot, focused ->
                        if (focused) previewSlot = slot else if (previewSlot == slot) previewSlot = null
                    },
                    playFocus = playFocus,
                    modifier = Modifier.weight(1f)
                )
            }
            RoundSection.TEMPLATES -> TemplateGrid(templateFocus) {
                onLoadTemplate(it)
                section = RoundSection.CUSTOM
                enterPanel++
            }
        }
    }

    // The saved cards live on another tab, so hovering a Guardar number fades in this popup: which preset changes + what changes
    if (backdropDim > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = backdropDim))
        )
    }
    AnimatedVisibility(
        visible = showPopup,
        enter = fadeIn(animationSpec = tween(durationMillis = 180)),
        exit = fadeOut(animationSpec = tween(durationMillis = 140)),
        modifier = Modifier.align(Alignment.Center)
    ) {
        SlotPreviewPopup(
            slotNumber = popupSlot + 1,
            preset = presetSlots[popupSlot.coerceIn(0, presetSlots.lastIndex)],
            after = builderConfig
        )
    }

    pendingSaveSlot?.let { slot ->
        SaveSlotDialog(
            slotNumber = slot + 1,
            before = presetSlots[slot].config,
            after = builderConfig,
            initialName = presetSlots[slot].customName.orEmpty(),
            onConfirm = { name ->
                onSaveToSlot(slot, name)
                pendingSaveSlot = null
                savedSlot = slot
                leaveSaveFlowTick++
            },
            onCancel = {
                pendingSaveSlot = null
                leaveSaveFlowTick++
            }
        )
    }

    AnimatedVisibility(
        visible = savedSlot != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 74.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xF50E281C))
                .border(2.dp, FeuColors.RestEmerald, RoundedCornerShape(999.dp))
                .padding(horizontal = 26.dp, vertical = 12.dp)
        ) {
            Text(
                text = stringResource(id = R.string.save_done, (savedSlot ?: 0) + 1),
                maxLines = 1,
                softWrap = false,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFB9F5D8)
                )
            )
        }
    }
    }
}

@Composable
private fun TemplateGrid(firstFocus: FocusRequester, onLoad: (RoundConfig) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PresetCatalog.templateLibrary.chunked(2).forEachIndexed { row, templates ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                templates.forEachIndexed { column, template ->
                    val label = when (template.titleKey) {
                        "tpl_open_mat" -> R.string.tpl_open_mat
                        "tpl_endurance" -> R.string.tpl_endurance
                        "tpl_black_belt" -> R.string.tpl_black_belt
                        "tpl_high_pace" -> R.string.tpl_high_pace
                        "tpl_shark_tank" -> R.string.tpl_shark_tank
                        else -> R.string.tpl_tabata
                    }
                    OptionPill(
                        label = stringResource(label),
                        selected = false,
                        onClick = { onLoad(template.config) },
                        modifier = Modifier.weight(1f).then(
                            if (row == 0 && column == 0) Modifier.focusRequester(firstFocus) else Modifier
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun presetTitleText(preset: RoundPresetOption): String = preset.customName?.takeIf { it.isNotBlank() } ?: when (preset.titleKey) {
    "preset_adult" -> stringResource(id = R.string.preset_adult)
    "preset_comp" -> stringResource(id = R.string.preset_comp)
    "preset_specific" -> stringResource(id = R.string.preset_specific)
    else -> stringResource(id = R.string.preset_kids_drills)
}

/** Non-interactive card that shows which preset slot would be overwritten and each value's before -> after. */
@Composable
private fun SlotPreviewPopup(slotNumber: Int, preset: RoundPresetOption, after: RoundConfig) {
    val oldM = metricTextsOf(preset.config)
    val newM = metricTextsOf(after)
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .width(470.dp)
            .clip(shape)
            .background(Color(0xF2151313))
            .border(2.dp, FeuColors.CrownGold, shape)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$slotNumber · ${presetTitleText(preset).uppercase()}",
                maxLines = 1,
                modifier = Modifier.weight(1f),
                style = FeuTypography.Eyebrow.copy(
                    color = FeuColors.CrownGold,
                    fontSize = 15.sp,
                    letterSpacing = 1.2.sp
                )
            )
            // Sharp mirror of the (blurred) Guardar 1-4 selector so you always see which slot you are on
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (n in 1..4) {
                    val active = n == slotNumber
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (active) FeuColors.CrownGold else FeuColors.CardSurfaceHover)
                            .border(2.dp, if (active) FeuColors.GiWhite else FeuColors.HairlineStrong, RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = "$n",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = if (active) FeuColors.TatamiBlack else FeuColors.GiRestIvory
                            )
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PopupDiffCell(stringResource(id = R.string.role_rounds), oldM.rep, newM.rep)
            PopupDiffCell(stringResource(id = R.string.role_work), oldM.work, newM.work)
            PopupDiffCell(stringResource(id = R.string.role_rest), oldM.rest, newM.rest)
            PopupDiffCell("Total", oldM.total, newM.total)
        }
    }
}

@Composable
private fun PopupDiffCell(label: String, old: String, new: String) {
    val changed = old != new
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label.uppercase(),
            maxLines = 1,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = FeuColors.GiRestIvory
            )
        )
        Text(
            text = if (changed) old else " ",
            maxLines = 1,
            softWrap = false,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = FeuColors.TextFaint,
                textDecoration = TextDecoration.LineThrough,
                fontFeatureSettings = FeuTypography.TABULAR_FEATURES
            )
        )
        Text(
            text = new,
            maxLines = 1,
            softWrap = false,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = if (changed) FeuColors.CrownGold else FeuColors.GiWhite,
                fontFeatureSettings = FeuTypography.TABULAR_FEATURES
            )
        )
    }
}

@Composable
private fun SaveSlotDialog(
    slotNumber: Int,
    before: RoundConfig,
    after: RoundConfig,
    initialName: String,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit
) {
    val okFocus = remember { FocusRequester() }
    var name by remember { mutableStateOf(initialName) }
    val oldM = metricTextsOf(before)
    val newM = metricTextsOf(after)
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        LaunchedEffect(Unit) { runCatching { okFocus.requestFocus() } }
        val shape = RoundedCornerShape(18.dp)
        Column(
            modifier = Modifier
                .onPreviewKeyEvent { event ->
                    val key = event.nativeKeyEvent
                    if (key.keyCode != KeyEvent.KEYCODE_ESCAPE && key.keyCode != KeyEvent.KEYCODE_BUTTON_B) {
                        false
                    } else {
                        if (key.action == KeyEvent.ACTION_UP && !key.isCanceled) onCancel()
                        true
                    }
                }
                .width(520.dp)
                .clip(shape)
                .background(Color(0xFF151313))
                .border(2.dp, FeuColors.CrownGold, shape)
                .padding(horizontal = 26.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(id = R.string.save_dialog_title, slotNumber),
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp, fontWeight = FontWeight.Black)
            )
            Spacer(modifier = Modifier.height(6.dp))
            DiffRow(stringResource(id = R.string.role_rounds), oldM.rep, newM.rep)
            DiffRow(stringResource(id = R.string.role_work), oldM.work, newM.work)
            DiffRow(stringResource(id = R.string.role_rest), oldM.rest, newM.rest)
            DiffRow("Total", oldM.total, newM.total)
            SlotNameField(
                value = name,
                onValueChange = { name = it.take(PresetNames.MAX_LENGTH) },
                onDone = { runCatching { okFocus.requestFocus() } }
            )
            Text(
                text = stringResource(id = R.string.save_dialog_hint),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, color = FeuColors.TextMuted),
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                TvFocusableSurface(
                    onClick = { onConfirm(name) },
                    shape = RoundedCornerShape(999.dp),
                    containerColor = FeuColors.FeuRedPrimary,
                    focusedContainerColor = FeuColors.GiWhite,
                    borderColor = Color.White.copy(alpha = 0.8f),
                    focusedBorderColor = FeuColors.FeuRed,
                    modifier = Modifier
                        .height(50.dp)
                        .focusRequester(okFocus)
                ) { isFocused ->
                    Text(
                        text = stringResource(id = R.string.save_ok),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isFocused) FeuColors.TatamiBlack else FeuColors.GiWhite
                        ),
                        modifier = Modifier.padding(horizontal = 28.dp)
                    )
                }
                TvFocusableSurface(
                    onClick = onCancel,
                    shape = RoundedCornerShape(999.dp),
                    containerColor = FeuColors.CardSurfaceHover,
                    focusedContainerColor = FeuColors.GiWhite,
                    borderColor = FeuColors.HairlineStrong,
                    focusedBorderColor = FeuColors.FeuRed,
                    modifier = Modifier.height(50.dp)
                ) { isFocused ->
                    Text(
                        text = stringResource(id = R.string.save_cancel),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isFocused) FeuColors.TatamiBlack else FeuColors.GiWhite
                        ),
                        modifier = Modifier.padding(horizontal = 28.dp)
                    )
                }
            }
        }
    }
}

/** Optional preset name: sits between the diff and the buttons, OK keeps initial focus so ignoring it costs nothing. */
@Composable
private fun SlotNameField(value: String, onValueChange: (String) -> Unit, onDone: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp,
            color = FeuColors.GiWhite
        ),
        cursorBrush = SolidColor(FeuColors.CrownGold),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(FeuColors.CardSurfaceHover)
                    .border(2.dp, if (focused) FeuColors.CrownGold else FeuColors.HairlineStrong, shape)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = stringResource(id = R.string.save_name_hint),
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = FeuColors.TextFaint
                        )
                    )
                }
                inner()
            }
        }
    )
}

@Composable
private fun DiffRow(label: String, old: String, new: String) {
    val changed = old != new
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold, color = FeuColors.GiRestIvory)
        )
        Text(
            text = old,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                color = FeuColors.TextFaint,
                textDecoration = if (changed) TextDecoration.LineThrough else TextDecoration.None,
                fontFeatureSettings = FeuTypography.TABULAR_FEATURES
            )
        )
        Text(text = "→", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 19.sp, color = FeuColors.TextMuted))
        Text(
            text = new,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 19.sp,
                fontWeight = FontWeight.Black,
                color = if (changed) FeuColors.CrownGold else FeuColors.GiWhite,
                fontFeatureSettings = FeuTypography.TABULAR_FEATURES
            )
        )
    }
}

private data class MetricTexts(val rep: String, val work: String, val rest: String, val total: String)

@Composable
private fun metricTextsOf(config: RoundConfig): MetricTexts {
    val (workVal, workKind) = TimeFormatter.displayValueAndUnit(config.workSeconds)
    val (restVal, restKind) = TimeFormatter.displayValueAndUnit(config.restSeconds)
    return MetricTexts(
        rep = if (config.isEndless) "∞" else config.repeat.toString(),
        work = "$workVal ${unitLabel(workKind)}",
        rest = "$restVal ${unitLabel(restKind)}",
        total = if (config.isEndless) "∞" else TimeFormatter.formatCompactDuration(config.totalDurationSeconds)
    )
}

@Composable
private fun PresetRoundCard(
    slotNumber: Int,
    preset: RoundPresetOption,
    previewConfig: RoundConfig?,
    savedFlash: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val titleText = presetTitleText(preset)
    // While a Guardar number is focused, this card shows what it WOULD become (old value struck through above)
    val shownConfig = previewConfig ?: preset.config
    val repStr = if (shownConfig.isEndless) "∞" else shownConfig.repeat.toString()
    val (workVal, workKind) = TimeFormatter.displayValueAndUnit(shownConfig.workSeconds)
    val (restVal, restKind) = TimeFormatter.displayValueAndUnit(shownConfig.restSeconds)
    val oldM = metricTextsOf(preset.config)
    val newM = metricTextsOf(shownConfig)
    val showDiff = previewConfig != null
    val totalStr = newM.total

    TvFocusableSurface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        containerColor = FeuColors.CardSurface,
        focusedContainerColor = Color.White.copy(alpha = 0.20f),
        borderColor = Color.White.copy(alpha = 0.45f),
        focusedBorderColor = FeuColors.FeuRed,
        glowColor = FeuColors.FeuRed,
        pulse = previewConfig != null || savedFlash,
        pulseColor = if (previewConfig == null && savedFlash) FeuColors.RestEmerald else FeuColors.CrownGold,
        modifier = modifier.height(138.dp)
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$slotNumber · ${titleText.uppercase()}",
                style = FeuTypography.Eyebrow.copy(
                    color = FeuColors.CrownGold,
                    fontSize = 13.sp,
                    letterSpacing = 1.0.sp
                ),
                maxLines = 1
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetricCell(
                    number = repStr,
                    oldText = if (showDiff && oldM.rep != newM.rep) oldM.rep else null,
                    unit = null,
                    role = stringResource(id = R.string.role_rounds),
                    roleColor = FeuColors.GiRestIvory
                )
                VerticalDivider(
                    modifier = Modifier.height(36.dp),
                    color = FeuColors.HairlineStrong
                )
                MetricCell(
                    number = workVal.toString(),
                    oldText = if (showDiff && oldM.work != newM.work) oldM.work else null,
                    unit = unitLabel(workKind),
                    role = stringResource(id = R.string.role_work),
                    roleColor = Color(0xFFFF7278)
                )
                VerticalDivider(
                    modifier = Modifier.height(36.dp),
                    color = FeuColors.HairlineStrong
                )
                MetricCell(
                    number = restVal.toString(),
                    oldText = if (showDiff && oldM.rest != newM.rest) oldM.rest else null,
                    unit = unitLabel(restKind),
                    role = stringResource(id = R.string.role_rest),
                    roleColor = Color(0xFF66DDA9)
                )
            }

            HorizontalDivider(color = FeuColors.Hairline)

            Text(
                text = if (showDiff && oldM.total != newM.total) {
                    stringResource(id = R.string.total_prefix, oldM.total) + " → " + newM.total
                } else {
                    stringResource(id = R.string.total_prefix, totalStr)
                },
                maxLines = 1,
                style = MaterialTheme.typography.labelLarge.copy(
                    color = FeuColors.GiWhite,
                    fontSize = 14.5.sp,
                    fontFeatureSettings = FeuTypography.TABULAR_FEATURES
                )
            )
        }
    }
}

@Composable
private fun MetricCell(
    number: String,
    unit: String?,
    role: String,
    roleColor: Color,
    oldText: String? = null
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Reserved row so the layout never jumps when the before/after preview appears
        Text(
            text = oldText ?: " ",
            maxLines = 1,
            softWrap = false,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = FeuColors.TextFaint,
                textDecoration = TextDecoration.LineThrough,
                fontFeatureSettings = FeuTypography.TABULAR_FEATURES
            ),
            modifier = Modifier.height(15.dp)
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = number,
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Black,
                    fontSize = 27.sp,
                    fontFeatureSettings = FeuTypography.TABULAR_FEATURES,
                    color = if (oldText != null) FeuColors.CrownGold else FeuColors.GiWhite
                )
            )
            if (unit != null) {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = FeuColors.GiRestIvory
                    ),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
        Text(
            text = role.uppercase(),
            style = FeuTypography.Eyebrow.copy(
                fontSize = 11.5.sp,
                letterSpacing = 0.6.sp,
                color = roleColor
            )
        )
    }
}

@Composable
private fun CustomRepeatColumn(
    customBuilder: CustomRoundBuilderState,
    onToggleEndless: () -> Unit,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    minusFocus: FocusRequester,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .height(152.dp)
            .clip(cardShape)
            .background(FeuColors.CardSurface)
            .border(1.25.dp, Color.White.copy(alpha = 0.45f), cardShape)
            .padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.custom_col_repeats).uppercase(),
                style = FeuTypography.Eyebrow.copy(
                    color = FeuColors.CrownGold,
                    fontSize = 13.sp,
                    letterSpacing = 1.0.sp
                )
            )
            MiniTagButton(
                label = "∞",
                isActive = customBuilder.isEndless,
                onClick = onToggleEndless,
                modifier = Modifier.focusProperties { down = minusFocus }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = if (customBuilder.isEndless) {
                    stringResource(id = R.string.unit_endless)
                } else {
                    stringResource(id = R.string.unit_rep)
                },
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = FeuColors.GiRestIvory
                )
            )
            Text(
                text = customBuilder.formattedRepeat,
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Black,
                    fontSize = 34.sp,
                    fontFeatureSettings = FeuTypography.TABULAR_FEATURES,
                    color = FeuColors.GiWhite
                )
            )
        }

        PlusMinusRow(onPlus = onIncrease, onMinus = onDecrease, minusFocus = minusFocus)
    }
}

@Composable
private fun CustomDurationColumn(
    title: String,
    valueText: String,
    unitText: String,
    onPlusStep: () -> Unit,
    onMinusStep: () -> Unit,
    onPlusMinute: () -> Unit,
    onMinusMinute: () -> Unit,
    minusFocus: FocusRequester,
    modifier: Modifier = Modifier,
    rightFocus: FocusRequester? = null
) {
    val cardShape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .height(152.dp)
            .clip(cardShape)
            .background(FeuColors.CardSurface)
            .border(1.25.dp, Color.White.copy(alpha = 0.45f), cardShape)
            .padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title.uppercase(),
                style = FeuTypography.Eyebrow.copy(
                    color = FeuColors.CrownGold,
                    fontSize = 12.5.sp,
                    letterSpacing = 0.8.sp
                ),
                maxLines = 1
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MiniTagButton(
                    label = "+1m",
                    isActive = false,
                    onClick = onPlusMinute,
                    modifier = Modifier.focusProperties { down = minusFocus }
                )
                MiniTagButton(
                    label = "−1m",
                    isActive = false,
                    onClick = onMinusMinute,
                    modifier = Modifier.focusProperties {
                        down = minusFocus
                        rightFocus?.let { right = it }
                    }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = unitText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = FeuColors.GiRestIvory
                )
            )
            Text(
                text = valueText,
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Black,
                    fontSize = 34.sp,
                    fontFeatureSettings = FeuTypography.TABULAR_FEATURES,
                    color = FeuColors.GiWhite
                )
            )
        }

        PlusMinusRow(onPlus = onPlusStep, onMinus = onMinusStep, minusFocus = minusFocus, minusRightFocus = rightFocus)
    }
}

@Composable
private fun PlusMinusRow(
    onPlus: () -> Unit,
    onMinus: () -> Unit,
    minusFocus: FocusRequester,
    minusRightFocus: FocusRequester? = null
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        TvFocusableSurface(
            onClick = onPlus,
            shape = RoundedCornerShape(8.dp),
            containerColor = FeuColors.CardSurfaceHover,
            focusedContainerColor = FeuColors.GiWhite,
            borderColor = FeuColors.HairlineStrong,
            focusedBorderColor = FeuColors.FeuRed,
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
        ) { isFocused ->
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "+",
                tint = if (isFocused) FeuColors.TatamiBlack else FeuColors.GiWhite,
                modifier = Modifier.size(24.dp)
            )
        }
        TvFocusableSurface(
            onClick = onMinus,
            shape = RoundedCornerShape(8.dp),
            containerColor = FeuColors.CardSurfaceHover,
            focusedContainerColor = FeuColors.GiWhite,
            borderColor = FeuColors.HairlineStrong,
            focusedBorderColor = FeuColors.FeuRed,
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .focusRequester(minusFocus)
                .focusProperties { minusRightFocus?.let { right = it } }
        ) { isFocused ->
            Icon(
                imageVector = Icons.Rounded.Remove,
                contentDescription = "-",
                tint = if (isFocused) FeuColors.TatamiBlack else FeuColors.GiWhite,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun CustomStartAndSaveColumn(
    totalDurationText: String,
    onStart: () -> Unit,
    onSaveSlot: (Int) -> Unit,
    onSlotFocusChange: (Int, Boolean) -> Unit,
    playFocus: FocusRequester,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.height(152.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(FeuColors.CardSurface)
                .border(1.25.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
        ) {
            Text(
                text = totalDurationText,
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Black,
                    fontSize = 21.sp,
                    fontFeatureSettings = FeuTypography.TABULAR_FEATURES,
                    color = FeuColors.GiWhite
                )
            )
        }

        TvFocusableSurface(
            onClick = onStart,
            shape = CircleShape,
            containerColor = FeuColors.GiWhite,
            focusedContainerColor = FeuColors.FeuRed,
            borderColor = Color.White,
            focusedBorderColor = FeuColors.GiWhite,
            glowColor = FeuColors.FeuRed,
            focusScale = 1.08f,
            modifier = Modifier.size(56.dp).focusRequester(playFocus)
        ) { isFocused ->
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = stringResource(id = R.string.custom_start_action),
                tint = if (isFocused) FeuColors.GiWhite else FeuColors.TatamiBlack,
                modifier = Modifier.size(34.dp)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = stringResource(id = R.string.custom_save_slot_prefix),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = FeuColors.GiRestIvory
                )
            )
            for (slot in 0..3) {
                MiniTagButton(
                    label = "${slot + 1}",
                    isActive = false,
                    onClick = { onSaveSlot(slot) },
                    onFocusChange = { focused -> onSlotFocusChange(slot, focused) }
                )
            }
        }
    }
}

@Composable
private fun MiniTagButton(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocusChange: ((Boolean) -> Unit)? = null
) {
    TvFocusableSurface(
        onClick = onClick,
        onFocusChange = onFocusChange,
        shape = RoundedCornerShape(6.dp),
        containerColor = if (isActive) FeuColors.CrownGold.copy(alpha = 0.30f) else FeuColors.CardSurfaceHover,
        focusedContainerColor = FeuColors.GiWhite,
        borderColor = if (isActive) FeuColors.CrownGold else FeuColors.HairlineStrong,
        focusedBorderColor = FeuColors.FeuRed,
        modifier = modifier.height(27.dp)
    ) { isFocused ->
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 12.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isFocused) FeuColors.TatamiBlack else if (isActive) FeuColors.CrownGold else FeuColors.GiWhite
            ),
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

@Composable
private fun unitLabel(kind: TimeUnitKind): String = when (kind) {
    TimeUnitKind.HOUR -> stringResource(id = R.string.unit_hour)
    TimeUnitKind.MINUTE -> stringResource(id = R.string.unit_min)
    TimeUnitKind.SECOND -> stringResource(id = R.string.unit_sec)
}

@Composable
private fun unitLabelForSeconds(seconds: Long): String = when {
    seconds >= 3600L -> stringResource(id = R.string.unit_hour)
    seconds >= 60L -> stringResource(id = R.string.unit_min)
    else -> stringResource(id = R.string.unit_sec)
}
