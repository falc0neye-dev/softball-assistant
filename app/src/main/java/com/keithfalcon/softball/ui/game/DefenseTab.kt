package com.keithfalcon.softball.ui.game

import android.content.ClipData
import android.content.ClipDescription
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keithfalcon.softball.SoftballApp
import com.keithfalcon.softball.data.AvailabilityStatus
import com.keithfalcon.softball.data.DEFENSE_POSITIONS
import com.keithfalcon.softball.data.DefenseAssignment
import com.keithfalcon.softball.data.InningParity
import com.keithfalcon.softball.data.Player
import com.keithfalcon.softball.data.Sex
import com.keithfalcon.softball.ui.common.softballViewModel
import com.keithfalcon.softball.ui.scorecard.SexDot
import com.keithfalcon.softball.ui.theme.AmberBorder
import com.keithfalcon.softball.ui.theme.TextFaint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DefenseUiState(
    /** Players available for this game (In + Tentative), the draggable pool. */
    val pool: List<Player> = emptyList(),
    val tentativeIds: Set<Long> = emptySet(),
    /** parity → position → player. */
    val grid: Map<InningParity, Map<String, Player>> = emptyMap(),
    val sittingOdd: List<Player> = emptyList(),
    val sittingEven: List<Player> = emptyList(),
    /** Available players with no position in either column — everyone should get a spot. */
    val unassigned: List<Player> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class DefenseViewModel(app: SoftballApp, private val gameId: Long) : ViewModel() {

    private val db = app.database
    private val defenseDao = db.defenseDao()

    private val playersFlow = db.teamDao().observeFirstTeam().flatMapLatest { team ->
        if (team == null) flowOf(emptyList()) else db.playerDao().observeActive(team.id)
    }

    val ui: StateFlow<DefenseUiState> = combine(
        playersFlow,
        db.availabilityDao().observeForGame(gameId),
        defenseDao.observeForGame(gameId),
    ) { players, availability, assignments ->
        val statusByPlayer = availability.associate { it.playerId to it.status }
        val pool = players.filter { statusByPlayer[it.id] != AvailabilityStatus.OUT }
        val byId = players.associateBy { it.id }
        val grid = InningParity.entries.associateWith { parity ->
            assignments.filter { it.parity == parity }
                .mapNotNull { a -> byId[a.playerId]?.let { a.position to it } }
                .toMap()
        }
        fun sitting(parity: InningParity): List<Player> {
            val assigned = grid[parity]?.values?.map { it.id }?.toSet() ?: emptySet()
            return pool.filter { it.id !in assigned }
        }
        val assignedAnywhere = grid.values.flatMap { it.values }.map { it.id }.toSet()
        DefenseUiState(
            pool = pool,
            tentativeIds = pool.filter { statusByPlayer[it.id] != AvailabilityStatus.IN }
                .map { it.id }.toSet(),
            grid = grid,
            sittingOdd = sitting(InningParity.ODD),
            sittingEven = sitting(InningParity.EVEN),
            unassigned = pool.filter { it.id !in assignedAnywhere },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DefenseUiState())

    fun assign(parity: InningParity, position: String, playerId: Long) {
        viewModelScope.launch {
            defenseDao.assign(DefenseAssignment(gameId, parity, position, playerId))
        }
    }

    fun clearCell(parity: InningParity, position: String) {
        viewModelScope.launch { defenseDao.clearCell(gameId, parity, position) }
    }

    fun copyOddToEven() {
        viewModelScope.launch { defenseDao.copyParity(gameId, InningParity.ODD, InningParity.EVEN) }
    }

    fun clearAll() {
        viewModelScope.launch {
            defenseDao.clearParity(gameId, InningParity.ODD)
            defenseDao.clearParity(gameId, InningParity.EVEN)
        }
    }
}

private const val PLAYER_MIME = ClipDescription.MIMETYPE_TEXT_PLAIN

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DefenseTab(gameId: Long) {
    val vm = softballViewModel(key = "defense-$gameId") { app -> DefenseViewModel(app, gameId) }
    val ui by vm.ui.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        if (ui.pool.isEmpty()) {
            Text(
                "Nobody is available yet — mark players In on the Availability tab.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        Text(
            "AVAILABLE — LONG-PRESS AND DRAG ONTO A POSITION",
            fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ui.pool.forEach { player ->
                PlayerChip(
                    player = player,
                    tentative = player.id in ui.tentativeIds,
                    needsPosition = ui.unassigned.any { it.id == player.id },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Column headers with fill counts
        val oddFilled = ui.grid[InningParity.ODD]?.size ?: 0
        val evenFilled = ui.grid[InningParity.EVEN]?.size ?: 0
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(44.dp))
            HeaderCell("ODD INNINGS · $oddFilled/${DEFENSE_POSITIONS.size}", Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            HeaderCell("EVEN INNINGS · $evenFilled/${DEFENSE_POSITIONS.size}", Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))

        DEFENSE_POSITIONS.forEach { position ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    position,
                    fontWeight = FontWeight.ExtraBold, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(44.dp),
                )
                DefenseCell(
                    player = ui.grid[InningParity.ODD]?.get(position),
                    pool = ui.pool,
                    tentativeIds = ui.tentativeIds,
                    onAssign = { vm.assign(InningParity.ODD, position, it) },
                    onClear = { vm.clearCell(InningParity.ODD, position) },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                DefenseCell(
                    player = ui.grid[InningParity.EVEN]?.get(position),
                    pool = ui.pool,
                    tentativeIds = ui.tentativeIds,
                    onAssign = { vm.assign(InningParity.EVEN, position, it) },
                    onClear = { vm.clearCell(InningParity.EVEN, position) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        CoverageBanner(unassigned = ui.unassigned, poolSize = ui.pool.size)

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::copyOddToEven, enabled = oddFilled > 0) {
                Text("Copy Odd → Even", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = vm::clearAll, enabled = oddFilled + evenFilled > 0) {
                Text("Clear all", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(14.dp))
        SittingLine("Sitting (odd)", ui.sittingOdd)
        Spacer(Modifier.height(4.dp))
        SittingLine("Sitting (even)", ui.sittingEven)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Tracks that every available player holds at least one position (either column). */
@Composable
private fun CoverageBanner(unassigned: List<Player>, poolSize: Int) {
    if (poolSize == 0) return
    val allCovered = unassigned.isEmpty()
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (allCovered) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            if (allCovered) "✓  Everyone available has a position"
            else "●  No position yet: ${unassigned.joinToString(", ") { it.firstName }}",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (allCovered) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun SittingLine(label: String, players: List<Player>) {
    Text(
        "$label: " + if (players.isEmpty()) "nobody" else players.joinToString(", ") { it.firstName },
        fontSize = 12.sp,
        color = TextFaint,
    )
}

/**
 * A draggable player chip; the drag payload is the player id as plain text.
 * [needsPosition] marks pool chips of players not yet assigned anywhere.
 */
@Composable
private fun PlayerChip(
    player: Player,
    tentative: Boolean,
    compact: Boolean = false,
    needsPosition: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (tentative) AmberBorder else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.dragAndDropSource { _ ->
            DragAndDropTransferData(ClipData.newPlainText("playerId", player.id.toString()))
        },
    ) {
        Row(
            Modifier.padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 4.dp else 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (needsPosition) {
                Box(
                    Modifier
                        .width(7.dp)
                        .height(7.dp)
                        .background(MaterialTheme.colorScheme.error, RoundedCornerShape(50)),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                if (compact) player.firstName else player.fullName,
                fontSize = if (compact) 12.sp else 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (player.sex == Sex.FEMALE) {
                Spacer(Modifier.width(5.dp))
                SexDot(Sex.FEMALE, size = 13.dp)
            }
        }
    }
}

/**
 * One grid cell: drop target for player chips, tap for a picker menu, filled cells are
 * themselves drag sources so an assignment can be dragged onward to another cell.
 */
@Composable
private fun DefenseCell(
    player: Player?,
    pool: List<Player>,
    tentativeIds: Set<Long>,
    onAssign: (Long) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var hovering by remember { mutableStateOf(false) }

    val dropTarget = remember(onAssign) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                hovering = false
                val id = event.toAndroidDragEvent().clipData
                    ?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)?.text?.toString()?.toLongOrNull()
                    ?: return false
                onAssign(id)
                return true
            }

            override fun onEntered(event: DragAndDropEvent) { hovering = true }
            override fun onExited(event: DragAndDropEvent) { hovering = false }
            override fun onEnded(event: DragAndDropEvent) { hovering = false }
        }
    }

    Box(
        modifier
            .heightIn(min = 40.dp)
            .background(
                when {
                    hovering -> MaterialTheme.colorScheme.primaryContainer
                    player == null -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.surface
                },
                RoundedCornerShape(10.dp),
            )
            .then(
                if (player != null)
                    Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                else Modifier
            )
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event -> event.mimeTypes().contains(PLAYER_MIME) },
                target = dropTarget,
            )
            .clickable { menuOpen = true },
        contentAlignment = Alignment.CenterStart,
    ) {
        if (player == null) {
            Text(
                "—",
                color = TextFaint,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
        } else {
            Box(Modifier.padding(3.dp)) {
                PlayerChip(player = player, tentative = player.id in tentativeIds, compact = true)
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            pool.forEach { p ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.fullName)
                            if (p.sex == Sex.FEMALE) {
                                Spacer(Modifier.width(6.dp))
                                SexDot(Sex.FEMALE, size = 13.dp)
                            }
                        }
                    },
                    onClick = { menuOpen = false; onAssign(p.id) },
                )
            }
            if (player != null) {
                DropdownMenuItem(
                    text = { Text("Clear", color = MaterialTheme.colorScheme.error) },
                    onClick = { menuOpen = false; onClear() },
                )
            }
        }
    }
}
