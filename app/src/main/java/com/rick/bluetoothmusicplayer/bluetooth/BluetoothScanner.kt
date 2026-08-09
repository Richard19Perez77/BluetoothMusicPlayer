package com.rick.bluetoothmusicplayer.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

/**
 * Wraps classic Bluetooth discovery so the UI can list nearby / paired devices.
 *
 * Headphones usually show up as AUDIO_VIDEO class devices. Playback itself is handled
 * by MediaPlayer — Android routes audio to an active A2DP headset automatically.
 */
class BluetoothScanner(
    private val context: Context,
    private val onDevicesChanged: (List<DiscoveredDevice>) -> Unit,
    private val onScanningChanged: (Boolean) -> Unit,
    private val onStatusChanged: (String) -> Unit,
    private val onDeviceBonded: (String) -> Unit,
) {
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter

    private val devicesByAddress = linkedMapOf<String, DiscoveredDevice>()
    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.parcelableDevice() ?: return
                    upsertDevice(device)
                }

                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    onScanningChanged(true)
                    onStatusChanged("Scanning for nearby devices…")
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    onScanningChanged(false)
                    onStatusChanged("Scan finished — tap a device to pair/connect.")
                }

                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = intent.parcelableDevice() ?: return
                    upsertDevice(device)
                    if (device.bondState == BluetoothDevice.BOND_BONDED) {
                        onDeviceBonded(device.address)
                        onStatusChanged("Paired with ${device.safeName()}. Ready to play.")
                    }
                }

                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR,
                    )
                    if (state == BluetoothAdapter.STATE_ON) {
                        onStatusChanged("Bluetooth is on.")
                        publishBondedDevices()
                    } else if (state == BluetoothAdapter.STATE_OFF) {
                        devicesByAddress.clear()
                        publish()
                        onStatusChanged("Bluetooth is off.")
                    }
                }
            }
        }
    }

    fun isBluetoothAvailable(): Boolean = adapter != null

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun start() {
        registerReceiverIfNeeded()
        publishBondedDevices()
        if (adapter?.isEnabled != true) {
            onStatusChanged("Turn on Bluetooth to scan for headphones.")
        } else {
            onStatusChanged("Ready — tap Scan to find headphones.")
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val bt = adapter
        if (bt == null) {
            onStatusChanged("This device has no Bluetooth adapter.")
            return
        }
        if (!bt.isEnabled) {
            onStatusChanged("Turn on Bluetooth first.")
            return
        }

        registerReceiverIfNeeded()

        // Keep already-bonded devices visible while refreshing nearby ones
        devicesByAddress.keys
            .filterNot { address ->
                bt.bondedDevices.any { it.address == address }
            }
            .toList()
            .forEach { devicesByAddress.remove(it) }
        publishBondedDevices()

        if (bt.isDiscovering) {
            bt.cancelDiscovery()
        }
        val started = bt.startDiscovery()
        if (!started) {
            onScanningChanged(false)
            onStatusChanged("Could not start discovery. Check permissions.")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        adapter?.takeIf { it.isDiscovering }?.cancelDiscovery()
        onScanningChanged(false)
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        val bt = adapter ?: return
        val device = bt.getRemoteDevice(address)
        stopScan()

        when (device.bondState) {
            BluetoothDevice.BOND_BONDED -> {
                onDeviceBonded(address)
                onStatusChanged(
                    "Already paired with ${device.safeName()}. " +
                        "Make sure the headphones are connected in system Bluetooth, then play.",
                )
            }

            BluetoothDevice.BOND_BONDING -> {
                onStatusChanged("Pairing with ${device.safeName()}…")
            }

            else -> {
                onStatusChanged("Pairing with ${device.safeName()}…")
                val started = device.createBond()
                if (!started) {
                    onStatusChanged("Could not start pairing with ${device.safeName()}.")
                }
            }
        }
        upsertDevice(device)
    }

    fun release() {
        stopScan()
        if (receiverRegistered) {
            context.unregisterReceiver(receiver)
            receiverRegistered = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun publishBondedDevices() {
        val bonded = adapter?.bondedDevices.orEmpty()
        bonded.forEach { upsertDevice(it, publish = false) }
        publish()
    }

    @SuppressLint("MissingPermission")
    private fun upsertDevice(device: BluetoothDevice, publish: Boolean = true) {
        val entry = DiscoveredDevice(
            name = device.safeName(),
            address = device.address,
            isBonded = device.bondState == BluetoothDevice.BOND_BONDED,
            isAudioDevice = device.isLikelyAudioDevice(),
            bondStateLabel = when (device.bondState) {
                BluetoothDevice.BOND_BONDED -> "Paired"
                BluetoothDevice.BOND_BONDING -> "Pairing…"
                else -> "Available"
            },
        )
        devicesByAddress[device.address] = entry
        if (publish) publish()
    }

    private fun publish() {
        val sorted = devicesByAddress.values
            .sortedWith(
                compareByDescending<DiscoveredDevice> { it.isBonded }
                    .thenByDescending { it.isAudioDevice }
                    .thenBy { it.name.lowercase() },
            )
        onDevicesChanged(sorted)
    }

    private fun registerReceiverIfNeeded() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        receiverRegistered = true
    }
}

@SuppressLint("MissingPermission")
private fun BluetoothDevice.safeName(): String =
    name?.takeIf { it.isNotBlank() } ?: "Unknown device"

private fun BluetoothDevice.isLikelyAudioDevice(): Boolean {
    val major = bluetoothClass?.majorDeviceClass ?: return false
    return major == BluetoothClass.Device.Major.AUDIO_VIDEO
}

@Suppress("DEPRECATION")
private fun Intent.parcelableDevice(): BluetoothDevice? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
    } else {
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
    }
