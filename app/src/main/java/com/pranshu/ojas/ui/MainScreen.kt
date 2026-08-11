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
import com.pranshu.ojas.vision.FaceTracker

private const val TAG = "MainScreen"

@Composable
fun MainScreen() {
    val viewModel: HeartRateViewModel = viewModel()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var cameraManager by remember { mutableStateOf<CameraManager?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var initError by remember { mutableStateOf<String?>(null) }

    // Helper state to know when FaceTracker is ready
    var safeFaceTracker by remember { mutableStateOf<FaceTracker?>(null) }

    // --- State Collection ---
    val heartRate by viewModel.heartRate.collectAsState()
    val signalBuffer by viewModel.signalBuffer.collectAsState()
    val status by viewModel.status.collectAsState()
    val confidence by viewModel.confidence.collectAsState()
    val stressLevel by viewModel.stressLevel.collectAsState()
    val qualityMsg by viewModel.signalQualityMsg.collectAsState()

    // --- Camera Lens State (FIX for Back Camera Grid) ---
    // Default to Front if manager isn't ready
    val currentLensFacing by cameraManager?.currentLensFacing?.collectAsState() ?: remember { mutableStateOf(CameraSelector.LENS_FACING_FRONT) }
    val isFrontCamera = currentLensFacing == CameraSelector.LENS_FACING_FRONT

    // --- Safe Face Data Collection ---
    val faceDetected = safeFaceTracker?.faceDetected?.collectAsState()?.value ?: false
    val landmarks = safeFaceTracker?.landmarks?.collectAsState()?.value ?: emptyList()

    // --- Camera Initialization ---
    LaunchedEffect(Unit) {
        try {
            val preview = PreviewView(context)
            preview.scaleType = PreviewView.ScaleType.FILL_CENTER
            previewView = preview

            // Wait for ViewModel to initialize FaceTracker
            while (viewModel.faceTracker == null) {
                kotlinx.coroutines.delay(100)
            }

            safeFaceTracker = viewModel.faceTracker

            val manager = CameraManager(context, lifecycleOwner, viewModel.faceTracker)
            cameraManager = manager
            manager.startCamera(preview)
        } catch (e: Exception) {
            initError = e.message
        }
    }

    DisposableEffect(Unit) {
        onDispose { cameraManager?.stopCamera() }
    }

    // --- Main Split Layout ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ---------------------------------------------------------
        // TOP HALF: Camera Preview (45%)
        // ---------------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.45f)
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(Color.Black)
        ) {
            if (initError != null) {
                ErrorScreen(initError!!)
            } else if (previewView != null) {
                // Camera View
                AndroidView(factory = { previewView!! }, modifier = Modifier.fillMaxSize())

                // Face Grid Overlay (Pass isFrontCamera to fix rotation)
                if (faceDetected && landmarks.isNotEmpty()) {
                    FaceLandmarkOverlay(landmarks, isFrontCamera)
                }

                // Camera Controls (Switch/Flash)
                if (cameraManager != null) {
                    CameraControls(
                        cameraManager = cameraManager!!,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                    )
                }

                // Lighting Warning Banner
                if (qualityMsg.isNotEmpty()) {
                    LightingWarningBanner(qualityMsg, Modifier.align(Alignment.TopCenter))
                }
            } else {
                LoadingScreen()
            }
        }

        // ---------------------------------------------------------
        // BOTTOM HALF: Dashboard (55%)
        // ---------------------------------------------------------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.55f)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // 1. Stress & Analysis Card
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(85.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Analysis", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if(stressLevel.contains("Analyzing")) "Gathering data..." else stressLevel,
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (status == MeasurementStatus.MEASURING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
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

            // 2. Info Row (Status + BPM)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left: Status & Quality
                ElevatedCard(
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.Start
                    ) {
                        Column {
                            Text("Status", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                            Text(
                                text = getStatusText(status),
                                color = getStatusColor(status),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Text("Signal Quality", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(getQualityColor(confidence))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = getQualityText(confidence),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        // Reset Button
                        FilledTonalIconButton(
                            onClick = { viewModel.reset() },
                            modifier = Modifier.size(32.dp).align(Alignment.End),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(Icons.Default.Refresh, "Reset", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Right: BPM Count
                ElevatedCard(
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(0.8f).fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("HEART RATE", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)

                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = if (heartRate > 0) "${heartRate.toInt()}" else "--",
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = " bpm",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        if (heartRate > 0 && status == MeasurementStatus.MEASURING) {
                            Spacer(modifier = Modifier.height(8.dp))
                            AnimatedHeartbeat(bpm = heartRate, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }

            // 3. Heart Beat Graph
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Live Pulse Signal", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    GraphContent(signalBuffer, confidence)
                }
            }
        }
    }
}

// ========== SUB-COMPOSABLES ==========

@Composable
fun FaceLandmarkOverlay(
    landmarks: List<Pair<Float, Float>>,
    isFrontCamera: Boolean // New parameter
) {
    val landmarkColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    Canvas(modifier = Modifier.fillMaxSize()) {
        landmarks.forEach { (oldX, oldY) ->
            // Fix rotation based on Camera Lens
            val rotatedX = 1f - oldY
            val rotatedY = if (isFrontCamera) {
                1f - oldX // Front: 270 deg (Mirror)
            } else {
                oldX      // Back: 90 deg (No Mirror)
            }

            drawCircle(
                color = landmarkColor,
                radius = 3f,
                center = Offset(
                    x = rotatedX * size.width,
                    y = rotatedY * size.height
                )
            )
        }
    }
}

// ========== HELPER COMPOSABLES ==========

@Composable
fun CameraControls(
    cameraManager: CameraManager,
    modifier: Modifier = Modifier
) {
    val isFlashOn by cameraManager.isFlashOn.collectAsState()
    val hasFlash by cameraManager.hasFlash.collectAsState()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Switch Camera Button
        FilledTonalIconButton(
            onClick = { cameraManager.switchCamera() },
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = "Switch Camera",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        // Flash Button
        if (hasFlash) {
            FilledTonalIconButton(
                onClick = { cameraManager.toggleFlash() },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (isFlashOn) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                    contentColor = if (isFlashOn) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Toggle Flash",
                    tint = if (isFlashOn) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun LightingWarningBanner(msg: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.9f)),
        modifier = modifier.padding(top = 48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(msg, color = MaterialTheme.colorScheme.onError, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun GraphContent(signalData: List<Float>, confidence: Float) {
    val strokeColor = getQualityColor(confidence)
    Canvas(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))) {
        if (signalData.isEmpty()) return@Canvas

        val width = size.width
        val height = size.height
        val minValue = signalData.minOrNull() ?: 0f
        val maxValue = signalData.maxOrNull() ?: 1f
        val range = (maxValue - minValue).coerceAtLeast(1f)

        val path = Path()
        val stepX = width / (signalData.size - 1).coerceAtLeast(1)

        signalData.forEachIndexed { index, value ->
            val x = index * stepX
            val normalizedValue = (value - minValue) / range
            val y = height - (normalizedValue * height * 0.8f) - height * 0.1f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            brush = Brush.horizontalGradient(
                colors = listOf(strokeColor.copy(alpha = 0.2f), strokeColor)
            ),
            style = Stroke(width = 5f)
        )
    }
}

@Composable
fun AnimatedHeartbeat(bpm: Float, modifier: Modifier = Modifier) {
    val beatInterval = (60000f / bpm).coerceAtLeast(200f).toLong()
    val infiniteTransition = rememberInfiniteTransition(label = "beat")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beat"
    )

    Icon(
        imageVector = Icons.Default.Favorite,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    )
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun ErrorScreen(error: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text("Camera Error: $error", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    }
}

// ========== HELPERS ==========

private fun getStatusText(status: MeasurementStatus): String = when (status) {
    MeasurementStatus.INITIALIZING -> "Starting..."
    MeasurementStatus.NO_FACE -> "No Face"
    MeasurementStatus.ACQUIRING -> "Acquiring..."
    MeasurementStatus.TRACKING -> "Tracking"
    MeasurementStatus.MEASURING -> "Measuring"
    MeasurementStatus.COMPLETED -> "Done"
}

@Composable
private fun getStatusColor(status: MeasurementStatus): Color = when (status) {
    MeasurementStatus.INITIALIZING, MeasurementStatus.ACQUIRING -> MaterialTheme.colorScheme.tertiary
    MeasurementStatus.NO_FACE -> MaterialTheme.colorScheme.error
    MeasurementStatus.TRACKING, MeasurementStatus.MEASURING -> MaterialTheme.colorScheme.primary
    MeasurementStatus.COMPLETED -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun getQualityColor(confidence: Float): Color = when {
    confidence >= 0.8f -> MaterialTheme.colorScheme.primary
    confidence >= 0.5f -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

private fun getQualityText(confidence: Float): String = when {
    confidence >= 0.8f -> "Good"
    confidence >= 0.5f -> "Fair"
    else -> "Poor"
}