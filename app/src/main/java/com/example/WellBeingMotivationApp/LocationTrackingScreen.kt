package com.example.WellBeingMotivationApp

import android.content.ContentValues.TAG
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.WellBeingMotivationApp.DbViewModel.DailyRecordViewModel
import com.example.WellBeingMotivationApp.MapViewModel.GeofenceVisitChecker
import com.example.WellBeingMotivationApp.MapViewModel.MapViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay

@Composable
fun LocationTrackingScreen(
    mapViewModel: MapViewModel,
    navController: NavController,
    viewModel: DailyRecordViewModel,
    context: Context
) {
    val records by viewModel.getTop5Records().observeAsState(listOf())
    var record = records.firstOrNull()

    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    val stepCounterSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    var currentActivity by rememberSaveable { mutableStateOf("Still") }
    //var previousActivity by rememberSaveable { mutableStateOf("Still") }

    var stepCount by rememberSaveable { mutableStateOf(record?.stepsTaken ?: 0) }

    var walkingTime by remember { mutableStateOf(record?.timeSpentWalking ?: 0L) }

    var runningTime by remember { mutableStateOf(record?.timeSpentRunning ?: 0L) }

    var activityStartTime by remember { mutableStateOf(System.currentTimeMillis()) }

    var lastTimestamp by remember { mutableStateOf(0L) }

    val stepIntervals = remember { mutableStateListOf<Double>() }

    var durationString by remember { mutableStateOf("") }
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

    val midnightFlag by viewModel.midnightFlag.observeAsState(false)

    var detectActivities = !midnightFlag

    val cameraPositionState = rememberCameraPositionState()
    val userLocation by mapViewModel.userLocation
    val userAddress by mapViewModel.userAddress

//    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Handle permission requests for accessing fine location
//    val permissionLauncher = rememberLauncherForActivityResult(
//        contract = ActivityResultContracts.RequestMultiplePermissions()
//    ) { permissions ->
//        val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
//        val backgroundGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//            permissions[android.Manifest.permission.ACCESS_BACKGROUND_LOCATION] == true
//        } else {
//            true
//        }
//
//        if (fineGranted && backgroundGranted) {
//            mapViewModel.fetchUserLocation(context, fusedLocationClient)
//        } else {
//            Log.e("Loc Perm Denied", "Location permission was denied by the user.")
//        }
//    }
//
//    LaunchedEffect(Unit) {
//        if ((ContextCompat.checkSelfPermission(
//                context,
//                android.Manifest.permission.ACCESS_FINE_LOCATION
//            ) == PackageManager.PERMISSION_GRANTED) &&
//            (ContextCompat.checkSelfPermission(
//                context,
//                android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
//            ) == PackageManager.PERMISSION_GRANTED)
//        ) {
//            mapViewModel.fetchUserLocation(context, fusedLocationClient)
//
//        } else {
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                permissionLauncher.launch(
//                    arrayOf(
//                        android.Manifest.permission.ACCESS_FINE_LOCATION,
//                        android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
//                    )
//                )
//            } else {
//                permissionLauncher.launch(
//                    arrayOf(
//                        android.Manifest.permission.ACCESS_FINE_LOCATION
//                    )
//                )
//            }
//        }
//    }

    LaunchedEffect(userLocation) {
        userLocation?.let { location ->
            cameraPositionState.position = CameraPosition.fromLatLngZoom(location, 17f)
        }
    }

    LaunchedEffect(records) {
        record = records.firstOrNull()
        stepCount = record?.stepsTaken ?: 0
        walkingTime = record?.timeSpentWalking ?: 0
        runningTime = record?.timeSpentRunning ?: 0
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)

            val now = System.currentTimeMillis()
            if (now - lastTimestamp >= 1500 && currentActivity != "Still") {
                val durationMillis = now - activityStartTime

                val totalSeconds = (durationMillis / 1000).toInt()
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                durationString = if (minutes > 0) {
                    "$minutes min, $seconds seconds"
                } else {
                    "$seconds seconds"
                }

                when (currentActivity) {
                    "Walking" -> {
                        walkingTime += durationMillis
                    }

                    "Running" -> {
                        runningTime += durationMillis
                    }
                }

                currentActivity = "Still"

                activityStartTime = currentTime
            }
        }
    }

    if (detectActivities) {

        LaunchedEffect(GeofenceVisitChecker.visitedRecCenter, GeofenceVisitChecker.visitedCampusCenter, GeofenceVisitChecker.visitedMorganHall) {
            record?.let {
                val updatedRecord = it.copy(
                    visitedRecCenter = if(GeofenceVisitChecker.visitedRecCenter > it.visitedRecCenter) 1 else it.visitedRecCenter,
                    visitedCampusCenter = if(GeofenceVisitChecker.visitedCampusCenter > it.visitedCampusCenter) 1 else it.visitedCampusCenter,
                    visitedMorgan = if(GeofenceVisitChecker.visitedMorganHall > it.visitedMorgan) 1 else it.visitedMorgan
                )
                viewModel.updateRecord(updatedRecord)
            }
        }

        LaunchedEffect(stepCount, walkingTime, runningTime) {
            record?.let {
                val stepsToAdd = stepCount - it.stepsTaken
                val updatedRecord = it.copy(
                    stepsTaken = if(stepsToAdd > 0) it.stepsTaken + stepsToAdd else it.stepsTaken,
                    timeSpentWalking = walkingTime,
                    timeSpentRunning = runningTime
                )
                viewModel.updateRecord(updatedRecord)
            }
        }

        DisposableEffect(stepCounterSensor) {
            val sensorListener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    event?.let {
                        if (it.sensor.type == Sensor.TYPE_STEP_DETECTOR) {

                            stepCount += 1

                            currentTime = System.currentTimeMillis()

                            if (lastTimestamp != 0L) {

                                //val deltaSteps = newSteps - lastStepCount
                                val deltaTimeSec = (currentTime - lastTimestamp) / 1000.0

                                stepIntervals.add(deltaTimeSec)

                                if (stepIntervals.size > 4) {
                                    stepIntervals.removeAt(0)
                                }

                                var averageCadence = 0.0
                                if (stepIntervals.sum() > 0) {
                                    averageCadence = stepIntervals.size / stepIntervals.sum()

                                    var newStatus = when {
                                        averageCadence == 0.0 -> "Still"
                                        averageCadence < 2.5 -> "Walking"
                                        else -> "Running"
                                    }

                                    if (newStatus != currentActivity) {

                                        //previousActivity = currentActivity

                                        val durationMillis = currentTime - activityStartTime
                                        val totalSeconds = (durationMillis / 1000).toInt()
                                        val minutes = totalSeconds / 60
                                        val seconds = totalSeconds % 60
                                        durationString = if (minutes > 0) {
                                            "$minutes min, $seconds seconds"
                                        } else {
                                            "$seconds seconds"
                                        }

                                        if (activityStartTime != currentTime) {

                                            when {
                                                currentActivity == "Still" -> {
                                                }

                                                currentActivity == "Walking" -> {
                                                    walkingTime += durationMillis
                                                }

                                                currentActivity == "Running" -> {
                                                    runningTime += durationMillis
                                                }
                                            }

                                        }

                                        currentActivity = newStatus
                                        activityStartTime = currentTime
                                    }
                                }
                            }

                            //lastStepCount = newSteps
                            lastTimestamp = currentTime
                            //stepCount = newSteps
                        }
                    }

                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                    Log.d(TAG, "Accuracy changed to: $accuracy")
                }
            }

            // Register the sensor listener if the sensor is available.
            stepCounterSensor?.let {
                if (currentActivity == "Running") {
                    sensorManager.registerListener(
                        sensorListener,
                        it,
                        SensorManager.SENSOR_DELAY_FASTEST
                    )
                } else {
                    sensorManager.registerListener(
                        sensorListener,
                        it,
                        SensorManager.SENSOR_DELAY_UI
                    )
                }

            }
            onDispose {
                sensorManager.unregisterListener(sensorListener)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color(0xFF35006C)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(65.dp)
                .background(color = Color(0xFF35006C)),
            contentAlignment = Alignment.Center
        )
        {
            Text(
                text = "Location Tracking Screen",
                color = Color(0xFFD7D8ED),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        GoogleMap(
            modifier = Modifier
                .height(325.dp),
            cameraPositionState = cameraPositionState
        ) {
            userLocation?.let { location ->

                val markerSnippet = userAddress ?: "Fetching address..."

                Marker(
                    state = MarkerState(position = location),
                    title = "Your location",
                    snippet = markerSnippet
                )

            }

            // Rec Center Geofence Circle
            Circle(
                center = LatLng(42.27450432743021, -71.81038211906494),
                radius = 50.0,
                fillColor = Color(0x330000FF),
                strokeColor = Color.Blue,
                strokeWidth = 2f
            )

            // Campus Center Geofence Circle
            Circle(
                center = LatLng(42.274601803705245, -71.80827834089293),
                radius = 50.0,
                fillColor = Color(0x330000FF),
                strokeColor = Color.Blue,
                strokeWidth = 2f
            )

            // Morgan Hall Geofence Circle
            Circle(
                center = LatLng(42.273402871206834, -71.81080353255687),
                radius = 50.0,
                fillColor = Color(0x330000FF),
                strokeColor = Color.Blue,
                strokeWidth = 2f
            )

            // Rec Center Geofence Marker
            Marker(
                state = MarkerState(position = LatLng(42.27450432743021, -71.81038211906494)),
                title = "Rec Center Geofence"
            )

            // Campus Center Geofence Marker
            Marker(
                state = MarkerState(position = LatLng(42.274601803705245, -71.80827834089293)),
                title = "Campus Center Geofence"
            )

            // Morgan Hall Geofence Circle
            Marker(
                state = MarkerState(position = LatLng(42.273402871206834, -71.81080353255687)),
                title = "Morgan Hall Geofence"
            )
        }

        Spacer(modifier = Modifier.height(35.dp))

//        Text(
//            text = "Visited Rec Center geofence: ${GeofenceVisitChecker.visitedRecCenter}",
//            color = Color.White,
//            fontWeight = FontWeight.Bold,
//            fontSize = 15.sp
//        )
//
//        Text(
//            text = "Visited Campus Center geofence: ${GeofenceVisitChecker.visitedCampusCenter}",
//            color = Color.White,
//            fontWeight = FontWeight.Bold,
//            fontSize = 15.sp
//        )
//
//        Text(
//            text = "Visited Morgan Hall geofence: ${GeofenceVisitChecker.visitedMorganHall}",
//            color = Color.White,
//            fontWeight = FontWeight.Bold,
//            fontSize = 15.sp
//        )

        record?.visitedRecCenter?.let {
            if (it == 0) {
                Text(
                    text = "Visited The Recreation Center Today: No",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text(
                    text = "Visited The Recreation Center Today: Yes",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }


        }
        record?.visitedCampusCenter?.let {
            if (it == 0) {
                Text(
                    text = "Visited The Campus Center Today: No",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text(
                    text = "Visited The Campus Center Today: Yes",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        record?.visitedMorgan?.let {
            if (it == 0) {
                Text(
                    text = "Visited Morgan Hall Today: No",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text(
                    text = "Visited Morgan Hall Today: Yes",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(15.dp))

        if (!detectActivities) {
            Text(
                text = "The current day is over!",
                color = Color.Red,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Please refresh the app to start the new day!",
                color = Color.Red,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            Text(
                text = "",
                color = Color.Red,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "",
                color = Color.Red,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

//        Button(onClick = {
//            val sharedPreferences: SharedPreferences = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
//            sharedPreferences.edit().putBoolean("midnight_flag", !midnightFlag).apply()
//        }) {
//            Text("Toggle Midnight Flag")
//        }
//        Spacer(modifier = Modifier.height(130.dp))

        Spacer(modifier = Modifier.height(175.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Button(
                    modifier = Modifier
                        .width(170.dp)
                        .height(45.dp),
                    shape = RoundedCornerShape(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD1CF0B),
                        contentColor = Color.Black
                    ),
                    onClick = {
                        if(detectActivities) {
                            val durationMillis =
                                System.currentTimeMillis() - activityStartTime
                            when (currentActivity) {
                                "Walking" -> {
                                    walkingTime += durationMillis
                                }

                                "Running" -> {
                                    runningTime += durationMillis
                                }
                            }
                        }
                        navController.navigate("face_detection_screen") {
                            popUpTo("face_detection_screen") { inclusive = true }
                        }
                    }) {
                    Text(
                        text = "Previous Screen",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Button(
                    modifier = Modifier
                        .width(170.dp)
                        .height(45.dp),
                    shape = RoundedCornerShape(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD1CF0B),
                        contentColor = Color.Black
                    ),
                    onClick = {
                        if(detectActivities) {
                            val durationMillis =
                                System.currentTimeMillis() - activityStartTime
                            when (currentActivity) {
                                "Walking" -> {
                                    walkingTime += durationMillis
                                }

                                "Running" -> {
                                    runningTime += durationMillis
                                }
                            }
                        }
                        navController.navigate("activity_detection_screen") {
                            popUpTo("activity_detection_screen") { inclusive = true }
                        }
                    }) {
                    Text(text = "Next Screen", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}