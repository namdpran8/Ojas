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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
    val faceDetected by viewModel.faceTracker?.faceDetected?.collectAsState()
    val landmarks by viewModel.faceTracker?.landmarks?.collectAsState()

    Log.d(TAG, "Current state - HR: $heartRate, Status: $status, Face: $faceDetected")

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

    Box(modifier = Modifier.fillMaxSize()) {
        // Error screen
        initError?.let { error ->
            ErrorScreen(error)
            return@Box
        }

        // Camera preview or loading
        previewView?.let { preview ->
            Log.d(TAG, "Rendering camera preview")
            AndroidView(
                factory = { preview },
                modifier = Modifier.fillMaxSize()
            )
        } ?: LoadingScreen()

        // Face landmarks
        if (faceDetected && landmarks.isNotEmpty()) {
            Log.d(TAG, "Drawing ${landmarks.size} landmarks")
            FaceLandmarkOverlay(landmarks = landmarks)
        }

        // Camera Controls (Top Left)
        cameraManager?.let { manager ->
            CameraControls(
                cameraManager = manager,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            )
        }

        // Enhanced signal graph
        EnhancedSignalGraph(
            signalData = signalBuffer,
            confidence = confidence,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .align(Alignment.Center)
        )

        // Signal quality badge
        if (confidence > 0.3f) {
            SignalQualityBadge(
                confidence = confidence,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .offset(y = 80.dp)
            )
        }

        // Main HUD
        EnhancedHeartRateHUD(
            heartRate = heartRate,
            status = status,
            confidence = confidence,
            onReset = {
                Log.d(TAG, "Reset button clicked")
                viewModel.reset()
            },
            modifier = Modifier.fillMaxSize()
        )

        // Animated heartbeat icon
        if (heartRate > 0 && status == MeasurementStatus.MEASURING) {
            AnimatedHeartbeat(
                bpm = heartRate,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .size(56.dp)
            )
        }
    }
}

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
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = "Switch Camera",
                tint = Color(0xFF00FF88),
                modifier = Modifier.size(28.dp)
            )
        }

        // Flash Button (only show if available)
        if (hasFlash) {
            FloatingActionButton(
                onClick = { cameraManager.toggleFlash() },
                containerColor = if (isFlashOn) {
                    Color(0xFFFFAA00).copy(alpha = 0.95f)
                } else {
                    Color(0xFF1A1F3A).copy(alpha = 0.95f)
                },
                contentColor = Color.White,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = if (isFlashOn) "Flash On" else "Flash Off",
                    tint = if (isFlashOn) Color.White else Color(0xFF00FF88),
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Camera indicator badge
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1F3A).copy(alpha = 0.95f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(8.dp, 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                        Icons.Default.CameraFront
                    } else {
                        Icons.Default.CameraRear
                    },
                    contentDescription = null,
                    tint = Color(0xFF00FF88),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (lensFacing == CameraSelector.LENS_FACING_FRONT) "Front" else "Back",
                    fontSize = 11.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun EnhancedSignalGraph(
    signalData: List<Float>,
    confidence: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1F3A).copy(alpha = 0.9f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header with quality
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Signal",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )

                // Quality indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(getQualityColor(confidence))
                    )
                    Text(
                        text = getQualityText(confidence),
                        fontSize = 11.sp,
                        color = getQualityColor(confidence)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Graph canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (signalData.isEmpty()) return@Canvas

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

@Composable
fun SignalQualityBadge(
    confidence: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1F3A).copy(alpha = 0.95f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PulsingDot(color = getQualityColor(confidence))

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = getQualityText(confidence),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = getQualityColor(confidence)
            )

            Text(
                text = "${(confidence * 100).toInt()}%",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun PulsingDot(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier.size(40.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(24.dp * scale)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.3f))
        )
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
fun AnimatedHeartbeat(bpm: Float, modifier: Modifier = Modifier) {
    val beatInterval = (60000f / bpm).toLong()
    var beat by remember { mutableStateOf(false) }

    LaunchedEffect(bpm) {
        while (true) {
            beat = true
            kotlinx.coroutines.delay(100)
            beat = false
            kotlinx.coroutines.delay(beatInterval - 100)
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (beat) 1.4f else 1.0f,
        animationSpec = tween(100),
        label = "heartbeat"
    )

    Icon(
        imageVector = Icons.Default.Favorite,
        contentDescription = "Heart",
        tint = Color(0xFFFF4444),
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    )
}

@Composable
fun EnhancedHeartRateHUD(
    heartRate: Float,
    status: MeasurementStatus,
    confidence: Float,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // Top status bar
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1F3A).copy(alpha = 0.95f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
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

                CircularProgressIndicator(
                    progress = confidence,
                    modifier = Modifier.size(36.dp),
                    color = Color(0xFF00FF88),
                    strokeWidth = 3.dp,
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
            }
        }

        // Center HR display
        Card(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 120.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1F3A).copy(alpha = 0.95f)
            ),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(36.dp, 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (heartRate > 0) "${heartRate.toInt()}" else "--",
                    fontSize = 80.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = "BPM",
                    fontSize = 18.sp,
                    color = Color(0xFF00FF88),
                    fontWeight = FontWeight.Medium
                )

                if (heartRate > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = getHRCategory(heartRate),
                        fontSize = 11.sp,
                        color = getHRCategoryColor(heartRate)
                    )
                }
            }
        }

        // Bottom controls
        FloatingActionButton(
            onClick = onReset,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            containerColor = Color(0xFF00FF88)
        ) {
            Icon(Icons.Default.Refresh, "Reset", tint = Color.Black)
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
                    color = Color.White.copy(alpha = 0.7f)
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

@Composable
fun FaceLandmarkOverlay(landmarks: List<Pair<Float, Float>>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        landmarks.forEach { (x, y) ->
            drawCircle(
                color = Color(0xFF00FF88).copy(alpha = 0.6f),
                radius = 2.5f,
                center = Offset(x * size.width, y * size.height)
            )
        }
    }
}

// Helper functions
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