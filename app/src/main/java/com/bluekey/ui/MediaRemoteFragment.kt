package com.bluekey.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.bluekey.MainActivity
import com.bluekey.bluetooth.KeyCodes
import com.bluekey.databinding.FragmentMediaBinding
import com.google.android.material.snackbar.Snackbar

class MediaRemoteFragment : Fragment() {

    private var _binding: FragmentMediaBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMediaBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Row 1: PREV, PLAY/PAUSE, NEXT
        setupConsumerButton(binding.btnPrev, KeyCodes.CONSUMER_PREV)
        setupConsumerButton(binding.btnPlayPause, KeyCodes.CONSUMER_PLAY_PAUSE)
        setupConsumerButton(binding.btnNext, KeyCodes.CONSUMER_NEXT)

        // Row 2: MUTE, VOL DOWN, VOL UP
        setupConsumerButton(binding.btnMute, KeyCodes.CONSUMER_MUTE)
        setupConsumerButton(binding.btnVolDown, KeyCodes.CONSUMER_VOL_DOWN)
        setupConsumerButton(binding.btnVolUp, KeyCodes.CONSUMER_VOL_UP)

        // Row 3: BRIGHTNESS DOWN, STOP, BRIGHTNESS UP
        setupConsumerButton(binding.btnBrightDown, KeyCodes.CONSUMER_BRIGHTNESS_DOWN)
        setupConsumerButton(binding.btnStop, KeyCodes.CONSUMER_STOP)
        setupConsumerButton(binding.btnBrightUp, KeyCodes.CONSUMER_BRIGHTNESS_UP)

        // Row 4: BACK (ALT+LEFT), HOME (SUPER/GUI), FORWARD (ALT+RIGHT)
        setupKeyboardButton(
            binding.btnBack,
            KeyCodes.MOD_LEFT_ALT.toByte(),
            byteArrayOf(KeyCodes.KEY_LEFT.toByte())
        )
        setupGuiButton(binding.btnHome)
        setupKeyboardButton(
            binding.btnForward,
            KeyCodes.MOD_LEFT_ALT.toByte(),
            byteArrayOf(KeyCodes.KEY_RIGHT.toByte())
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupConsumerButton(btn: Button, usage: Int) {
        btn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!isConnected()) {
                        Snackbar.make(binding.root, "Not connected", Snackbar.LENGTH_SHORT).show()
                        return@setOnTouchListener true
                    }
                    (activity as? MainActivity)?.btHidManager?.sendConsumerReport(usage)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    (activity as? MainActivity)?.btHidManager?.sendConsumerRelease()
                    true
                }
                else -> false
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupKeyboardButton(btn: Button, modifier: Byte, keys: ByteArray) {
        btn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!isConnected()) {
                        Snackbar.make(binding.root, "Not connected", Snackbar.LENGTH_SHORT).show()
                        return@setOnTouchListener true
                    }
                    (activity as? MainActivity)?.btHidManager?.sendKeyboardReport(modifier, keys)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    (activity as? MainActivity)?.btHidManager?.sendKeyboardRelease()
                    true
                }
                else -> false
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGuiButton(btn: Button) {
        btn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!isConnected()) {
                        Snackbar.make(binding.root, "Not connected", Snackbar.LENGTH_SHORT).show()
                        return@setOnTouchListener true
                    }
                    (activity as? MainActivity)?.btHidManager?.sendKeyboardReport(
                        KeyCodes.MOD_LEFT_GUI.toByte(),
                        byteArrayOf(0)
                    )
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    (activity as? MainActivity)?.btHidManager?.sendKeyboardRelease()
                    true
                }
                else -> false
            }
        }
    }

    private fun isConnected(): Boolean {
        return (activity as? MainActivity)?.btHidManager?.isConnected == true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
