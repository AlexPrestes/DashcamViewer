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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.alexprestes.dashcamviewer.domain.usecase.ListVolumesWithDashcamVideoCountUseCase
import com.alexprestes.dashcamviewer.ui.permission.PermissionScreen
import com.alexprestes.dashcamviewer.ui.player.PlayerScreen
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
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "volumeList") {
                        composable("volumeList") {
                            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                            when (val state = uiState) {
                                is VolumeUiState.Loading -> {
                                    // TODO: Show a loading indicator
                                }

                                is VolumeUiState.Success -> {
                                    VolumeScreen(
                                        volumes = state.volumes,
                                        onVolumeSelected = { volume ->
                                            navController.navigate("player/${volume.name}")
                                        }
                                    )
                                }

                                is VolumeUiState.Error -> {
                                    // TODO: Show an error message
                                }
                            }
                        }
                        composable(
                            "player/{volumeName}",
                            arguments = listOf(navArgument("volumeName") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val volumeName = backStackEntry.arguments?.getString("volumeName")
                            PlayerScreen(volumeName = volumeName)
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
