package com.keithfalcon.softball.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keithfalcon.softball.SoftballApp
import com.keithfalcon.softball.data.Player
import com.keithfalcon.softball.data.backup.BackupManager
import com.keithfalcon.softball.logic.StatsEngine
import com.keithfalcon.softball.ui.common.softballViewModel
import com.keithfalcon.softball.ui.theme.MonoDigits
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StatRow(val player: Player, val stats: StatsEngine.PlayerStats)

class SettingsViewModel(private val app: SoftballApp) : ViewModel() {

    private val backupManager = BackupManager(app, app.database)

    val statRows = combine(
        app.database.playerDao().observeEveryone(),
        app.database.plateAppearanceDao().observeForAll(),
    ) { players, pas ->
        val stats = StatsEngine.compute(pas)
        players.mapNotNull { p -> stats[p.id]?.let { StatRow(p, it) } }
            .sortedByDescending { it.stats.avg }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun export(uri: Uri, onDone: (Result<Unit>) -> Unit) {
        viewModelScope.launch { onDone(backupManager.exportTo(uri)) }
    }

    fun import(uri: Uri, onDone: (Result<String>) -> Unit) {
        viewModelScope.launch {
            onDone(
                backupManager.importFrom(uri).map { data ->
                    "${data.players.size} players, ${data.games.size} games restored"
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(onDismiss: () -> Unit) {
    val vm = softballViewModel { app -> SettingsViewModel(app) }
    val context = LocalContext.current
    var showStats by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let {
            vm.export(it) { result ->
                toast(result.fold({ "Backup saved" }, { e -> "Export failed: ${e.message}" }))
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { pendingImportUri = it }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Settings & backup", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "All data lives on this phone only. Export a backup regularly — it's your safety net for phone upgrades.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    exportLauncher.launch("softball-backup-$stamp.json")
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("⬆  Export backup (JSON)", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("⬇  Import backup", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showStats = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("📊  Season stats", fontWeight = FontWeight.Bold) }
        }
    }

    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("Replace all data?") },
            text = { Text("Importing a backup replaces everything currently in the app — roster, games, and scorecards. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingImportUri = null
                    vm.import(uri) { result ->
                        toast(result.fold({ "Import complete: $it" }, { e -> "Import failed: ${e.message}" }))
                    }
                }) { Text("Replace data", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { pendingImportUri = null }) { Text("Cancel") } },
        )
    }

    if (showStats) {
        StatsSheet(onDismiss = { showStats = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsSheet(onDismiss: () -> Unit) {
    val vm = softballViewModel { app -> SettingsViewModel(app) }
    val rows by vm.statRows.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Season stats", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            if (rows.isEmpty()) {
                Text(
                    "No plate appearances recorded yet — stats build automatically as you score games.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(Modifier.fillMaxWidth()) {
                    Text("PLAYER", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    listOf("GP", "AB", "H", "BB", "R", "AVG").forEach {
                        Text(it, style = MonoDigits.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(if (it == "AVG") 44.dp else 30.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(rows.size, key = { rows[it].player.id }) { i ->
                        val row = rows[i]
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                row.player.fullName,
                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            listOf(
                                "${row.stats.games}", "${row.stats.atBats}", "${row.stats.hits}",
                                "${row.stats.walks}", "${row.stats.runs}",
                            ).forEach {
                                Text(it, style = MonoDigits, modifier = Modifier.width(30.dp))
                            }
                            Text(
                                StatsEngine.format3(row.stats.avg),
                                style = MonoDigits, fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(44.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
