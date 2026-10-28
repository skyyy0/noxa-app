package com.example.media

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** One audio output device, as Android reports it to the app. */
data class AudioOutputDevice(
    /** Marketing name of the device (\"TWS Pro\", \"Galaxy Buds2\" …). */
    val name: String,
    /** One of the [AudioDeviceInfo] TYPE_* constants. */
    val type: Int
) {
    /** True for the classic A2DP path most TWS earbuds use. */
    val isA2dp: Boolean
        get() = type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP

    /** Short label for the tuning popup. */
    val kindLabel: String
        get() = when (type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "TWS / Bluetooth"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth"
            AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Bluetooth LE"
            else -> "Nirkabel"
        }

    /** Stable key for once-per-device deduplication. */
    val key: String
        get() = "$type|$name"
}

/**
 * Watches the audio **output** side of the device and reports every wireless
 * connection (TWS earbuds, headphones, speakers, LE audio) exactly when it
 * happens — including one that was already connected when NOXA launched.
 *
 * Deliberately built on [AudioManager.registerAudioDeviceCallback] instead of
 * Bluetooth broadcasts: it needs **no Bluetooth permission** (NOXA has never
 * asked for one), it sees every route audio can actually take — including
 * devices that only show up as a USB dongle or LE endpoint — and it reports
 * the product name straight from the platform.
 */
class OutputDeviceWatcher(context: Context) {

    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _connections = MutableSharedFlow<AudioOutputDevice>(extraBufferCapacity = 8)
    /**
     * Emits once per wireless output that becomes available. The first value
     * usually arrives immediately with whatever was already connected.
     */
    val connections: SharedFlow<AudioOutputDevice> = _connections.asSharedFlow()

    private val _active = MutableStateFlow<AudioOutputDevice?>(null)
    /** The wireless output currently carrying audio, or null. */
    val active: StateFlow<AudioOutputDevice?> = _active.asStateFlow()

    private var started = false

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            addedDevices.forEach { device ->
                val output = device.toOutputDeviceOrNull() ?: return@forEach
                _active.value = output
                _connections.tryEmit(output)
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            val stillWireless = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .mapNotNull { it.toOutputDeviceOrNull() }
            _active.value = stillWireless.lastOrNull()
        }
    }

    /** Safe to call more than once; registration happens on the main looper. */
    fun start() {
        if (started) return
        started = true
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
    }

    fun stop() {
        if (!started) return
        started = false
        audioManager.unregisterAudioDeviceCallback(callback)
    }

    private fun AudioDeviceInfo.toOutputDeviceOrNull(): AudioOutputDevice? {
        if (!isSink || !isWirelessOutput) return null
        val name = productName?.toString()?.trim().orEmpty()
            .ifEmpty { "Perangkat nirkabel" }
        return AudioOutputDevice(name = name, type = type)
    }

    private val AudioDeviceInfo.isWirelessOutput: Boolean
        get() = type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            // BLE constants are compile-time ints: inlined on older APIs, so
            // referencing them is safe from minSdk 24 upward.
            type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            type == AudioDeviceInfo.TYPE_BLE_SPEAKER
}
