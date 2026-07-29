package com.bluekey.bluetooth

interface HidSender {
    val isConnected: Boolean
    val connectedLabel: String

    fun sendKeyboardReport(modifiers: Byte, keys: ByteArray)
    fun sendKeyboardRelease()
    fun sendMouseReport(buttons: Byte, dx: Int, dy: Int, wheel: Int)
    fun sendMouseRelease()
    fun sendConsumerReport(usage: Int)
    fun sendConsumerRelease()
    fun sendGamepadReport(buttons: Int, lx: Int, ly: Int, rx: Int, ry: Int)
    fun sendGamepadRelease()
}
