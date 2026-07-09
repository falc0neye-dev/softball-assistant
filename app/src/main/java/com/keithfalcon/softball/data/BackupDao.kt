package com.keithfalcon.softball.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Bulk operations used only by JSON backup import (spec §9). */
@Dao
interface BackupDao {

    suspend fun clearAll() {
        clearPlateAppearances()
        clearLineupEntries()
        clearLineups()
        clearAvailability()
        clearOpponentInnings()
        clearGames()
        clearPlayers()
        clearTeams()
    }

    @Query("DELETE FROM plate_appearances") suspend fun clearPlateAppearances()
    @Query("DELETE FROM lineup_entries") suspend fun clearLineupEntries()
    @Query("DELETE FROM lineups") suspend fun clearLineups()
    @Query("DELETE FROM availability") suspend fun clearAvailability()
    @Query("DELETE FROM opponent_innings") suspend fun clearOpponentInnings()
    @Query("DELETE FROM games") suspend fun clearGames()
    @Query("DELETE FROM players") suspend fun clearPlayers()
    @Query("DELETE FROM teams") suspend fun clearTeams()

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTeams(items: List<Team>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPlayers(items: List<Player>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertGames(items: List<Game>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAvailability(items: List<Availability>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertLineups(items: List<Lineup>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertLineupEntries(items: List<LineupEntry>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPlateAppearances(items: List<PlateAppearance>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertOpponentInnings(items: List<OpponentInning>)
}
