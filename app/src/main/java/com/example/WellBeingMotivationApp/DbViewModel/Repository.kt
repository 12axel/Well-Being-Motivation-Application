package com.example.WellBeingMotivationApp.DbViewModel

import com.example.WellBeingMotivationApp.roomDb.DailyRecord
import com.example.WellBeingMotivationApp.roomDb.DailyRecordDatabase


class Repository(private val db: DailyRecordDatabase){

    suspend fun insertRecord(dailyRecord: DailyRecord){
        db.dao.insertRecord(dailyRecord)
    }

    fun getTop5Records() = db.dao.getTop5Records()

    fun getRecordForToday(date: String) = db.dao.getRecordForToday(date)

    suspend fun updateRecord(dailyRecord: DailyRecord){
        db.dao.updateRecord(dailyRecord)
    }
}