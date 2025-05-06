package com.example.WellBeingMotivationApp.roomDb

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomDao {

    @Insert
    suspend fun insertRecord(dailyRecord: DailyRecord)

    @Query("SELECT * FROM daily_record WHERE date = :date")
    fun getRecordForToday(date: String): Flow<List<DailyRecord>>

    @Query("SELECT * FROM daily_record WHERE id IN " +
            "(SELECT MAX(id) FROM daily_record GROUP BY date) " +
            "ORDER BY id DESC LIMIT 5")
    fun getTop5Records(): Flow<List<DailyRecord>>

    @Update
    suspend fun updateRecord(dailyRecord: DailyRecord)

}