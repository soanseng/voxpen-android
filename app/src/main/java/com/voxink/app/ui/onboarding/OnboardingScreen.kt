package com.voxink.app.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.voxink.app.R

@Composable
fun OnboardingScreenContent(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Check keyboard and permission state on each composition
    LaunchedEffect(state.currentStep) {
        val isEnabled =
            try {
                val imm = context.getSystemService(InputMethodManager::class.java)
                imm.enabledInputMethodList.any { it.packageName == context.packageName }
            } catch (_: Exception) {
                false
            }
        viewModel.updateKeyboardEnabled(isEnabled)

        val hasMic =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
        viewModel.updateMicPermission(hasMic)
    }

    Scaffold { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StepProgress(state.currentStep)
            Spacer(Modifier.height(32.dp))
            when (state.currentStep) {
                OnboardingStep.WELCOME -> WelcomeStep()
                OnboardingStep.API_KEY -> ApiKeyStep(state, viewModel)
                OnboardingStep.ENABLE_KEYBOARD -> EnableKeyboardStep(state)
                OnboardingStep.GRANT_PERMISSION -> PermissionStep(state, viewModel)
                OnboardingStep.PRACTICE -> PracticeStep(state, viewModel)
                OnboardingStep.DONE -> DoneStep()
            }
            Spacer(Modifier.height(32.dp))
            NavigationButtons(
                state = state,
                onBack = { viewModel.previousStep() },
                onNext = { viewModel.nextStep() },
                onDone = {
                    viewModel.completeOnboarding()
                    onComplete()
                },
            )
        }
    }
}

@Composable
private fun StepProgress(currentStep: OnboardingStep) {
    val progress = (currentStep.ordinal + 1).toFloat() / OnboardingStep.entries.size
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    val progressText =
        when (currentStep) {
            OnboardingStep.WELCOME, OnboardingStep.API_KEY -> null
            OnboardingStep.ENABLE_KEYBOARD -> stringResource(R.string.onboarding_progress_few_more)
            OnboardingStep.GRANT_PERMISSION -> stringResource(R.string.onboarding_progress_almost)
            OnboardingStep.PRACTICE -> stringResource(R.string.onboarding_progress_last)
            OnboardingStep.DONE -> null
        }
    progressText?.let {
        Text(
            it,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WelcomeStep() {
    Text(
        stringResource(R.string.onboarding_welcome_title),
        style = MaterialTheme.typography.headlineMedium,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        stringResource(R.string.onboarding_welcome_description),
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun ApiKeyStep(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel,
) {
    var keyInput by remember { mutableStateOf("") }

    Text(
        stringResource(R.string.onboarding_api_key_title),
        style = MaterialTheme.typography.headlineMedium,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        stringResource(R.string.onboarding_api_key_description),
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = keyInput,
        onValueChange = { keyInput = it },
        label = { Text(stringResource(R.string.settings_groq_api_key)) },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = {
            if (keyInput.isNotBlank()) {
                viewModel.saveApiKey(keyInput)
                keyInput = ""
            }
        },
    ) {
        Text(stringResource(R.string.settings_save))
    }
    if (state.isApiKeyConfigured) {
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding_api_key_saved),
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Spacer(Modifier.height(8.dp))
    val uriHandler = LocalUriHandler.current
    TextButton(onClick = { uriHandler.openUri("https://console.groq.com") }) {
        Text(
            stringResource(R.string.onboarding_api_key_link),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun EnableKeyboardStep(state: OnboardingUiState) {
    val context = LocalContext.current
    Text(
        stringResource(R.string.onboarding_keyboard_title),
        style = MaterialTheme.typography.headlineMedium,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        stringResource(R.string.onboarding_keyboard_description),
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(Modifier.height(16.dp))
    if (state.isKeyboardEnabled) {
        Text(
            stringResource(R.string.onboarding_keyboard_enabled),
            color = MaterialTheme.colorScheme.primary,
        )
    } else {
        Button(onClick = {
            context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }) {
            Text(stringResource(R.string.open_keyboard_settings))
        }
    }
}

@Composable
private fun PermissionStep(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel,
) {
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.updateMicPermission(granted)
        }

    Text(
        stringResource(R.string.onboarding_permission_title),
        style = MaterialTheme.typography.headlineMedium,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        stringResource(R.string.onboarding_permission_description),
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(Modifier.height(16.dp))
    if (state.hasMicPermission) {
        Text(
            stringResource(R.string.onboarding_permission_granted),
            color = MaterialTheme.colorScheme.primary,
        )
    } else {
        Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
            Text(stringResource(R.string.settings_grant_mic))
        }
    }
}

@Composable
private fun PracticeStep(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel,
) {
    Text(
        stringResource(R.string.onboarding_practice_title),
        style = MaterialTheme.typography.headlineMedium,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        stringResource(R.string.onboarding_practice_description),
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(Modifier.height(24.dp))

    if (state.isPracticing) {
        CircularProgressIndicator()
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.processing))
    } else if (state.hasPracticed && state.practiceOriginal != null) {
        Text(
            stringResource(R.string.onboarding_practice_success),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    stringResource(R.string.onboarding_practice_original),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(state.practiceOriginal, style = MaterialTheme.typography.bodyMedium)
                if (state.practiceRefined != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.onboarding_practice_refined),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        state.practiceRefined,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { viewModel.clearPractice() }) {
            Text(stringResource(R.string.onboarding_practice_retry))
        }
    } else {
        // Simple record button for practice
        Button(onClick = { /* Recording wired in practice -- for now show placeholder */ }) {
            Text(stringResource(R.string.keyboard_record))
        }
    }
}

@Composable
private fun DoneStep() {
    Text(
        stringResource(R.string.onboarding_done_title),
        style = MaterialTheme.typography.headlineMedium,
    )
    Spacer(Modifier.height(16.dp))
    Text(
        stringResource(R.string.onboarding_done_description),
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun NavigationButtons(
    state: OnboardingUiState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onDone: () -> Unit,
) {
    val canProceed =
        when (state.currentStep) {
            OnboardingStep.WELCOME -> true
            OnboardingStep.API_KEY -> state.isApiKeyConfigured
            OnboardingStep.ENABLE_KEYBOARD -> state.isKeyboardEnabled
            OnboardingStep.GRANT_PERMISSION -> state.hasMicPermission
            OnboardingStep.PRACTICE -> state.hasPracticed
            OnboardingStep.DONE -> true
        }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (state.currentStep != OnboardingStep.WELCOME) {
            OutlinedButton(onClick = onBack) {
                Text(stringResource(R.string.onboarding_back))
            }
        } else {
            Spacer(Modifier.width(1.dp))
        }
        if (state.currentStep == OnboardingStep.DONE) {
            Button(onClick = onDone) {
                Text(stringResource(R.string.onboarding_finish))
            }
        } else {
            Button(onClick = onNext, enabled = canProceed) {
                Text(stringResource(R.string.onboarding_next))
            }
        }
    }
}
