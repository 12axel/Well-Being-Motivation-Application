package com.example.WellBeingMotivationApp.MidnightTaskViewModel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class MidnightFlagBroadcastReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sharedPreferences = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("midnight_flag", true).apply()
        Log.d("Task Executed", "Midnight flag set to true")
    }
}