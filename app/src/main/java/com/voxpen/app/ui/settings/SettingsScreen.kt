package com.voxpen.app.ui.settings

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.TextButton
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
import com.voxpen.app.BuildConfig
import com.voxpen.app.R
import com.voxpen.app.billing.ProSource
import com.voxpen.app.billing.ProStatus
import com.voxpen.app.data.model.RecordingMode
import com.voxpen.app.data.model.SttLanguage
import com.voxpen.app.data.model.LlmProvider
import com.voxpen.app.data.model.ToneStyle

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
            ProStatusSection(state, context as? Activity, viewModel)
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
            LlmProviderSection(state, viewModel)
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            RecordingModeSection(state, viewModel)
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            RefinementSection(state, viewModel)
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            ToneStyleSection(
                selectedTone = state.toneStyle,
                onToneSelected = { viewModel.setToneStyle(it) },
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            if (state.toneStyle == ToneStyle.Custom) {
                CustomPromptSection(state, viewModel)
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            }
            DictionaryEntryRow(onNavigateToDictionary)
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            PermissionSection(hasMicPermission) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    LicenseActivationDialog(state, viewModel)
}

@Composable
private fun ProStatusSection(
    state: SettingsUiState,
    activity: Activity?,
    viewModel: SettingsViewModel,
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
            val statusLabel = when {
                state.proStatus is ProStatus.Pro && state.proStatus.source == ProSource.LICENSE_KEY ->
                    stringResource(R.string.license_status_active)
                state.proStatus.isPro -> stringResource(R.string.pro_status_pro)
                else -> stringResource(R.string.pro_status_free)
            }
            Text(statusLabel, style = MaterialTheme.typography.titleMedium)

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
                    stringResource(R.string.usage_transcription_remaining, state.remainingFileTranscriptionSeconds),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { activity?.let { viewModel.launchPurchaseFlow(it) } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.usage_upgrade_pro))
                }
                OutlinedButton(
                    onClick = { viewModel.restorePurchases() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.pro_restore_purchase))
                }
            }

            if (state.isActivatingLicense) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(16.dp).padding(end = 8.dp),
                    )
                    Text(
                        stringResource(R.string.license_activating),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            state.licenseError?.let { error ->
                Spacer(Modifier.height(8.dp))
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            val isLicensePro = state.proStatus is ProStatus.Pro &&
                (state.proStatus as? ProStatus.Pro)?.source == ProSource.LICENSE_KEY
            if (isLicensePro) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.deactivateLicense() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.license_deactivate_button))
                }
            }
        }
    }
}

@Composable
private fun LicenseActivationDialog(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    var showLicenseDialog by remember { mutableStateOf(false) }

    if (!state.proStatus.isPro) {
        TextButton(onClick = { showLicenseDialog = true }) {
            Text(stringResource(R.string.upgrade_prompt_license))
        }
    }

    if (showLicenseDialog) {
        var licenseKey by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            title = { Text(stringResource(R.string.license_activate_title)) },
            text = {
                OutlinedTextField(
                    value = licenseKey,
                    onValueChange = { licenseKey = it },
                    label = { Text(stringResource(R.string.license_activate_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.activateLicense(licenseKey)
                        showLicenseDialog = false
                    },
                    enabled = licenseKey.isNotBlank(),
                ) {
                    Text(stringResource(R.string.license_activate_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLicenseDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
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
    var showMore by remember { mutableStateOf(false) }
    val isExtraLanguageSelected =
        state.language in
            listOf(
                SttLanguage.Korean,
                SttLanguage.French,
                SttLanguage.German,
                SttLanguage.Spanish,
                SttLanguage.Vietnamese,
                SttLanguage.Indonesian,
                SttLanguage.Thai,
            )

    SectionHeader(stringResource(R.string.settings_language_section))

    listOf(
        SttLanguage.Auto to stringResource(R.string.lang_auto),
        SttLanguage.Chinese to stringResource(R.string.lang_zh),
        SttLanguage.English to stringResource(R.string.lang_en),
        SttLanguage.Japanese to stringResource(R.string.lang_ja),
    ).forEach { (lang, label) ->
        RadioRow(label, state.language == lang) { viewModel.setLanguage(lang) }
        if (lang == SttLanguage.Chinese) {
            Text(
                stringResource(R.string.lang_zh_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 48.dp, bottom = 4.dp),
            )
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { showMore = !showMore }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.lang_more) + if (showMore) " ▲" else " ▼",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp),
        )
    }

    AnimatedVisibility(visible = showMore || isExtraLanguageSelected) {
        Column {
            listOf(
                SttLanguage.Korean to stringResource(R.string.lang_ko),
                SttLanguage.French to stringResource(R.string.lang_fr),
                SttLanguage.German to stringResource(R.string.lang_de),
                SttLanguage.Spanish to stringResource(R.string.lang_es),
                SttLanguage.Vietnamese to stringResource(R.string.lang_vi),
                SttLanguage.Indonesian to stringResource(R.string.lang_id),
                SttLanguage.Thai to stringResource(R.string.lang_th),
            ).forEach { (lang, label) ->
                SmallRadioRow(label, state.language == lang) { viewModel.setLanguage(lang) }
            }
        }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LlmProviderSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    SectionHeader(stringResource(R.string.settings_llm_provider_section))

    val providerLabels = mapOf(
        LlmProvider.Groq to "Groq",
        LlmProvider.OpenAI to "OpenAI",
        LlmProvider.OpenRouter to "OpenRouter",
        LlmProvider.Custom to stringResource(R.string.provider_custom),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LlmProvider.all.forEach { provider ->
            FilterChip(
                selected = provider == state.llmProvider,
                onClick = { viewModel.setLlmProvider(provider) },
                label = { Text(providerLabels[provider] ?: provider.key) },
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    if (state.llmProvider != LlmProvider.Groq) {
        ProviderApiKeyField(state, viewModel)
        Spacer(Modifier.height(8.dp))
    }

    if (state.llmProvider == LlmProvider.Custom) {
        CustomProviderFields(state, viewModel)
    } else {
        ProviderModelList(state, viewModel)
    }
}

@Composable
private fun ProviderApiKeyField(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    var keyInput by remember { mutableStateOf("") }
    val isConfigured = state.providerApiKeys[state.llmProvider.key] == true
    if (isConfigured) {
        Text(
            stringResource(R.string.provider_key_configured),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    OutlinedTextField(
        value = keyInput,
        onValueChange = { keyInput = it },
        label = { Text(stringResource(R.string.provider_api_key_hint, providerDisplayName(state.llmProvider))) },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = {
            if (keyInput.isNotBlank()) {
                viewModel.saveProviderApiKey(state.llmProvider, keyInput)
                keyInput = ""
            }
        },
        modifier = Modifier.padding(top = 4.dp),
    ) { Text(stringResource(R.string.settings_save)) }
}

@Composable
private fun ProviderModelList(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    val tagLabels = mapOf(
        "recommended" to stringResource(R.string.model_tag_recommended),
        "fast" to stringResource(R.string.model_tag_fast),
        "cheapest" to stringResource(R.string.model_tag_cheapest),
        "quality" to stringResource(R.string.model_tag_quality),
        "best_chinese" to stringResource(R.string.model_tag_best_chinese),
    )
    state.llmProvider.models.forEach { model ->
        val label = buildString {
            append(model.label)
            model.tag?.let { tag ->
                append(" — ")
                append(tagLabels[tag] ?: tag)
            }
        }
        RadioRow(label, state.llmModel == model.id) {
            viewModel.setLlmModel(model.id)
        }
    }
}

@Composable
private fun CustomProviderFields(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    OutlinedTextField(
        value = state.customBaseUrl,
        onValueChange = { viewModel.setCustomBaseUrl(it) },
        label = { Text(stringResource(R.string.provider_custom_base_url)) },
        placeholder = { Text("https://api.example.com/") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = state.customLlmModel,
        onValueChange = { viewModel.setCustomLlmModel(it) },
        label = { Text(stringResource(R.string.provider_custom_model)) },
        placeholder = { Text("llama3.1:8b") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun providerDisplayName(provider: LlmProvider): String =
    when (provider) {
        LlmProvider.Groq -> "Groq"
        LlmProvider.OpenAI -> "OpenAI"
        LlmProvider.OpenRouter -> "OpenRouter"
        LlmProvider.Custom -> "Custom"
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
private fun SmallRadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToneStyleSection(
    selectedTone: ToneStyle,
    onToneSelected: (ToneStyle) -> Unit,
) {
    SectionHeader(stringResource(R.string.settings_tone_section))
    val toneLabels = mapOf(
        ToneStyle.Casual to stringResource(R.string.tone_casual),
        ToneStyle.Professional to stringResource(R.string.tone_professional),
        ToneStyle.Email to stringResource(R.string.tone_email),
        ToneStyle.Note to stringResource(R.string.tone_note),
        ToneStyle.Social to stringResource(R.string.tone_social),
        ToneStyle.Custom to stringResource(R.string.tone_custom),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ToneStyle.all.forEach { tone ->
            FilterChip(
                selected = tone == selectedTone,
                onClick = { onToneSelected(tone) },
                label = { Text(toneLabels[tone] ?: tone.key) },
            )
        }
    }
}

@Composable
private fun CustomPromptSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    SectionHeader(stringResource(R.string.settings_custom_prompt_section))
    OutlinedTextField(
        value = state.customPromptDraft,
        onValueChange = { viewModel.updateCustomPromptDraft(it) },
        label = { Text(stringResource(R.string.settings_custom_prompt_hint)) },
        modifier =
            Modifier
                .fillMaxWidth()
                .height(200.dp),
        maxLines = 10,
    )
    Text(
        stringResource(R.string.settings_custom_prompt_dictionary_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
    Row(modifier = Modifier.padding(top = 8.dp)) {
        Button(
            onClick = { viewModel.saveCustomPrompt() },
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.settings_custom_prompt_save))
        }
        Spacer(Modifier.padding(horizontal = 4.dp))
        OutlinedButton(
            onClick = { viewModel.resetCustomPrompt() },
        ) {
            Text(stringResource(R.string.settings_custom_prompt_reset))
        }
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
