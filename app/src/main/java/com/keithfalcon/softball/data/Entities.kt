package com.keithfalcon.softball.data

import androidx.room.Entity
import kotlinx.serialization.Serializable
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class Sex { MALE, FEMALE }

enum class HomeAway { HOME, AWAY }

enum class GameStatus { SCHEDULED, IN_PROGRESS, FINAL }

enum class AvailabilityStatus { IN, TENTATIVE, OUT }

enum class LineupType { STATIC, DYNAMIC }

/** Which ordered list a lineup entry belongs to. STATIC lineups use a single BATTING list. */
enum class LineupQueue { BATTING, MALE, FEMALE }

/**
 * Plate-appearance outcomes (spec §6.2). AUTO_OUT is the co-ed empty-female-slot rule;
 * MANUAL_OUT is the "+1 out" button for plays between recorded at-bats. Both are
 * playerless rows so the 3-out inning math stays derived from the sequence alone.
 */
enum class Outcome(val label: String, val reachedBase: Boolean) {
    SINGLE("1B", true),
    DOUBLE("2B", true),
    TRIPLE("3B", true),
    HOME_RUN("HR", true),
    WALK("BB", true),
    FIELDERS_CHOICE("FC", true),
    REACHED_ON_ERROR("E", true),
    HIT_BY_PITCH("HBP", true),
    GROUNDOUT("GO", false),
    FLYOUT("FO", false),
    STRIKEOUT("K", false),
    SAC_FLY("SF", false),
    FC_OUT("FC-O", false),
    OUT("OUT", false),
    AUTO_OUT("AUTO", false),
    MANUAL_OUT("+1", false),
}

enum class RunnerResult { NONE, ON_BASE, SCORED, OUT, LEFT_ON_BASE }

@Serializable
@Entity(tableName = "teams")
data class Team(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "players",
    foreignKeys = [ForeignKey(
        entity = Team::class,
        parentColumns = ["id"],
        childColumns = ["teamId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("teamId")],
)
data class Player(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val teamId: Long,
    val firstName: String,
    val lastName: String,
    val position: String = "",
    val sex: Sex,
    val isActive: Boolean = true,
) {
    val fullName: String get() = "$firstName $lastName".trim()
}

@Serializable
@Entity(
    tableName = "games",
    foreignKeys = [ForeignKey(
        entity = Team::class,
        parentColumns = ["id"],
        childColumns = ["teamId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("teamId")],
)
data class Game(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val teamId: Long,
    val opponent: String,
    val dateTime: Long,
    val location: String? = null,
    val homeAway: HomeAway = HomeAway.HOME,
    val notes: String? = null,
    val status: GameStatus = GameStatus.SCHEDULED,
    val ourScore: Int = 0,
    val theirScore: Int = 0,
)

@Serializable
@Entity(
    tableName = "availability",
    primaryKeys = ["gameId", "playerId"],
    foreignKeys = [
        ForeignKey(
            entity = Game::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Player::class,
            parentColumns = ["id"],
            childColumns = ["playerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playerId")],
)
data class Availability(
    val gameId: Long,
    val playerId: Long,
    val status: AvailabilityStatus,
)

/** One lineup per game; also carries the dynamic-generation settings. */
@Serializable
@Entity(
    tableName = "lineups",
    foreignKeys = [ForeignKey(
        entity = Game::class,
        parentColumns = ["id"],
        childColumns = ["gameId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class Lineup(
    @PrimaryKey val gameId: Long,
    val type: LineupType = LineupType.STATIC,
    val ratioMale: Int = 3,
    val ratioFemale: Int = 1,
    val autoOutOnEmptyFemaleSlot: Boolean = false,
)

@Serializable
@Entity(
    tableName = "lineup_entries",
    primaryKeys = ["gameId", "queue", "orderInQueue"],
    foreignKeys = [
        ForeignKey(
            entity = Lineup::class,
            parentColumns = ["gameId"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Player::class,
            parentColumns = ["id"],
            childColumns = ["playerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playerId")],
)
data class LineupEntry(
    val gameId: Long,
    val queue: LineupQueue,
    val orderInQueue: Int,
    val playerId: Long,
)

/**
 * One scorecard row (spec §3.6). `sequence` is the global batting order in the game and is
 * the single source of truth: innings, outs, and scores are always recomputed from the
 * ordered sequence (spec §12.2), never edited directly. `playerId` is null for AUTO_OUT
 * and MANUAL_OUT rows.
 */
@Serializable
@Entity(
    tableName = "plate_appearances",
    foreignKeys = [
        ForeignKey(
            entity = Game::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Player::class,
            parentColumns = ["id"],
            childColumns = ["playerId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("gameId"), Index("playerId")],
)
data class PlateAppearance(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: Long,
    val sequence: Int,
    val playerId: Long?,
    val outcome: Outcome,
    val runnerResult: RunnerResult = RunnerResult.NONE,
    val rbi: Int = 0,
    val note: String? = null,
)

/** Opponent runs per inning — simple +/- entry, we don't score their at-bats (spec §6.6). */
@Serializable
@Entity(
    tableName = "opponent_innings",
    primaryKeys = ["gameId", "inning"],
    foreignKeys = [ForeignKey(
        entity = Game::class,
        parentColumns = ["id"],
        childColumns = ["gameId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class OpponentInning(
    val gameId: Long,
    val inning: Int,
    val runs: Int,
)
