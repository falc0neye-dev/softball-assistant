package com.keithfalcon.softball.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keithfalcon.softball.SoftballApp
import com.keithfalcon.softball.data.AvailabilityStatus
import com.keithfalcon.softball.data.Game
import com.keithfalcon.softball.data.GameStatus
import com.keithfalcon.softball.data.Lineup
import com.keithfalcon.softball.data.LineupEntry
import com.keithfalcon.softball.data.LineupQueue
import com.keithfalcon.softball.data.LineupType
import com.keithfalcon.softball.data.Outcome
import com.keithfalcon.softball.data.Player
import com.keithfalcon.softball.data.Sex
import com.keithfalcon.softball.logic.LineupEngine
import com.keithfalcon.softball.ui.common.softballViewModel
import com.keithfalcon.softball.ui.scorecard.SexDot
import com.keithfalcon.softball.ui.theme.AmberBorder
import com.keithfalcon.softball.ui.theme.AmberDeep
import com.keithfalcon.softball.ui.theme.AmberTint
import com.keithfalcon.softball.ui.theme.MonoDigits
import com.keithfalcon.softball.ui.theme.Steel
import com.keithfalcon.softball.ui.theme.TextFaint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import sh.calvin.reorderable.ReorderableColumn

data class PreviewSlot(val label: String, val isFemaleSlot: Boolean, val isAutoOut: Boolean)

data class LineupUiState(
    val game: Game? = null,
    val lineup: Lineup? = null,
    val battingQueue: List<Player> = emptyList(),
    val maleQueue: List<Player> = emptyList(),
    val femaleQueue: List<Player> = emptyList(),
    val addablePlayers: List<Player> = emptyList(),
    val preview: List<PreviewSlot> = emptyList(),
    val gameStarted: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class LineupViewModel(app: SoftballApp, private val gameId: Long) : ViewModel() {

    private val db = app.database
    private val lineupDao = db.lineupDao()
    private val editMutex = Mutex()

    init {
        // First open: seed queues from availability so the tab is never empty busywork.
        viewModelScope.launch {
            editMutex.withLock {
                if (lineupDao.byGame(gameId) == null) seedFromAvailabilityLocked()
            }
        }
    }

    private val playersFlow = db.teamDao().observeFirstTeam().flatMapLatest { team ->
        if (team == null) flowOf(emptyList()) else db.playerDao().observeActive(team.id)
    }

    private val lineupFlow = combine(
        lineupDao.observe(gameId),
        lineupDao.observeEntries(gameId),
    ) { lineup, entries -> lineup to entries }

    private val contextFlow = combine(
        db.gameDao().observe(gameId),
        db.availabilityDao().observeForGame(gameId),
        db.plateAppearanceDao().observeForGame(gameId),
    ) { game, availability, pas -> Triple(game, availability, pas) }

    val ui: StateFlow<LineupUiState> = combine(
        playersFlow,
        lineupFlow,
        contextFlow,
    ) { players, (lineup, entries), (game, availability, pas) ->
        val byId = players.associateBy { it.id }
        fun queue(q: LineupQueue) = entries.filter { it.queue == q }
            .sortedBy { it.orderInQueue }
            .mapNotNull { byId[it.playerId] }

        val batting = queue(LineupQueue.BATTING)
        val males = queue(LineupQueue.MALE)
        val females = queue(LineupQueue.FEMALE)
        val inLineup = entries.map { it.playerId }.toSet()
        val statusByPlayer = availability.associate { it.playerId to it.status }
        val addable = players.filter {
            it.id !in inLineup && statusByPlayer[it.id] != AvailabilityStatus.OUT
        }

        val config = lineup?.let {
            LineupEngine.Config(
                type = it.type,
                battingQueue = batting.map(Player::id),
                maleQueue = males.map(Player::id),
                femaleQueue = females.map(Player::id),
                ratioMale = it.ratioMale,
                ratioFemale = it.ratioFemale,
                autoOutOnEmptyFemaleSlot = it.autoOutOnEmptyFemaleSlot,
            )
        }
        val history = pas.filter { it.outcome != Outcome.MANUAL_OUT }
            .map { if (it.outcome == Outcome.AUTO_OUT) null else it.playerId }
        val preview = config?.let { c ->
            LineupEngine.preview(c, history, PREVIEW_COUNT).map { slot ->
                when (slot) {
                    is LineupEngine.Slot.Batter -> PreviewSlot(
                        label = byId[slot.playerId]?.fullName ?: "?",
                        isFemaleSlot = slot.isFemaleSlot,
                        isAutoOut = false,
                    )
                    LineupEngine.Slot.AutoOut -> PreviewSlot("Automatic out", isFemaleSlot = true, isAutoOut = true)
                }
            }
        } ?: emptyList()

        LineupUiState(
            game = game,
            lineup = lineup,
            battingQueue = batting,
            maleQueue = males,
            femaleQueue = females,
            addablePlayers = addable,
            preview = preview,
            gameStarted = game?.status != GameStatus.SCHEDULED,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LineupUiState())

    private suspend fun seedFromAvailabilityLocked() {
        val game = db.gameDao().byId(gameId) ?: return
        val team = db.teamDao().firstTeam() ?: return
        val availability = db.availabilityDao().forGame(gameId).associate { it.playerId to it.status }
        val players = db.playerDao().all().filter { it.teamId == team.id && it.isActive }
        val available = players.filter { availability[it.id] == AvailabilityStatus.IN }
        val existing = lineupDao.byGame(gameId)
        val lineup = existing ?: Lineup(gameId = gameId, type = LineupType.DYNAMIC)
        val entries = buildEntries(lineup.type, available)
        lineupDao.replace(lineup, entries)
    }

    private fun buildEntries(type: LineupType, players: List<Player>): List<LineupEntry> =
        when (type) {
            LineupType.STATIC -> players.mapIndexed { i, p ->
                LineupEntry(gameId, LineupQueue.BATTING, i, p.id)
            }
            LineupType.DYNAMIC -> {
                val males = players.filter { it.sex == Sex.MALE }
                val females = players.filter { it.sex == Sex.FEMALE }
                males.mapIndexed { i, p -> LineupEntry(gameId, LineupQueue.MALE, i, p.id) } +
                    females.mapIndexed { i, p -> LineupEntry(gameId, LineupQueue.FEMALE, i, p.id) }
            }
        }

    /** Rebuild queues from the current availability (spec: only In players are eligible). */
    fun regenerateFromAvailability() {
        viewModelScope.launch {
            editMutex.withLock {
                val lineup = lineupDao.byGame(gameId) ?: return@withLock
                val availability = db.availabilityDao().forGame(gameId).associate { it.playerId to it.status }
                val team = db.teamDao().firstTeam() ?: return@withLock
                val players = db.playerDao().all().filter { it.teamId == team.id && it.isActive }
                val available = players.filter { availability[it.id] == AvailabilityStatus.IN }
                lineupDao.replace(lineup, buildEntries(lineup.type, available))
            }
        }
    }

    fun setType(type: LineupType) {
        viewModelScope.launch {
            editMutex.withLock {
                val lineup = lineupDao.byGame(gameId) ?: return@withLock
                if (lineup.type == type) return@withLock
                // Rebuild entries in the new shape from the same players, keeping order.
                val entries = lineupDao.entries(gameId).sortedBy { it.orderInQueue }
                val players = entries.mapNotNull { db.playerDao().byId(it.playerId) }
                val ordered = when (type) {
                    // static keeps the generated interleave? Simplest: males then females in queue order
                    LineupType.STATIC -> players.distinctBy { it.id }
                    LineupType.DYNAMIC -> players.distinctBy { it.id }
                }
                lineupDao.replace(lineup.copy(type = type), buildEntries(type, ordered))
            }
        }
    }

    fun setRatio(male: Int, female: Int) {
        viewModelScope.launch {
            editMutex.withLock {
                lineupDao.byGame(gameId)?.let {
                    lineupDao.upsert(it.copy(ratioMale = male, ratioFemale = female))
                }
            }
        }
    }

    fun setAutoOut(enabled: Boolean) {
        viewModelScope.launch {
            editMutex.withLock {
                lineupDao.byGame(gameId)?.let {
                    lineupDao.upsert(it.copy(autoOutOnEmptyFemaleSlot = enabled))
                }
            }
        }
    }

    fun move(queue: LineupQueue, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            editMutex.withLock {
                val lineup = lineupDao.byGame(gameId) ?: return@withLock
                val all = lineupDao.entries(gameId)
                val inQueue = all.filter { it.queue == queue }.sortedBy { it.orderInQueue }.toMutableList()
                if (fromIndex !in inQueue.indices || toIndex !in inQueue.indices) return@withLock
                inQueue.add(toIndex, inQueue.removeAt(fromIndex))
                val renumbered = inQueue.mapIndexed { i, e -> e.copy(orderInQueue = i) }
                lineupDao.replace(lineup, all.filter { it.queue != queue } + renumbered)
            }
        }
    }

    fun remove(playerId: Long) {
        viewModelScope.launch {
            editMutex.withLock {
                val lineup = lineupDao.byGame(gameId) ?: return@withLock
                val remaining = lineupDao.entries(gameId).filterNot { it.playerId == playerId }
                val renumbered = remaining.groupBy { it.queue }.flatMap { (_, es) ->
                    es.sortedBy { it.orderInQueue }.mapIndexed { i, e -> e.copy(orderInQueue = i) }
                }
                lineupDao.replace(lineup, renumbered)
            }
        }
    }

    fun add(player: Player) {
        viewModelScope.launch {
            editMutex.withLock {
                val lineup = lineupDao.byGame(gameId) ?: return@withLock
                val entries = lineupDao.entries(gameId)
                if (entries.any { it.playerId == player.id }) return@withLock
                val queue = when {
                    lineup.type == LineupType.STATIC -> LineupQueue.BATTING
                    player.sex == Sex.FEMALE -> LineupQueue.FEMALE
                    else -> LineupQueue.MALE
                }
                val maxOrder = entries.filter { it.queue == queue }.maxOfOrNull { it.orderInQueue } ?: -1
                lineupDao.insertEntries(listOf(LineupEntry(gameId, queue, maxOrder + 1, player.id)))
            }
        }
    }

    fun startGame() {
        viewModelScope.launch {
            db.gameDao().byId(gameId)?.let {
                if (it.status == GameStatus.SCHEDULED) {
                    db.gameDao().update(it.copy(status = GameStatus.IN_PROGRESS))
                }
            }
        }
    }

    companion object {
        private const val PREVIEW_COUNT = 12
    }
}

@Composable
fun LineupTab(gameId: Long, onStartGame: () -> Unit) {
    val vm = softballViewModel(key = "lineup-$gameId") { app -> LineupViewModel(app, gameId) }
    val ui by vm.ui.collectAsState()
    var ratioMenuOpen by remember { mutableStateOf(false) }
    var addMenuOpen by remember { mutableStateOf(false) }

    val lineup = ui.lineup

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        if (lineup == null) {
            Text("Setting up lineup…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }

        // Type toggle + ratio chip
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp)).padding(2.dp),
            ) {
                @Composable
                fun typeSegment(label: String, value: LineupType) {
                    val selected = lineup.type == value
                    Box(
                        Modifier
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                RoundedCornerShape(16.dp),
                            )
                            .clickable { vm.setType(value) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            label,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                typeSegment("Static", LineupType.STATIC)
                typeSegment("Dynamic", LineupType.DYNAMIC)
            }
            Spacer(Modifier.width(10.dp))
            if (lineup.type == LineupType.DYNAMIC) {
                Box {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.Transparent,
                        border = BorderStroke(1.5.dp, AmberBorder),
                        modifier = Modifier.clickable { ratioMenuOpen = true },
                    ) {
                        Text(
                            "Ratio ${lineup.ratioMale} M : ${lineup.ratioFemale} F ▾",
                            fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = AmberDeep,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                    DropdownMenu(expanded = ratioMenuOpen, onDismissRequest = { ratioMenuOpen = false }) {
                        listOf(2 to 1, 3 to 1, 4 to 1, 5 to 1, 1 to 1).forEach { (m, f) ->
                            DropdownMenuItem(
                                text = { Text("$m M : $f F") },
                                onClick = { vm.setRatio(m, f); ratioMenuOpen = false },
                            )
                        }
                    }
                }
            }
        }

        if (lineup.type == LineupType.DYNAMIC) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = lineup.autoOutOnEmptyFemaleSlot, onCheckedChange = vm::setAutoOut)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Auto-out when no female available (league rule)",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (lineup.type == LineupType.STATIC) {
            QueueSection(
                title = "BATTING ORDER (${ui.battingQueue.size})",
                titleColor = MaterialTheme.colorScheme.primary,
                players = ui.battingQueue,
                highlight = false,
                onMove = { from, to -> vm.move(LineupQueue.BATTING, from, to) },
                onRemove = { vm.remove(it) },
            )
        } else {
            QueueSection(
                title = "MALE QUEUE (${ui.maleQueue.size})",
                titleColor = Steel,
                players = ui.maleQueue,
                highlight = false,
                onMove = { from, to -> vm.move(LineupQueue.MALE, from, to) },
                onRemove = { vm.remove(it) },
            )
            Spacer(Modifier.height(16.dp))
            QueueSection(
                title = "FEMALE QUEUE (${ui.femaleQueue.size})",
                titleColor = AmberDeep,
                players = ui.femaleQueue,
                highlight = true,
                onMove = { from, to -> vm.move(LineupQueue.FEMALE, from, to) },
                onRemove = { vm.remove(it) },
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                TextButton(onClick = { addMenuOpen = true }, enabled = ui.addablePlayers.isNotEmpty()) {
                    Text("+ Add player", fontWeight = FontWeight.Bold)
                }
                DropdownMenu(expanded = addMenuOpen, onDismissRequest = { addMenuOpen = false }) {
                    ui.addablePlayers.forEach { p ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(p.fullName)
                                    Spacer(Modifier.width(8.dp))
                                    SexDot(p.sex)
                                }
                            },
                            onClick = { vm.add(p); addMenuOpen = false },
                        )
                    }
                }
            }
            TextButton(onClick = vm::regenerateFromAvailability) {
                Text("⟳ Rebuild from availability", fontWeight = FontWeight.Bold)
            }
            val context = androidx.compose.ui.platform.LocalContext.current
            TextButton(
                onClick = { shareLineup(context, ui) },
                enabled = ui.preview.isNotEmpty(),
            ) { Text("Share", fontWeight = FontWeight.Bold) }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            "PREVIEW — BATTING ORDER",
            fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        PreviewGrid(ui.preview)
        if (lineup.type == LineupType.DYNAMIC) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Queues cycle independently — order continues all game",
                fontSize = 11.sp, color = TextFaint,
            )
        }

        Spacer(Modifier.height(18.dp))
        Button(
            onClick = { vm.startGame(); onStartGame() },
            enabled = ui.preview.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text(
                if (ui.gameStarted) "▶  Open scorecard" else "▶  Start game",
                fontWeight = FontWeight.ExtraBold, fontSize = 15.sp,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Spec §7.2 — formatted batting order for the team group chat, via the system share sheet. */
private fun shareLineup(context: android.content.Context, ui: LineupUiState) {
    val game = ui.game
    val header = buildString {
        append("Lineup")
        if (game != null) {
            append(" vs. ${game.opponent}")
            append(" — ")
            append(java.text.SimpleDateFormat("EEE MMM d, h:mm a", java.util.Locale.US).format(java.util.Date(game.dateTime)))
        }
    }
    val body = ui.preview.mapIndexed { i, slot ->
        "${i + 1}. ${slot.label}${if (slot.isFemaleSlot && !slot.isAutoOut) " (F)" else ""}"
    }.joinToString("\n")
    val footer = if (ui.lineup?.type == LineupType.DYNAMIC)
        "\n(first ${ui.preview.size} — order keeps cycling)" else ""
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, "$header\n$body$footer")
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Share lineup"))
}

@Composable
private fun QueueSection(
    title: String,
    titleColor: Color,
    players: List<Player>,
    highlight: Boolean,
    onMove: (Int, Int) -> Unit,
    onRemove: (Long) -> Unit,
) {
    Text(title, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp, color = titleColor)
    Spacer(Modifier.height(8.dp))
    if (players.isEmpty()) {
        Text(
            "Nobody here — mark players In on the Availability tab, then rebuild.",
            fontSize = 12.sp, color = TextFaint,
        )
        return
    }
    ReorderableColumn(
        list = players,
        onSettle = onMove,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { _, player, isDragging ->
        key(player.id) {
            var menuOpen by remember { mutableStateOf(false) }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (highlight) AmberTint.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (highlight) AmberBorder else MaterialTheme.colorScheme.outline),
                shadowElevation = if (isDragging) 4.dp else 0.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "≡",
                        fontSize = 18.sp, color = TextFaint,
                        modifier = Modifier.draggableHandle().padding(end = 12.dp),
                    )
                    Text(
                        player.fullName,
                        fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).clickable { menuOpen = true },
                    )
                    SexDot(player.sex)
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Remove from lineup", color = MaterialTheme.colorScheme.error) },
                    onClick = { menuOpen = false; onRemove(player.id) },
                )
            }
        }
    }
}

@Composable
private fun PreviewGrid(preview: List<PreviewSlot>) {
    val half = (preview.size + 1) / 2
    Row(Modifier.fillMaxWidth()) {
        @Composable
        fun columnOf(range: IntRange) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                range.forEach { i ->
                    val slot = preview.getOrNull(i) ?: return@forEach
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("%2d".format(i + 1), style = MonoDigits, color = TextFaint, modifier = Modifier.width(28.dp))
                        if (slot.isFemaleSlot && !slot.isAutoOut) {
                            Box(Modifier.background(AmberTint, RoundedCornerShape(11.dp))) {
                                Text(
                                    "${slot.label} · F",
                                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AmberDeep,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        } else {
                            Text(
                                slot.label,
                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = if (slot.isAutoOut) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
        columnOf(0 until half)
        columnOf(half until preview.size)
    }
}
