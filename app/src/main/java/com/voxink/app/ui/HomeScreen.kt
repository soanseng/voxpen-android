package com.voxink.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.voxink.app.R
import com.voxink.app.ads.BannerAdView
import com.voxink.app.ui.settings.SettingsViewModel

object HomeScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenContent(
    onNavigateToSettings: () -> Unit,
    onNavigateToTranscription: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val isKeyboardEnabled =
        try {
            val imm = context.getSystemService(InputMethodManager::class.java)
            imm.enabledInputMethodList.any { it.packageName == context.packageName }
        } catch (_: Exception) {
            false
        }
    val hasMicPerm =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WelcomeHeader()
            Spacer(Modifier.height(24.dp))
            if (!state.proStatus.isPro) {
                UsageSummaryCard(state)
                Spacer(Modifier.height(16.dp))
            }
            SetupChecklist(isKeyboardEnabled, state.isApiKeyConfigured, hasMicPerm)
            Spacer(Modifier.height(24.dp))
            HomeActions(isKeyboardEnabled, onNavigateToSettings, onNavigateToTranscription)
            if (!state.proStatus.isPro) {
                Spacer(Modifier.height(16.dp))
                BannerAdView()
            }
        }
    }
}

@Composable
private fun UsageSummaryCard(state: com.voxink.app.ui.settings.SettingsUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.pro_status_free),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.usage_voice_remaining, state.remainingVoiceInputs),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(R.string.usage_refinement_remaining, state.remainingRefinements),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(R.string.usage_transcription_remaining, state.remainingFileTranscriptions),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun WelcomeHeader() {
    Text(
        stringResource(R.string.welcome_message),
        style = MaterialTheme.typography.titleLarge,
    )
    Text(
        stringResource(R.string.welcome_description),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SetupChecklist(
    isKeyboardEnabled: Boolean,
    isApiKeyConfigured: Boolean,
    hasMicPerm: Boolean,
) {
    Text(
        stringResource(
            R.string.setup_keyboard,
            if (isKeyboardEnabled) {
                stringResource(
                    R.string.status_enabled,
                )
            } else {
                stringResource(R.string.status_disabled)
            },
        ),
    )
    Text(
        stringResource(
            R.string.setup_api_key,
            if (isApiKeyConfigured) {
                stringResource(
                    R.string.status_configured,
                )
            } else {
                stringResource(R.string.status_not_configured)
            },
        ),
    )
    Text(
        stringResource(
            R.string.setup_permission,
            if (hasMicPerm) stringResource(R.string.status_granted) else stringResource(R.string.status_denied),
        ),
    )
}

@Composable
private fun HomeActions(
    isKeyboardEnabled: Boolean,
    onNavigateToSettings: () -> Unit,
    onNavigateToTranscription: () -> Unit,
) {
    val context = LocalContext.current
    if (!isKeyboardEnabled) {
        Button(
            onClick = { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
            Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.open_keyboard_settings))
        }
        Spacer(Modifier.height(8.dp))
    }
    Button(onClick = onNavigateToTranscription, Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.transcribe_audio))
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onNavigateToSettings, Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.open_settings))
    }
}
