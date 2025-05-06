package com.example.WellBeingMotivationApp.roomDb

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DailyRecord::class],
    version = 1
)
abstract class DailyRecordDatabase: RoomDatabase() {
    abstract val dao : RoomDao
}