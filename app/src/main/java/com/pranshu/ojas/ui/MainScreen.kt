package com.pranshu.ojas.ui

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pranshu.ojas.camera.CameraManager
import com.pranshu.ojas.viewmodel.HeartRateViewModel
import com.pranshu.ojas.viewmodel.MeasurementStatus

private const val TAG = "MainScreen"

@Composable
fun MainScreen() {
    Log.d(TAG, "MainScreen composing...")

    val viewModel: HeartRateViewModel = viewModel()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var cameraManager by remember { mutableStateOf<CameraManager?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var initError by remember { mutableStateOf<String?>(null) }

    val heartRate by viewModel.heartRate.collectAsState()
    val signalBuffer by viewModel.signalBuffer.collectAsState()
    val status by viewModel.status.collectAsState()
    val confidence by viewModel.confidence.collectAsState()
    val faceDetected by viewModel.faceDetected.collectAsState()
    val landmarks by viewModel.landmarks.collectAsState()
    val stressLevel by viewModel.stressLevel.collectAsState()

    // Initialize camera
    LaunchedEffect(Unit) {
        Log.d(TAG, "LaunchedEffect: Initializing camera...")
        try {
            val preview = PreviewView(context)
            previewView = preview
            Log.d(TAG, "PreviewView created")

            val manager = CameraManager(context, lifecycleOwner, viewModel.faceTracker)
            cameraManager = manager
            Log.d(TAG, "CameraManager created")

            manager.startCamera(preview)
            Log.d(TAG, "Camera started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize camera", e)
            initError = e.message
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            Log.d(TAG, "Disposing camera resources")
            cameraManager?.stopCamera()
        }
    }

    // Error or Loading state
    if (initError != null) {
        ErrorScreen(initError!!)
        return
    }

    if (previewView == null) {
        LoadingScreen()
        return
    }

    // Main Layout - Vertical Stack matching reference
    Box(modifier = Modifier.fillMaxSize()) {
        // Background: Camera preview
        previewView?.let { preview ->
            AndroidView(
                factory = { preview },
                modifier = Modifier.fillMaxSize().zIndex(0f)
            )
        }

        // Face landmarks overlay
        if (faceDetected && landmarks.isNotEmpty()) {
            FaceLandmarkOverlay(
                landmarks = landmarks,
                modifier = Modifier.fillMaxSize().zIndex(1f)
            )
        }

        // Camera controls (floating top-left)
        cameraManager?.let { manager ->
            CameraControls(
                cameraManager = manager,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .zIndex(10f)
            )
        }

        // Main Content - Vertical sections
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .zIndex(5f),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP SECTION: Camera Preview Area (with status overlay)
            CameraPreviewSection(
                status = status,
                confidence = confidence,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.5f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // MIDDLE SECTION: Stress and Other Info
            StressInfoSection(
                stressLevel = stressLevel,
                status = status,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.6f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // BOTTOM ROW: Some Useful Info + BPM Count
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.5f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left: Useful Info Card
                UsefulInfoSection(
                    heartRate = heartRate,
                    confidence = confidence,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )

                // Right: BPM Count
                BPMCountSection(
                    heartRate = heartRate,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // BOTTOM SECTION: Heart Beat Graph
            HeartBeatGraphSection(
                signalData = signalBuffer,
                confidence = confidence,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }

        // Floating reset button (bottom center)
        FloatingActionButton(
            onClick = {
                Log.d(TAG, "Reset button clicked")
                viewModel.reset()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .zIndex(10f),
            containerColor = Color(0xFF00FF88),
            contentColor = Color.Black
        ) {
            Icon(Icons.Default.Refresh, "Reset")
        }
    }
}

// ========== SECTION COMPOSABLES ==========

@Composable
fun CameraPreviewSection(
    status: MeasurementStatus,
    confidence: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1F3A).copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "camera preview",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Status indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusIcon(status)
                    Text(
                        text = getStatusText(status),
                        color = getStatusColor(status),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Confidence progress
                LinearProgressIndicator(
                    progress = { confidence },
                    modifier = Modifier
                        .width(200.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFF00FF88),
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
            }
        }
    }
}

@Composable
fun StressInfoSection(
    stressLevel: String,
    status: MeasurementStatus,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1F3A).copy(alpha = 0.95f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "stress and other info",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Show stress info if measuring
            if (status == MeasurementStatus.MEASURING) {
                Text(
                    text = stressLevel,
                    fontSize = 14.sp,
                    color = Color(0xFF00FF88),
                    lineHeight = 20.sp
                )
            } else {
                Text(
                    text = "Place your face in frame to begin measurement",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
fun UsefulInfoSection(
    heartRate: Float,
    confidence: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1F3A).copy(alpha = 0.95f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "some useful info",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.7f)
            )

            Column {
                // HR Category
                if (heartRate > 0) {
                    Text(
                        text = "Status: ${getHRCategory(heartRate)}",
                        fontSize = 12.sp,
                        color = getHRCategoryColor(heartRate)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Quality
                Text(
                    text = "Quality: ${getQualityText(confidence)}",
                    fontSize = 12.sp,
                    color = getQualityColor(confidence)
                )
            }
        }
    }
}

@Composable
fun BPMCountSection(
    heartRate: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1F3A).copy(alpha = 0.95f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "bpm count",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.7f)
            )

            // Large BPM number
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (heartRate > 0) "${heartRate.toInt()}" else "--",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00FF88)
                )
                Text(
                    text = "BPM",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }
    }
}

@Composable
fun HeartBeatGraphSection(
    signalData: List<Float>,
    confidence: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1F3A).copy(alpha = 0.95f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "heart beat graph",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Graph canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (signalData.isEmpty()) {
                    // Empty state
                    drawLine(
                        color = Color.White.copy(alpha = 0.2f),
                        start = Offset(0f, size.height / 2),
                        end = Offset(size.width, size.height / 2),
                        strokeWidth = 2f
                    )
                    return@Canvas
                }

                val width = size.width
                val height = size.height

                val minValue = signalData.minOrNull() ?: 0f
                val maxValue = signalData.maxOrNull() ?: 1f
                val range = (maxValue - minValue).coerceAtLeast(1f)

                // Grid lines
                for (i in 1..3) {
                    val y = height * i / 4
                    drawLine(
                        color = Color.White.copy(alpha = 0.1f),
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1f
                    )
                }

                // Signal path
                val path = Path()
                val stepX = width / (signalData.size - 1).coerceAtLeast(1)

                signalData.forEachIndexed { index, value ->
                    val x = index * stepX
                    val normalizedValue = (value - minValue) / range
                    val y = height - (normalizedValue * height * 0.8f) - height * 0.1f

                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }

                // Gradient stroke
                val strokeColor = getQualityColor(confidence)
                drawPath(
                    path = path,
                    brush = Brush.horizontalGradient(
                        colors = listOf(strokeColor.copy(alpha = 0.5f), strokeColor)
                    ),
                    style = Stroke(width = 3f)
                )

                // Glow effect
                drawPath(
                    path = path,
                    color = strokeColor.copy(alpha = 0.3f),
                    style = Stroke(width = 8f)
                )
            }
        }
    }
}

// ========== HELPER COMPOSABLES ==========

@Composable
fun CameraControls(
    cameraManager: CameraManager,
    modifier: Modifier = Modifier
) {
    val lensFacing by cameraManager.currentLensFacing.collectAsState()
    val isFlashOn by cameraManager.isFlashOn.collectAsState()
    val hasFlash by cameraManager.hasFlash.collectAsState()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Camera Switch Button
        FloatingActionButton(
            onClick = { cameraManager.switchCamera() },
            containerColor = Color(0xFF1A1F3A).copy(alpha = 0.95f),
            contentColor = Color.White,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = "Switch Camera",
                tint = Color(0xFF00FF88),
                modifier = Modifier.size(24.dp)
            )
        }

        // Flash Button
        if (hasFlash) {
            FloatingActionButton(
                onClick = { cameraManager.toggleFlash() },
                containerColor = if (isFlashOn) {
                    Color(0xFFFFAA00).copy(alpha = 0.95f)
                } else {
                    Color(0xFF1A1F3A).copy(alpha = 0.95f)
                },
                contentColor = Color.White,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = if (isFlashOn) "Flash On" else "Flash Off",
                    tint = if (isFlashOn) Color.White else Color(0xFF00FF88),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun StatusIcon(status: MeasurementStatus) {
    val icon = when (status) {
        MeasurementStatus.MEASURING -> Icons.Default.CheckCircle
        MeasurementStatus.NO_FACE -> Icons.Default.Warning
        MeasurementStatus.TRACKING -> Icons.Default.Search
        else -> Icons.Default.Info
    }

    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = getStatusColor(status),
        modifier = Modifier.size(20.dp)
    )
}

@Composable
fun FaceLandmarkOverlay(
    landmarks: List<Pair<Float, Float>>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        landmarks.forEach { (x, y) ->
            drawCircle(
                color = Color(0xFF00FF88).copy(alpha = 0.6f),
                radius = 2.5f,
                center = Offset(x * size.width, y * size.height)
            )
        }
    }
}

@Composable
fun ErrorScreen(error: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E27)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1F3A)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFF4444),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Error",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E27)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = Color(0xFF00FF88),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Initializing...",
                color = Color.White,
                fontSize = 16.sp
            )
        }
    }
}

// ========== HELPER FUNCTIONS ==========

private fun getStatusText(status: MeasurementStatus): String = when (status) {
    MeasurementStatus.INITIALIZING -> "Initializing..."
    MeasurementStatus.NO_FACE -> "No Face Detected"
    MeasurementStatus.ACQUIRING -> "Acquiring Signal..."
    MeasurementStatus.TRACKING -> "Tracking..."
    MeasurementStatus.MEASURING -> "Measuring"
}

private fun getStatusColor(status: MeasurementStatus): Color = when (status) {
    MeasurementStatus.INITIALIZING -> Color(0xFFFFAA00)
    MeasurementStatus.NO_FACE -> Color(0xFFFF4444)
    MeasurementStatus.ACQUIRING -> Color(0xFFFFAA00)
    MeasurementStatus.TRACKING -> Color(0xFF00AAFF)
    MeasurementStatus.MEASURING -> Color(0xFF00FF88)
}

private fun getQualityColor(confidence: Float): Color = when {
    confidence >= 0.8f -> Color(0xFF00FF88)
    confidence >= 0.6f -> Color(0xFF00DDFF)
    confidence >= 0.4f -> Color(0xFFFFAA00)
    else -> Color(0xFFFF4444)
}

private fun getQualityText(confidence: Float): String = when {
    confidence >= 0.8f -> "Excellent"
    confidence >= 0.6f -> "Good"
    confidence >= 0.4f -> "Fair"
    else -> "Poor"
}

private fun getHRCategory(hr: Float): String = when {
    hr < 60 -> "Resting"
    hr < 100 -> "Normal"
    hr < 140 -> "Elevated"
    else -> "High"
}

private fun getHRCategoryColor(hr: Float): Color = when {
    hr < 60 -> Color(0xFF00AAFF)
    hr < 100 -> Color(0xFF00FF88)
    hr < 140 -> Color(0xFFFFAA00)
    else -> Color(0xFFFF4444)
}