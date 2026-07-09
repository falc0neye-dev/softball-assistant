package com.keithfalcon.softball.ui.schedule

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keithfalcon.softball.data.Game
import com.keithfalcon.softball.data.GameStatus
import com.keithfalcon.softball.data.HomeAway
import com.keithfalcon.softball.ui.common.softballViewModel
import com.keithfalcon.softball.ui.theme.FieldGreen
import com.keithfalcon.softball.ui.theme.GreenOnDark
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ScheduleScreen(onOpenGame: (Long) -> Unit) {
    val vm = softballViewModel { app -> ScheduleViewModel(app) }
    val ui by vm.ui.collectAsState()

    var editingGame by remember { mutableStateOf<Game?>(null) }
    var addingGame by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Game?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (ui.team != null) {
                ExtendedFloatingActionButton(
                    onClick = { addingGame = true },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = Color.White,
                ) { Text("+  Add game", fontWeight = FontWeight.Bold) }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Surface(color = FieldGreen) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text(
                        "SCHEDULE",
                        color = GreenOnDark, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 2.5.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        ui.team?.name ?: "No team yet",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
            }

            if (ui.team == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "Create your team on the Roster tab first.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Column
            }

            if (ui.upcoming.isEmpty() && ui.past.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "No games yet — add your first game.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (ui.upcoming.isNotEmpty()) {
                        item { SectionLabel("UPCOMING") }
                        items(ui.upcoming.size, key = { ui.upcoming[it].id }) { i ->
                            GameCard(
                                game = ui.upcoming[i],
                                onClick = { onOpenGame(ui.upcoming[i].id) },
                                onEdit = { editingGame = ui.upcoming[i] },
                                onDuplicate = { vm.duplicateGame(ui.upcoming[i]) },
                                onDelete = { confirmDelete = ui.upcoming[i] },
                            )
                        }
                    }
                    if (ui.past.isNotEmpty()) {
                        item { SectionLabel("PAST") }
                        items(ui.past.size, key = { ui.past[it].id }) { i ->
                            GameCard(
                                game = ui.past[i],
                                onClick = { onOpenGame(ui.past[i].id) },
                                onEdit = { editingGame = ui.past[i] },
                                onDuplicate = { vm.duplicateGame(ui.past[i]) },
                                onDelete = { confirmDelete = ui.past[i] },
                            )
                        }
                    }
                }
            }
        }
    }

    if (addingGame) {
        GameSheet(
            title = "Add game",
            initial = null,
            onSave = { vm.saveGame(it); addingGame = false },
            onDismiss = { addingGame = false },
        )
    }
    editingGame?.let { game ->
        GameSheet(
            title = "Edit game",
            initial = game,
            onSave = { vm.saveGame(it); editingGame = null },
            onDismiss = { editingGame = null },
        )
    }
    confirmDelete?.let { game ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete game?") },
            text = { Text("vs. ${game.opponent} — this removes its availability, lineup, and scorecard. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteGame(game); confirmDelete = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

private val dateFormat = SimpleDateFormat("EEE MMM d · h:mm a", Locale.US)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameCard(
    game: Game,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(14.dp),
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
            Column(Modifier.weight(1f)) {
                Text(
                    "vs. ${game.opponent}",
                    fontWeight = FontWeight.ExtraBold, fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    listOfNotNull(
                        dateFormat.format(Date(game.dateTime)),
                        game.location?.takeIf { it.isNotBlank() },
                        if (game.homeAway == HomeAway.HOME) "Home" else "Away",
                    ).joinToString(" · "),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (game.status) {
                GameStatus.FINAL -> {
                    val won = game.ourScore > game.theirScore
                    val tied = game.ourScore == game.theirScore
                    Text(
                        "${if (won) "W" else if (tied) "T" else "L"} ${game.ourScore}–${game.theirScore}",
                        fontWeight = FontWeight.Black, fontSize = 15.sp,
                        color = if (won) MaterialTheme.colorScheme.primary
                        else if (tied) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.error,
                    )
                }
                GameStatus.IN_PROGRESS -> Box(
                    Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(10.dp)),
                ) {
                    Text(
                        "LIVE",
                        fontSize = 11.sp, fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                GameStatus.SCHEDULED -> Text(
                    "›", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        DropdownMenuItem(text = { Text("Edit details") }, onClick = { menuOpen = false; onEdit() })
        DropdownMenuItem(text = { Text("Duplicate (doubleheader)") }, onClick = { menuOpen = false; onDuplicate() })
        DropdownMenuItem(
            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
            onClick = { menuOpen = false; onDelete() },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameSheet(
    title: String,
    initial: Game?,
    onSave: (Game) -> Unit,
    onDismiss: () -> Unit,
) {
    var opponent by remember { mutableStateOf(initial?.opponent ?: "") }
    var location by remember { mutableStateOf(initial?.location ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var homeAway by remember { mutableStateOf(initial?.homeAway ?: HomeAway.HOME) }
    var dateTimeMillis by remember {
        mutableStateOf(initial?.dateTime ?: defaultGameTime())
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = opponent, onValueChange = { opponent = it },
                label = { Text("Opponent") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                    Text(SimpleDateFormat("EEE MMM d, yyyy", Locale.US).format(Date(dateTimeMillis)))
                }
                OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.weight(1f)) {
                    Text(SimpleDateFormat("h:mm a", Locale.US).format(Date(dateTimeMillis)))
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = location, onValueChange = { location = it },
                label = { Text("Location (optional)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = homeAway == HomeAway.HOME,
                    onClick = { homeAway = HomeAway.HOME },
                    label = { Text("Home", fontWeight = FontWeight.Bold) },
                )
                FilterChip(
                    selected = homeAway == HomeAway.AWAY,
                    onClick = { homeAway = HomeAway.AWAY },
                    label = { Text("Away", fontWeight = FontWeight.Bold) },
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    onSave(
                        (initial ?: Game(teamId = 0, opponent = "", dateTime = 0))
                            .copy(
                                opponent = opponent.trim(),
                                dateTime = dateTimeMillis,
                                location = location.trim().ifBlank { null },
                                homeAway = homeAway,
                                notes = notes.trim().ifBlank { null },
                            )
                    )
                },
                enabled = opponent.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save game", fontWeight = FontWeight.ExtraBold) }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = dateTimeMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { picked ->
                        dateTimeMillis = mergeDate(dateTimeMillis, picked)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }

    if (showTimePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = dateTimeMillis }
        val state = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Game time") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    dateTimeMillis = mergeTime(dateTimeMillis, state.hour, state.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
        )
    }
}

private fun defaultGameTime(): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_YEAR, 1)
    set(Calendar.HOUR_OF_DAY, 10)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** Keep the time-of-day, replace the calendar date (date picker returns UTC midnight). */
private fun mergeDate(current: Long, pickedUtcMidnight: Long): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = current }
    val utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = pickedUtcMidnight }
    cal.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
    return cal.timeInMillis
}

private fun mergeTime(current: Long, hour: Int, minute: Int): Long =
    Calendar.getInstance().apply {
        timeInMillis = current
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
