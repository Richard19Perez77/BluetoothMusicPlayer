package com.rick.bluetoothmusicplayer.bluetooth

data class DiscoveredDevice(
    val name: String,
    val address: String,
    val isBonded: Boolean,
    val isAudioDevice: Boolean,
    val bondStateLabel: String,
)
