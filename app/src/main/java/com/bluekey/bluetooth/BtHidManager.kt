package com.bluekey.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import java.util.concurrent.Executors

@SuppressLint("MissingPermission")
class BtHidManager(private val context: Context) : HidSender {

    companion object {
        private const val TAG = "BtHidManager"
        private const val REPORT_ID_KEYBOARD = 1
        private const val REPORT_ID_MOUSE = 2
        private const val REPORT_ID_CONSUMER = 3
        private const val REPORT_ID_GAMEPAD = 4
    }

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    override val isConnected: Boolean get() = connectedDevice != null
    val connectedDeviceName: String get() = connectedDevice?.name ?: ""
    override val connectedLabel: String get() = if (isConnected) "BT: ${connectedDevice?.name}" else ""

    // Callbacks set by UI layers
    var onRegistered: ((Boolean) -> Unit)? = null
    var onConnectionChanged: ((BluetoothDevice?, Int) -> Unit)? = null

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.d(TAG, "onAppStatusChanged: registered=$registered, device=$pluggedDevice")
            onRegistered?.invoke(registered)
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            Log.d(TAG, "onConnectionStateChanged: device=${device?.name}, state=$state")
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevice = device
                    onConnectionChanged?.invoke(device, state)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedDevice = null
                    onConnectionChanged?.invoke(device, state)
                }
                else -> onConnectionChanged?.invoke(device, state)
            }
        }

        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            Log.d(TAG, "onGetReport: type=$type, id=$id")
            hidDevice?.replyReport(device, type, id, ByteArray(bufferSize))
        }

        override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) {
            Log.d(TAG, "onSetReport: type=$type, id=$id")
            hidDevice?.reportError(device, BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ)
        }

        override fun onInterruptData(device: BluetoothDevice?, reportId: Byte, data: ByteArray?) {
            // Host sent data to us (e.g., LED state for keyboard)
        }
    }

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            Log.d(TAG, "HID service connected")
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = proxy as? BluetoothHidDevice
                registerApp()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            Log.d(TAG, "HID service disconnected")
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                connectedDevice = null
                onRegistered?.invoke(false)
            }
        }
    }

    private fun registerApp() {
        val sdpSettings = BluetoothHidDeviceAppSdpSettings(
            "BlueKey HID",
            "Bluetooth HID Peripheral",
            "BlueKey",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            HidDescriptors.COMBINED_DESCRIPTOR
        )
        val executor = Executors.newCachedThreadPool()
        val registered = hidDevice?.registerApp(sdpSettings, null, null, executor, hidCallback)
        Log.d(TAG, "registerApp result: $registered")
    }

    fun connect() {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            Log.e(TAG, "Bluetooth not available")
            onRegistered?.invoke(false)
            return
        }
        if (!adapter.isEnabled) {
            Log.e(TAG, "Bluetooth not enabled")
            onRegistered?.invoke(false)
            return
        }
        val success = adapter.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE)
        Log.d(TAG, "getProfileProxy result: $success")
    }

    fun connectToDevice(device: BluetoothDevice) {
        val hid = hidDevice
        if (hid == null) {
            Log.e(TAG, "HID device profile not available")
            return
        }
        Log.d(TAG, "Connecting to device: ${device.name}")
        hid.connect(device)
    }

    fun disconnect() {
        val device = connectedDevice ?: return
        hidDevice?.disconnect(device)
    }

    fun close() {
        hidDevice?.unregisterApp()
        bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        hidDevice = null
        connectedDevice = null
    }

    // ---- Report sending methods ----

    override fun sendKeyboardReport(modifiers: Byte, keys: ByteArray) {
        // 8-byte keyboard report: modifier, reserved, key[0..5]
        val report = ByteArray(8)
        report[0] = modifiers
        report[1] = 0x00 // reserved
        for (i in 0 until minOf(keys.size, 6)) {
            report[2 + i] = keys[i]
        }
        sendReport(REPORT_ID_KEYBOARD, report)
    }

    override fun sendKeyboardRelease() {
        sendReport(REPORT_ID_KEYBOARD, ByteArray(8))
    }

    override fun sendMouseReport(buttons: Byte, dx: Int, dy: Int, wheel: Int) {
        val report = ByteArray(4)
        report[0] = buttons
        report[1] = dx.coerceIn(-127, 127).toByte()
        report[2] = dy.coerceIn(-127, 127).toByte()
        report[3] = wheel.coerceIn(-127, 127).toByte()
        sendReport(REPORT_ID_MOUSE, report)
    }

    override fun sendMouseRelease() {
        sendReport(REPORT_ID_MOUSE, ByteArray(4))
    }

    override fun sendConsumerReport(usage: Int) {
        val report = ByteArray(2)
        report[0] = (usage and 0xFF).toByte()
        report[1] = ((usage shr 8) and 0xFF).toByte()
        sendReport(REPORT_ID_CONSUMER, report)
    }

    override fun sendConsumerRelease() {
        sendReport(REPORT_ID_CONSUMER, ByteArray(2))
    }

    override fun sendGamepadReport(buttons: Int, lx: Int, ly: Int, rx: Int, ry: Int) {
        val report = ByteArray(6)
        report[0] = (buttons and 0xFF).toByte()
        report[1] = ((buttons shr 8) and 0xFF).toByte()
        report[2] = lx.coerceIn(-127, 127).toByte()
        report[3] = ly.coerceIn(-127, 127).toByte()
        report[4] = rx.coerceIn(-127, 127).toByte()
        report[5] = ry.coerceIn(-127, 127).toByte()
        sendReport(REPORT_ID_GAMEPAD, report)
    }

    override fun sendGamepadRelease() {
        sendReport(REPORT_ID_GAMEPAD, ByteArray(6))
    }

    private fun sendReport(id: Int, data: ByteArray) {
        val device = connectedDevice ?: return
        val hid = hidDevice ?: return
        try {
            hid.sendReport(device, id, data)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send report id=$id: ${e.message}")
        }
    }
}
