package com.example.WellBeingMotivationApp.MapViewModel

import android.content.BroadcastReceiver
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent

object GeofenceVisitChecker {
    var visitedRecCenter by mutableStateOf(0)
    var visitedCampusCenter by mutableStateOf(0)
    var visitedMorganHall by mutableStateOf(0)
}

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.e("GeofenceReceiver", "We got to onReceive")

        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent?.hasError() == true) {
            val errorMessage = geofencingEvent.let {
                GeofenceStatusCodes
                    .getStatusCodeString(it.errorCode)
            }
            Log.e(TAG, errorMessage)
            return
        }


        when (geofencingEvent?.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_DWELL -> {
                geofencingEvent.triggeringGeofences?.forEach { geofence ->
                    when(geofence.requestId) {
                        "Rec Center" -> {
                            if (GeofenceVisitChecker.visitedRecCenter == 0) {
                                GeofenceVisitChecker.visitedRecCenter++
                            }
                        }

                        "Campus Center" -> {
                            if (GeofenceVisitChecker.visitedCampusCenter == 0) {
                                GeofenceVisitChecker.visitedCampusCenter++
                            }
                        }

                        "Morgan Hall" -> {
                            if (GeofenceVisitChecker.visitedMorganHall == 0) {
                                GeofenceVisitChecker.visitedMorganHall++
                            }
                        }
                    }
                }
            }
            else -> {
                Log.e(TAG, "Error in setting up the geofence")
            }
        }
    }
}