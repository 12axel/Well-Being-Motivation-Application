package com.example.WellBeingMotivationApp

import android.content.ContentValues.TAG
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture.OnImageCapturedCallback
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.WellBeingMotivationApp.DbViewModel.DailyRecordViewModel
import com.example.WellBeingMotivationApp.MapViewModel.GeofenceVisitChecker
import com.example.WellBeingMotivationApp.MapViewModel.MapViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.delay

@Composable
fun FaceDetectionScreen(
    mapViewModel: MapViewModel,
    navController: NavController,
    viewModel: DailyRecordViewModel,
    context: Context
) {

    // Observe records from the database
    val records by viewModel.getTop5Records().observeAsState(listOf())
    var record by remember { mutableStateOf(records.firstOrNull()) }
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

    val faceCount = remember { mutableStateOf(0) }
    val lastSelectedOption = remember { mutableStateOf("None") }
    val isProcessing = remember { mutableStateOf(false) }

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
        }
    }

    val displayMode = remember { mutableStateOf("preview") }
    var captureImage = remember { mutableStateOf<Bitmap?>(null) }
    var rotationDegrees = remember { mutableStateOf(0) }
    var isSmiling = remember { mutableStateOf(false) }
    var picturesTaken = remember { mutableStateOf(0) }

    LaunchedEffect(userLocation) {
        userLocation?.let { location ->
            cameraPositionState.position = CameraPosition.fromLatLngZoom(location, 17f)
        }
    }

    LaunchedEffect(records) {
        record = records.firstOrNull()
        if (record != null) {
            stepCount = record?.stepsTaken ?: 0
            walkingTime = record?.timeSpentWalking ?: 0
            runningTime = record?.timeSpentRunning ?: 0
        }
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

        LaunchedEffect(picturesTaken.value) {
            record?.let {
                val updatedRecord = it.copy(
                    picturesTaken = it.picturesTaken + 1,
                    picturesTakenSmiling = if (isSmiling.value) it.picturesTakenSmiling + 1 else it.picturesTakenSmiling
                )
                viewModel.updateRecord(updatedRecord)
            }
        }

        LaunchedEffect(
            GeofenceVisitChecker.visitedRecCenter,
            GeofenceVisitChecker.visitedCampusCenter,
            GeofenceVisitChecker.visitedMorganHall
        ) {
            record?.let {
                val updatedRecord = it.copy(
                    visitedRecCenter = if (GeofenceVisitChecker.visitedRecCenter > it.visitedRecCenter) 1 else it.visitedRecCenter,
                    visitedCampusCenter = if (GeofenceVisitChecker.visitedCampusCenter > it.visitedCampusCenter) 1 else it.visitedCampusCenter,
                    visitedMorgan = if (GeofenceVisitChecker.visitedMorganHall > it.visitedMorgan) 1 else it.visitedMorgan
                )
                viewModel.updateRecord(updatedRecord)
            }
        }

        LaunchedEffect(stepCount, walkingTime, runningTime) {
            record?.let {
                val stepsToAdd = stepCount - it.stepsTaken
                val updatedRecord = it.copy(
                    stepsTaken = if (stepsToAdd > 0) it.stepsTaken + stepsToAdd else it.stepsTaken,
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
            .fillMaxHeight()
            .background(color = Color(0xFF35006C)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(505.dp),
            contentAlignment = Alignment.Center
        ) {
            if (displayMode.value == "image" && captureImage.value != null) {
                Image(
                    bitmap = captureImage.value!!.asImageBitmap(),
                    contentDescription = "Captured Image",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(0.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                CameraPreview(
                    controller = controller,
                    modifier = Modifier
                        .fillMaxSize()
                        .height(450.dp),
                    rotationDegrees = rotationDegrees.value
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally
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
                        text = "Smile Detection Screen",
                        color = Color(0xFFD7D8ED),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(100.dp))

                if (isProcessing.value) {
                    Text(
                        text = "Processing...",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Red
                    )
                } else {
                    Text(
                        text = "",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Red
                    )
                }

                Spacer(modifier = Modifier.height(240.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(color = Color(0xFFD1CF0B)),
                    contentAlignment = Alignment.Center
                )
                {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(45.dp)
                            .padding(start = 10.dp, end = 10.dp),
                        onClick = {
                            if (displayMode.value == "image") {
                                lastSelectedOption.value = "None"
                                isProcessing.value = false
                                isSmiling.value = false
                                displayMode.value = "preview"
                            } else {
                                isProcessing.value = true
                                takePhoto(
                                    controller = controller,
                                    isProcessing,
                                    onPhotoTaken = { bitmap ->
                                        captureImage.value = bitmap
                                        displayMode.value = "image"
                                        isProcessing.value = false
                                    },
                                    faceCount,
                                    picturesTaken,
                                    context,
                                    isSmiling
                                )
                            }
                        },
                        shape = RoundedCornerShape(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            contentColor = Color.White,
                            containerColor = Color(0xFF66999C)
                        )
                    ) {
                        Text(
                            text = "Take Your Next Picture",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .background(color = Color(0xFFD1CF0B)),
            contentAlignment = Alignment.Center
        ) {

            if (displayMode.value == "image" && isProcessing.value == false) {
                if (isSmiling.value) {
                    Text(
                        text = "You Are Smiling!",
                        fontSize = 35.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A661D)
                    )
                } else {
                    Text(
                        text = "You Are Not Smiling!",
                        fontSize = 35.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Red
                    )
                }
            } else {
                Text(
                    text = "Awaiting Capture...",
                    fontSize = 35.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        record?.let {
            Text(
                text = "Pictures Taken Today: ${it.picturesTaken}",
                color = Color(0xFFD7D8ED),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Pictures Taken Today Smiling: ${it.picturesTakenSmiling}",
                color = Color(0xFFD7D8ED),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

        }

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

        Spacer(modifier = Modifier.height(30.dp))

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
                        if (detectActivities) {
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
                        if (detectActivities) {
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
                    Text(text = "Next Screen", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

    }
}

@Composable
fun CameraPreview(
    controller: LifecycleCameraController,
    modifier: Modifier,
    rotationDegrees: Int
) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(500.dp)
    ) {
        AndroidView(
            factory = { context ->
                PreviewView(context).apply {
                    this.controller = controller
                    controller.cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                    controller.bindToLifecycle(lifecycleOwner)
                    this.scaleType = PreviewView.ScaleType.FILL_CENTER
                    this.rotation = rotationDegrees.toFloat()
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun takePhoto(
    controller: LifecycleCameraController,
    isProcessing: MutableState<Boolean>,
    onPhotoTaken: (Bitmap?) -> Unit,
    faceCount: MutableState<Int>,
    picturesTaken: MutableState<Int>,
    context: Context,
    isSmiling: MutableState<Boolean>
) {
    isProcessing.value = true

    controller.takePicture(
        ContextCompat.getMainExecutor(context),
        object : OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                super.onCaptureSuccess(image)
                val rotationDegrees = image.imageInfo.rotationDegrees
                val originalBitmap = image.toBitmap()
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rotatedBitmap = Bitmap.createBitmap(
                    originalBitmap,
                    0,
                    0,
                    originalBitmap.width,
                    originalBitmap.height,
                    matrix,
                    true
                )
                image.close()

                runFaceDetection(
                    rotatedBitmap,
                    isSmiling,
                    picturesTaken
                ) { faceBitmap, detectedFaceCount ->
                    faceCount.value = detectedFaceCount
                    onPhotoTaken(faceBitmap ?: rotatedBitmap)
                    isProcessing.value = false
                }

            }

            override fun onError(exception: ImageCaptureException) {
                super.onError(exception)
                Log.e("CameraXApp", "Photo capture failed: ${exception.message}", exception)
                isProcessing.value = false
                onPhotoTaken(null)
            }
        }
    )
}

private fun runFaceDetection(
    bitmap: Bitmap,
    isSmiling: MutableState<Boolean>,
    picturesTaken: MutableState<Int>,
    onFaceDetectionResult: (Bitmap?, Int) -> Unit
) {
    val highAccuracyOpts = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .build()

    val image = InputImage.fromBitmap(bitmap, 0)
    val detector = FaceDetection.getClient(highAccuracyOpts)

    val result = detector.process(image)
        .addOnSuccessListener { faces ->
            val faceCount = faces.size
            val resultBitmap =
                drawFaceRectangleAndDetectSmile(bitmap, faces, isSmiling, picturesTaken)
            onFaceDetectionResult(resultBitmap, faceCount)
        }
        .addOnFailureListener {
            it.printStackTrace()
            onFaceDetectionResult(null, 0)
        }
}

private fun drawFaceRectangleAndDetectSmile(
    bitmap: Bitmap,
    faces: List<Face>,
    isSmiling: MutableState<Boolean>,
    picturesTaken: MutableState<Int>
): Bitmap? {
    val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(mutableBitmap)
    val paint = Paint().apply {
        color = Color.Red.toArgb()
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }
    val face = faces.firstOrNull()

    if (face != null) {
        val smileProb = face.smilingProbability ?: -1.0f
        Log.d("Smile Probability", smileProb.toString())
        val bounds = face.boundingBox
        canvas.drawRect(bounds, paint)

        if (smileProb > 0.5f) {
            isSmiling.value = true
        } else {
            isSmiling.value = false
        }
    }

    picturesTaken.value++

    return mutableBitmap

}