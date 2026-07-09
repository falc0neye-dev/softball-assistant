package com.keithfalcon.softball.ui.scorecard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keithfalcon.softball.data.GameStatus
import com.keithfalcon.softball.data.Outcome
import com.keithfalcon.softball.data.PlateAppearance
import com.keithfalcon.softball.data.Player
import com.keithfalcon.softball.data.RunnerResult
import com.keithfalcon.softball.data.Sex
import com.keithfalcon.softball.logic.LineupEngine
import com.keithfalcon.softball.logic.ScorecardEngine
import com.keithfalcon.softball.ui.common.softballViewModel
import com.keithfalcon.softball.ui.theme.Amber
import com.keithfalcon.softball.ui.theme.AmberDeep
import com.keithfalcon.softball.ui.theme.FieldGreen
import com.keithfalcon.softball.ui.theme.GreenOnDark
import com.keithfalcon.softball.ui.theme.MonoDigits
import com.keithfalcon.softball.ui.theme.Steel
import com.keithfalcon.softball.ui.theme.TextFaint

private val quickOutcomes = listOf(
    Outcome.SINGLE, Outcome.DOUBLE, Outcome.HOME_RUN,
    Outcome.WALK, Outcome.FIELDERS_CHOICE, Outcome.OUT,
)

private val onBaseOutcomes = listOf(
    Outcome.SINGLE, Outcome.DOUBLE, Outcome.TRIPLE, Outcome.HOME_RUN,
    Outcome.WALK, Outcome.FIELDERS_CHOICE, Outcome.REACHED_ON_ERROR, Outcome.HIT_BY_PITCH,
)

private val outOutcomes = listOf(
    Outcome.GROUNDOUT, Outcome.FLYOUT, Outcome.STRIKEOUT,
    Outcome.SAC_FLY, Outcome.FC_OUT, Outcome.OUT,
)

@Composable
fun ScorecardTab(gameId: Long) {
    val vm = softballViewModel(key = "scorecard-$gameId") { app -> ScorecardViewModel(app, gameId) }
    val ui by vm.ui.collectAsState()

    var editTarget by remember { mutableStateOf<PlateAppearance?>(null) }
    var insertAfterId by remember { mutableStateOf<Long?>(-1L) } // -1 sentinel = closed
    var showFullOutcomes by remember { mutableStateOf(false) }
    var showOpponentDialog by remember { mutableStateOf(false) }

    val isFinal = ui.game?.status == GameStatus.FINAL

    Column(Modifier.fillMaxSize()) {
        ScoreHeader(ui, onEndGame = vm::endGame, onReopen = vm::reopenGame)

        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(10.dp))
            LineScoreStrip(
                runsByInning = ui.runsByInning,
                oppByInning = ui.oppByInning,
                currentInning = ui.currentInning,
                ourTotal = ui.ourTotal,
                oppTotal = ui.oppTotal,
                onOpponentTap = { showOpponentDialog = true },
            )
            Spacer(Modifier.height(10.dp))
        }

        if (!ui.hasLineup) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "No lineup yet — set one on the Lineup tab,\nthen start the game.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        val listState = rememberLazyListState()
        LaunchedEffect(ui.rows.size) {
            if (ui.rows.isNotEmpty()) listState.animateScrollToItem(ui.rows.size - 1)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(ui.rows.size, key = { ui.rows[it].pa.id }) { index ->
                val row = ui.rows[index]
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PaRow(
                        row = row,
                        player = row.pa.playerId?.let { ui.playersById[it] },
                        onClick = { if (!isFinal) editTarget = row.pa },
                        onInsertAfter = { if (!isFinal) insertAfterId = row.pa.id },
                        onGlyphTap = {
                            if (!isFinal && row.pa.outcome.reachedBase) {
                                vm.setRunnerResult(row.pa.id, nextRunnerResult(row.pa.runnerResult))
                            }
                        },
                    )
                    if (row.endsInning) {
                        InningSeparator(
                            inning = row.inning,
                            runsThisInning = row.runsInEndedInning,
                            ourTotal = ui.runsByInning.filterKeys { it <= row.inning }.values.sum(),
                            oppTotal = ui.oppByInning.filterKeys { it <= row.inning }.values.sum(),
                        )
                    }
                }
            }
            if (ui.rows.isEmpty()) {
                item {
                    Text(
                        "Game on — tap an outcome below to record the first batter.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 20.dp).fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        if (!isFinal) {
            NowBattingCard(
                slot = ui.nextSlot,
                player = (ui.nextSlot as? LineupEngine.Slot.Batter)?.let { ui.playersById[it.playerId] },
                sequence = ui.rows.size + 1,
                onOutcome = vm::recordOutcome,
                onAutoOut = vm::recordAutoOut,
                onMore = { showFullOutcomes = true },
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = vm::undo,
                    enabled = ui.canUndo,
                    modifier = Modifier.weight(1f),
                ) { Text("↩  Undo") }
                OutlinedButton(
                    onClick = vm::addManualOut,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                ) { Text("+1 Out (baserunner)", color = MaterialTheme.colorScheme.error) }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text("Game is final — reopen from the header to edit.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    // --- Sheets & dialogs ---
    editTarget?.let { pa ->
        EditPaSheet(
            title = "Edit plate appearance",
            initial = pa,
            players = ui.playersById.values.sortedBy { it.fullName },
            onSave = { vm.updateRow(it); editTarget = null },
            onDelete = { vm.deleteRow(pa.id); editTarget = null },
            onMove = { delta -> vm.moveRow(pa.id, delta) },
            onDismiss = { editTarget = null },
        )
    }
    if (insertAfterId != -1L) {
        val afterId = insertAfterId
        EditPaSheet(
            title = "Insert plate appearance",
            initial = PlateAppearance(
                gameId = gameId, sequence = 0, playerId = null,
                outcome = Outcome.OUT, runnerResult = RunnerResult.NONE,
            ),
            players = ui.playersById.values.sortedBy { it.fullName },
            onSave = {
                vm.insertRow(afterId, it.playerId, it.outcome, it.runnerResult)
                insertAfterId = -1L
            },
            onDelete = null,
            onMove = null,
            onDismiss = { insertAfterId = -1L },
        )
    }
    if (showFullOutcomes) {
        FullOutcomeSheet(
            onPick = { vm.recordOutcome(it); showFullOutcomes = false },
            onDismiss = { showFullOutcomes = false },
        )
    }
    if (showOpponentDialog) {
        OpponentScoreDialog(
            currentInning = ui.currentInning,
            oppByInning = ui.oppByInning,
            onSet = vm::setOpponentRuns,
            onDismiss = { showOpponentDialog = false },
        )
    }
}

@Composable
private fun ScoreHeader(ui: ScorecardUiState, onEndGame: () -> Unit, onReopen: () -> Unit) {
    Surface(color = FieldGreen) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "SCORECARD · VS. ${ui.game?.opponent?.uppercase() ?: ""}",
                        color = GreenOnDark, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${ui.teamName.uppercase().ifEmpty { "US" }} ${ui.ourTotal} — ${ui.oppTotal} ${ui.game?.opponent?.uppercase() ?: "THEM"}",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Box(
                        Modifier
                            .background(Color(0xFF174226), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(
                            ordinal(ui.currentInning),
                            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "OUTS ", color = GreenOnDark, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        )
                        OutDots(ui.outs)
                    }
                }
            }
            val status = ui.game?.status
            if (status == GameStatus.IN_PROGRESS || status == GameStatus.FINAL) {
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (status == GameStatus.FINAL) {
                        TextButton(onClick = onReopen) {
                            Text("REOPEN GAME", color = GreenOnDark, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        }
                    } else {
                        TextButton(onClick = onEndGame) {
                            Text("END GAME", color = GreenOnDark, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PaRow(
    row: ScorecardEngine.Row,
    player: Player?,
    onClick: () -> Unit,
    onInsertAfter: () -> Unit,
    onGlyphTap: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true }),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "%2d".format(row.pa.sequence),
                style = MonoDigits,
                color = TextFaint,
                modifier = Modifier.width(30.dp),
            )
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    player?.fullName ?: playerlessLabel(row.pa.outcome),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (player == null) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                )
                if (player?.sex == Sex.FEMALE) {
                    Spacer(Modifier.width(6.dp))
                    SexDot(Sex.FEMALE)
                }
            }
            OutcomeBadge(row.pa.outcome)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(24.dp).combinedClickable(onClick = onGlyphTap), contentAlignment = Alignment.Center) {
                DiamondGlyph(row.displayRunnerResult, size = 20.dp)
            }
        }
    }
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        DropdownMenuItem(text = { Text("Insert row after this one") }, onClick = { menuOpen = false; onInsertAfter() })
        DropdownMenuItem(text = { Text("Edit") }, onClick = { menuOpen = false; onClick() })
    }
}

private fun playerlessLabel(outcome: Outcome) = when (outcome) {
    Outcome.AUTO_OUT -> "Auto out (no female)"
    Outcome.MANUAL_OUT -> "Out on bases"
    else -> "(no player)"
}

@Composable
fun SexDot(sex: Sex, size: androidx.compose.ui.unit.Dp = 16.dp) {
    val (bg, letter) = when (sex) {
        Sex.MALE -> Steel to "M"
        Sex.FEMALE -> Amber to "F"
    }
    Box(Modifier.size(size).background(bg, CircleShape), contentAlignment = Alignment.Center) {
        Text(letter, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun OutcomeBadge(outcome: Outcome) {
    val onBase = outcome.reachedBase
    Box(
        Modifier
            .background(
                if (onBase) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(13.dp),
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            outcome.label,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (onBase) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NowBattingCard(
    slot: LineupEngine.Slot?,
    player: Player?,
    sequence: Int,
    onOutcome: (Outcome) -> Unit,
    onAutoOut: () -> Unit,
    onMore: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "NOW BATTING · #$sequence",
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(4.dp))
            when (slot) {
                is LineupEngine.Slot.Batter -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            player?.fullName ?: "Unknown player",
                            fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (player?.sex == Sex.FEMALE) {
                            Spacer(Modifier.width(8.dp))
                            SexDot(Sex.FEMALE)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        quickOutcomes.forEach { outcome ->
                            OutcomeChipButton(outcome, onClick = { onOutcome(outcome) })
                        }
                        TextButton(onClick = onMore, contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)) {
                            Text("···", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Black)
                        }
                    }
                }
                LineupEngine.Slot.AutoOut -> {
                    Text(
                        "Female slot — no female available",
                        fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onAutoOut) {
                        Text("Record automatic out", color = MaterialTheme.colorScheme.error)
                    }
                }
                null -> Text(
                    "No eligible batters — check the rotation on the Lineup tab.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OutcomeChipButton(outcome: Outcome, onClick: () -> Unit) {
    val isOut = !outcome.reachedBase
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            outcome.label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (isOut) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullOutcomeSheet(onPick: (Outcome) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("ON BASE", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp, color = FieldGreen)
            Spacer(Modifier.height(8.dp))
            ChipFlow(onBaseOutcomes, onPick)
            Spacer(Modifier.height(16.dp))
            Text("OUT", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            ChipFlow(outOutcomes, onPick)
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(outcomes: List<Outcome>, onPick: (Outcome) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        outcomes.forEach { outcome ->
            FilterChip(
                selected = false,
                onClick = { onPick(outcome) },
                label = { Text(fullLabel(outcome), fontWeight = FontWeight.Bold) },
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}

fun fullLabel(outcome: Outcome): String = when (outcome) {
    Outcome.SINGLE -> "1B Single"
    Outcome.DOUBLE -> "2B Double"
    Outcome.TRIPLE -> "3B Triple"
    Outcome.HOME_RUN -> "HR Home run"
    Outcome.WALK -> "BB Walk"
    Outcome.FIELDERS_CHOICE -> "FC (safe)"
    Outcome.REACHED_ON_ERROR -> "E Error"
    Outcome.HIT_BY_PITCH -> "HBP"
    Outcome.GROUNDOUT -> "Groundout"
    Outcome.FLYOUT -> "Flyout"
    Outcome.STRIKEOUT -> "Strikeout"
    Outcome.SAC_FLY -> "Sac fly"
    Outcome.FC_OUT -> "FC (out)"
    Outcome.OUT -> "Out"
    Outcome.AUTO_OUT -> "Auto out"
    Outcome.MANUAL_OUT -> "Manual out"
}

private fun nextRunnerResult(current: RunnerResult): RunnerResult = when (current) {
    RunnerResult.ON_BASE -> RunnerResult.SCORED
    RunnerResult.SCORED -> RunnerResult.OUT
    else -> RunnerResult.ON_BASE
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun EditPaSheet(
    title: String,
    initial: PlateAppearance,
    players: List<Player>,
    onSave: (PlateAppearance) -> Unit,
    onDelete: (() -> Unit)?,
    onMove: ((Int) -> Unit)?,
    onDismiss: () -> Unit,
) {
    var playerId by remember { mutableStateOf(initial.playerId) }
    var outcome by remember { mutableStateOf(initial.outcome) }
    var runnerResult by remember { mutableStateOf(initial.runnerResult) }
    var playerMenuOpen by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(14.dp))

            Text("BATTER", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Box {
                OutlinedButton(onClick = { playerMenuOpen = true }) {
                    Text(players.firstOrNull { it.id == playerId }?.fullName ?: "— no batter —")
                }
                DropdownMenu(expanded = playerMenuOpen, onDismissRequest = { playerMenuOpen = false }) {
                    players.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.fullName) },
                            onClick = { playerId = p.id; playerMenuOpen = false },
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("OUTCOME", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                (onBaseOutcomes + outOutcomes).forEach { o ->
                    FilterChip(
                        selected = outcome == o,
                        onClick = {
                            outcome = o
                            runnerResult = ScorecardViewModel.defaultRunnerResult(o)
                        },
                        label = { Text(o.label, fontWeight = FontWeight.Bold) },
                    )
                }
            }

            if (outcome.reachedBase) {
                Spacer(Modifier.height(14.dp))
                Text("RUNNER", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        RunnerResult.ON_BASE to "On base",
                        RunnerResult.SCORED to "Scored",
                        RunnerResult.OUT to "Out on bases",
                    ).forEach { (r, label) ->
                        FilterChip(
                            selected = runnerResult == r,
                            onClick = { runnerResult = r },
                            label = { Text(label, fontWeight = FontWeight.Bold) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Button(
                    onClick = {
                        onSave(
                            initial.copy(
                                playerId = playerId,
                                outcome = outcome,
                                runnerResult = if (outcome.reachedBase) runnerResult else RunnerResult.NONE,
                            )
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Save", fontWeight = FontWeight.ExtraBold) }
                if (onMove != null) {
                    IconButton(onClick = { onMove(-1) }) { Text("↑", fontSize = 18.sp, fontWeight = FontWeight.Black) }
                    IconButton(onClick = { onMove(1) }) { Text("↓", fontSize = 18.sp, fontWeight = FontWeight.Black) }
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun OpponentScoreDialog(
    currentInning: Int,
    oppByInning: Map<Int, Int>,
    onSet: (inning: Int, runs: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var inning by remember { mutableStateOf(currentInning) }
    val runs = oppByInning[inning] ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", fontWeight = FontWeight.Bold) } },
        title = { Text("Opponent runs") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { if (inning > 1) inning-- }) { Text("◀") }
                    Text("Inning $inning", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    IconButton(onClick = { inning++ }) { Text("▶") }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = { if (runs > 0) onSet(inning, runs - 1) }) { Text("−", fontSize = 18.sp) }
                    Text("$runs", fontSize = 24.sp, fontWeight = FontWeight.Black)
                    OutlinedButton(onClick = { onSet(inning, runs + 1) }) { Text("+", fontSize = 18.sp) }
                }
            }
        },
    )
}
