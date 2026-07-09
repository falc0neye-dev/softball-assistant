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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keithfalcon.softball.SoftballApp
import com.keithfalcon.softball.data.Availability
import com.keithfalcon.softball.data.AvailabilityStatus
import com.keithfalcon.softball.data.Player
import com.keithfalcon.softball.data.Sex
import com.keithfalcon.softball.ui.common.softballViewModel
import com.keithfalcon.softball.ui.theme.AmberChip
import com.keithfalcon.softball.ui.theme.AmberDeep
import com.keithfalcon.softball.ui.theme.FieldGreen
import com.keithfalcon.softball.ui.theme.OutRed
import com.keithfalcon.softball.ui.theme.RedTint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AvailabilityUiState(
    val players: List<Player> = emptyList(),
    val statusByPlayer: Map<Long, AvailabilityStatus> = emptyMap(),
    val inCount: Int = 0,
    val tentativeCount: Int = 0,
    val outCount: Int = 0,
    val hasPreviousGame: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class AvailabilityViewModel(app: SoftballApp, private val gameId: Long) : ViewModel() {

    private val db = app.database

    private val playersFlow = db.teamDao().observeFirstTeam().flatMapLatest { team ->
        if (team == null) flowOf(emptyList()) else db.playerDao().observeActive(team.id)
    }

    val ui: StateFlow<AvailabilityUiState> = combine(
        playersFlow,
        db.availabilityDao().observeForGame(gameId),
    ) { players, availabilities ->
        val statusByPlayer = availabilities.associate { it.playerId to it.status }
        // Unset = treated as Tentative in the summary (spec §3.4)
        val effective = players.map { statusByPlayer[it.id] ?: AvailabilityStatus.TENTATIVE }
        AvailabilityUiState(
            players = players,
            statusByPlayer = statusByPlayer,
            inCount = effective.count { it == AvailabilityStatus.IN },
            tentativeCount = effective.count { it == AvailabilityStatus.TENTATIVE },
            outCount = effective.count { it == AvailabilityStatus.OUT },
            hasPreviousGame = true, // cheap default; copy is a no-op if none exists
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AvailabilityUiState())

    fun setStatus(playerId: Long, status: AvailabilityStatus) {
        viewModelScope.launch {
            db.availabilityDao().upsert(Availability(gameId, playerId, status))
        }
    }

    /** Spec §7.1 — copy availability from the most recent earlier game. */
    fun copyFromLastGame() {
        viewModelScope.launch {
            val game = db.gameDao().byId(gameId) ?: return@launch
            val previous = db.gameDao().previousGame(game.teamId, game.id, game.dateTime) ?: return@launch
            val prevAvailability = db.availabilityDao().forGame(previous.id)
            if (prevAvailability.isNotEmpty()) {
                db.availabilityDao().upsertAll(prevAvailability.map { it.copy(gameId = gameId) })
            }
        }
    }
}

@Composable
fun AvailabilityTab(gameId: Long) {
    val vm = softballViewModel(key = "availability-$gameId") { app -> AvailabilityViewModel(app, gameId) }
    val ui by vm.ui.collectAsState()

    Column(Modifier.fillMaxSize()) {
        // Summary chips
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SummaryChip("${ui.inCount} IN", FieldGreen, Color.White)
            SummaryChip("${ui.tentativeCount} TENTATIVE", AmberChip, AmberDeep)
            SummaryChip("${ui.outCount} OUT", RedTint, OutRed)
        }

        if (ui.players.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "No active players — add teammates on the Roster tab.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(ui.players.size, key = { ui.players[it].id }) { i ->
                    val player = ui.players[i]
                    AvailabilityRow(
                        player = player,
                        status = ui.statusByPlayer[player.id],
                        onSet = { vm.setStatus(player.id, it) },
                    )
                }
            }
        }

        OutlinedButton(
            onClick = vm::copyFromLastGame,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        ) {
            Text("⟳  Copy availability from last game", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SummaryChip(text: String, bg: Color, fg: Color) {
    Box(Modifier.background(bg, RoundedCornerShape(15.dp))) {
        Text(
            text,
            color = fg, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun AvailabilityRow(
    player: Player,
    status: AvailabilityStatus?,
    onSet: (AvailabilityStatus) -> Unit,
) {
    val borderColor = when (status) {
        AvailabilityStatus.TENTATIVE -> com.keithfalcon.softball.ui.theme.AmberBorder
        AvailabilityStatus.OUT -> Color(0xFFE3B7A9)
        else -> MaterialTheme.colorScheme.outline
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    player.fullName,
                    fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    color = if (status == AvailabilityStatus.OUT) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    listOf(
                        player.position.ifBlank { null },
                        if (player.sex == Sex.MALE) "M" else "F",
                    ).filterNotNull().joinToString(" · "),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ThreeStateControl(status = status, onSet = onSet)
        }
    }
}

@Composable
private fun ThreeStateControl(status: AvailabilityStatus?, onSet: (AvailabilityStatus) -> Unit) {
    Row(
        Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(15.dp)).padding(2.dp),
    ) {
        @Composable
        fun segment(label: String, value: AvailabilityStatus, selectedBg: Color) {
            val selected = status == value
            Box(
                Modifier
                    .background(if (selected) selectedBg else Color.Transparent, RoundedCornerShape(13.dp))
                    .clickable { onSet(value) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    label,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        segment("IN", AvailabilityStatus.IN, FieldGreen)
        segment("TENT", AvailabilityStatus.TENTATIVE, com.keithfalcon.softball.ui.theme.Amber)
        segment("OUT", AvailabilityStatus.OUT, OutRed)
    }
}
