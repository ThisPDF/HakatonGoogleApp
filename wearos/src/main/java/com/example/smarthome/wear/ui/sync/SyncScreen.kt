package com.example.smarthome.wear.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay

@Composable
fun SyncScreen(
    viewModel: SyncViewModel = hiltViewModel(),
    onSyncComplete: () -> Unit
) {
    val syncState by viewModel.syncState.collectAsState()
    val phoneConnected by viewModel.phoneConnected.collectAsState()
    
    LaunchedEffect(key1 = syncState) {
        if (syncState == SyncViewModel.SyncState.COMPLETE) {
            delay(1000) // Show success message briefly
            onSyncComplete()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (syncState) {
            SyncViewModel.SyncState.CHECKING -> {
                CircularProgressIndicator()
                Text(
                    text = "Checking connection...",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            SyncViewModel.SyncState.CONNECTING -> {
                CircularProgressIndicator()
                Text(
                    text = "Connecting to phone...",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            SyncViewModel.SyncState.SYNCING -> {
                CircularProgressIndicator()
                Text(
                    text = "Syncing data...",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            SyncViewModel.SyncState.COMPLETE -> {
                Text(
                    text = "Sync complete!",
                    textAlign = TextAlign.Center
                )
            }
            SyncViewModel.SyncState.ERROR -> {
                Text(
                    text = "Connection error",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                if (!phoneConnected) {
                    Text(
                        text = "Please open the Smart Home app on your phone",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Button(onClick = { viewModel.retry() }) {
                    Text("Retry")
                }
            }
        }
    }
}
