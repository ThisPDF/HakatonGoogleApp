package com.example.smarthome.wear.ui.sensors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

@Composable
fun SensorsScreen(
    viewModel: SensorsViewModel = hiltViewModel()
) {
    val heartRate by viewModel.heartRate.collectAsState()
    val steps by viewModel.steps.collectAsState()
    val location by viewModel.location.collectAsState()
    val isActive by viewModel.isDataCollectionActive.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Sensor Data",
            style = MaterialTheme.typography.title1,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Heart Rate: $heartRate BPM",
            style = MaterialTheme.typography.body1,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Steps: $steps",
            style = MaterialTheme.typography.body1,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        location?.let {
            Text(
                text = "Location: ${String.format("%.6f", it.latitude)}, ${String.format("%.6f", it.longitude)}",
                style = MaterialTheme.typography.body2,
                textAlign = TextAlign.Center
            )
        } ?: Text(
            text = "Location: Not available",
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = {
                    if (isActive) {
                        viewModel.stopDataCollection()
                    } else {
                        viewModel.startDataCollection()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (isActive) 
                        MaterialTheme.colors.error 
                    else 
                        MaterialTheme.colors.primary
                )
            ) {
                Text(text = if (isActive) "Stop" else "Start")
            }
        }
    }
}
