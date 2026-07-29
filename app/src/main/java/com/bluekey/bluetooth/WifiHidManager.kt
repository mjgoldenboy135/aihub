package com.bluekey.bluetooth

import android.os.Handler
import android.os.Looper
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket

class WifiHidManager : HidSender {

    private var socket: Socket? = null
    private var writer: PrintWriter? = null

    override var isConnected: Boolean = false
        private set
    override var connectedLabel: String = ""
        private set

    var onConnectionChanged: ((Boolean, String) -> Unit)? = null

    fun connect(host: String, port: Int = 8080) {
        Thread {
            try {
                val s = Socket()
                s.connect(InetSocketAddress(host, port), 5_000)
                s.keepAlive = true
                socket = s
                writer = PrintWriter(BufferedWriter(OutputStreamWriter(s.outputStream)), true)
                isConnected = true
                connectedLabel = "WiFi: $host"
                postMain { onConnectionChanged?.invoke(true, host) }
            } catch (e: Exception) {
                isConnected = false
                postMain { onConnectionChanged?.invoke(false, e.message ?: "Connection failed") }
            }
        }.start()
    }

    fun disconnect() {
        runCatching { socket?.close() }
        socket = null
        writer = null
        isConnected = false
        connectedLabel = ""
        postMain { onConnectionChanged?.invoke(false, "Disconnected") }
    }

    private fun send(json: String) {
        if (!isConnected) return
        Thread {
            try {
                writer?.println(json)
            } catch (e: Exception) {
                isConnected = false
                postMain { onConnectionChanged?.invoke(false, "Connection lost") }
            }
        }.start()
    }

    override fun sendKeyboardReport(modifiers: Byte, keys: ByteArray) {
        val keysJson = keys.joinToString(",") { (it.toInt() and 0xFF).toString() }
        send("""{"type":"keyboard","modifier":${modifiers.toInt() and 0xFF},"keys":[$keysJson]}""")
    }

    override fun sendKeyboardRelease() {
        send("""{"type":"keyboard","modifier":0,"keys":[0,0,0,0,0,0]}""")
    }

    override fun sendMouseReport(buttons: Byte, dx: Int, dy: Int, wheel: Int) {
        send("""{"type":"mouse","buttons":${buttons.toInt() and 0xFF},"dx":$dx,"dy":$dy,"wheel":$wheel}""")
    }

    override fun sendMouseRelease() {
        send("""{"type":"mouse","buttons":0,"dx":0,"dy":0,"wheel":0}""")
    }

    override fun sendConsumerReport(usage: Int) {
        send("""{"type":"consumer","usage":$usage}""")
    }

    override fun sendConsumerRelease() {
        send("""{"type":"consumer","usage":0}""")
    }

    override fun sendGamepadReport(buttons: Int, lx: Int, ly: Int, rx: Int, ry: Int) {
        send("""{"type":"gamepad","buttons":$buttons,"lx":$lx,"ly":$ly,"rx":$rx,"ry":$ry}""")
    }

    override fun sendGamepadRelease() {
        send("""{"type":"gamepad","buttons":0,"lx":0,"ly":0,"rx":0,"ry":0}""")
    }

    private fun postMain(block: () -> Unit) = Handler(Looper.getMainLooper()).post(block)
}
