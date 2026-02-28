package com.lifo.chat.domain.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real-time audio output routing monitor.
 *
 * Detects whether headphones (wired, Bluetooth, USB) are connected and emits
 * routing changes as a StateFlow. Used to:
 *
 * 1. **Bypass AEC** when headphones are connected (no acoustic echo path)
 * 2. **Adjust VAD thresholds** — lower sensitivity with headphones (less noise)
 * 3. **Skip MODE_IN_COMMUNICATION** — MODE_NORMAL is fine with headphones
 * 4. **Diagnostic**: If echo disappears with headphones → confirms HW AEC failure
 *
 * Uses [AudioDeviceCallback] for instant plug/unplug detection.
 */
@Singleton
class HeadphoneDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    enum class AudioOutputRoute {
        SPEAKER,
        WIRED_HEADSET,
        BLUETOOTH_A2DP,
        BLUETOOTH_SCO,
        USB_HEADSET
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _currentRoute = MutableStateFlow(AudioOutputRoute.SPEAKER)
    val currentRoute: StateFlow<AudioOutputRoute> = _currentRoute.asStateFlow()

    private var deviceCallback: AudioDeviceCallback? = null
    private var isMonitoring = false

    val isHeadphoneConnected: Boolean
        get() = _currentRoute.value != AudioOutputRoute.SPEAKER

    fun startMonitoring() {
        if (isMonitoring) return

        // Check initial state
        updateCurrentRoute()

        // Register for real-time changes
        deviceCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                updateCurrentRoute()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                updateCurrentRoute()
            }
        }
        audioManager.registerAudioDeviceCallback(deviceCallback, null)
        isMonitoring = true
        println("[HeadphoneDetector] Monitoring started — current route: ${_currentRoute.value}")
    }

    fun stopMonitoring() {
        deviceCallback?.let { audioManager.unregisterAudioDeviceCallback(it) }
        deviceCallback = null
        isMonitoring = false
        println("[HeadphoneDetector] Monitoring stopped")
    }

    private fun updateCurrentRoute() {
        val outputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val previousRoute = _currentRoute.value

        _currentRoute.value = classifyRoute(outputDevices)

        if (_currentRoute.value != previousRoute) {
            println("[HeadphoneDetector] Route changed: $previousRoute → ${_currentRoute.value}")
        }
    }

    private fun classifyRoute(devices: Array<AudioDeviceInfo>): AudioOutputRoute {
        // Priority: Bluetooth A2DP > BT SCO > Wired > USB > Speaker
        // Check for active output devices (isSink = true for output devices)
        for (device in devices) {
            if (!device.isSink) continue
            when (device.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> return AudioOutputRoute.BLUETOOTH_A2DP
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> return AudioOutputRoute.BLUETOOTH_SCO
                else -> { /* continue */ }
            }
        }
        for (device in devices) {
            if (!device.isSink) continue
            when (device.type) {
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> return AudioOutputRoute.WIRED_HEADSET
                AudioDeviceInfo.TYPE_USB_HEADSET -> return AudioOutputRoute.USB_HEADSET
                else -> { /* continue */ }
            }
        }
        return AudioOutputRoute.SPEAKER
    }

    fun getDiagnostics(): Map<String, Any> = mapOf(
        "currentRoute" to _currentRoute.value.name,
        "isHeadphoneConnected" to isHeadphoneConnected,
        "isMonitoring" to isMonitoring,
        "outputDevices" to audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.isSink }
            .map { "${it.productName} (type=${it.type})" }
    )
}
