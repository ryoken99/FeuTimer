package pt.feubjj.timer.ui.viewmodel

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pt.feubjj.timer.audio.SoundCue
import pt.feubjj.timer.audio.SoundPack
import pt.feubjj.timer.audio.SoundPlayerPort
import pt.feubjj.timer.data.TimerStorage
import pt.feubjj.timer.domain.engine.TimeFormatter
import pt.feubjj.timer.domain.engine.TimerEngine
import pt.feubjj.timer.domain.model.CustomRoundBuilderState
import pt.feubjj.timer.domain.model.MatchResultRecord
import pt.feubjj.timer.domain.model.MatchScoreState
import pt.feubjj.timer.domain.model.MatchWinnerSide
import pt.feubjj.timer.domain.model.QuickTimerOptions
import pt.feubjj.timer.domain.model.RoundConfig
import pt.feubjj.timer.domain.model.SoundPreferences
import pt.feubjj.timer.domain.model.TimerPhase
import pt.feubjj.timer.domain.model.TimerSnapshot
import pt.feubjj.timer.domain.presets.PresetCatalog
import pt.feubjj.timer.domain.presets.RoundPresetOption
import pt.feubjj.timer.ui.input.HomeShortcut
import pt.feubjj.timer.domain.util.SimpleJson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class FeuTimerUiState(
    val screen: TvScreen = TvScreen.CLOCK,
    val soundPreferences: SoundPreferences = SoundPreferences(),
    val presetSlots: List<RoundPresetOption> = PresetCatalog.coreRoundPresets,
    val customBuilder: CustomRoundBuilderState = CustomRoundBuilderState(),
    val lastSessionConfig: RoundConfig? = null,
    val timer: TimerSnapshot = TimerSnapshot(),
    val matchScore: MatchScoreState = MatchScoreState(),
    val matchResults: List<MatchResultRecord> = emptyList(),
    val quickOptions: QuickTimerOptions = QuickTimerOptions(),
    val scoreboardDurationSeconds: Long = 300L,
    val scoreboardArmed: Boolean = true,
    val localeOverride: String? = null,
    val lanRefereeUrl: String? = null,
    val lanConnectedClients: Int = 0
) {
    val lastSessionBadge: String?
        get() = lastSessionConfig?.summaryBadge()
}

/**
 * Platform-independent reactive controller driving TV screens, vocal & tatami cues,
 * saveable preset slots, IBJJF scoreboard, persisted match results, and zero-delay LAN mobile sync.
 */
class FeuTimerSessionController(
    private val storage: TimerStorage,
    private val soundPlayer: SoundPlayerPort,
    private val scope: CoroutineScope,
    private val tickDispatcher: CoroutineDispatcher = Dispatchers.Main
) {
    private val _uiState = MutableStateFlow(
        FeuTimerUiState(
            screen = TvScreen.CLOCK,
            soundPreferences = storage.loadSoundPreferences(),
            presetSlots = storage.loadPresetSlots(),
            customBuilder = storage.loadCustomRound()?.let(CustomRoundBuilderState::fromRoundConfig)
                ?: CustomRoundBuilderState(),
            lastSessionConfig = storage.loadLastSession(),
            matchResults = storage.loadMatchResults(),
            quickOptions = storage.loadQuickOptions(),
            localeOverride = storage.loadLocaleOverride()
        )
    )
    val uiState: StateFlow<FeuTimerUiState> = _uiState.asStateFlow()

    private var tickerJob: Job? = null

    /** Home remote wheel: each D-pad direction (and OK) on the idle clock goes straight to one destination. */
    fun runHomeShortcut(shortcut: HomeShortcut) {
        if (_uiState.value.screen != TvScreen.CLOCK) return
        when (shortcut) {
            HomeShortcut.UP -> openQuickTimers()
            HomeShortcut.LEFT -> openRounds()
            HomeShortcut.RIGHT -> openScoreboard()
            HomeShortcut.DOWN -> openSettings()
            HomeShortcut.OK -> startStopwatch()
        }
    }

    fun openQuickTimers() {
        stopActiveTimerInternal()
        _uiState.update { it.copy(screen = TvScreen.QUICK_TIMERS) }
    }

    fun openRounds() {
        stopActiveTimerInternal()
        _uiState.update { it.copy(screen = TvScreen.ROUNDS) }
    }

    fun openScoreboard() {
        if (_uiState.value.screen == TvScreen.SCOREBOARD) return
        stopActiveTimerInternal()
        val initialMatch = TimerEngine.start(
            config = RoundConfig.single(_uiState.value.scoreboardDurationSeconds),
            preferences = _uiState.value.soundPreferences,
            includeLeadIn = false
        ).snapshot.let(TimerEngine::pause)
        _uiState.update {
            it.copy(
                screen = TvScreen.SCOREBOARD,
                timer = initialMatch,
                scoreboardArmed = true
            )
        }
    }

    fun openSettings() {
        stopActiveTimerInternal()
        _uiState.update { it.copy(screen = TvScreen.SETTINGS) }
    }

    fun returnToClock() {
        stopActiveTimerInternal()
        _uiState.update {
            it.copy(
                screen = TvScreen.CLOCK,
                timer = TimerSnapshot(phase = TimerPhase.IDLE)
            )
        }
    }

    fun onBackPressed(): Boolean {
        return if (_uiState.value.screen != TvScreen.CLOCK) {
            returnToClock()
            true
        } else {
            false
        }
    }

    // Sound, Voice & Display Settings
    private fun playSound(cue: SoundCue, pack: SoundPack, voiceEnabled: Boolean) {
        if (_uiState.value.soundPreferences.soundEnabled) soundPlayer.play(cue, pack, voiceEnabled)
    }

    fun toggleSoundEnabled() {
        updateSoundPrefs { it.copy(soundEnabled = !it.soundEnabled) }
        if (!_uiState.value.soundPreferences.soundEnabled) soundPlayer.stopAll()
    }

    fun toggleVoiceEnabled() = updateSoundPrefs { it.copy(voiceEnabled = !it.voiceEnabled) }
    fun toggleWarnHalfway() = updateSoundPrefs { it.copy(warnHalfway = !it.warnHalfway) }
    fun toggleWarn2m() = updateSoundPrefs { it.copy(warn2m = !it.warn2m) }
    fun toggleWarn1m() = updateSoundPrefs { it.copy(warn1m = !it.warn1m) }
    fun toggleBeep50s() = updateSoundPrefs { it.copy(beep50s = !it.beep50s) }
    fun toggleWarn30s() = updateSoundPrefs { it.copy(warn30s = !it.warn30s) }
    fun toggleBeep10s() = updateSoundPrefs { it.copy(beep10s = !it.beep10s) }
    fun toggle24HourClock() = updateSoundPrefs { it.copy(use24HourClock = !it.use24HourClock) }

    fun setPrepSeconds(seconds: Int) {
        updateSoundPrefs { it.copy(prepSeconds = seconds.coerceIn(0, 15)) }
    }

    fun setSoundPack(pack: SoundPack) {
        updateSoundPrefs { it.copy(soundPack = pack) }
        val prefs = _uiState.value.soundPreferences
        playSound(SoundCue.FIGHT_HORN, pack, prefs.voiceEnabled)
    }

    fun testSoundCue() {
        val prefs = _uiState.value.soundPreferences
        playSound(SoundCue.FIGHT_HORN, prefs.soundPack, prefs.voiceEnabled)
    }

    fun soundManualBuzzer() {
        val prefs = _uiState.value.soundPreferences
        playSound(SoundCue.ROUND_END_ALARM, prefs.soundPack, voiceEnabled = false)
    }

    fun setLocaleOverride(localeTag: String?) {
        storage.saveLocaleOverride(localeTag)
        _uiState.update { it.copy(localeOverride = localeTag) }
    }

    // Custom Builder & Saveable Preset Slots
    fun toggleCustomEndless() {
        _uiState.update { it.copy(customBuilder = it.customBuilder.toggleEndless()) }
    }

    fun adjustCustomRepeat(increase: Boolean, step: Int = 1) {
        _uiState.update { it.copy(customBuilder = it.customBuilder.adjustRepeat(increase, step)) }
    }

    fun adjustCustomWork(increase: Boolean, stepSeconds: Long = 10L) {
        _uiState.update { it.copy(customBuilder = it.customBuilder.adjustWorkSeconds(increase, stepSeconds)) }
    }

    fun adjustCustomWorkSmart(increase: Boolean) {
        _uiState.update { it.copy(customBuilder = it.customBuilder.adjustWorkSmart(increase)) }
    }

    fun adjustCustomRest(increase: Boolean, stepSeconds: Long = 10L) {
        _uiState.update { it.copy(customBuilder = it.customBuilder.adjustRestSeconds(increase, stepSeconds)) }
    }

    fun adjustCustomRestSmart(increase: Boolean) {
        _uiState.update { it.copy(customBuilder = it.customBuilder.adjustRestSmart(increase)) }
    }

    fun loadTemplateIntoBuilder(config: RoundConfig) {
        _uiState.update {
            it.copy(customBuilder = CustomRoundBuilderState.fromRoundConfig(config))
        }
    }

    fun saveCustomToPresetSlot(slotIndex: Int, name: String? = null) {
        val config = _uiState.value.customBuilder.toRoundConfig()
        storage.savePresetSlot(slotIndex, config, name)
        storage.saveCustomRound(config)
        _uiState.update { it.copy(presetSlots = storage.loadPresetSlots()) }
    }

    fun resetPresetSlots() {
        storage.resetPresetSlots()
        _uiState.update { it.copy(presetSlots = storage.loadPresetSlots()) }
    }

    // Starting Timers
    fun adjustQuickRepeat(increase: Boolean) = updateQuickOptions { it.adjustRepeat(increase) }

    fun adjustQuickRest(increase: Boolean) = updateQuickOptions { it.adjustRest(increase) }

    private fun updateQuickOptions(transform: (QuickTimerOptions) -> QuickTimerOptions) {
        val next = transform(_uiState.value.quickOptions)
        storage.saveQuickOptions(next)
        _uiState.update { it.copy(quickOptions = next) }
    }

    fun startQuickTimer(mmSsLabel: String) {
        val seconds = TimeFormatter.parseMmSs(mmSsLabel)
        startRound(_uiState.value.quickOptions.toRoundConfig(seconds))
    }

    fun startStopwatch() {
        startRound(RoundConfig.stopwatch())
    }

    fun startPresetRound(config: RoundConfig) {
        startRound(config)
    }

    fun startCustomRound() {
        val config = _uiState.value.customBuilder.toRoundConfig()
        storage.saveCustomRound(config)
        startRound(config)
    }

    fun repeatLastSession() {
        val last = _uiState.value.lastSessionConfig ?: return
        startRound(last)
    }

    fun startRound(config: RoundConfig) {
        tickerJob?.cancel()
        soundPlayer.stopAll()
        storage.saveLastSession(config)

        val prefs = _uiState.value.soundPreferences
        val initialStep = TimerEngine.start(config = config, preferences = prefs, includeLeadIn = true)

        _uiState.update {
            it.copy(
                screen = TvScreen.ACTIVE_TIMER,
                lastSessionConfig = config,
                timer = initialStep.snapshot
            )
        }
        initialStep.cue?.let { cue -> playSound(cue, prefs.soundPack, prefs.voiceEnabled) }
        launchTickerLoop(activeScreen = TvScreen.ACTIVE_TIMER)
    }

    // Live Active Timer Controls
    fun pauseTimer() {
        val screen = _uiState.value.screen
        if (screen != TvScreen.ACTIVE_TIMER && screen != TvScreen.SCOREBOARD) return
        val paused = TimerEngine.pause(_uiState.value.timer)
        if (paused.phase == TimerPhase.PAUSED) {
            soundPlayer.stopAll()
            _uiState.update { it.copy(timer = paused) }
        }
    }

    fun resumeTimer() {
        val screen = _uiState.value.screen
        if (screen != TvScreen.ACTIVE_TIMER && screen != TvScreen.SCOREBOARD) return
        val resumed = TimerEngine.resume(_uiState.value.timer)
        _uiState.update { it.copy(timer = resumed) }
        if (tickerJob?.isActive != true) {
            launchTickerLoop(activeScreen = screen)
        }
    }

    fun togglePlayPause() {
        val screen = _uiState.value.screen
        if (screen != TvScreen.ACTIVE_TIMER && screen != TvScreen.SCOREBOARD) return
        if (screen == TvScreen.SCOREBOARD && _uiState.value.scoreboardArmed &&
            _uiState.value.timer.phase == TimerPhase.PAUSED
        ) {
            startScoreboardMatchWithCountIn()
            return
        }
        when (_uiState.value.timer.phase) {
            TimerPhase.PAUSED -> resumeTimer()
            TimerPhase.LEAD_IN,
            TimerPhase.RUNNING_WORK,
            TimerPhase.RUNNING_REST,
            TimerPhase.RUNNING_STOPWATCH -> pauseTimer()
            TimerPhase.FINISHED -> {
                if (screen == TvScreen.SCOREBOARD) resetScoreboardTimerOnly()
            }
            else -> Unit
        }
    }

    fun adjustActiveTime(deltaSeconds: Long) {
        val adjusted = TimerEngine.adjustRemainingSeconds(_uiState.value.timer, deltaSeconds)
        _uiState.update { it.copy(timer = adjusted) }
    }

    fun setExactRemainingTime(exactSeconds: Long) {
        val updated = TimerEngine.setExactRemainingSeconds(_uiState.value.timer, exactSeconds)
        _uiState.update { it.copy(timer = updated) }
    }

    fun restartActivePhase() {
        soundPlayer.stopAll()
        val restarted = TimerEngine.restartCurrentPhase(_uiState.value.timer)
        _uiState.update { it.copy(timer = restarted) }
    }

    fun skipActivePhase() {
        if (_uiState.value.screen != TvScreen.ACTIVE_TIMER) return
        soundPlayer.stopAll()
        val prefs = _uiState.value.soundPreferences
        val step = TimerEngine.skipToNextPhase(_uiState.value.timer, prefs)
        _uiState.update { it.copy(timer = step.snapshot) }
        step.cue?.let { cue -> playSound(cue, prefs.soundPack, prefs.voiceEnabled) }
        if (step.snapshot.phase == TimerPhase.FINISHED) {
            returnToClock()
        }
    }

    fun previousActiveRound() {
        if (_uiState.value.screen != TvScreen.ACTIVE_TIMER) return
        soundPlayer.stopAll()
        val prev = TimerEngine.previousRound(_uiState.value.timer)
        _uiState.update { it.copy(timer = prev) }
    }

    // IBJJF Scoreboard & Live Mobile Referee Controls
    fun selectScoreboardDuration(seconds: Long) {
        val clamped = seconds.coerceIn(10L, 3600L)
        stopActiveTimerInternal()
        val pausedMatch = TimerEngine.start(
            config = RoundConfig.single(clamped),
            preferences = _uiState.value.soundPreferences,
            includeLeadIn = false
        ).snapshot.let(TimerEngine::pause)
        _uiState.update {
            it.copy(
                screen = TvScreen.SCOREBOARD,
                scoreboardDurationSeconds = clamped,
                scoreboardArmed = true,
                timer = pausedMatch
            )
        }
    }

    fun resetScoreboardMatch(keepNames: Boolean = false) {
        stopActiveTimerInternal()
        val pausedMatch = TimerEngine.start(
            config = RoundConfig.single(_uiState.value.scoreboardDurationSeconds),
            preferences = _uiState.value.soundPreferences,
            includeLeadIn = false
        ).snapshot.let(TimerEngine::pause)
        _uiState.update { state ->
            state.copy(
                screen = TvScreen.SCOREBOARD,
                matchScore = if (keepNames) state.matchScore.resetScoresKeepNames() else MatchScoreState(),
                scoreboardArmed = true,
                timer = pausedMatch
            )
        }
    }

    private fun resetScoreboardTimerOnly() {
        val pausedMatch = TimerEngine.start(
            config = RoundConfig.single(_uiState.value.scoreboardDurationSeconds),
            preferences = _uiState.value.soundPreferences,
            includeLeadIn = false
        ).snapshot.let(TimerEngine::pause)
        _uiState.update { it.copy(timer = pausedMatch, scoreboardArmed = true) }
    }

    private fun startScoreboardMatchWithCountIn() {
        val state = _uiState.value
        val secs = state.timer.remainingSeconds.coerceAtLeast(1L)
        val prefs = state.soundPreferences
        val initial = TimerEngine.start(RoundConfig.single(secs), prefs, includeLeadIn = true)
        _uiState.update { it.copy(timer = initial.snapshot, scoreboardArmed = false) }
        initial.cue?.let { playSound(it, prefs.soundPack, prefs.voiceEnabled) }
        launchTickerLoop(activeScreen = TvScreen.SCOREBOARD)
    }

    private fun resolveCue(cue: SoundCue, screen: TvScreen): SoundCue =
        if (screen == TvScreen.SCOREBOARD && cue == SoundCue.ROUND_END_ALARM) SoundCue.MATCH_END else cue

    fun setFighterName(isFighterA: Boolean, name: String) {
        ensureScoreboardVisible()
        _uiState.update { state ->
            val next = if (isFighterA) state.matchScore.withFighterAName(name) else state.matchScore.withFighterBName(name)
            state.copy(matchScore = next)
        }
    }

    fun setCategoryLabel(label: String) {
        ensureScoreboardVisible()
        _uiState.update { state ->
            state.copy(matchScore = state.matchScore.withCategoryLabel(label))
        }
    }

    fun swapScoreboardFighters() {
        ensureScoreboardVisible()
        _uiState.update { state ->
            state.copy(matchScore = state.matchScore.swapSides())
        }
    }

    fun adjustScore(isFighterA: Boolean, kind: ScoreMetricKind, delta: Int) {
        ensureScoreboardVisible()
        _uiState.update { state ->
            val s = state.matchScore
            val next = when (kind) {
                ScoreMetricKind.POINTS -> if (isFighterA) s.addFighterAPoints(delta) else s.addFighterBPoints(delta)
                ScoreMetricKind.ADVANTAGES -> if (isFighterA) s.addFighterAAdvantages(delta) else s.addFighterBAdvantages(delta)
                ScoreMetricKind.PENALTIES -> if (isFighterA) s.addFighterAPenalties(delta) else s.addFighterBPenalties(delta)
            }
            state.copy(matchScore = next)
        }
    }

    fun setExactScore(isFighterA: Boolean, kind: ScoreMetricKind, exactValue: Int) {
        ensureScoreboardVisible()
        _uiState.update { state ->
            val s = state.matchScore
            val next = when (kind) {
                ScoreMetricKind.POINTS -> if (isFighterA) s.setFighterAPoints(exactValue) else s.setFighterBPoints(exactValue)
                ScoreMetricKind.ADVANTAGES -> if (isFighterA) s.setFighterAAdvantages(exactValue) else s.setFighterBAdvantages(exactValue)
                ScoreMetricKind.PENALTIES -> if (isFighterA) s.setFighterAPenalties(exactValue) else s.setFighterBPenalties(exactValue)
            }
            state.copy(matchScore = next)
        }
    }

    // Match Results Persistence (Save / Delete Single / Delete All)
    fun saveCurrentMatchResult(
        winnerOverride: MatchWinnerSide? = null,
        methodOverride: String? = null
    ): MatchResultRecord {
        val state = _uiState.value
        val s = state.matchScore
        val winner = winnerOverride ?: s.computeIbjjfWinner()
        val method = methodOverride?.takeIf { it.isNotBlank() } ?: s.defaultIbjjfMethod()
        val timestamp = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())

        val record = MatchResultRecord(
            id = UUID.randomUUID().toString().take(8),
            timestamp = timestamp,
            category = s.categoryLabel,
            fighterAName = s.fighterAName.ifBlank { "Atleta 1 (Branco)" },
            fighterAPoints = s.fighterAPoints,
            fighterAAdvantages = s.fighterAAdvantages,
            fighterAPenalties = s.fighterAPenalties,
            fighterBName = s.fighterBName.ifBlank { "Atleta 2 (Vermelho)" },
            fighterBPoints = s.fighterBPoints,
            fighterBAdvantages = s.fighterBAdvantages,
            fighterBPenalties = s.fighterBPenalties,
            winnerSide = winner,
            method = method,
            durationSeconds = state.scoreboardDurationSeconds,
            remainingSeconds = state.timer.remainingSeconds
        )

        val updated = listOf(record) + state.matchResults
        storage.saveMatchResults(updated)
        _uiState.update { it.copy(matchResults = updated) }
        return record
    }

    fun deleteMatchResult(id: String) {
        val updated = _uiState.value.matchResults.filterNot { it.id == id }
        storage.saveMatchResults(updated)
        _uiState.update { it.copy(matchResults = updated) }
    }

    fun clearAllMatchResults() {
        storage.saveMatchResults(emptyList())
        _uiState.update { it.copy(matchResults = emptyList()) }
    }

    // LAN Wi-Fi Server Status & Real-Time JSON Protocol
    fun updateLanServerStatus(url: String?, connectedClients: Int) {
        _uiState.update {
            it.copy(
                lanRefereeUrl = url,
                lanConnectedClients = connectedClients.coerceAtLeast(0)
            )
        }
    }

    fun toRefereeStateJson(): String {
        val st = _uiState.value
        val sc = st.matchScore
        val isRunning = st.screen == TvScreen.SCOREBOARD &&
            (st.timer.phase == TimerPhase.RUNNING_WORK || st.timer.phase == TimerPhase.LEAD_IN)
        val leadInLabel = if (st.screen == TvScreen.SCOREBOARD && st.timer.phase == TimerPhase.LEAD_IN) {
            if (st.timer.leadInCount > 0) st.timer.leadInCount.toString() else "GO"
        } else ""
        val remSec = if (st.screen == TvScreen.SCOREBOARD) st.timer.remainingSeconds else st.scoreboardDurationSeconds
        return SimpleJson.encodeObject(
            linkedMapOf(
                "screen" to st.screen.name,
                "isRunning" to isRunning,
                "leadInLabel" to leadInLabel,
                "remainingSeconds" to remSec,
                "formattedClock" to TimeFormatter.formatMmSs(remSec),
                "durationSeconds" to st.scoreboardDurationSeconds,
                "fighterAName" to sc.fighterAName,
                "fighterBName" to sc.fighterBName,
                "category" to sc.categoryLabel,
                "aPoints" to sc.fighterAPoints,
                "aAdvantages" to sc.fighterAAdvantages,
                "aPenalties" to sc.fighterAPenalties,
                "bPoints" to sc.fighterBPoints,
                "bAdvantages" to sc.fighterBAdvantages,
                "bPenalties" to sc.fighterBPenalties,
                "computedWinner" to sc.computeIbjjfWinner().name,
                "defaultMethod" to sc.defaultIbjjfMethod(),
                "results" to st.matchResults.map { it.toMap() }
            )
        )
    }

    /** Executes an incoming mobile WebSocket/HTTP referee command immediately with zero buffering. */
    fun handleRefereeJsonCommand(rawJson: String): String {
        val obj = SimpleJson.parseObject(rawJson)
        val cmd = obj["cmd"]?.toString().orEmpty()
        val isSideA = obj["side"]?.toString() != "B"

        when (cmd) {
            // A phone (re)connecting must never interrupt a running timer: only hop from the idle clock
            "open_scoreboard" -> if (_uiState.value.screen == TvScreen.CLOCK) openScoreboard()
            "set_name" -> setFighterName(isSideA, obj["name"]?.toString().orEmpty())
            "set_category" -> setCategoryLabel(obj["category"]?.toString().orEmpty())
            "score_delta" -> {
                val kind = parseScoreKind(obj["field"]?.toString())
                val delta = (obj["delta"] as? Number)?.toInt() ?: 0
                adjustScore(isSideA, kind, delta)
            }
            "score_set" -> {
                val kind = parseScoreKind(obj["field"]?.toString())
                val value = (obj["value"] as? Number)?.toInt() ?: 0
                setExactScore(isSideA, kind, value)
            }
            "swap_sides" -> swapScoreboardFighters()
            "play_pause" -> {
                ensureScoreboardVisible()
                togglePlayPause()
            }
            "time_delta" -> {
                ensureScoreboardVisible()
                val delta = (obj["delta"] as? Number)?.toLong() ?: 0L
                adjustActiveTime(delta)
            }
            "time_set" -> {
                ensureScoreboardVisible()
                val sec = (obj["seconds"] as? Number)?.toLong() ?: 300L
                setExactRemainingTime(sec)
            }
            "set_duration" -> {
                val sec = (obj["seconds"] as? Number)?.toLong() ?: 300L
                selectScoreboardDuration(sec)
            }
            "reset_match" -> {
                val keepNames = obj["keepNames"] == true
                resetScoreboardMatch(keepNames = keepNames)
            }
            "buzzer" -> soundManualBuzzer()
            "save_result" -> {
                val winStr = obj["winner"]?.toString()
                val winSide = MatchWinnerSide.entries.firstOrNull { it.name == winStr }
                val method = obj["method"]?.toString()
                saveCurrentMatchResult(winSide, method)
            }
            "delete_result" -> {
                val id = obj["id"]?.toString().orEmpty()
                if (id.isNotEmpty()) deleteMatchResult(id)
            }
            "clear_results" -> clearAllMatchResults()
        }
        return toRefereeStateJson()
    }

    private fun parseScoreKind(field: String?): ScoreMetricKind = when (field) {
        "advantages", "adv" -> ScoreMetricKind.ADVANTAGES
        "penalties", "pen" -> ScoreMetricKind.PENALTIES
        else -> ScoreMetricKind.POINTS
    }

    private fun ensureScoreboardVisible() {
        if (_uiState.value.screen != TvScreen.SCOREBOARD) {
            openScoreboard()
        }
    }

    fun dispose() {
        tickerJob?.cancel()
        soundPlayer.release()
    }

    private fun updateSoundPrefs(transform: (SoundPreferences) -> SoundPreferences) {
        val next = transform(_uiState.value.soundPreferences)
        storage.saveSoundPreferences(next)
        _uiState.update { it.copy(soundPreferences = next) }
    }

    private fun stopActiveTimerInternal() {
        tickerJob?.cancel()
        tickerJob = null
        soundPlayer.stopAll()
    }

    private fun launchTickerLoop(activeScreen: TvScreen) {
        tickerJob?.cancel()
        tickerJob = scope.launch(tickDispatcher) {
            while (isActive) {
                delay(1_000L)
                val state = _uiState.value
                if (state.screen != activeScreen) break

                if (state.timer.phase == TimerPhase.PAUSED) {
                    continue
                }

                val step = TimerEngine.tickOneSecond(state.timer, state.soundPreferences)
                _uiState.update { it.copy(timer = step.snapshot) }
                step.cue?.let { cue ->
                    playSound(
                        resolveCue(cue, activeScreen),
                        state.soundPreferences.soundPack,
                        state.soundPreferences.voiceEnabled
                    )
                }

                if (step.snapshot.phase == TimerPhase.FINISHED) {
                    if (activeScreen == TvScreen.ACTIVE_TIMER) {
                        delay(3_000L)
                        if (_uiState.value.screen == TvScreen.ACTIVE_TIMER &&
                            _uiState.value.timer.phase == TimerPhase.FINISHED
                        ) {
                            _uiState.update {
                                it.copy(
                                    screen = TvScreen.CLOCK,
                                    timer = TimerSnapshot(phase = TimerPhase.IDLE)
                                )
                            }
                        }
                    }
                    break
                }
            }
        }
    }
}

enum class ScoreMetricKind {
    POINTS,
    ADVANTAGES,
    PENALTIES
}
