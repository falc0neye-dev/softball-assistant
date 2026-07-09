package com.keithfalcon.softball.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Team::class,
        Player::class,
        Game::class,
        Availability::class,
        Lineup::class,
        LineupEntry::class,
        PlateAppearance::class,
        OpponentInning::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun teamDao(): TeamDao
    abstract fun playerDao(): PlayerDao
    abstract fun gameDao(): GameDao
    abstract fun availabilityDao(): AvailabilityDao
    abstract fun lineupDao(): LineupDao
    abstract fun plateAppearanceDao(): PlateAppearanceDao
    abstract fun opponentInningDao(): OpponentInningDao
    abstract fun backupDao(): BackupDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "softball.db")
                .build()
    }
}
