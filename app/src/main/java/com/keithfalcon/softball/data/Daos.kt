package com.keithfalcon.softball.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TeamDao {
    @Query("SELECT * FROM teams ORDER BY createdAt LIMIT 1")
    fun observeFirstTeam(): Flow<Team?>

    @Query("SELECT * FROM teams ORDER BY createdAt LIMIT 1")
    suspend fun firstTeam(): Team?

    @Insert
    suspend fun insert(team: Team): Long

    @Update
    suspend fun update(team: Team)

    @Query("SELECT * FROM teams")
    suspend fun all(): List<Team>
}

@Dao
interface PlayerDao {
    @Query("SELECT * FROM players WHERE teamId = :teamId AND isActive = 1 ORDER BY lastName, firstName")
    fun observeActive(teamId: Long): Flow<List<Player>>

    @Query("SELECT * FROM players WHERE teamId = :teamId ORDER BY lastName, firstName")
    fun observeAll(teamId: Long): Flow<List<Player>>

    @Query("SELECT * FROM players WHERE id = :id")
    suspend fun byId(id: Long): Player?

    @Query("SELECT * FROM players")
    fun observeEveryone(): Flow<List<Player>>

    @Insert
    suspend fun insert(player: Player): Long

    @Update
    suspend fun update(player: Player)

    @Query("SELECT * FROM players")
    suspend fun all(): List<Player>
}

@Dao
interface GameDao {
    @Query("SELECT * FROM games WHERE teamId = :teamId ORDER BY dateTime DESC")
    fun observeAll(teamId: Long): Flow<List<Game>>

    @Query("SELECT * FROM games WHERE id = :id")
    fun observe(id: Long): Flow<Game?>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun byId(id: Long): Game?

    @Query(
        "SELECT * FROM games WHERE teamId = :teamId AND id != :excludeGameId AND dateTime < :before " +
            "ORDER BY dateTime DESC LIMIT 1"
    )
    suspend fun previousGame(teamId: Long, excludeGameId: Long, before: Long): Game?

    @Insert
    suspend fun insert(game: Game): Long

    @Update
    suspend fun update(game: Game)

    @Delete
    suspend fun delete(game: Game)

    @Query("SELECT * FROM games")
    suspend fun all(): List<Game>
}

@Dao
interface AvailabilityDao {
    @Query("SELECT * FROM availability WHERE gameId = :gameId")
    fun observeForGame(gameId: Long): Flow<List<Availability>>

    @Query("SELECT * FROM availability WHERE gameId = :gameId")
    suspend fun forGame(gameId: Long): List<Availability>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(availability: Availability)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(availabilities: List<Availability>)

    @Query("DELETE FROM availability WHERE gameId = :gameId AND playerId = :playerId")
    suspend fun clear(gameId: Long, playerId: Long)

    @Query("SELECT * FROM availability")
    suspend fun all(): List<Availability>
}

@Dao
interface LineupDao {
    @Query("SELECT * FROM lineups WHERE gameId = :gameId")
    fun observe(gameId: Long): Flow<Lineup?>

    @Query("SELECT * FROM lineups WHERE gameId = :gameId")
    suspend fun byGame(gameId: Long): Lineup?

    @Query("SELECT * FROM lineup_entries WHERE gameId = :gameId ORDER BY queue, orderInQueue")
    fun observeEntries(gameId: Long): Flow<List<LineupEntry>>

    @Query("SELECT * FROM lineup_entries WHERE gameId = :gameId ORDER BY queue, orderInQueue")
    suspend fun entries(gameId: Long): List<LineupEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(lineup: Lineup)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<LineupEntry>)

    @Query("DELETE FROM lineup_entries WHERE gameId = :gameId")
    suspend fun clearEntries(gameId: Long)

    /** Atomic replace so a crash mid-save can't leave a half-written lineup. */
    @Transaction
    suspend fun replace(lineup: Lineup, entries: List<LineupEntry>) {
        upsert(lineup)
        clearEntries(lineup.gameId)
        insertEntries(entries)
    }

    @Query("SELECT * FROM lineups")
    suspend fun allLineups(): List<Lineup>

    @Query("SELECT * FROM lineup_entries")
    suspend fun allEntries(): List<LineupEntry>
}

@Dao
interface PlateAppearanceDao {
    @Query("SELECT * FROM plate_appearances WHERE gameId = :gameId ORDER BY sequence")
    fun observeForGame(gameId: Long): Flow<List<PlateAppearance>>

    @Query("SELECT * FROM plate_appearances WHERE gameId = :gameId ORDER BY sequence")
    suspend fun forGame(gameId: Long): List<PlateAppearance>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pa: PlateAppearance): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(pas: List<PlateAppearance>)

    @Query("DELETE FROM plate_appearances WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM plate_appearances WHERE gameId = :gameId")
    suspend fun deleteForGame(gameId: Long)

    /**
     * Rewrite the full ordered sequence for a game in one transaction — used after
     * insert/delete/reorder edits so `sequence` stays dense and consistent (spec §6.5).
     */
    @Transaction
    suspend fun replaceForGame(gameId: Long, pas: List<PlateAppearance>) {
        deleteForGame(gameId)
        upsertAll(pas)
    }

    @Query("SELECT * FROM plate_appearances")
    suspend fun all(): List<PlateAppearance>

    @Query("SELECT * FROM plate_appearances")
    fun observeForAll(): Flow<List<PlateAppearance>>
}

@Dao
interface OpponentInningDao {
    @Query("SELECT * FROM opponent_innings WHERE gameId = :gameId ORDER BY inning")
    fun observeForGame(gameId: Long): Flow<List<OpponentInning>>

    @Query("SELECT * FROM opponent_innings WHERE gameId = :gameId ORDER BY inning")
    suspend fun forGame(gameId: Long): List<OpponentInning>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(inning: OpponentInning)

    @Query("SELECT * FROM opponent_innings")
    suspend fun all(): List<OpponentInning>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(innings: List<OpponentInning>)
}
