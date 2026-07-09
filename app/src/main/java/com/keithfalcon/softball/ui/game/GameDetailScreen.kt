package com.keithfalcon.softball.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.keithfalcon.softball.data.Game
import com.keithfalcon.softball.ui.common.softballViewModel
import com.keithfalcon.softball.ui.scorecard.ScorecardTab
import com.keithfalcon.softball.ui.theme.FieldGreen
import com.keithfalcon.softball.ui.theme.GreenOnDark
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private class GameHeaderViewModel(app: SoftballApp, gameId: Long) : ViewModel() {
    val game: StateFlow<Game?> = app.database.gameDao().observe(gameId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

private val headerDate = SimpleDateFormat("EEE MMM d · h:mm a", Locale.US)

@Composable
fun GameDetailScreen(gameId: Long, onBack: () -> Unit) {
    val vm = softballViewModel(key = "game-header-$gameId") { app -> GameHeaderViewModel(app, gameId) }
    val game by vm.game.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Green app bar (hidden on the scorecard tab, which brings its own score header)
        if (tab != 2) {
            Surface(color = FieldGreen) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "‹",
                            color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp),
                        )
                        Column {
                            Text(
                                listOfNotNull(
                                    game?.let { headerDate.format(Date(it.dateTime)).uppercase() },
                                    game?.location?.takeIf { it.isNotBlank() }?.uppercase(),
                                ).joinToString(" · "),
                                color = GreenOnDark, fontSize = 10.sp,
                                fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "vs. ${game?.opponent ?: ""}",
                                color = Color.White,
                                style = MaterialTheme.typography.headlineMedium,
                            )
                        }
                    }
                }
            }
        } else {
            // Slim back strip above the scorecard header
            Surface(color = FieldGreen) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "‹ Back",
                        color = GreenOnDark, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClick = onBack).padding(vertical = 4.dp),
                    )
                }
            }
        }

        // Tabs (clay active indicator per mockups)
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
            Row(Modifier.fillMaxWidth()) {
                listOf("Availability", "Lineup", "Scorecard").forEachIndexed { i, label ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable { tab = i }
                            .padding(top = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            label,
                            fontWeight = if (tab == i) FontWeight.ExtraBold else FontWeight.Medium,
                            fontSize = 14.sp,
                            color = if (tab == i) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier
                                .width(64.dp)
                                .height(4.dp)
                                .background(
                                    if (tab == i) MaterialTheme.colorScheme.secondary else Color.Transparent,
                                    RoundedCornerShape(2.dp),
                                ),
                        )
                    }
                }
            }
        }

        when (tab) {
            0 -> AvailabilityTab(gameId)
            1 -> LineupTab(gameId, onStartGame = { tab = 2 })
            2 -> ScorecardTab(gameId)
        }
    }
}
