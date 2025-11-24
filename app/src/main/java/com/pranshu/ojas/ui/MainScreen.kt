package com.pranshu.ojas.ui


import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pranshu.ojas.camera.CameraManager
import com.pranshu.ojas.viewmodel.HeartRateViewModel
import com.pranshu.ojas.viewmodel.MeasurementStatus

@Composable
fun MainScreen() {
    val viewModel: HeartRateViewModel = viewModel()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var cameraManager by remember { mutableStateOf<CameraManager?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    val heartRate by viewModel.heartRate.collectAsState()
    val signalBuffer by viewModel.signalBuffer.collectAsState()
    val status by viewModel.status.collectAsState()
    val confidence by viewModel.confidence.collectAsState()
    val faceDetected by viewModel.faceTracker.faceDetected.collectAsState()
    val landmarks by viewModel.faceTracker.landmarks.collectAsState()

    // Initialize camera on first composition
    LaunchedEffect(Unit) {
        val preview = PreviewView(context)
        previewView = preview
        cameraManager = CameraManager(context, lifecycleOwner, viewModel.faceTracker)
        cameraManager?.startCamera(preview)
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraManager?.stopCamera()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera Preview Background
        previewView?.let { preview ->
            AndroidView(
                factory = { preview },
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f))
            )
        }

        // Face Landmark Overlay
        if (faceDetected && landmarks.isNotEmpty()) {
            FaceLandmarkOverlay(landmarks = landmarks)
        }

        // Signal Waveform Graph
        SignalGraph(
            signalData = signalBuffer,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .align(Alignment.Center)
        )

        // HUD Overlay
        HeartRateHUD(
            heartRate = heartRate,
            status = status,
            confidence = confidence,
            onReset = { viewModel.reset() },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun FaceLandmarkOverlay(landmarks: List<Pair<Float, Float>>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Draw face mesh landmarks in green
        landmarks.forEach { (x, y) ->
            val px = x * width
            val py = y * height

            drawCircle(
                color = Color.Green.copy(alpha = 0.6f),
                radius = 3f,
                center = Offset(px, py)
            )
        }
    }
}

@Composable
fun SignalGraph(signalData: List<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.padding(16.dp)) {
        if (signalData.isEmpty()) return@Canvas

        val width = size.width
        val height = size.height

        // Normalize signal to fit canvas
        val minValue = signalData.minOrNull() ?: 0f
        val maxValue = signalData.maxOrNull() ?: 1f
        val range = maxValue - minValue

        if (range <= 0) return@Canvas

        val path = Path()
        val stepX = width / (signalData.size - 1).coerceAtLeast(1)

        signalData.forEachIndexed { index, value ->
            val x = index * stepX
            val normalizedValue = (value - minValue) / range
            val y = height - (normalizedValue * height * 0.8f) - height * 0.1f

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        // Draw gradient background
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0A0E27).copy(alpha = 0.7f),
                    Color(0xFF1A1F3A).copy(alpha = 0.7f)
                )
            )
        )

        // Draw signal line with glow effect
        drawPath(
            path = path,
            color = Color(0xFF00FF88),
            style = Stroke(width = 3f)
        )

        // Draw glow
        drawPath(
            path = path,
            color = Color(0xFF00FF88).copy(alpha = 0.3f),
            style = Stroke(width = 8f)
        )
    }
}

@Composable
fun HeartRateHUD(
    heartRate: Float,
    status: MeasurementStatus,
    confidence: Float,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // Top Status Bar
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1F3A).copy(alpha = 0.9f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = getStatusText(status),
                    color = getStatusColor(status),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                // Confidence indicator
                Text(
                    text = "${(confidence * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }

        // Center Heart Rate Display
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (heartRate > 0) "${heartRate.toInt()}" else "--",
                fontSize = 96.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                style = MaterialTheme.typography.displayLarge
            )

            Text(
                text = "BPM",
                fontSize = 24.sp,
                color = Color(0xFF00FF88),
                fontWeight = FontWeight.Medium
            )
        }

        // Bottom Controls
        Button(
            onClick = onReset,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00FF88)
            )
        ) {
            Text(
                text = "RESET",
                color = Color.Black,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun getStatusText(status: MeasurementStatus): String {
    return when (status) {
        MeasurementStatus.INITIALIZING -> "Initializing..."
        MeasurementStatus.NO_FACE -> "No Face Detected"
        MeasurementStatus.ACQUIRING -> "Acquiring Signal..."
        MeasurementStatus.TRACKING -> "Tracking..."
        MeasurementStatus.MEASURING -> "Measuring"
    }
}

private fun getStatusColor(status: MeasurementStatus): Color {
    return when (status) {
        MeasurementStatus.INITIALIZING -> Color(0xFFFFAA00)
        MeasurementStatus.NO_FACE -> Color(0xFFFF4444)
        MeasurementStatus.ACQUIRING -> Color(0xFFFFAA00)
        MeasurementStatus.TRACKING -> Color(0xFF00AAFF)
        MeasurementStatus.MEASURING -> Color(0xFF00FF88)
    }
}