package com.alexprestes.dashcamviewer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alexprestes.dashcamviewer.domain.usecase.ListVolumesWithDashcamVideoCountUseCase
import com.alexprestes.dashcamviewer.ui.permission.PermissionScreen
import com.alexprestes.dashcamviewer.ui.theme.DashcamViewerTheme
import com.alexprestes.dashcamviewer.ui.volume.VolumeScreen
import com.alexprestes.dashcamviewer.ui.volume.VolumeUiState
import com.alexprestes.dashcamviewer.ui.volume.VolumeViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: VolumeViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val useCase = ListVolumesWithDashcamVideoCountUseCase(applicationContext)
                @Suppress("UNCHECKED_CAST")
                return VolumeViewModel(useCase) as T
            }
        }
    }

    private var permissionGranted by mutableStateOf(false)

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            permissionGranted = isGranted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionGranted = hasStoragePermission()

        setContent {
            DashcamViewerTheme {
                if (!permissionGranted) {
                    PermissionScreen(onRequestPermission = { requestStoragePermission() })
                } else {
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    when (val state = uiState) {
                        is VolumeUiState.Loading -> {
                            // TODO: Show a loading indicator
                        }
                        is VolumeUiState.Success -> {
                            VolumeScreen(
                                volumes = state.volumes,
                                onVolumeSelected = {
                                    // TODO: Navigate to Player Screen
                                }
                            )
                        }
                        is VolumeUiState.Error -> {
                            // TODO: Show an error message
                        }
                    }
                }
            }
        }
    }

    private fun hasStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestStoragePermission() {
        requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}
