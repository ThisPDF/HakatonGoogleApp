package com.example.smarthome.wear.ui.sync

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SyncScreen(
    viewModel: SyncViewModel = hiltViewModel(),
    onSyncComplete: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(uiState.syncComplete) {
        if (uiState.syncComplete) {
            onSyncComplete()
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Smart Home",
                style = MaterialTheme.typography.title1,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (uiState.isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 4.dp
                )
                Text(
                    text = "Syncing with phone...",
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Center
                )
            } else if (uiState.error != null) {
                Text(
                    text = "Error: ${uiState.error}",
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colors.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.startSync() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Retry")
                }
            } else {
                Text(
                    text = "Sync with your phone to get the latest device data",
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.startSync() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sync Now")
                }
            }
        }
    }
}
