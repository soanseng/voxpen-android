package com.voxink.app.ui.settings

import android.Manifest
import android.app.Activity
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import com.voxink.app.BuildConfig
import com.voxink.app.R
import com.voxink.app.ads.BannerAdView
import com.voxink.app.data.model.RecordingMode
import com.voxink.app.data.model.SttLanguage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    onNavigateBack: () -> Unit,
    onNavigateToDictionary: () -> Unit = {},
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
            ProStatusSection(state, context as? Activity)
            if (BuildConfig.DEBUG) {
                DebugProToggle(state, viewModel)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            ApiKeySection(state, apiKeyInput, { apiKeyInput = it }) {
                viewModel.saveApiKey(apiKeyInput)
                apiKeyInput = ""
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            LanguageSection(state, viewModel)
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SttModelSection(state, viewModel)
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            LlmModelSection(state, viewModel)
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            RecordingModeSection(state, viewModel)
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            RefinementSection(state, viewModel)
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            DictionaryEntryRow(onNavigateToDictionary)
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            PermissionSection(hasMicPermission) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            if (!state.proStatus.isPro) {
                Spacer(modifier = Modifier.height(16.dp))
                BannerAdView()
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ProStatusSection(
    state: SettingsUiState,
    activity: Activity?,
) {
    SectionHeader(stringResource(R.string.pro_section_title))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (state.proStatus.isPro) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                if (state.proStatus.isPro) {
                    stringResource(R.string.pro_status_pro)
                } else {
                    stringResource(R.string.pro_status_free)
                },
                style = MaterialTheme.typography.titleMedium,
            )
            if (!state.proStatus.isPro) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.pro_upgrade_description),
                    style = MaterialTheme.typography.bodySmall,
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
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        activity?.let {
                            val billingManager =
                                dagger.hilt.android.EntryPointAccessors.fromApplication(
                                    it.applicationContext,
                                    com.voxink.app.ime.VoxInkIMEEntryPoint::class.java,
                                ).billingManager()
                            billingManager.launchPurchaseFlow(it)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.usage_upgrade_pro))
                }
                OutlinedButton(
                    onClick = {
                        activity?.let {
                            val billingManager =
                                dagger.hilt.android.EntryPointAccessors.fromApplication(
                                    it.applicationContext,
                                    com.voxink.app.ime.VoxInkIMEEntryPoint::class.java,
                                ).billingManager()
                            billingManager.restorePurchases()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.pro_restore_purchase))
                }
            }
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
private fun RefinementSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    SectionHeader(stringResource(R.string.settings_refinement_section))
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.settings_refinement_toggle),
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = state.refinementEnabled,
            onCheckedChange = { viewModel.setRefinementEnabled(it) },
        )
    }
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
private fun SttModelSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    SectionHeader(stringResource(R.string.settings_stt_model_section))
    RadioRow(
        stringResource(R.string.settings_stt_model_turbo),
        state.sttModel == "whisper-large-v3-turbo",
    ) { viewModel.setSttModel("whisper-large-v3-turbo") }
    RadioRow(
        stringResource(R.string.settings_stt_model_v3),
        state.sttModel == "whisper-large-v3",
    ) { viewModel.setSttModel("whisper-large-v3") }
    Text(
        stringResource(R.string.settings_stt_model_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun LlmModelSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    SectionHeader(stringResource(R.string.settings_llm_model_section))
    RadioRow(
        stringResource(R.string.settings_llm_model_llama),
        state.llmModel == "llama-3.3-70b-versatile",
    ) { viewModel.setLlmModel("llama-3.3-70b-versatile") }
    RadioRow(
        stringResource(R.string.settings_llm_model_gpt_oss_120b),
        state.llmModel == "gpt-oss-120b",
    ) { viewModel.setLlmModel("gpt-oss-120b") }
    RadioRow(
        stringResource(R.string.settings_llm_model_gpt_oss_20b),
        state.llmModel == "gpt-oss-20b",
    ) { viewModel.setLlmModel("gpt-oss-20b") }
    Text(
        stringResource(R.string.settings_llm_model_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
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

@Composable
private fun DebugProToggle(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "\uD83D\uDEE0 Debug: Force Pro",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Switch(
            checked = state.proStatus.isPro,
            onCheckedChange = { viewModel.toggleDebugPro() },
        )
    }
}

@Composable
private fun DictionaryEntryRow(onNavigate: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigate)
                .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.dictionary_title),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Text(
            "\u203A",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
