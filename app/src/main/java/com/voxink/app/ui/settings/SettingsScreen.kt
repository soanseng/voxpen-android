package com.voxink.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.voxink.app.R
import com.voxink.app.data.model.RecordingMode
import com.voxink.app.data.model.SttLanguage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var apiKeyInput by remember { mutableStateOf("") }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted -> hasMicPermission = granted }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_menu_revert),
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            ApiKeySection(state, apiKeyInput, { apiKeyInput = it }) {
                viewModel.saveApiKey(apiKeyInput)
                apiKeyInput = ""
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            LanguageSection(state, viewModel)
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            RecordingModeSection(state, viewModel)
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            PermissionSection(hasMicPermission) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ApiKeySection(
    state: SettingsUiState,
    apiKeyInput: String,
    onInputChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    SectionHeader(stringResource(R.string.settings_api_key_section))
    if (state.isApiKeyConfigured) {
        Text(state.apiKeyDisplay, style = MaterialTheme.typography.bodyMedium)
    }
    OutlinedTextField(
        value = apiKeyInput,
        onValueChange = onInputChange,
        label = { Text(stringResource(R.string.settings_groq_api_key)) },
        placeholder = { Text(stringResource(R.string.settings_api_key_hint)) },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = { if (apiKeyInput.isNotBlank()) onSave() },
        modifier = Modifier.padding(top = 8.dp),
    ) { Text(stringResource(R.string.settings_save)) }
}

@Composable
private fun LanguageSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    SectionHeader(stringResource(R.string.settings_language_section))
    listOf(
        SttLanguage.Auto to stringResource(R.string.lang_auto),
        SttLanguage.Chinese to stringResource(R.string.lang_zh),
        SttLanguage.English to stringResource(R.string.lang_en),
        SttLanguage.Japanese to stringResource(R.string.lang_ja),
    ).forEach { (lang, label) ->
        RadioRow(label, state.language == lang) { viewModel.setLanguage(lang) }
    }
}

@Composable
private fun RecordingModeSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    SectionHeader(stringResource(R.string.settings_recording_section))
    RadioRow(
        stringResource(R.string.settings_tap_to_toggle),
        state.recordingMode == RecordingMode.TAP_TO_TOGGLE,
    ) { viewModel.setRecordingMode(RecordingMode.TAP_TO_TOGGLE) }
    RadioRow(
        stringResource(R.string.settings_hold_to_record),
        state.recordingMode == RecordingMode.HOLD_TO_RECORD,
    ) { viewModel.setRecordingMode(RecordingMode.HOLD_TO_RECORD) }
}

@Composable
private fun PermissionSection(
    hasMicPermission: Boolean,
    onRequestPermission: () -> Unit,
) {
    SectionHeader(stringResource(R.string.settings_permission_section))
    if (hasMicPermission) {
        Text(
            stringResource(R.string.status_granted),
            color = MaterialTheme.colorScheme.primary,
        )
    } else {
        Button(onClick = onRequestPermission) {
            Text(stringResource(R.string.settings_grant_mic))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun RadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
