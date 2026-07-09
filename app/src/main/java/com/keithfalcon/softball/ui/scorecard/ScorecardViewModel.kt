package com.keithfalcon.softball.ui.scorecard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keithfalcon.softball.SoftballApp
import com.keithfalcon.softball.data.Game
import com.keithfalcon.softball.data.GameStatus
import com.keithfalcon.softball.data.LineupEntry
import com.keithfalcon.softball.data.LineupQueue
import com.keithfalcon.softball.data.LineupType
import com.keithfalcon.softball.data.OpponentInning
import com.keithfalcon.softball.data.Outcome
import com.keithfalcon.softball.data.PlateAppearance
import com.keithfalcon.softball.data.Player
import com.keithfalcon.softball.data.RunnerResult
import com.keithfalcon.softball.logic.LineupEngine
import com.keithfalcon.softball.logic.ScorecardEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ScorecardUiState(
    val game: Game? = null,
    val teamName: String = "",
    val playersById: Map<Long, Player> = emptyMap(),
    val rows: List<ScorecardEngine.Row> = emptyList(),
    val currentInning: Int = 1,
    val outs: Int = 0,
    val runsByInning: Map<Int, Int> = emptyMap(),
    val ourTotal: Int = 0,
    val oppByInning: Map<Int, Int> = emptyMap(),
    val oppTotal: Int = 0,
    val nextSlot: LineupEngine.Slot? = null,
    /** The five batters after [nextSlot], for the scrollable "up next" list. */
    val upcoming: List<LineupEngine.Slot> = emptyList(),
    val hasLineup: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)

class ScorecardViewModel(app: SoftballApp, private val gameId: Long) : ViewModel() {

    private val db = app.database
    private val paDao = db.plateAppearanceDao()
    private val gameDao = db.gameDao()
    private val oppDao = db.opponentInningDao()
    private val lineupDao = db.lineupDao()

    private val editMutex = Mutex()
    private val undoStack = ArrayDeque<List<PlateAppearance>>()
    private val redoStack = ArrayDeque<List<PlateAppearance>>()
    private val undoState = MutableStateFlow(0 to 0) // (undo size, redo size)

    private data class LineupData(
        val config: LineupEngine.Config?,
        val hasLineup: Boolean,
    )

    private val lineupFlow = combine(
        lineupDao.observe(gameId),
        lineupDao.observeEntries(gameId),
    ) { lineup, entries ->
        if (lineup == null || entries.isEmpty()) LineupData(null, false)
        else LineupData(
            LineupEngine.Config(
                type = lineup.type,
                battingQueue = entries.filter { it.queue == LineupQueue.BATTING }
                    .sortedBy { it.orderInQueue }.map { it.playerId },
                maleQueue = entries.filter { it.queue == LineupQueue.MALE }
                    .sortedBy { it.orderInQueue }.map { it.playerId },
                femaleQueue = entries.filter { it.queue == LineupQueue.FEMALE }
                    .sortedBy { it.orderInQueue }.map { it.playerId },
                ratioMale = lineup.ratioMale,
                ratioFemale = lineup.ratioFemale,
                autoOutOnEmptyFemaleSlot = lineup.autoOutOnEmptyFemaleSlot,
            ),
            true,
        )
    }

    private val coreFlow = combine(
        gameDao.observe(gameId),
        db.teamDao().observeFirstTeam(),
        db.playerDao().observeEveryone(),
    ) { game, team, players -> Triple(game, team, players) }

    private val scoringFlow = combine(
        paDao.observeForGame(gameId),
        oppDao.observeForGame(gameId),
    ) { pas, opp -> pas to opp }

    val ui: StateFlow<ScorecardUiState> = combine(
        coreFlow,
        scoringFlow,
        lineupFlow,
        undoState,
    ) { (game, team, players), (pas, opp), lineup, (undoSize, redoSize) ->
        val derived = ScorecardEngine.derive(pas)
        val oppByInning = opp.associate { it.inning to it.runs }
        // One simulated preview covers both the now-batting card (first slot) and the
        // "up next" list (the five after it).
        val lookahead = lineup.config?.let {
            if (game?.status == GameStatus.FINAL) emptyList()
            else LineupEngine.preview(it, battingHistory(pas), UPCOMING_COUNT + 1)
        } ?: emptyList()
        ScorecardUiState(
            game = game,
            teamName = team?.name ?: "",
            playersById = players.associateBy { it.id },
            rows = derived.rows,
            currentInning = derived.currentInning,
            outs = derived.outsInCurrentInning,
            runsByInning = derived.runsByInning,
            ourTotal = derived.totalRuns,
            oppByInning = oppByInning,
            oppTotal = oppByInning.values.sum(),
            nextSlot = lookahead.firstOrNull(),
            upcoming = lookahead.drop(1),
            hasLineup = lineup.hasLineup,
            canUndo = undoSize > 0,
            canRedo = redoSize > 0,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScorecardUiState())

    /** Batting slots consumed so far: every PA except manual "+1 out" rows (spec §5/§6.4). */
    private fun battingHistory(pas: List<PlateAppearance>): List<Long?> =
        pas.filter { it.outcome != Outcome.MANUAL_OUT }
            .map { if (it.outcome == Outcome.AUTO_OUT) null else it.playerId }

    // ---- Mutations (all snapshot for undo, write through, resync game score) ----

    private fun mutate(block: suspend (current: List<PlateAppearance>) -> List<PlateAppearance>?) {
        viewModelScope.launch {
            editMutex.withLock {
                val current = paDao.forGame(gameId)
                val next = block(current) ?: return@withLock
                undoStack.addLast(current)
                while (undoStack.size > UNDO_LIMIT) undoStack.removeFirst()
                redoStack.clear()
                paDao.replaceForGame(gameId, ScorecardEngine.resequence(next))
                afterMutation()
            }
        }
    }

    private suspend fun afterMutation() {
        undoState.value = undoStack.size to redoStack.size
        syncGameScore()
    }

    private suspend fun syncGameScore() {
        val game = gameDao.byId(gameId) ?: return
        val our = ScorecardEngine.derive(paDao.forGame(gameId)).totalRuns
        val opp = oppDao.forGame(gameId).sumOf { it.runs }
        if (game.ourScore != our || game.theirScore != opp) {
            gameDao.update(game.copy(ourScore = our, theirScore = opp))
        }
    }

    fun recordOutcome(outcome: Outcome, rbi: Int = 0) {
        mutate { current ->
            val slot = LineupEngine.nextSlot(currentConfig() ?: return@mutate null, battingHistory(current))
                ?: return@mutate null
            val (playerId, actualOutcome) = when (slot) {
                is LineupEngine.Slot.Batter -> slot.playerId to outcome
                LineupEngine.Slot.AutoOut -> null to Outcome.AUTO_OUT
            }
            current + PlateAppearance(
                gameId = gameId,
                sequence = current.size + 1,
                playerId = playerId,
                outcome = actualOutcome,
                runnerResult = defaultRunnerResult(actualOutcome),
                rbi = rbi,
            )
        }
        markInProgress()
    }

    fun recordAutoOut() {
        mutate { current ->
            current + PlateAppearance(
                gameId = gameId,
                sequence = current.size + 1,
                playerId = null,
                outcome = Outcome.AUTO_OUT,
            )
        }
    }

    fun addManualOut() {
        mutate { current ->
            current + PlateAppearance(
                gameId = gameId,
                sequence = current.size + 1,
                playerId = null,
                outcome = Outcome.MANUAL_OUT,
                note = "Manual out",
            )
        }
    }

    fun updateRow(updated: PlateAppearance) {
        mutate { current -> current.map { if (it.id == updated.id) updated else it } }
    }

    fun setRunnerResult(paId: Long, result: RunnerResult) {
        mutate { current -> current.map { if (it.id == paId) it.copy(runnerResult = result) else it } }
    }

    fun deleteRow(paId: Long) {
        mutate { current -> current.filterNot { it.id == paId } }
    }

    /** Insert a blank-ish PA before/after an existing row (spec §6.5 — forgot a batter). */
    fun insertRow(afterPaId: Long?, playerId: Long?, outcome: Outcome, runnerResult: RunnerResult) {
        mutate { current ->
            val newPa = PlateAppearance(
                gameId = gameId,
                sequence = 0, // resequenced on save
                playerId = playerId,
                outcome = outcome,
                runnerResult = runnerResult,
            )
            if (afterPaId == null) listOf(newPa) + current
            else current.flatMap { if (it.id == afterPaId) listOf(it, newPa) else listOf(it) }
        }
    }

    fun moveRow(paId: Long, delta: Int) {
        mutate { current ->
            val index = current.indexOfFirst { it.id == paId }
            val target = index + delta
            if (index < 0 || target < 0 || target >= current.size) return@mutate null
            current.toMutableList().apply { add(target, removeAt(index)) }
        }
    }

    fun undo() {
        viewModelScope.launch {
            editMutex.withLock {
                val previous = undoStack.removeLastOrNull() ?: return@withLock
                redoStack.addLast(paDao.forGame(gameId))
                paDao.replaceForGame(gameId, previous)
                afterMutation()
            }
        }
    }

    fun redo() {
        viewModelScope.launch {
            editMutex.withLock {
                val next = redoStack.removeLastOrNull() ?: return@withLock
                undoStack.addLast(paDao.forGame(gameId))
                paDao.replaceForGame(gameId, next)
                afterMutation()
            }
        }
    }

    fun setOpponentRuns(inning: Int, runs: Int) {
        viewModelScope.launch {
            editMutex.withLock {
                oppDao.upsert(OpponentInning(gameId, inning, runs.coerceAtLeast(0)))
                syncGameScore()
            }
        }
    }

    fun endGame() = setStatus(GameStatus.FINAL)

    fun reopenGame() = setStatus(GameStatus.IN_PROGRESS)

    private fun markInProgress() {
        viewModelScope.launch {
            val game = gameDao.byId(gameId) ?: return@launch
            if (game.status == GameStatus.SCHEDULED) {
                gameDao.update(game.copy(status = GameStatus.IN_PROGRESS))
            }
        }
    }

    private fun setStatus(status: GameStatus) {
        viewModelScope.launch {
            gameDao.byId(gameId)?.let { gameDao.update(it.copy(status = status)) }
        }
    }

    /** Remove a no-show from the rotation mid-game; past at-bats stay (spec §12.2). */
    fun removeFromRotation(playerId: Long) {
        viewModelScope.launch {
            editMutex.withLock {
                val lineup = lineupDao.byGame(gameId) ?: return@withLock
                val entries = lineupDao.entries(gameId).filterNot { it.playerId == playerId }
                lineupDao.replace(lineup, renumber(entries))
            }
        }
    }

    /** Late arrival joins the back of their queue (spec §5.3). */
    fun addToRotation(player: Player) {
        viewModelScope.launch {
            editMutex.withLock {
                val lineup = lineupDao.byGame(gameId) ?: return@withLock
                val entries = lineupDao.entries(gameId)
                if (entries.any { it.playerId == player.id }) return@withLock
                val queue = when {
                    lineup.type == LineupType.STATIC -> LineupQueue.BATTING
                    player.sex == com.keithfalcon.softball.data.Sex.FEMALE -> LineupQueue.FEMALE
                    else -> LineupQueue.MALE
                }
                val maxOrder = entries.filter { it.queue == queue }.maxOfOrNull { it.orderInQueue } ?: -1
                lineupDao.insertEntries(
                    listOf(LineupEntry(gameId, queue, maxOrder + 1, player.id))
                )
            }
        }
    }

    private fun renumber(entries: List<LineupEntry>): List<LineupEntry> =
        entries.groupBy { it.queue }.flatMap { (_, queueEntries) ->
            queueEntries.sortedBy { it.orderInQueue }
                .mapIndexed { i, e -> e.copy(orderInQueue = i) }
        }

    private suspend fun currentConfig(): LineupEngine.Config? {
        val lineup = lineupDao.byGame(gameId) ?: return null
        val entries = lineupDao.entries(gameId)
        if (entries.isEmpty()) return null
        return LineupEngine.Config(
            type = lineup.type,
            battingQueue = entries.filter { it.queue == LineupQueue.BATTING }
                .sortedBy { it.orderInQueue }.map { it.playerId },
            maleQueue = entries.filter { it.queue == LineupQueue.MALE }
                .sortedBy { it.orderInQueue }.map { it.playerId },
            femaleQueue = entries.filter { it.queue == LineupQueue.FEMALE }
                .sortedBy { it.orderInQueue }.map { it.playerId },
            ratioMale = lineup.ratioMale,
            ratioFemale = lineup.ratioFemale,
            autoOutOnEmptyFemaleSlot = lineup.autoOutOnEmptyFemaleSlot,
        )
    }

    companion object {
        private const val UNDO_LIMIT = 20
        private const val UPCOMING_COUNT = 5

        fun defaultRunnerResult(outcome: Outcome): RunnerResult = when {
            outcome == Outcome.HOME_RUN -> RunnerResult.SCORED
            outcome.reachedBase -> RunnerResult.ON_BASE
            else -> RunnerResult.NONE
        }
    }
}
