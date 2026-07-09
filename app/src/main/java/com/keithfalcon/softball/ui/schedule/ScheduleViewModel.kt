package com.keithfalcon.softball.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keithfalcon.softball.SoftballApp
import com.keithfalcon.softball.data.Game
import com.keithfalcon.softball.data.Team
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ScheduleUiState(
    val team: Team? = null,
    val upcoming: List<Game> = emptyList(),
    val past: List<Game> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModel(app: SoftballApp) : ViewModel() {

    private val db = app.database

    private val gamesFlow = db.teamDao().observeFirstTeam().flatMapLatest { team ->
        if (team == null) flowOf(emptyList()) else db.gameDao().observeAll(team.id)
    }

    val ui: StateFlow<ScheduleUiState> = combine(
        db.teamDao().observeFirstTeam(),
        gamesFlow,
    ) { team, games ->
        val now = System.currentTimeMillis()
        // A game is "upcoming" until ~4 hours after first pitch, so tonight's game
        // stays on top while you're scoring it.
        val cutoff = now - 4 * 60 * 60 * 1000
        val (upcoming, past) = games.partition { it.dateTime >= cutoff }
        ScheduleUiState(
            team = team,
            upcoming = upcoming.sortedBy { it.dateTime },
            past = past.sortedByDescending { it.dateTime },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScheduleUiState())

    fun saveGame(game: Game) {
        viewModelScope.launch {
            val team = db.teamDao().firstTeam() ?: return@launch
            if (game.id == 0L) db.gameDao().insert(game.copy(teamId = team.id))
            else db.gameDao().update(game)
        }
    }

    fun deleteGame(game: Game) {
        viewModelScope.launch { db.gameDao().delete(game) }
    }

    /** Doubleheader helper (spec §7.9): clone a game one hour later. */
    fun duplicateGame(game: Game) {
        viewModelScope.launch {
            db.gameDao().insert(
                game.copy(
                    id = 0,
                    dateTime = game.dateTime + 60 * 60 * 1000,
                    status = com.keithfalcon.softball.data.GameStatus.SCHEDULED,
                    ourScore = 0,
                    theirScore = 0,
                )
            )
        }
    }
}
