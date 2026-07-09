package com.keithfalcon.softball.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.keithfalcon.softball.data.AppDatabase
import com.keithfalcon.softball.data.Availability
import com.keithfalcon.softball.data.Game
import com.keithfalcon.softball.data.Lineup
import com.keithfalcon.softball.data.LineupEntry
import com.keithfalcon.softball.data.OpponentInning
import com.keithfalcon.softball.data.PlateAppearance
import com.keithfalcon.softball.data.Player
import com.keithfalcon.softball.data.Team
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Full-database JSON backup (spec §9). The export file is the recovery net for phone
 * upgrades and a lost signing key — there is no cloud.
 */
@Serializable
data class BackupData(
    val schemaVersion: Int = SCHEMA_VERSION,
    val exportedAt: Long = 0,
    val teams: List<Team> = emptyList(),
    val players: List<Player> = emptyList(),
    val games: List<Game> = emptyList(),
    val availability: List<Availability> = emptyList(),
    val lineups: List<Lineup> = emptyList(),
    val lineupEntries: List<LineupEntry> = emptyList(),
    val plateAppearances: List<PlateAppearance> = emptyList(),
    val opponentInnings: List<OpponentInning> = emptyList(),
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

class BackupManager(private val context: Context, private val db: AppDatabase) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun exportTo(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val data = BackupData(
                exportedAt = System.currentTimeMillis(),
                teams = db.teamDao().all(),
                players = db.playerDao().all(),
                games = db.gameDao().all(),
                availability = db.availabilityDao().all(),
                lineups = db.lineupDao().allLineups(),
                lineupEntries = db.lineupDao().allEntries(),
                plateAppearances = db.plateAppearanceDao().all(),
                opponentInnings = db.opponentInningDao().all(),
            )
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(json.encodeToString(BackupData.serializer(), data).toByteArray())
            } ?: error("Could not open the selected file for writing")
        }
    }

    suspend fun importFrom(uri: Uri): Result<BackupData> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().decodeToString()
            } ?: error("Could not read the selected file")
            val data = json.decodeFromString(BackupData.serializer(), text)
            require(data.schemaVersion == BackupData.SCHEMA_VERSION) {
                "Backup schema version ${data.schemaVersion} is not supported by this app version"
            }
            db.withTransaction {
                // Delete children before parents, insert parents before children.
                db.backupDao().clearAll()
                db.backupDao().insertTeams(data.teams)
                db.backupDao().insertPlayers(data.players)
                db.backupDao().insertGames(data.games)
                db.backupDao().insertAvailability(data.availability)
                db.backupDao().insertLineups(data.lineups)
                db.backupDao().insertLineupEntries(data.lineupEntries)
                db.backupDao().insertPlateAppearances(data.plateAppearances)
                db.backupDao().insertOpponentInnings(data.opponentInnings)
            }
            data
        }
    }
}
