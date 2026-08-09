package com.rick.bluetoothmusicplayer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rick.bluetoothmusicplayer.audio.TrackPlayer
import com.rick.bluetoothmusicplayer.bluetooth.BluetoothScanner
import com.rick.bluetoothmusicplayer.bluetooth.DiscoveredDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerUiState(
    val hasPermissions: Boolean = false,
    val bluetoothAvailable: Boolean = true,
    val bluetoothEnabled: Boolean = false,
    val isScanning: Boolean = false,
    val devices: List<DiscoveredDevice> = emptyList(),
    val selectedAddress: String? = null,
    val isPlaying: Boolean = false,
    val statusMessage: String = "Grant Bluetooth permissions to begin.",
)

class BluetoothPlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val trackPlayer = TrackPlayer(application)
    private var scanner: BluetoothScanner? = null

    fun onPermissionsResult(granted: Boolean) {
        _uiState.update {
            it.copy(
                hasPermissions = granted,
                statusMessage = if (granted) {
                    "Permissions granted."
                } else {
                    "Bluetooth permissions are required to scan for headphones."
                },
            )
        }
        if (granted) {
            ensureScanner()
        }
    }

    fun startScan() {
        if (!_uiState.value.hasPermissions) {
            _uiState.update {
                it.copy(statusMessage = "Bluetooth permissions are required.")
            }
            return
        }
        ensureScanner()?.startScan()
    }

    fun stopScan() {
        scanner?.stopScan()
    }

    fun selectDevice(address: String) {
        if (!_uiState.value.hasPermissions) return
        _uiState.update { it.copy(selectedAddress = address) }
        ensureScanner()?.connect(address)
    }

    fun togglePlayback() {
        val state = _uiState.value
        if (state.selectedAddress == null) {
            _uiState.update {
                it.copy(statusMessage = "Select a paired headphone first.")
            }
            return
        }

        viewModelScope.launch {
            if (trackPlayer.isPlaying) {
                trackPlayer.pause()
                _uiState.update {
                    it.copy(
                        isPlaying = false,
                        statusMessage = "Paused.",
                    )
                }
            } else {
                trackPlayer.play()
                _uiState.update {
                    it.copy(
                        isPlaying = true,
                        statusMessage = "Playing studymusic.mp3 — audio routes to connected headphones.",
                    )
                }
            }
        }
    }

    fun refreshBluetoothState() {
        val active = ensureScanner() ?: return
        _uiState.update {
            it.copy(
                bluetoothAvailable = active.isBluetoothAvailable(),
                bluetoothEnabled = active.isBluetoothEnabled(),
            )
        }
    }

    private fun ensureScanner(): BluetoothScanner? {
        if (!_uiState.value.hasPermissions) return null
        if (scanner != null) {
            refreshAdapterFlags()
            return scanner
        }

        scanner = BluetoothScanner(
            context = getApplication(),
            onDevicesChanged = { devices ->
                _uiState.update { it.copy(devices = devices) }
            },
            onScanningChanged = { scanning ->
                _uiState.update { it.copy(isScanning = scanning) }
            },
            onStatusChanged = { message ->
                _uiState.update { it.copy(statusMessage = message) }
                refreshAdapterFlags()
            },
            onDeviceBonded = { address ->
                _uiState.update { it.copy(selectedAddress = address) }
            },
        ).also {
            it.start()
            refreshAdapterFlags()
        }
        return scanner
    }

    private fun refreshAdapterFlags() {
        val active = scanner ?: return
        _uiState.update {
            it.copy(
                bluetoothAvailable = active.isBluetoothAvailable(),
                bluetoothEnabled = active.isBluetoothEnabled(),
            )
        }
    }

    override fun onCleared() {
        trackPlayer.release()
        scanner?.release()
        scanner = null
        super.onCleared()
    }
}
