package com.voxpen.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.voxpen.app.ui.dictionary.DictionaryScreenContent
import com.voxpen.app.ui.onboarding.OnboardingScreenContent
import com.voxpen.app.ui.settings.SettingsScreenContent
import com.voxpen.app.ui.theme.VoxPenTheme
import com.voxpen.app.ui.transcription.TranscriptionScreenContent
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoxPenTheme {
                VoxPenNavHost()
            }
        }
    }
}

@Composable
private fun VoxPenNavHost() {
    val navController = rememberNavController()
    val mainViewModel: MainViewModel = hiltViewModel()
    val isOnboardingCompleted by mainViewModel.onboardingCompleted.collectAsState(initial = true)

    val startDestination = if (isOnboardingCompleted) "home" else "onboarding"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("onboarding") {
            OnboardingScreenContent(
                onComplete = {
                    navController.navigate("home") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                },
            )
        }
        composable("home") {
            HomeScreenContent(
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToTranscription = { navController.navigate("transcription") },
            )
        }
        composable("settings") {
            SettingsScreenContent(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDictionary = { navController.navigate("dictionary") },
            )
        }
        composable("transcription") {
            TranscriptionScreenContent(onNavigateBack = { navController.popBackStack() })
        }
        composable("dictionary") {
            DictionaryScreenContent(onNavigateBack = { navController.popBackStack() })
        }
    }
}
