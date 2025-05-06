package com.example.WellBeingMotivationApp.roomDb

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_record")
data class DailyRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,
    val picturesTaken: Int,
    val picturesTakenSmiling: Int,
    val stepsTaken: Int,
    val timeSpentRunning: Long,
    val timeSpentWalking: Long,
    val visitedRecCenter: Int,
    val visitedCampusCenter: Int,
    val visitedMorgan: Int,
    val moodPoints: Int
)
