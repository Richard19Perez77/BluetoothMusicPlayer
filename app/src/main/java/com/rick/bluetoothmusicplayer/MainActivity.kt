package com.rick.bluetoothmusicplayer

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rick.bluetoothmusicplayer.ui.BluetoothPlayerScreen
import com.rick.bluetoothmusicplayer.ui.theme.BluetoothMusicPlayerTheme

class MainActivity : ComponentActivity() {
    private val viewModel: BluetoothPlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BluetoothMusicPlayerTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val context = LocalContext.current

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions(),
                ) { results ->
                    val granted = results.values.all { it }
                    viewModel.onPermissionsResult(granted)
                }

                fun requiredPermissions(): Array<String> =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        arrayOf(
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT,
                        )
                    } else {
                        arrayOf(
                            Manifest.permission.BLUETOOTH,
                            Manifest.permission.BLUETOOTH_ADMIN,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                        )
                    }

                fun hasAllPermissions(): Boolean =
                    requiredPermissions().all { permission ->
                        ContextCompat.checkSelfPermission(
                            context,
                            permission,
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    }

                LaunchedEffect(Unit) {
                    if (hasAllPermissions()) {
                        viewModel.onPermissionsResult(true)
                    }
                }

                BluetoothPlayerScreen(
                    state = state,
                    onRequestPermissions = {
                        permissionLauncher.launch(requiredPermissions())
                    },
                    onScanClick = viewModel::startScan,
                    onStopScanClick = viewModel::stopScan,
                    onDeviceClick = { device ->
                        viewModel.selectDevice(device.address)
                    },
                    onPlayClick = viewModel::togglePlayback,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshBluetoothState()
    }
}
