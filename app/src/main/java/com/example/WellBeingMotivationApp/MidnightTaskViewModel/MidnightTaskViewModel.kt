package com.example.WellBeingMotivationApp.MidnightTaskViewModel

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime


class MidnightTaskViewModel(context: Context): ViewModel() {

    private fun calculateTimeUntilMidnight(): Long {
        val now = LocalDateTime.now()

        val midnight = LocalDateTime.of(now.toLocalDate().plusDays(1), LocalTime.MIDNIGHT)

        return Duration.between(now, midnight).toMillis()
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val intent = Intent(context, MidnightFlagBroadcastReceiver::class.java)
    private val pendingIntent = PendingIntent.getBroadcast(context, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun setMidnightTask() {
        Log.d("Time Until Midnight", calculateTimeUntilMidnight().toString())

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + calculateTimeUntilMidnight(),
            pendingIntent
        )
    }

    fun cancelMidnightTask() {
        alarmManager.cancel(pendingIntent)
    }
}