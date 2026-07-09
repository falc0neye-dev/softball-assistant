package com.keithfalcon.softball.ui.roster

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keithfalcon.softball.SoftballApp
import com.keithfalcon.softball.data.Player
import com.keithfalcon.softball.data.Sex
import com.keithfalcon.softball.data.Team
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class RosterSort { LAST_NAME, FIRST_NAME, POSITION }

data class RosterUiState(
    val team: Team? = null,
    val players: List<Player> = emptyList(),
    val showInactive: Boolean = false,
    val sort: RosterSort = RosterSort.LAST_NAME,
    val activeCount: Int = 0,
    val femaleCount: Int = 0,
    val maleCount: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class RosterViewModel(app: SoftballApp) : ViewModel() {

    private val db = app.database
    private val sort = MutableStateFlow(RosterSort.LAST_NAME)
    private val showInactive = MutableStateFlow(false)

    private val playersFlow = db.teamDao().observeFirstTeam().flatMapLatest { team ->
        if (team == null) flowOf(emptyList()) else db.playerDao().observeAll(team.id)
    }

    val ui: StateFlow<RosterUiState> = combine(
        db.teamDao().observeFirstTeam(),
        playersFlow,
        sort,
        showInactive,
    ) { team, players, sort, showInactive ->
        val active = players.filter { it.isActive }
        val visible = (if (showInactive) players else active).sortedWith(
            when (sort) {
                RosterSort.LAST_NAME -> compareBy({ it.lastName.lowercase() }, { it.firstName.lowercase() })
                RosterSort.FIRST_NAME -> compareBy({ it.firstName.lowercase() }, { it.lastName.lowercase() })
                RosterSort.POSITION -> compareBy({ it.position.lowercase() }, { it.lastName.lowercase() })
            }
        )
        RosterUiState(
            team = team,
            players = visible,
            showInactive = showInactive,
            sort = sort,
            activeCount = active.size,
            femaleCount = active.count { it.sex == Sex.FEMALE },
            maleCount = active.count { it.sex == Sex.MALE },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RosterUiState())

    fun setSort(value: RosterSort) { sort.value = value }
    fun setShowInactive(value: Boolean) { showInactive.value = value }

    fun createTeam(name: String) {
        viewModelScope.launch {
            if (db.teamDao().firstTeam() == null && name.isNotBlank()) {
                db.teamDao().insert(Team(name = name.trim()))
            }
        }
    }

    fun renameTeam(name: String) {
        viewModelScope.launch {
            db.teamDao().firstTeam()?.let {
                if (name.isNotBlank()) db.teamDao().update(it.copy(name = name.trim()))
            }
        }
    }

    fun savePlayer(player: Player) {
        viewModelScope.launch {
            val team = db.teamDao().firstTeam() ?: return@launch
            if (player.id == 0L) db.playerDao().insert(player.copy(teamId = team.id))
            else db.playerDao().update(player)
        }
    }

    fun setActive(player: Player, active: Boolean) {
        viewModelScope.launch { db.playerDao().update(player.copy(isActive = active)) }
    }
}
