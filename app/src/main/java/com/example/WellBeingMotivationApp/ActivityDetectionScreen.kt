package com.example.WellBeingMotivationApp

import android.content.ContentValues.TAG
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.WellBeingMotivationApp.DbViewModel.DailyRecordViewModel
import com.example.WellBeingMotivationApp.MapViewModel.GeofenceVisitChecker
import com.example.WellBeingMotivationApp.MapViewModel.MapViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay

@Composable
fun ActivityDetectionScreen(
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

    var walkingTime by rememberSaveable { mutableStateOf(record?.timeSpentWalking ?: 0L) }

    var runningTime by rememberSaveable { mutableStateOf(record?.timeSpentRunning ?: 0L) }

    var activityStartTime by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }

    var lastTimestamp by remember { mutableStateOf(0L) }

    val stepIntervals = remember { mutableStateListOf<Double>() }

    var durationString by remember { mutableStateOf("") }
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

    val midnightFlag by viewModel.midnightFlag.observeAsState(false)

    var detectActivities = !midnightFlag

    val cameraPositionState = rememberCameraPositionState()
    val userLocation by mapViewModel.userLocation

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
                //previousActivity = currentActivity
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
            .background(color = Color(0xFFD1CF0B)),
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
                text = "Activity Detection Screen",
                color = Color(0xFFD7D8ED),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(325.dp)
                .background(color = Color.White),
            contentAlignment = Alignment.Center
        )
        {
            when (currentActivity) {
                "Still" -> {
                    Image(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(325.dp),
                        painter = painterResource(id = R.drawable.man_still),
                        contentDescription = "Man Still"
                    )
                }

                "Walking" -> {
                    Image(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(325.dp),
                        painter = painterResource(id = R.drawable.man_walking),
                        contentDescription = "Man Walking"
                    )
                }

                "Running" -> {
                    Image(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(325.dp),
                        painter = painterResource(id = R.drawable.man_running),
                        contentDescription = "Man Running"
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(color = Color(0xFFD1CF0B)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .background(color = Color(0xFFD1CF0B)),
                contentAlignment = Alignment.Center
            )
            {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(color = Color(0xFFD1CF0B)),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "You are currently",
                        fontSize = 35.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        text = "$currentActivity!",
                        fontSize = 35.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(color = Color(0xFF35006C))
            )
            {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(20.dp))
//                    Text(text = "Number Of Steps Taken Today: $stepCount")
                    record?.let {
                        Text(
                            text = "Steps Taken Today: ${it.stepsTaken}",
                            color = Color(0xFFD7D8ED),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                    }

                    record?.let {
                        val walkingMilliseconds = (it.timeSpentWalking % 1000).toInt()
                        val walkingSecondsRealTime = (it.timeSpentWalking / 1000).toInt()
                        val walkingMinutes = walkingSecondsRealTime / 60
                        val walkingSeconds = walkingSecondsRealTime % 60
                        var walkingTimeString = "${walkingMilliseconds}ms"
                        if (walkingSeconds > 0) {
                            walkingTimeString = "${walkingSeconds}s ${walkingMilliseconds}ms"
                        }
                        if (walkingMinutes > 0) {
                            walkingTimeString =
                                "${walkingMinutes}min ${walkingSeconds}s ${walkingMilliseconds}ms"
                        }

//                        Text(text = "Time Spent Walking Today: ${it.timeSpentWalking}")
                        Text(
                            text = "Time Spent Walking Today: $walkingTimeString",
                            color = Color(0xFFD7D8ED),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        val runningMilliseconds = (it.timeSpentRunning % 1000).toInt()
                        val runningSecondsRealTime = (it.timeSpentRunning / 1000).toInt()
                        val runningMinutes = runningSecondsRealTime / 60
                        val runningSeconds = runningSecondsRealTime % 60
                        var runningTimeString = "${runningMilliseconds}ms"
                        if (runningSeconds > 0) {
                            runningTimeString = "${runningSeconds}s ${runningMilliseconds}ms"
                        }
                        if (runningMinutes > 0) {
                            runningTimeString =
                                "${runningMinutes}min ${runningSeconds}s ${runningMilliseconds}ms"
                        }
//                        Text(text = "Time Spent Running Today: ${it.timeSpentRunning}")
                        Text(
                            text = "Time Spent Running Today: $runningTimeString",
                            color = Color(0xFFD7D8ED),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )

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


                    Spacer(modifier = Modifier.height(15.dp))


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
                                    navController.navigate("location_tracking_screen") {
                                        popUpTo("location_tracking_screen") { inclusive = true }
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
                                    navController.navigate("data_display_screen") {
                                        popUpTo("data_display_screen") { inclusive = true }
                                    }
                                }) {
                                Text(
                                    text = "Next Screen",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}