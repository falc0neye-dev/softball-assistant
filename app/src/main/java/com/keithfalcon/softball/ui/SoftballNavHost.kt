package com.keithfalcon.softball.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.keithfalcon.softball.ui.game.GameDetailScreen
import com.keithfalcon.softball.ui.roster.RosterScreen
import com.keithfalcon.softball.ui.schedule.ScheduleScreen

@Composable
fun SoftballNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(onOpenGame = { gameId -> navController.navigate("game/$gameId") })
        }
        composable(
            "game/{gameId}",
            arguments = listOf(navArgument("gameId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val gameId = backStackEntry.arguments?.getLong("gameId") ?: return@composable
            GameDetailScreen(gameId = gameId, onBack = { navController.popBackStack() })
        }
    }
}

@Composable
private fun HomeScreen(onOpenGame: (Long) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = {},
                    label = {
                        Text("Roster", fontWeight = if (tab == 0) FontWeight.ExtraBold else FontWeight.Medium)
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = {},
                    label = {
                        Text("Schedule", fontWeight = if (tab == 1) FontWeight.ExtraBold else FontWeight.Medium)
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (tab == 0) RosterScreen() else ScheduleScreen(onOpenGame = onOpenGame)
        }
    }
}
