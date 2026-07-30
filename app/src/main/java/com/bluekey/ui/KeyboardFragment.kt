package com.bluekey.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bluekey.MainActivity
import com.bluekey.bluetooth.KeyCodes
import com.bluekey.databinding.FragmentKeyboardBinding
import com.google.android.material.snackbar.Snackbar

class KeyboardFragment : Fragment() {

    private var _binding: FragmentKeyboardBinding? = null
    private val binding get() = _binding!!

    private var isShifted = false
    private var isCapsLock = false
    private var isSymbols = false

    private var lastShiftTapTime = 0L
    private val doubleTapInterval = 400L

    // KeyDef holds display label, keyCode, modifier byte, and weight for layout
    data class KeyDef(
        val label: String,
        val keyCode: Int,
        val modifier: Int = 0,
        val weight: Float = 1f,
        val isSpecial: Boolean = false
    )

    private val letterRows: List<List<KeyDef>> = listOf(
        listOf(
            KeyDef("q", KeyCodes.KEY_Q), KeyDef("w", KeyCodes.KEY_W),
            KeyDef("e", KeyCodes.KEY_E), KeyDef("r", KeyCodes.KEY_R),
            KeyDef("t", KeyCodes.KEY_T), KeyDef("y", KeyCodes.KEY_Y),
            KeyDef("u", KeyCodes.KEY_U), KeyDef("i", KeyCodes.KEY_I),
            KeyDef("o", KeyCodes.KEY_O), KeyDef("p", KeyCodes.KEY_P)
        ),
        listOf(
            KeyDef("a", KeyCodes.KEY_A), KeyDef("s", KeyCodes.KEY_S),
            KeyDef("d", KeyCodes.KEY_D), KeyDef("f", KeyCodes.KEY_F),
            KeyDef("g", KeyCodes.KEY_G), KeyDef("h", KeyCodes.KEY_H),
            KeyDef("j", KeyCodes.KEY_J), KeyDef("k", KeyCodes.KEY_K),
            KeyDef("l", KeyCodes.KEY_L)
        ),
        listOf(
            KeyDef("Shift", 0, 0, 1.5f, isSpecial = true),
            KeyDef("z", KeyCodes.KEY_Z), KeyDef("x", KeyCodes.KEY_X),
            KeyDef("c", KeyCodes.KEY_C), KeyDef("v", KeyCodes.KEY_V),
            KeyDef("b", KeyCodes.KEY_B), KeyDef("n", KeyCodes.KEY_N),
            KeyDef("m", KeyCodes.KEY_M),
            KeyDef("⌫", KeyCodes.KEY_BACKSPACE, 0, 1.5f, isSpecial = true)
        ),
        listOf(
            KeyDef("123", 0, 0, 1.5f, isSpecial = true),
            KeyDef(",", KeyCodes.KEY_COMMA),
            KeyDef("Space", KeyCodes.KEY_SPACE, 0, 5f),
            KeyDef(".", KeyCodes.KEY_PERIOD),
            KeyDef("↵", KeyCodes.KEY_ENTER, 0, 1.5f, isSpecial = true)
        )
    )

    private val symbolRows: List<List<KeyDef>> = listOf(
        listOf(
            KeyDef("1", KeyCodes.KEY_1), KeyDef("2", KeyCodes.KEY_2),
            KeyDef("3", KeyCodes.KEY_3), KeyDef("4", KeyCodes.KEY_4),
            KeyDef("5", KeyCodes.KEY_5), KeyDef("6", KeyCodes.KEY_6),
            KeyDef("7", KeyCodes.KEY_7), KeyDef("8", KeyCodes.KEY_8),
            KeyDef("9", KeyCodes.KEY_9), KeyDef("0", KeyCodes.KEY_0)
        ),
        listOf(
            KeyDef("!", KeyCodes.KEY_1, KeyCodes.MOD_LEFT_SHIFT),
            KeyDef("@", KeyCodes.KEY_2, KeyCodes.MOD_LEFT_SHIFT),
            KeyDef("#", KeyCodes.KEY_3, KeyCodes.MOD_LEFT_SHIFT),
            KeyDef("$", KeyCodes.KEY_4, KeyCodes.MOD_LEFT_SHIFT),
            KeyDef("%", KeyCodes.KEY_5, KeyCodes.MOD_LEFT_SHIFT),
            KeyDef("^", KeyCodes.KEY_6, KeyCodes.MOD_LEFT_SHIFT),
            KeyDef("&", KeyCodes.KEY_7, KeyCodes.MOD_LEFT_SHIFT),
            KeyDef("*", KeyCodes.KEY_8, KeyCodes.MOD_LEFT_SHIFT),
            KeyDef("(", KeyCodes.KEY_9, KeyCodes.MOD_LEFT_SHIFT),
            KeyDef(")", KeyCodes.KEY_0, KeyCodes.MOD_LEFT_SHIFT)
        ),
        listOf(
            KeyDef("-", KeyCodes.KEY_MINUS),
            KeyDef("_", KeyCodes.KEY_MINUS, KeyCodes.MOD_LEFT_SHIFT),
            KeyDef("=", KeyCodes.KEY_EQUALS),
            KeyDef("+", KeyCodes.KEY_EQUALS, KeyCodes.MOD_LEFT_SHIFT),
            KeyDef("[", KeyCodes.KEY_LEFT_BRACKET),
            KeyDef("]", KeyCodes.KEY_RIGHT_BRACKET),
            KeyDef("{", KeyCodes.KEY_LEFT_BRACKET, KeyCodes.MOD_LEFT_SHIFT),
            KeyDef("}", KeyCodes.KEY_RIGHT_BRACKET, KeyCodes.MOD_LEFT_SHIFT),
            KeyDef("\\", KeyCodes.KEY_BACKSLASH),
            KeyDef("|", KeyCodes.KEY_BACKSLASH, KeyCodes.MOD_LEFT_SHIFT)
        ),
        listOf(
            KeyDef("ABC", 0, 0, 1.5f, isSpecial = true),
            KeyDef(";", KeyCodes.KEY_SEMICOLON),
            KeyDef("Space", KeyCodes.KEY_SPACE, 0, 5f),
            KeyDef(":", KeyCodes.KEY_SEMICOLON, KeyCodes.MOD_LEFT_SHIFT),
            KeyDef("↵", KeyCodes.KEY_ENTER, 0, 1.5f, isSpecial = true)
        )
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentKeyboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateConnectionStatus()
        buildKeyboard()
    }

    private fun updateConnectionStatus() {
        val mgr = (activity as? MainActivity)?.hidSender
        if (mgr?.isConnected == true) {
            binding.tvConnectionDot.setTextColor(Color.parseColor("#4CAF50"))
            binding.tvConnectionDot.text = "●"
            binding.tvConnectionLabel.text = "Connected: ${mgr.connectedLabel}"
        } else {
            binding.tvConnectionDot.setTextColor(Color.parseColor("#F44336"))
            binding.tvConnectionDot.text = "●"
            binding.tvConnectionLabel.text = "Not connected"
        }
    }

    private fun buildKeyboard() {
        binding.keyboardContainer.removeAllViews()
        val rows = if (isSymbols) symbolRows else letterRows
        for (row in rows) {
            val rowLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            }
            for (keyDef in row) {
                val btn = createKeyButton(keyDef)
                rowLayout.addView(btn)
            }
            binding.keyboardContainer.addView(rowLayout)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createKeyButton(keyDef: KeyDef): Button {
        val btn = Button(requireContext()).apply {
            text = getKeyLabel(keyDef)
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            isAllCaps = false

            val bg = GradientDrawable().apply {
                setColor(Color.parseColor(if (keyDef.isSpecial) "#3A3A3A" else "#2A2A2A"))
                cornerRadius = 8f * resources.displayMetrics.density
            }
            background = bg

            val params = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                keyDef.weight
            ).apply {
                val margin = (4 * resources.displayMetrics.density).toInt()
                setMargins(margin, margin, margin, margin)
            }
            layoutParams = params
        }

        btn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    handleKeyDown(keyDef, btn)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handleKeyUp(keyDef)
                    true
                }
                else -> false
            }
        }

        return btn
    }

    private fun getKeyLabel(keyDef: KeyDef): String {
        if (keyDef.isSpecial) return keyDef.label
        if (isSymbols) return keyDef.label
        return when {
            isShifted || isCapsLock -> keyDef.label.uppercase()
            else -> keyDef.label.lowercase()
        }
    }

    private fun handleKeyDown(keyDef: KeyDef, btn: Button) {
        val mgr = (activity as? MainActivity)?.hidSender
        if (mgr?.isConnected != true) {
            Snackbar.make(binding.root, "Not connected", Snackbar.LENGTH_SHORT).show()
            return
        }

        when (keyDef.label) {
            "Shift" -> {
                val now = System.currentTimeMillis()
                if (now - lastShiftTapTime < doubleTapInterval) {
                    // Double tap = caps lock
                    isCapsLock = !isCapsLock
                    isShifted = false
                } else {
                    isShifted = !isShifted
                }
                lastShiftTapTime = now
                buildKeyboard()
                return
            }
            "ABC" -> {
                isSymbols = false
                buildKeyboard()
                return
            }
            "123" -> {
                isSymbols = true
                buildKeyboard()
                return
            }
            else -> {
                val effectiveMod = if ((isShifted || isCapsLock) && keyDef.modifier == 0 && !isSymbols) {
                    KeyCodes.MOD_LEFT_SHIFT
                } else {
                    keyDef.modifier
                }
                mgr.sendKeyboardReport(
                    effectiveMod.toByte(),
                    byteArrayOf(keyDef.keyCode.toByte())
                )
            }
        }
    }

    private fun handleKeyUp(keyDef: KeyDef) {
        if (keyDef.isSpecial && (keyDef.label == "Shift" || keyDef.label == "ABC" || keyDef.label == "123")) {
            return
        }
        val mgr = (activity as? MainActivity)?.hidSender
        mgr?.sendKeyboardRelease()

        // One-shot shift: auto-clear after a key press
        if (isShifted && !keyDef.isSpecial) {
            isShifted = false
            buildKeyboard()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
