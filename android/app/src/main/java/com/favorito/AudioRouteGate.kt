package com.favorito

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/**
 * Runs once per prompt attempt, never in a background loop.
 *
 * Android does not expose a perfect per-stream "currently routed to speaker"
 * check for every API/OEM combination. This gate is conservative: it allows
 * prompting only when an output commonly used for private/listening contexts is
 * connected, and it rejects the built-in speaker-only case.
 */
object AudioRouteGate {
    fun isPrivateOrVehicleOutputAvailable(context: Context): Boolean {
        val audio = context.getSystemService(AudioManager::class.java)

        if (!audio.isMusicActive) return false

        @Suppress("DEPRECATION")
        val routedToPrivateOutput =
            audio.isWiredHeadsetOn || audio.isBluetoothA2dpOn || audio.isBluetoothScoOn
        if (routedToPrivateOutput) return true

        val outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val hasAllowedOutput = outputs.any { it.isAllowedMusicOutput() }
        val hasOnlySpeaker = outputs.none { it.isAllowedMusicOutput() } &&
            outputs.any { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

        return hasAllowedOutput && !hasOnlySpeaker
    }

    private fun AudioDeviceInfo.isAllowedMusicOutput(): Boolean {
        return when (type) {
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_USB_HEADSET -> true
            else -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                (type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                    type == AudioDeviceInfo.TYPE_BLE_BROADCAST)
        }
    }
}
