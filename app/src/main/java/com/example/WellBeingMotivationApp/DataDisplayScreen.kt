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
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun DataDisplayScreen(
    mapViewModel: MapViewModel,
    navController: NavController,
    viewModel: DailyRecordViewModel,
    context: Context
) {
    val records by viewModel.getTop5Records().observeAsState(listOf())
    var record = records.firstOrNull()

    var record2 = if (records.size >= 2) records[1] else null
    var record3 = if (records.size >= 3) records[2] else null
    var record4 = if (records.size >= 4) records[3] else null
    var record5 = if (records.size >= 5) records[4] else null

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
                text = "Data Display Screen",
                color = Color(0xFFD7D8ED),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(color = Color(0xFFD1CF0B)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Your Data From The Last 4",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Text(
                text = "Recorded Days And Today",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 25.dp, end = 25.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .width(150.dp)
                        .height(140.dp)
                        .background(
                            color = if (record != null) Color(0xFF66999C) else Color(
                                0xFFD1CF0B
                            )
                        ),
                    contentAlignment = Alignment.CenterStart
                ) {
                    record?.let {
                        Column(
                            modifier = Modifier
                                .fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            record?.date?.let { it1 ->
                                val inputFormat =
                                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                val outputFormat =
                                    SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
                                val date = inputFormat.parse(it1)
                                val formattedDate = date?.let { it2 -> outputFormat.format(it2) }

                                if (formattedDate != null) {
                                    Text(
                                        text = formattedDate,
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            record?.picturesTaken?.let {
                                Text(
                                    text = "Pictures Taken: $it",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                            }
                            record?.picturesTakenSmiling?.let {
                                Text(
                                    text = "Pictures Taken Smiling: $it",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                            }
                            record?.stepsTaken?.let {
                                Text(
                                    text = "Steps Taken: $it",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                            }
                            record?.timeSpentWalking?.let {
                                val walkingMilliseconds = (it % 1000).toInt()
                                val walkingSecondsRealTime = (it / 1000).toInt()
                                val walkingMinutes = walkingSecondsRealTime / 60
                                val walkingSeconds = walkingSecondsRealTime % 60
                                var walkingTimeString = "${walkingMilliseconds}ms"
                                if (walkingSeconds > 0) {
                                    walkingTimeString =
                                        "${walkingSeconds}s ${walkingMilliseconds}ms"
                                }
                                if (walkingMinutes > 0) {
                                    walkingTimeString =
                                        "${walkingMinutes}min ${walkingSeconds}s ${walkingMilliseconds}ms"
                                }
                                Text(
                                    text = "Time Spent Walking:",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = walkingTimeString,
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                            }
                            record?.timeSpentRunning?.let {
                                val runningMilliseconds = (it % 1000).toInt()
                                val runningSecondsRealTime = (it / 1000).toInt()
                                val runningMinutes = runningSecondsRealTime / 60
                                val runningSeconds = runningSecondsRealTime % 60
                                var runningTimeString = "${runningMilliseconds}ms"
                                if (runningSeconds > 0) {
                                    runningTimeString =
                                        "${runningSeconds}s ${runningMilliseconds}ms"
                                }
                                if (runningMinutes > 0) {
                                    runningTimeString =
                                        "${runningMinutes}min ${runningSeconds}s ${runningMilliseconds}ms"
                                }
                                Text(
                                    text = "Time Spent Running:",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = runningTimeString,
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            record?.visitedRecCenter?.let {
                                if (it == 0) {
                                    Text(
                                        text = "Visited The Recreation Center: No",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    Text(
                                        text = "Visited The Recreation Center: Yes",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }


                            }
                            record?.visitedCampusCenter?.let {
                                if (it == 0) {
                                    Text(
                                        text = "Visited The Campus Center: No",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    Text(
                                        text = "Visited The Campus Center: Yes",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            record?.visitedMorgan?.let {
                                if (it == 0) {
                                    Text(
                                        text = "Visited Morgan Hall: No",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    Text(
                                        text = "Visited Morgan Hall: Yes",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            record?.moodPoints?.let {
                                Text(
                                    text = "Mood Points: In Progress",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .width(150.dp)
                        .height(140.dp)
                        .background(
                            color = if (record2 != null) Color(0xFF66999C) else Color(
                                0xFFD1CF0B
                            )
                        ),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    record2?.let {
                        Column(
                            modifier = Modifier
                                .fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            record2?.date?.let { it1 ->
                                val inputFormat =
                                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                val outputFormat =
                                    SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
                                val date = inputFormat.parse(it1)
                                val formattedDate =
                                    date?.let { it2 -> outputFormat.format(it2) }

                                if (formattedDate != null) {
                                    Text(
                                        text = formattedDate,
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            record2?.picturesTaken?.let {
                                Text(
                                    text = "Pictures Taken: $it",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                            }
                            record2?.picturesTakenSmiling?.let {
                                Text(
                                    text = "Pictures Taken Smiling: $it",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                            }
                            record2?.stepsTaken?.let {
                                Text(
                                    text = "Steps Taken: $it",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                            }
                            record2?.timeSpentWalking?.let {
                                val walkingMilliseconds = (it % 1000).toInt()
                                val walkingSecondsRealTime = (it / 1000).toInt()
                                val walkingMinutes = walkingSecondsRealTime / 60
                                val walkingSeconds = walkingSecondsRealTime % 60
                                var walkingTimeString = "${walkingMilliseconds}ms"
                                if (walkingSeconds > 0) {
                                    walkingTimeString =
                                        "${walkingSeconds}s ${walkingMilliseconds}ms"
                                }
                                if (walkingMinutes > 0) {
                                    walkingTimeString =
                                        "${walkingMinutes}min ${walkingSeconds}s ${walkingMilliseconds}ms"
                                }
                                Text(
                                    text = "Time Spent Walking:",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = walkingTimeString,
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                            }
                            record2?.timeSpentRunning?.let {
                                val runningMilliseconds = (it % 1000).toInt()
                                val runningSecondsRealTime = (it / 1000).toInt()
                                val runningMinutes = runningSecondsRealTime / 60
                                val runningSeconds = runningSecondsRealTime % 60
                                var runningTimeString = "${runningMilliseconds}ms"
                                if (runningSeconds > 0) {
                                    runningTimeString =
                                        "${runningSeconds}s ${runningMilliseconds}ms"
                                }
                                if (runningMinutes > 0) {
                                    runningTimeString =
                                        "${runningMinutes}min ${runningSeconds}s ${runningMilliseconds}ms"
                                }
                                Text(
                                    text = "Time Spent Running:",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = runningTimeString,
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            record2?.visitedRecCenter?.let {
                                if (it == 0) {
                                    Text(
                                        text = "Visited The Recreation Center: No",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    Text(
                                        text = "Visited The Recreation Center: Yes",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }


                            }
                            record2?.visitedCampusCenter?.let {
                                if (it == 0) {
                                    Text(
                                        text = "Visited The Campus Center: No",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    Text(
                                        text = "Visited The Campus Center: Yes",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            record2?.visitedMorgan?.let {
                                if (it == 0) {
                                    Text(
                                        text = "Visited Morgan Hall: No",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    Text(
                                        text = "Visited Morgan Hall: Yes",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            record2?.moodPoints?.let {
                                Text(
                                    text = "Mood Points: $it",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 25.dp, end = 25.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .width(150.dp)
                        .height(140.dp)
                        .background(
                            color = if (record3 != null) Color(0xFF66999C) else Color(
                                0xFFD1CF0B
                            )
                        ),
                    contentAlignment = Alignment.CenterStart
                ) {
                    record3?.let {
                        Column(
                            modifier = Modifier
                                .fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            record3?.date?.let { it1 ->
                                val inputFormat =
                                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                val outputFormat =
                                    SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
                                val date = inputFormat.parse(it1)
                                val formattedDate = date?.let { it2 -> outputFormat.format(it2) }

                                if (formattedDate != null) {
                                    Text(
                                        text = formattedDate,
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            record3?.picturesTaken?.let {
                                Text(
                                    text = "Pictures Taken: $it",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                            }
                            record3?.picturesTakenSmiling?.let {
                                Text(
                                    text = "Pictures Taken Smiling: $it",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                            }
                            record3?.stepsTaken?.let {
                                Text(
                                    text = "Steps Taken: $it",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                            }
                            record3?.timeSpentWalking?.let {
                                val walkingMilliseconds = (it % 1000).toInt()
                                val walkingSecondsRealTime = (it / 1000).toInt()
                                val walkingMinutes = walkingSecondsRealTime / 60
                                val walkingSeconds = walkingSecondsRealTime % 60
                                var walkingTimeString = "${walkingMilliseconds}ms"
                                if (walkingSeconds > 0) {
                                    walkingTimeString =
                                        "${walkingSeconds}s ${walkingMilliseconds}ms"
                                }
                                if (walkingMinutes > 0) {
                                    walkingTimeString =
                                        "${walkingMinutes}min ${walkingSeconds}s ${walkingMilliseconds}ms"
                                }
                                Text(
                                    text = "Time Spent Walking:",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = walkingTimeString,
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                            }
                            record3?.timeSpentRunning?.let {
                                val runningMilliseconds = (it % 1000).toInt()
                                val runningSecondsRealTime = (it / 1000).toInt()
                                val runningMinutes = runningSecondsRealTime / 60
                                val runningSeconds = runningSecondsRealTime % 60
                                var runningTimeString = "${runningMilliseconds}ms"
                                if (runningSeconds > 0) {
                                    runningTimeString =
                                        "${runningSeconds}s ${runningMilliseconds}ms"
                                }
                                if (runningMinutes > 0) {
                                    runningTimeString =
                                        "${runningMinutes}min ${runningSeconds}s ${runningMilliseconds}ms"
                                }
                                Text(
                                    text = "Time Spent Running:",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = runningTimeString,
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            record3?.visitedRecCenter?.let {
                                if (it == 0) {
                                    Text(
                                        text = "Visited The Recreation Center: No",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    Text(
                                        text = "Visited The Recreation Center: Yes",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }


                            }
                            record3?.visitedCampusCenter?.let {
                                if (it == 0) {
                                    Text(
                                        text = "Visited The Campus Center: No",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    Text(
                                        text = "Visited The Campus Center: Yes",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            record3?.visitedMorgan?.let {
                                if (it == 0) {
                                    Text(
                                        text = "Visited Morgan Hall: No",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    Text(
                                        text = "Visited Morgan Hall: Yes",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            record3?.moodPoints?.let {
                                Text(
                                    text = "Mood Points: $it",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .width(150.dp)
                        .height(140.dp)
                        .background(
                            color = if (record4 != null) Color(0xFF66999C) else Color(
                                0xFFD1CF0B
                            )
                        ),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    record4?.let {
                        Column(
                            modifier = Modifier
                                .fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            record4?.date?.let { it1 ->
                                val inputFormat =
                                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                val outputFormat =
                                    SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
                                val date = inputFormat.parse(it1)
                                val formattedDate =
                                    date?.let { it2 -> outputFormat.format(it2) }

                                if (formattedDate != null) {
                                    Text(
                                        text = formattedDate,
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            record4?.picturesTaken?.let {
                                Text(
                                    text = "Pictures Taken: $it",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                            }
                            record4?.picturesTakenSmiling?.let {
                                Text(
                                    text = "Pictures Taken Smiling: $it",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                            }
                            record4?.stepsTaken?.let {
                                Text(
                                    text = "Steps Taken: $it",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                            }
                            record4?.timeSpentWalking?.let {
                                val walkingMilliseconds = (it % 1000).toInt()
                                val walkingSecondsRealTime = (it / 1000).toInt()
                                val walkingMinutes = walkingSecondsRealTime / 60
                                val walkingSeconds = walkingSecondsRealTime % 60
                                var walkingTimeString = "${walkingMilliseconds}ms"
                                if (walkingSeconds > 0) {
                                    walkingTimeString =
                                        "${walkingSeconds}s ${walkingMilliseconds}ms"
                                }
                                if (walkingMinutes > 0) {
                                    walkingTimeString =
                                        "${walkingMinutes}min ${walkingSeconds}s ${walkingMilliseconds}ms"
                                }
                                Text(
                                    text = "Time Spent Walking:",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = walkingTimeString,
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                            }
                            record4?.timeSpentRunning?.let {
                                val runningMilliseconds = (it % 1000).toInt()
                                val runningSecondsRealTime = (it / 1000).toInt()
                                val runningMinutes = runningSecondsRealTime / 60
                                val runningSeconds = runningSecondsRealTime % 60
                                var runningTimeString = "${runningMilliseconds}ms"
                                if (runningSeconds > 0) {
                                    runningTimeString =
                                        "${runningSeconds}s ${runningMilliseconds}ms"
                                }
                                if (runningMinutes > 0) {
                                    runningTimeString =
                                        "${runningMinutes}min ${runningSeconds}s ${runningMilliseconds}ms"
                                }
                                Text(
                                    text = "Time Spent Running:",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = runningTimeString,
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            record4?.visitedRecCenter?.let {
                                if (it == 0) {
                                    Text(
                                        text = "Visited The Recreation Center: No",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    Text(
                                        text = "Visited The Recreation Center: Yes",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }


                            }
                            record4?.visitedCampusCenter?.let {
                                if (it == 0) {
                                    Text(
                                        text = "Visited The Campus Center: No",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    Text(
                                        text = "Visited The Campus Center: Yes",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            record4?.visitedMorgan?.let {
                                if (it == 0) {
                                    Text(
                                        text = "Visited Morgan Hall: No",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    Text(
                                        text = "Visited Morgan Hall: Yes",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            record4?.moodPoints?.let {
                                Text(
                                    text = "Mood Points: $it",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .width(150.dp)
                    .height(140.dp)
                    .background(color = if (record5 != null) Color(0xFF66999C) else Color(0xFFD1CF0B)),
                contentAlignment = Alignment.Center
            ) {
                record5?.let {
                    Column(
                        modifier = Modifier
                            .fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        record5?.date?.let { it1 ->
                            val inputFormat =
                                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            val outputFormat =
                                SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
                            val date = inputFormat.parse(it1)
                            val formattedDate =
                                date?.let { it2 -> outputFormat.format(it2) }

                            if (formattedDate != null) {
                                Text(
                                    text = formattedDate,
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        record5?.picturesTaken?.let {
                            Text(
                                text = "Pictures Taken: $it",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                        }
                        record5?.picturesTakenSmiling?.let {
                            Text(
                                text = "Pictures Taken Smiling: $it",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                        }

                        record5?.stepsTaken?.let {
                            Text(
                                text = "Steps Taken: $it",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                        }

                        record5?.timeSpentWalking?.let {
                            val walkingMilliseconds = (it % 1000).toInt()
                            val walkingSecondsRealTime = (it / 1000).toInt()
                            val walkingMinutes = walkingSecondsRealTime / 60
                            val walkingSeconds = walkingSecondsRealTime % 60
                            var walkingTimeString = "${walkingMilliseconds}ms"
                            if (walkingSeconds > 0) {
                                walkingTimeString =
                                    "${walkingSeconds}s ${walkingMilliseconds}ms"
                            }
                            if (walkingMinutes > 0) {
                                walkingTimeString =
                                    "${walkingMinutes}min ${walkingSeconds}s ${walkingMilliseconds}ms"
                            }
                            Text(
                                text = "Time Spent Walking:",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = walkingTimeString,
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                        }

                        record5?.timeSpentRunning?.let {
                            val runningMilliseconds = (it % 1000).toInt()
                            val runningSecondsRealTime = (it / 1000).toInt()
                            val runningMinutes = runningSecondsRealTime / 60
                            val runningSeconds = runningSecondsRealTime % 60
                            var runningTimeString = "${runningMilliseconds}ms"
                            if (runningSeconds > 0) {
                                runningTimeString =
                                    "${runningSeconds}s ${runningMilliseconds}ms"
                            }
                            if (runningMinutes > 0) {
                                runningTimeString =
                                    "${runningMinutes}min ${runningSeconds}s ${runningMilliseconds}ms"
                            }
                            Text(
                                text = "Time Spent Running:",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = runningTimeString,
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        record5?.visitedRecCenter?.let {
                            if (it == 0) {
                                Text(
                                    text = "Visited The Recreation Center: No",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Text(
                                    text = "Visited The Recreation Center: Yes",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }


                        }
                        record5?.visitedCampusCenter?.let {
                            if (it == 0) {
                                Text(
                                    text = "Visited The Campus Center: No",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Text(
                                    text = "Visited The Campus Center: Yes",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        record5?.visitedMorgan?.let {
                            if (it == 0) {
                                Text(
                                    text = "Visited Morgan Hall: No",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Text(
                                    text = "Visited Morgan Hall: Yes",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        record5?.moodPoints?.let {
                            Text(
                                text = "Mood Points: $it",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(45.dp))

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

            Spacer(modifier = Modifier.height(45.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(color = Color(0xFF35006C)),
            ) {

                Spacer(modifier = Modifier.height(10.dp))
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
                                navController.navigate("activity_detection_screen") {
                                    popUpTo("activity_detection_screen") { inclusive = true }
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
                                navController.navigate("face_detection_screen") {
                                    popUpTo("face_detection_screen") { inclusive = true }
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
