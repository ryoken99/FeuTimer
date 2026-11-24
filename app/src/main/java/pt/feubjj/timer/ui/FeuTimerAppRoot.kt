package pt.feubjj.timer.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import pt.feubjj.timer.ui.input.HomeShortcut
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import pt.feubjj.timer.ui.components.BottomControlBar
import pt.feubjj.timer.ui.components.TatamiBackdrop
import pt.feubjj.timer.ui.components.TopChromeBar
import pt.feubjj.timer.ui.screens.ActiveTimerScreen
import pt.feubjj.timer.ui.screens.ClockScreen
import pt.feubjj.timer.ui.screens.QuickTimersScreen
import pt.feubjj.timer.ui.screens.RoundsScreen
import pt.feubjj.timer.ui.screens.ScoreboardScreen
import pt.feubjj.timer.ui.screens.SettingsScreen
import pt.feubjj.timer.ui.theme.FeuTimerTheme
import pt.feubjj.timer.ui.theme.TvViewportScaler
import pt.feubjj.timer.ui.viewmodel.FeuTimerSessionController
import pt.feubjj.timer.ui.viewmodel.TvScreen
import java.util.Locale

/**
 * Root Compose tree connecting [FeuTimerSessionController] to all 6 Android TV screens
 * with canonical `960 × 540` viewport density scaling (guaranteeing zero-cutoff full-size 10-foot UI
 * across every OEM TV / Android box DPI), dynamic in-app `PT / EN / ES` locale switching,
 * and live Wi-Fi Mobile Referee URL display.
 */
@Composable
fun FeuTimerAppRoot(
    controller: FeuTimerSessionController,
    modifier: Modifier = Modifier,
    pressedHomeShortcut: HomeShortcut? = null
) {
    val state by controller.uiState.collectAsState()
    val baseContext = LocalContext.current
    val baseDensity = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    var timeFaceFocused by remember(state.screen) { mutableStateOf(false) }
    val scan = remember { HorizontalFocusScan() }
    DisposableEffect(state.screen, windowFocused) { onDispose { scan.stop() } }
    val localizedContext = remember(baseContext, state.localeOverride) {
        baseContext.withLocaleOverride(state.localeOverride)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (state.screen == TvScreen.QUICK_TIMERS && timeFaceFocused) return@onPreviewKeyEvent false
                val horizontal = event.key == Key.DirectionLeft || event.key == Key.DirectionRight
                if (horizontal && event.type == KeyEventType.KeyUp) {
                    scan.stop()
                    return@onPreviewKeyEvent false
                }
                if (horizontal && event.type == KeyEventType.KeyDown) {
                    // The initial press moves spatially. Ignore OS repeats, including a held key
                    // that opened this screen from the clock.
                    if (event.nativeKeyEvent.repeatCount > 0) return@onPreviewKeyEvent true
                    scan.stop()
                    if (state.screen != TvScreen.CLOCK && windowFocused) {
                        val dir = if (event.key == Key.DirectionRight) FocusDirection.Next else FocusDirection.Previous
                        scan.job = scope.launch {
                            delay(android.view.ViewConfiguration.getLongPressTimeout().toLong())
                            while (true) {
                                focusManager.moveFocus(dir)
                                delay(280)
                            }
                        }
                    }
                }
                false
            }
            .onKeyEvent { event ->
                // Never-trapped D-pad: if a directional move has no geometric candidate (e.g. lone Back button),
                // fall back to sequential traversal so every control is always reachable.
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val dir = when (event.key) {
                    Key.DirectionUp -> FocusDirection.Up
                    Key.DirectionDown -> FocusDirection.Down
                    Key.DirectionLeft -> FocusDirection.Left
                    Key.DirectionRight -> FocusDirection.Right
                    else -> return@onKeyEvent false
                }
                if (focusManager.moveFocus(dir)) {
                    true
                } else {
                    val seq = if (dir == FocusDirection.Up || dir == FocusDirection.Left) FocusDirection.Previous else FocusDirection.Next
                    focusManager.moveFocus(seq)
                }
            }
    ) {
        val targetDensityValue = TvViewportScaler.computeTargetDensity(
            widthPx = constraints.maxWidth,
            heightPx = constraints.maxHeight,
            fallbackDensity = baseDensity.density
        )
        val tvDensity = remember(targetDensityValue) {
            Density(density = targetDensityValue, fontScale = 1f)
        }
        val activeSessionBadge = (state.timer.config ?: state.lastSessionConfig)?.summaryBadge()

        CompositionLocalProvider(
            LocalContext provides localizedContext,
            LocalDensity provides tvDensity
        ) {
            FeuTimerTheme {
                TatamiBackdrop(
                    screen = state.screen,
                    timerPhase = state.timer.phase,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TopChromeBar(
                            screen = state.screen,
                            sessionBadge = activeSessionBadge,
                            use24HourClock = state.soundPreferences.use24HourClock
                        )

                        AnimatedContent(
                            targetState = state.screen,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(220)) togetherWith
                                    fadeOut(animationSpec = tween(160))
                            },
                            label = "tvScreenTransition",
                            modifier = Modifier.weight(1f)
                        ) { targetScreen ->
                            when (targetScreen) {
                                TvScreen.CLOCK -> ClockScreen(
                                    pressedShortcut = pressedHomeShortcut,
                                    onShortcut = controller::runHomeShortcut,
                                    use24HourClock = state.soundPreferences.use24HourClock
                                )

                                TvScreen.QUICK_TIMERS -> QuickTimersScreen(
                                    onRepeat = if (state.lastSessionConfig != null) controller::repeatLastSession else null,
                                    workSeconds = state.quickSeconds,
                                    onFocusTime = { timeFaceFocused = it },
                                    onAdd = controller::addQuickSeconds,
                                    onSecond = controller::advanceQuickSecond,
                                    onMinute = controller::adjustQuickMinute,
                                    onStart = controller::startQuickCustom,
                                    endless = state.quickOptions.endless,
                                    onEndless = controller::toggleQuickEndless,
                                    onRestMinute = controller::adjustQuickRestMinute,
                                    quickRepeat = state.quickOptions.repeat,
                                    quickRestSeconds = state.quickOptions.restSeconds,
                                    onAdjustRepeat = controller::adjustQuickRepeat,
                                    onAdjustRest = controller::adjustQuickRest,
                                    onStartStopwatch = controller::startStopwatch
                                )

                                TvScreen.ROUNDS -> RoundsScreen(
                                    presetSlots = state.presetSlots,
                                    customBuilder = state.customBuilder,
                                    onSelectPreset = controller::startPresetRound,
                                    onStopwatch = controller::startStopwatch,
                                    onToggleEndless = controller::toggleCustomEndless,
                                    onAdjustRepeat = { inc -> controller.adjustCustomRepeat(inc) },
                                    onAdjustWorkSmart = controller::adjustCustomWorkSmart,
                                    onAdjustWorkMinute = { inc -> controller.adjustCustomWork(inc, 60L) },
                                    onAdjustRestSmart = controller::adjustCustomRestSmart,
                                    onAdjustRestMinute = { inc -> controller.adjustCustomRest(inc, 60L) },
                                    onSaveToSlot = { slot, name -> controller.saveCustomToPresetSlot(slot, name) },
                                    onStartCustom = controller::startCustomRound
                                )

                                TvScreen.SCOREBOARD -> ScoreboardScreen(
                                    score = state.matchScore,
                                    timer = state.timer,
                                    selectedDurationSeconds = state.scoreboardDurationSeconds,
                                    onSelectDuration = controller::selectScoreboardDuration,
                                    onTogglePlayPause = controller::togglePlayPause,
                                    onResetMatch = { controller.resetScoreboardMatch(keepNames = false) },
                                    onAdjustTime = controller::adjustActiveTime,
                                    onAdjustScore = controller::adjustScore
                                )

                                TvScreen.SETTINGS -> SettingsScreen(
                                    preferences = state.soundPreferences,
                                    localeOverride = state.localeOverride,
                                    onSetPrepSeconds = controller::setPrepSeconds,
                                    onToggleHalfway = controller::toggleWarnHalfway,
                                    onToggleWarn2m = controller::toggleWarn2m,
                                    onToggleWarn1m = controller::toggleWarn1m,
                                    onToggleBeep50s = controller::toggleBeep50s,
                                    onToggleWarn30s = controller::toggleWarn30s,
                                    onToggleFinal10s = controller::toggleBeep10s,
                                    onToggleVoice = controller::toggleVoiceEnabled,
                                    onSelectSoundPack = controller::setSoundPack,
                                    onTestSound = controller::testSoundCue,
                                    onToggle24Hour = controller::toggle24HourClock,
                                    onSelectLocale = controller::setLocaleOverride,
                                    onResetPresets = controller::resetPresetSlots
                                )

                                TvScreen.ACTIVE_TIMER -> ActiveTimerScreen(
                                    snapshot = state.timer
                                )
                            }
                        }

                        BottomControlBar(
                            screen = state.screen,
                            timerPhase = state.timer.phase,
                            isMultiRoundActive = state.timer.config?.isMultiRound == true,
                            soundPreferences = state.soundPreferences,
                            lanRefereeUrl = state.lanRefereeUrl,
                            lanConnectedClients = state.lanConnectedClients,
                            savedResultsCount = state.matchResults.size,
                            pressedHomeShortcut = pressedHomeShortcut,
                            onHomeShortcut = controller::runHomeShortcut,
                            onTogglePlayPause = controller::togglePlayPause,
                            onRestartPhase = controller::restartActivePhase,
                            onAdjustTime = controller::adjustActiveTime,
                            onSkipPhase = controller::skipActivePhase,
                            onBackToClock = { controller.onBackPressed() },
                            onToggleVoice = controller::toggleVoiceEnabled
                        )
                    }
                }
            }
        }
    }
}

private class HorizontalFocusScan {
    var job: Job? = null
    fun stop() {
        job?.cancel()
        job = null
    }
}

private fun Context.withLocaleOverride(localeTag: String?): Context {
    if (localeTag.isNullOrBlank()) return this
    val locale = when (localeTag) {
        "pt" -> Locale("pt", "PT")
        "en" -> Locale.ENGLISH
        "es" -> Locale("es", "ES")
        else -> Locale.forLanguageTag(localeTag)
    }
    Locale.setDefault(locale)
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    return createConfigurationContext(config)
}
