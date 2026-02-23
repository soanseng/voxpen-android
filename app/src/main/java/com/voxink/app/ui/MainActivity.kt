package com.voxink.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.voxink.app.ui.settings.SettingsScreenContent
import com.voxink.app.ui.theme.VoxInkTheme
import com.voxink.app.ui.transcription.TranscriptionScreenContent
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoxInkTheme {
                VoxInkNavHost()
            }
        }
    }
}

@Composable
private fun VoxInkNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreenContent(
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToTranscription = { navController.navigate("transcription") },
            )
        }
        composable("settings") {
            SettingsScreenContent(onNavigateBack = { navController.popBackStack() })
        }
        composable("transcription") {
            TranscriptionScreenContent(onNavigateBack = { navController.popBackStack() })
        }
    }
}
