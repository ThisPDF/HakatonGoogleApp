package com.example.smarthome.wear.ui.sensors

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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Text

@Composable
fun SensorsScreen(
    viewModel: SensorsViewModel = hiltViewModel()
) {
    val heartRate by viewModel.heartRate.collectAsState()
    val steps by viewModel.steps.collectAsState()
    val isMeasuring by viewModel.isMeasuring.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.checkSensorsAvailability()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Heart Rate: $heartRate BPM")
        Text(text = "Steps: $steps")
        
        Button(
            onClick = {
                if (isMeasuring) {
                    viewModel.stopMeasurements()
                } else {
                    viewModel.startMeasurements()
                }
            },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(text = if (isMeasuring) "Stop" else "Start")
        }
    }
}
