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
import com.voxink.app.ui.settings.SettingsViewModel

class HomeScreen {
    companion object
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenContent(
    onNavigateToSettings: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val isKeyboardEnabled = try {
        val imm = context.getSystemService(InputMethodManager::class.java)
        imm.enabledInputMethodList.any { it.packageName == context.packageName }
    } catch (_: Exception) {
        false
    }
    val hasMicPerm = ContextCompat.checkSelfPermission(
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
            Text(
                stringResource(R.string.welcome_message),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(R.string.welcome_description),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(32.dp))

            Text(
                stringResource(
                    R.string.setup_keyboard,
                    if (isKeyboardEnabled) {
                        stringResource(R.string.status_enabled)
                    } else {
                        stringResource(R.string.status_disabled)
                    },
                ),
            )
            Text(
                stringResource(
                    R.string.setup_api_key,
                    if (state.isApiKeyConfigured) {
                        stringResource(R.string.status_configured)
                    } else {
                        stringResource(R.string.status_not_configured)
                    },
                ),
            )
            Text(
                stringResource(
                    R.string.setup_permission,
                    if (hasMicPerm) {
                        stringResource(R.string.status_granted)
                    } else {
                        stringResource(R.string.status_denied)
                    },
                ),
            )
            Spacer(Modifier.height(24.dp))

            if (!isKeyboardEnabled) {
                Button(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    },
                    Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.open_keyboard_settings))
                }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(onClick = onNavigateToSettings, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.open_settings))
            }
        }
    }
}
