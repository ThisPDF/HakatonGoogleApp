package com.example.smarthome.ui.wearable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WearableScreen(
    viewModel: WearableViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Connection status
        ConnectionStatusCard(
            isConnected = uiState.isConnected,
            status = uiState.connectionStatus
        )
        
        // Sensor data
        SensorDataCard(
            heartRate = uiState.heartRate,
            steps = uiState.steps,
            accelerometerData = uiState.accelerometerData,
            onRequestData = { viewModel.requestSensorData() }
        )
        
        // Location data
        LocationDataCard(
            locationData = uiState.locationData,
            onRequestData = { viewModel.requestLocationData() }
        )
        
        // Error display
        if (uiState.error != null) {
            ErrorCard(error = uiState.error!!)
        }
    }
}

@Composable
fun ConnectionStatusCard(
    isConnected: Boolean,
    status: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Watch Connection",
                style = MaterialTheme.typography.titleLarge
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .background(
                            color = if (isConnected) Color.Green else Color.Red,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(4.dp)
                ) {
                    Text(
                        text = if (isConnected) "CONNECTED" else "DISCONNECTED",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
fun SensorDataCard(
    heartRate: Float?,
    steps: Int?,
    accelerometerData: WearableViewModel.AccelerometerData?,
    onRequestData: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Sensor Data",
                style = MaterialTheme.typography.titleLarge
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Heart rate
            Text(
                text = "Heart Rate:",
                fontWeight = FontWeight.Bold
            )
            Text(
                text = heartRate?.let { "$it BPM" } ?: "No data"
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))
            
            // Steps
            Text(
                text = "Steps:",
                fontWeight = FontWeight.Bold
            )
            Text(
                text = steps?.toString() ?: "No data"
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            Divider()
            Spacer(modifier = Modifier.height(8.dp))
            
            // Accelerometer
            Text(
                text = "Accelerometer:",
                fontWeight = FontWeight.Bold
            )
            if (accelerometerData != null) {
                Text(text = "X: ${accelerometerData.x}")
                Text(text = "Y: ${accelerometerData.y}")
                Text(text = "Z: ${accelerometerData.z}")
            } else {
                Text(text = "No data")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onRequestData,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Request Sensor Data")
            }
        }
    }
}

@Composable
fun LocationDataCard(
    locationData: WearableViewModel.LocationData?,
    onRequestData: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Location Data",
                style = MaterialTheme.typography.titleLarge
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (locationData != null) {
                Text(
                    text = "Latitude:",
                    fontWeight = FontWeight.Bold
                )
                Text(text = "${locationData.latitude}")
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Longitude:",
                    fontWeight = FontWeight.Bold
                )
                Text(text = "${locationData.longitude}")
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Accuracy:",
                    fontWeight = FontWeight.Bold
                )
                Text(text = "${locationData.accuracy} meters")
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Timestamp:",
                    fontWeight = FontWeight.Bold
                )
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                Text(text = dateFormat.format(Date(locationData.timestamp)))
            } else {
                Text(text = "No location data available")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onRequestData,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Request Location Data")
            }
        }
    }
}

@Composable
fun ErrorCard(error: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Error",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = error,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}
