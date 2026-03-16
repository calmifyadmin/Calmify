package com.lifo.chat.domain.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.lifo.chat.domain.model.AudioDevice
import com.lifo.chat.domain.model.AudioDeviceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Real-time audio device monitor.
 *
 * Detects connected audio output devices and emits changes as StateFlow.
 * Provides both the current routing classification and a full list of
 * available devices for user selection.
 */
class HeadphoneDetector constructor(
    private val context: Context
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

    private val _availableDevices = MutableStateFlow<List<AudioDevice>>(emptyList())
    val availableDevices: StateFlow<List<AudioDevice>> = _availableDevices.asStateFlow()

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
        _availableDevices.value = buildDeviceList(outputDevices)

        if (_currentRoute.value != previousRoute) {
            println("[HeadphoneDetector] Route changed: $previousRoute → ${_currentRoute.value}")
            println("[HeadphoneDetector] Available devices: ${_availableDevices.value.map { it.name }}")
        }
    }

    private fun classifyRoute(devices: Array<AudioDeviceInfo>): AudioOutputRoute {
        // Priority: Bluetooth A2DP > BT SCO > Wired > USB > Speaker
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

    /**
     * Builds a user-friendly list of available audio output devices.
     * Filters out internal/system devices that aren't selectable.
     */
    private fun buildDeviceList(devices: Array<AudioDeviceInfo>): List<AudioDevice> {
        val result = mutableListOf<AudioDevice>()
        val currentRoute = _currentRoute.value

        // Always add speaker as first option
        val speakerDevice = devices.firstOrNull {
            it.isSink && it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        }
        result.add(AudioDevice(
            id = speakerDevice?.id ?: 0,
            name = "Speaker",
            type = AudioDeviceType.SPEAKER,
            isActive = currentRoute == AudioOutputRoute.SPEAKER
        ))

        // Add earpiece if available
        devices.firstOrNull {
            it.isSink && it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
        }?.let { earpiece ->
            result.add(AudioDevice(
                id = earpiece.id,
                name = "Earpiece",
                type = AudioDeviceType.EARPIECE,
                isActive = false // Earpiece is not auto-detected as a route
            ))
        }

        // Add external devices
        for (device in devices) {
            if (!device.isSink) continue
            val (deviceType, isActive) = when (device.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> AudioDeviceType.BLUETOOTH to (currentRoute == AudioOutputRoute.BLUETOOTH_A2DP)
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> AudioDeviceType.BLUETOOTH to (currentRoute == AudioOutputRoute.BLUETOOTH_SCO)
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> AudioDeviceType.WIRED_HEADSET to (currentRoute == AudioOutputRoute.WIRED_HEADSET)
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE -> AudioDeviceType.USB to (currentRoute == AudioOutputRoute.USB_HEADSET)
                else -> continue
            }
            val name = device.productName.toString().ifBlank {
                when (deviceType) {
                    AudioDeviceType.BLUETOOTH -> "Bluetooth"
                    AudioDeviceType.WIRED_HEADSET -> "Wired Headset"
                    AudioDeviceType.USB -> "USB Audio"
                    else -> "Unknown"
                }
            }
            // Avoid duplicates (e.g., same BT device showing as both A2DP and SCO)
            if (result.none { it.name == name && it.type == deviceType }) {
                result.add(AudioDevice(
                    id = device.id,
                    name = name,
                    type = deviceType,
                    isActive = isActive
                ))
            }
        }

        return result
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
