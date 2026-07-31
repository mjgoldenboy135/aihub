package com.bluekey.ui

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.bluekey.MainActivity
import com.bluekey.databinding.FragmentGamepadBinding
import com.google.android.material.snackbar.Snackbar

class GamepadFragment : Fragment() {

    private var _binding: FragmentGamepadBinding? = null
    private val binding get() = _binding!!

    // Button bitmask — each bit corresponds to a gamepad button (1-indexed from bit 0)
    private var currentButtons: Int = 0
    private var leftX: Int = 0
    private var leftY: Int = 0
    private var rightX: Int = 0
    private var rightY: Int = 0

    // Button bit indices (0-based)
    companion object {
        const val BTN_A = 0
        const val BTN_B = 1
        const val BTN_X = 2
        const val BTN_Y = 3
        const val BTN_L1 = 4
        const val BTN_L2 = 5
        const val BTN_R1 = 6
        const val BTN_R2 = 7
        const val BTN_DPAD_UP = 8
        const val BTN_DPAD_DOWN = 9
        const val BTN_DPAD_LEFT = 10
        const val BTN_DPAD_RIGHT = 11
        const val BTN_SELECT = 12
        const val BTN_START = 13
        const val BTN_HOME = 14
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGamepadBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Left joystick
        binding.joystickLeft.onJoystickMoved = { x, y ->
            leftX = x
            leftY = y
            sendGamepad()
        }

        // Right joystick
        binding.joystickRight.onJoystickMoved = { x, y ->
            rightX = x
            rightY = y
            sendGamepad()
        }

        // Face buttons
        setupGamepadButton(binding.btnA, BTN_A)
        setupGamepadButton(binding.btnB, BTN_B)
        setupGamepadButton(binding.btnX, BTN_X)
        setupGamepadButton(binding.btnY, BTN_Y)

        // Shoulder buttons
        setupGamepadButton(binding.btnL1, BTN_L1)
        setupGamepadButton(binding.btnL2, BTN_L2)
        setupGamepadButton(binding.btnR1, BTN_R1)
        setupGamepadButton(binding.btnR2, BTN_R2)

        // D-pad
        setupGamepadButton(binding.btnDpadUp, BTN_DPAD_UP)
        setupGamepadButton(binding.btnDpadDown, BTN_DPAD_DOWN)
        setupGamepadButton(binding.btnDpadLeft, BTN_DPAD_LEFT)
        setupGamepadButton(binding.btnDpadRight, BTN_DPAD_RIGHT)

        // Center buttons
        setupGamepadButton(binding.btnSelect, BTN_SELECT)
        setupGamepadButton(binding.btnStart, BTN_START)
        setupGamepadButton(binding.btnHome, BTN_HOME)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGamepadButton(btn: Button, bitIndex: Int) {
        val originalTint = btn.backgroundTintList
        btn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!isConnected()) {
                        Snackbar.make(binding.root, "Not connected", Snackbar.LENGTH_SHORT).show()
                        return@setOnTouchListener true
                    }
                    currentButtons = currentButtons or (1 shl bitIndex)
                    btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#BB86FC"))
                    sendGamepad()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    currentButtons = currentButtons and (1 shl bitIndex).inv()
                    btn.backgroundTintList = originalTint
                    sendGamepad()
                    true
                }
                else -> false
            }
        }
    }

    private fun sendGamepad() {
        val mgr = (activity as? MainActivity)?.hidSender ?: return
        mgr.sendGamepadReport(currentButtons, leftX, leftY, rightX, rightY)
    }

    private fun isConnected(): Boolean {
        return (activity as? MainActivity)?.hidSender?.isConnected == true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val mgr = (activity as? MainActivity)?.hidSender
        mgr?.sendGamepadRelease()
        _binding = null
    }
}
