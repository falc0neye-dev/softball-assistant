package com.keithfalcon.softball.ui.roster

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.keithfalcon.softball.data.Player
import com.keithfalcon.softball.data.Sex
import com.keithfalcon.softball.ui.common.softballViewModel
import com.keithfalcon.softball.ui.scorecard.SexDot
import com.keithfalcon.softball.ui.theme.AmberDeep
import com.keithfalcon.softball.ui.theme.AmberTint
import com.keithfalcon.softball.ui.theme.FieldGreen
import com.keithfalcon.softball.ui.theme.GreenOnDark
import com.keithfalcon.softball.ui.theme.GreenTint

private val commonPositions = listOf(
    "P", "C", "1B", "2B", "3B", "SS", "LF", "LC", "RC", "RF", "DH",
)

@Composable
fun RosterScreen() {
    val vm = softballViewModel { app -> RosterViewModel(app) }
    val ui by vm.ui.collectAsState()

    var editingPlayer by remember { mutableStateOf<Player?>(null) }
    var addingPlayer by remember { mutableStateOf(false) }
    var editingTeamName by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (ui.team != null) {
                ExtendedFloatingActionButton(
                    onClick = { addingPlayer = true },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = Color.White,
                ) { Text("+  Add player", fontWeight = FontWeight.Bold) }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Green app header per mockup 01
            Surface(color = FieldGreen) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text(
                        "KEITH'S SOFTBALL ASSISTANT",
                        color = GreenOnDark, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 2.5.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        ui.team?.name ?: "No team yet",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.clickable(enabled = ui.team != null) { editingTeamName = true },
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (ui.team != null)
                            "${ui.activeCount} active players · ${ui.femaleCount} F · ${ui.maleCount} M"
                        else "Create your team to get started",
                        color = GreenOnDark, fontSize = 12.sp,
                    )
                }
            }

            if (ui.team == null) {
                CreateTeamPrompt(onCreate = vm::createTeam)
                return@Column
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    TextButton(onClick = { sortMenuOpen = true }) {
                        Text(
                            "Sort: ${sortLabel(ui.sort)} ▾",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        )
                    }
                    DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                        RosterSort.entries.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(sortLabel(s)) },
                                onClick = { vm.setSort(s); sortMenuOpen = false },
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("Inactive", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Switch(checked = ui.showInactive, onCheckedChange = vm::setShowInactive)
            }

            if (ui.players.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "No players yet — add your first teammate.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(ui.players.size, key = { ui.players[it].id }) { i ->
                        PlayerCard(player = ui.players[i], onClick = { editingPlayer = ui.players[i] })
                    }
                }
            }
        }
    }

    if (addingPlayer) {
        PlayerSheet(
            title = "Add player",
            initial = null,
            onSave = { vm.savePlayer(it); addingPlayer = false },
            onDismiss = { addingPlayer = false },
            onDeactivate = null,
        )
    }
    editingPlayer?.let { player ->
        PlayerSheet(
            title = "Edit player",
            initial = player,
            onSave = { vm.savePlayer(it); editingPlayer = null },
            onDismiss = { editingPlayer = null },
            onDeactivate = {
                vm.setActive(player, !player.isActive)
                editingPlayer = null
            },
        )
    }
    if (editingTeamName) {
        TeamNameDialog(
            initial = ui.team?.name ?: "",
            title = "Rename team",
            onSave = { vm.renameTeam(it); editingTeamName = false },
            onDismiss = { editingTeamName = false },
        )
    }
}

private fun sortLabel(sort: RosterSort) = when (sort) {
    RosterSort.LAST_NAME -> "Last name"
    RosterSort.FIRST_NAME -> "First name"
    RosterSort.POSITION -> "Position"
}

@Composable
private fun CreateTeamPrompt(onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Text("Name your team", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Team name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = { onCreate(name) }, enabled = name.isNotBlank()) {
            Text("Create team", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PlayerCard(player: Player, onClick: () -> Unit) {
    val avatarBg = if (player.sex == Sex.FEMALE) AmberTint else GreenTint
    val avatarFg = if (player.sex == Sex.FEMALE) AmberDeep else FieldGreen
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).background(avatarBg, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    initials(player),
                    color = avatarFg, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    player.fullName,
                    fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    color = if (player.isActive) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!player.isActive) {
                    Text("Inactive", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }
            }
            if (player.position.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(13.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Text(
                        player.position,
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
            }
            SexDot(player.sex, size = 24.dp)
        }
    }
}

private fun initials(player: Player): String =
    "${player.firstName.firstOrNull() ?: ' '}${player.lastName.firstOrNull() ?: ' '}".trim().uppercase()

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PlayerSheet(
    title: String,
    initial: Player?,
    onSave: (Player) -> Unit,
    onDismiss: () -> Unit,
    onDeactivate: (() -> Unit)?,
) {
    var firstName by remember { mutableStateOf(initial?.firstName ?: "") }
    var lastName by remember { mutableStateOf(initial?.lastName ?: "") }
    var position by remember { mutableStateOf(initial?.position ?: "") }
    var sex by remember { mutableStateOf(initial?.sex ?: Sex.MALE) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = firstName, onValueChange = { firstName = it },
                    label = { Text("First name") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = lastName, onValueChange = { lastName = it },
                    label = { Text("Last name") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("POSITION", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                commonPositions.forEach { pos ->
                    FilterChip(
                        selected = position == pos,
                        onClick = { position = if (position == pos) "" else pos },
                        label = { Text(pos, fontWeight = FontWeight.Bold) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = position, onValueChange = { position = it },
                label = { Text("Position (or type your own)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text("SEX (drives co-ed lineup)", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = sex == Sex.MALE,
                    onClick = { sex = Sex.MALE },
                    label = { Text("Male", fontWeight = FontWeight.Bold) },
                )
                FilterChip(
                    selected = sex == Sex.FEMALE,
                    onClick = { sex = Sex.FEMALE },
                    label = { Text("Female", fontWeight = FontWeight.Bold) },
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        onSave(
                            (initial ?: Player(teamId = 0, firstName = "", lastName = "", sex = sex))
                                .copy(
                                    firstName = firstName.trim(),
                                    lastName = lastName.trim(),
                                    position = position.trim(),
                                    sex = sex,
                                )
                        )
                    },
                    enabled = firstName.isNotBlank() && lastName.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) { Text("Save", fontWeight = FontWeight.ExtraBold) }
                if (onDeactivate != null && initial != null) {
                    TextButton(onClick = onDeactivate) {
                        Text(
                            if (initial.isActive) "Deactivate" else "Reactivate",
                            color = if (initial.isActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TeamNameDialog(initial: String, title: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Team name") }, singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
