package com.voxink.app.ui.dictionary

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.voxink.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreenContent(
    onNavigateBack: () -> Unit,
    viewModel: DictionaryViewModel = hiltViewModel(),
) {
    val entries by viewModel.entries.collectAsState()
    val count by viewModel.count.collectAsState()
    val isPro by viewModel.isPro.collectAsState()
    val isLimitReached by viewModel.isLimitReached.collectAsState()
    val showDuplicate by viewModel.showDuplicateToast.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var showHelp by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val limit = DictionaryViewModel.FREE_DICTIONARY_LIMIT

    LaunchedEffect(showDuplicate) {
        if (showDuplicate) {
            Toast.makeText(context, context.getString(R.string.dictionary_duplicate), Toast.LENGTH_SHORT).show()
            viewModel.dismissDuplicateToast()
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text(stringResource(R.string.dictionary_title)) },
            text = { Text(stringResource(R.string.dictionary_help)) },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) { Text("OK") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dictionary_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showHelp = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Help")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text(stringResource(R.string.dictionary_add_hint)) },
                    modifier = Modifier.weight(1f),
                    enabled = !isLimitReached,
                    singleLine = true,
                )
                Button(
                    onClick = {
                        viewModel.addWord(inputText)
                        inputText = ""
                    },
                    modifier = Modifier.padding(start = 8.dp),
                    enabled = !isLimitReached && inputText.isNotBlank(),
                ) {
                    Text(stringResource(R.string.dictionary_add_button))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text =
                    if (isPro) {
                        stringResource(R.string.dictionary_count_unlimited, count)
                    } else {
                        stringResource(R.string.dictionary_count, count, limit)
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (isLimitReached) {
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                ) {
                    Text(
                        stringResource(R.string.dictionary_upgrade),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (entries.isEmpty()) {
                Text(
                    stringResource(R.string.dictionary_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 32.dp),
                )
            } else {
                LazyColumn {
                    items(entries, key = { it.id }) { entry ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                entry.word,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            IconButton(onClick = { viewModel.removeWord(entry) }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
