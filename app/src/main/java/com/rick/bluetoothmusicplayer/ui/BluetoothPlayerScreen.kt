package com.rick.bluetoothmusicplayer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rick.bluetoothmusicplayer.PlayerUiState
import com.rick.bluetoothmusicplayer.bluetooth.DiscoveredDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothPlayerScreen(
    state: PlayerUiState,
    onRequestPermissions: () -> Unit,
    onScanClick: () -> Unit,
    onStopScanClick: () -> Unit,
    onDeviceClick: (DiscoveredDevice) -> Unit,
    onPlayClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bluetooth Music Player") },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            StatusBanner(state = state)

            Spacer(modifier = Modifier.height(12.dp))

            ActionRow(
                state = state,
                onRequestPermissions = onRequestPermissions,
                onScanClick = onScanClick,
                onStopScanClick = onStopScanClick,
                onPlayClick = onPlayClick,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Nearby & paired devices",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Audio devices (headphones) appear first. Tap one to pair, then Play.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            when {
                !state.hasPermissions -> {
                    EmptyHint("Bluetooth permissions are needed to list devices.")
                }

                state.devices.isEmpty() -> {
                    EmptyHint(
                        if (state.isScanning) {
                            "Scanning…"
                        } else {
                            "No devices yet. Put headphones in pairing mode and tap Scan."
                        },
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.devices, key = { it.address }) { device ->
                            DeviceRow(
                                device = device,
                                selected = device.address == state.selectedAddress,
                                onClick = { onDeviceClick(device) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBanner(state: PlayerUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        !state.bluetoothAvailable -> "No Bluetooth adapter"
                        !state.hasPermissions -> "Permissions required"
                        !state.bluetoothEnabled -> "Bluetooth is off"
                        state.isScanning -> "Scanning…"
                        state.isPlaying -> "Playing"
                        else -> "Idle"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
                if (state.isScanning) {
                    Spacer(modifier = Modifier.width(10.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = state.statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActionRow(
    state: PlayerUiState,
    onRequestPermissions: () -> Unit,
    onScanClick: () -> Unit,
    onStopScanClick: () -> Unit,
    onPlayClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!state.hasPermissions) {
            Button(
                onClick = onRequestPermissions,
                modifier = Modifier.weight(1f),
            ) {
                Text("Grant permissions")
            }
            return
        }

        if (state.isScanning) {
            OutlinedButton(
                onClick = onStopScanClick,
                modifier = Modifier.weight(1f),
            ) {
                Text("Stop scan")
            }
        } else {
            FilledTonalButton(
                onClick = onScanClick,
                modifier = Modifier.weight(1f),
                enabled = state.bluetoothEnabled,
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Scan")
            }
        }

        Button(
            onClick = onPlayClick,
            modifier = Modifier.weight(1f),
            enabled = state.selectedAddress != null,
        ) {
            Icon(
                imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (state.isPlaying) "Pause" else "Play")
        }
    }
}

@Composable
private fun DeviceRow(
    device: DiscoveredDevice,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (device.isAudioDevice) {
                    Icons.Default.Headphones
                } else {
                    Icons.Default.Bluetooth
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val subtitle = buildString {
                    append(device.bondStateLabel)
                    if (device.isAudioDevice) append(" · Headphones / audio")
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun EmptyHint(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.Headphones,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
