package com.example.WellBeingMotivationApp.MapViewModel

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.google.android.gms.maps.model.LatLng
import androidx.compose.runtime.State
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.Locale

class MapViewModel : ViewModel(){
    private val _userLocation = mutableStateOf<LatLng?>(null)
    val userLocation: State<LatLng?> = _userLocation

    private val _userAddress = mutableStateOf<String?>(null)
    val userAddress: State<String?> = _userAddress

    val geofenceList = mutableListOf<Geofence>()

    fun fetchUserLocation(context: Context, fusedLocationClient: FusedLocationProviderClient){
        if(ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000).apply {
                            setMinUpdateDistanceMeters(10f)
                            setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
                            setWaitForAccurateLocation(true)
                        }.build()

                        val locationCallback = object : LocationCallback() {
                            override fun onLocationResult(locationResult: LocationResult) {
                                locationResult.locations.firstOrNull()?.let {
                                    val userLatLng = LatLng(it.latitude, it.longitude)
                                    _userLocation.value = userLatLng

                                    getAddressForLocation(it.latitude, it.longitude, context)
                                }
                            }
                        }

                        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
                    } catch (e: SecurityException) {
                        Log.e("Perm Loc Revoked","Permission for location access was revoked: ${e.localizedMessage}")
                    }
                } else {
                    Log.e("Back Loc Denied","Background Location permission is not granted.")
                }
            }

            else{
                try {

                    val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000).apply {
                        setMinUpdateDistanceMeters(10f)
                        setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
                        setWaitForAccurateLocation(true)
                    }.build()

                    val locationCallback = object : LocationCallback() {
                        override fun onLocationResult(locationResult: LocationResult) {
                            locationResult.locations.firstOrNull()?.let {
                                val userLatLng = LatLng(it.latitude, it.longitude)
                                _userLocation.value = userLatLng

                                // Get the address for the user's location
                                getAddressForLocation(it.latitude, it.longitude, context)
                            }
                        }
                    }

                    fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())

                } catch (e: SecurityException) {
                    Log.e("Perm Loc Acc Removed", "Permission for location access was revoked: ${e.localizedMessage}")
                }
            }
        } else {
            Log.e("Perm Loc Not Granted","Location permission is not granted.")
        }

    }

    private fun getAddressForLocation(latitude: Double, longitude: Double, context: Context) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                    if(addresses.isNotEmpty()) {
                        _userAddress.value = addresses[0].getAddressLine(0)
                    } else {
                        _userAddress.value = "Address not found"
                    }
                }
            } else {
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if(addresses != null){
                    if(addresses.isNotEmpty()) {
                        _userAddress.value =
                            addresses[0]?.getAddressLine(0)
                    } else{
                        _userAddress.value = "Address not found"
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("Could not get address","Error getting address: ${e.message}")
            _userAddress.value = "Error retrieving address"
        }
    }

    fun createGeofences(context: Context) {

        val geofencingClient = LocationServices.getGeofencingClient(context)

        geofenceList.add(
            Geofence.Builder()
                .setRequestId("Rec Center")
                .setCircularRegion(42.27450432743021, -71.81038211906494, 50f)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_DWELL)
                .setLoiteringDelay(5000)
                .build()
        )

        geofenceList.add(
            Geofence.Builder()
                .setRequestId("Campus Center")
                .setCircularRegion(42.274601803705245, -71.80827834089293, 50f)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_DWELL)
                .setLoiteringDelay(5000)
                .build()
        )

        geofenceList.add(
            Geofence.Builder()
                .setRequestId("Morgan Hall")
                .setCircularRegion(42.273402871206834, -71.81080353255687, 50f)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_DWELL)
                .setLoiteringDelay(5000)
                .build()
        )

        val geofencingRequest = GeofencingRequest.Builder()
            .addGeofences(geofenceList)
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .build()

        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            geofencingClient.addGeofences(geofencingRequest, pendingIntent)
                .addOnSuccessListener {
                    Log.d("Geofences added", "Geofences added successfully")
                }
                .addOnFailureListener { e ->
                    Log.e("Error adding geofences", "Failed to add geofences: ${e.localizedMessage}")
                }
        } else {
            Log.e("Permission", "Missing ACCESS_FINE_LOCATION permission for geofencing")
        }
    }
}

