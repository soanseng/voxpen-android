package com.voxink.app.ui.transcription

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.voxink.app.R
import com.voxink.app.ads.BannerAdView
import com.voxink.app.billing.UsageLimiter
import com.voxink.app.data.local.TranscriptionEntity
import com.voxink.app.data.model.SttLanguage
import com.voxink.app.util.ExportHelper
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptionScreenContent(
    onNavigateBack: () -> Unit,
    viewModel: TranscriptionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val filePicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent(),
        ) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            viewModel.onFileSelected(uri)
            // Check if onFileSelected rejected due to limits
            if (!state.isTranscribing && state.error != null) return@rememberLauncherForActivityResult
            scope.launch {
                try {
                    val fileBytes =
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.readBytes()
                        }
                    if (fileBytes == null) {
                        viewModel.onTranscriptionError("Could not read file")
                        return@launch
                    }
                    val fileName =
                        uri.lastPathSegment?.substringAfterLast('/') ?: "audio"
                    val entryPoint =
                        EntryPointAccessors.fromApplication(
                            context.applicationContext,
                            TranscriptionEntryPoint::class.java,
                        )
                    val useCase = entryPoint.transcribeFileUseCase()
                    val apiKey = entryPoint.apiKeyManager().getGroqApiKey() ?: ""
                    var entity: TranscriptionEntity? = null
                    var errorMsg: String? = null
                    withContext(Dispatchers.IO) {
                        val result =
                            useCase(
                                fileBytes = fileBytes,
                                fileName = fileName,
                                language = SttLanguage.Auto,
                                apiKey = apiKey,
                            )
                        entity = result.getOrNull()
                        errorMsg = result.exceptionOrNull()?.message
                    }
                    val e = entity
                    if (e != null) {
                        viewModel.onTranscriptionComplete(e)
                    } else {
                        viewModel.onTranscriptionError(errorMsg ?: "Transcription failed")
                    }
                } catch (e: Exception) {
                    viewModel.onTranscriptionError(e.message ?: "Unknown error")
                }
            }
        }

    val activity = context as? Activity

    // Rewarded ad dialog when file transcription limit reached
    if (state.showRewardedAdPrompt) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissRewardedPrompt() },
            title = { Text(stringResource(R.string.usage_limit_reached)) },
            text = {
                Text(
                    stringResource(
                        R.string.ad_reward_prompt,
                        UsageLimiter.REWARDED_AD_BONUS,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    activity?.let { act ->
                        val entryPoint =
                            EntryPointAccessors.fromApplication(
                                context.applicationContext,
                                TranscriptionEntryPoint::class.java,
                            )
                        val rewardedAdLoader = entryPoint.rewardedAdLoader()
                        rewardedAdLoader.loadAndShow(
                            activity = act,
                            onRewarded = { _ ->
                                viewModel.onRewardedAdWatched()
                            },
                            onAdNotAvailable = {
                                viewModel.dismissRewardedPrompt()
                            },
                        )
                    }
                }) {
                    Text(stringResource(R.string.usage_watch_ad, UsageLimiter.REWARDED_AD_BONUS))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRewardedPrompt() }) {
                    Text(stringResource(R.string.transcription_cancel))
                }
            },
        )
    }

    // Interstitial ad after transcription completes (free users only)
    LaunchedEffect(state.showInterstitialAfterTranscription) {
        if (state.showInterstitialAfterTranscription && activity != null) {
            val entryPoint =
                EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    TranscriptionEntryPoint::class.java,
                )
            val interstitialAdLoader = entryPoint.interstitialAdLoader()
            interstitialAdLoader.preload(activity)
            val shown = interstitialAdLoader.show(activity) {
                viewModel.onInterstitialShown()
            }
            if (!shown) {
                viewModel.onInterstitialShown()
            }
        }
    }

    if (state.selectedTranscription != null) {
        TranscriptionDetailScreen(
            entity = state.selectedTranscription!!,
            isPro = state.proStatus.isPro,
            onBack = { viewModel.clearSelection() },
            onDelete = { id ->
                viewModel.deleteTranscription(id)
            },
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.transcription_title)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
            floatingActionButton = {
                if (!state.isTranscribing && state.canTranscribeFile) {
                    FloatingActionButton(onClick = { filePicker.launch("audio/*") }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.transcription_pick_file))
                    }
                }
            },
        ) { innerPadding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (!state.proStatus.isPro) {
                    Text(
                        stringResource(R.string.usage_transcription_remaining, state.remainingFileTranscriptions),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                if (state.isTranscribing) {
                    TranscribingIndicator(state.progress)
                }
                state.error?.let { error ->
                    ErrorBanner(error) { viewModel.clearError() }
                }
                if (state.transcriptions.isEmpty() && !state.isTranscribing) {
                    EmptyState()
                } else {
                    TranscriptionList(
                        transcriptions = state.transcriptions,
                        onSelect = { viewModel.selectTranscription(it) },
                    )
                }
                if (!state.proStatus.isPro) {
                    Spacer(Modifier.weight(1f))
                    BannerAdView(modifier = Modifier.padding(bottom = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun TranscribingIndicator(progress: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.height(24.dp).width(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(progress.ifBlank { stringResource(R.string.transcription_processing) })
    }
}

@Composable
private fun ErrorBanner(
    error: String,
    onDismiss: () -> Unit,
) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable(onClick = onDismiss),
    ) {
        Text(
            error,
            Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.transcription_empty),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.transcription_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TranscriptionList(
    transcriptions: List<TranscriptionEntity>,
    onSelect: (TranscriptionEntity) -> Unit,
) {
    LazyColumn {
        items(transcriptions, key = { it.id }) { entity ->
            TranscriptionItem(entity = entity, onClick = { onSelect(entity) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun TranscriptionItem(
    entity: TranscriptionEntity,
    onClick: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text(entity.fileName, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            entity.displayText,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            dateFormat.format(Date(entity.createdAt)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranscriptionDetailScreen(
    entity: TranscriptionEntity,
    isPro: Boolean,
    onBack: () -> Unit,
    onDelete: (Long) -> Unit,
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.transcription_delete_title)) },
            text = { Text(stringResource(R.string.transcription_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(entity.id)
                    showDeleteDialog = false
                }) {
                    Text(stringResource(R.string.transcription_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.transcription_cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(entity.fileName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { copyToClipboard(context, entity) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                    }
                    if (isPro) {
                        IconButton(onClick = { shareTranscription(context, entity) }) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
        ) {
            if (entity.refinedText != null) {
                Text(
                    stringResource(R.string.transcription_refined),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(entity.refinedText, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(R.string.transcription_original),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    entity.originalText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(entity.originalText, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

private fun copyToClipboard(
    context: Context,
    entity: TranscriptionEntity,
) {
    val text = ExportHelper.toPlainText(entity)
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Transcription", text))
    Toast.makeText(context, R.string.transcription_copied, Toast.LENGTH_SHORT).show()
}

private fun shareTranscription(
    context: Context,
    entity: TranscriptionEntity,
) {
    val text = ExportHelper.toPlainText(entity)
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, entity.fileName)
        }
    context.startActivity(Intent.createChooser(intent, null))
}
