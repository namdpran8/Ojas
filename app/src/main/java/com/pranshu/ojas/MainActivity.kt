package com.pranshu.ojas

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.pranshu.ojas.ui.theme.MainScreen

import com.pranshu.ojas.ui.theme.OjasTheme


class MainActivity : ComponentActivity() {

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d(TAG, "Camera permission result: $isGranted")
        if (isGranted) {
            setupUI()
        } else {
            showPermissionDenied()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")

        // Check if permission is already granted
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                Log.d(TAG, "Camera permission already granted")
                setupUI()
            }
            else -> {
                Log.d(TAG, "Requesting camera permission")
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun setupUI() {
        Log.d(TAG, "Setting up UI")
        try {
            setContent {
                OjasTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MainScreen()
                    }
                }
            }
            Log.d(TAG, "UI setup complete")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up UI", e)
            showError("Failed to initialize: ${e.message}")
        }
    }

    private fun showPermissionDenied() {
        setContent {
            OjasTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Camera permission is required for heart rate monitoring",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        Toast.makeText(this, "Camera permission denied", Toast.LENGTH_LONG).show()
    }

    private fun showError(message: String) {
        setContent {
            OjasTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Error: $message",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}