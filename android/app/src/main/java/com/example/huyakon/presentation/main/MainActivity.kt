package com.example.huyakon.presentation.main

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.huyakon.presentation.theme.HuyakonTheme
import com.example.huyakon.service.OverlayService

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val capturePermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                OverlayService.start(this, result.resultCode, data)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HuyakonTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    HomeScreen(
                        state = state,
                        onStart = ::requestOverlayAndCapture,
                        modifier = Modifier.padding(padding)
                    )
                }
            }
        }
    }

    private fun requestOverlayAndCapture() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        }
        val manager = getSystemService(MediaProjectionManager::class.java)
        capturePermission.launch(manager.createScreenCaptureIntent())
    }
}
