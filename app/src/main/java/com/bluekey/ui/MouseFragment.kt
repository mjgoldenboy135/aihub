package com.bluekey.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import com.bluekey.MainActivity
import com.bluekey.databinding.FragmentMouseBinding
import com.google.android.material.snackbar.Snackbar
import kotlin.math.abs
import kotlin.math.sqrt

class MouseFragment : Fragment() {

    private var _binding: FragmentMouseBinding? = null
    private val binding get() = _binding!!

    private var sensitivity = 1.5f
    private var isScrollMode = false

    private var lastX = 0f
    private var lastY = 0f
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var hasMoved = false
    private val clickThreshold = 5f // pixels — less than this = single tap

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMouseBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Sensitivity seekbar: 0 = 0.5x, 50 = 1.5x (default), 100 = 3.0x
        binding.seekSensitivity.max = 100
        binding.seekSensitivity.progress = 50
        binding.tvSensitivity.text = "Sensitivity: 1.5x"

        binding.seekSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                sensitivity = 0.5f + (progress / 100f) * 2.5f
                binding.tvSensitivity.text = "Sensitivity: ${"%.1f".format(sensitivity)}x"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Scroll mode toggle
        binding.btnScroll.setOnClickListener {
            isScrollMode = !isScrollMode
            binding.btnScroll.text = if (isScrollMode) "SCROLL ON" else "SCROLL"
            binding.btnScroll.alpha = if (isScrollMode) 1.0f else 0.7f
        }

        // Left click button
        binding.btnLeftClick.setOnClickListener {
            if (!isConnected()) {
                Snackbar.make(binding.root, "Not connected", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sendClick(button = 0x01)
        }

        // Right click button
        binding.btnRightClick.setOnClickListener {
            if (!isConnected()) {
                Snackbar.make(binding.root, "Not connected", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sendClick(button = 0x02)
        }

        // Touchpad for mouse movement
        binding.touchpad.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!isConnected()) {
                        Snackbar.make(binding.root, "Not connected", Snackbar.LENGTH_SHORT).show()
                        return@setOnTouchListener true
                    }
                    lastX = event.x
                    lastY = event.y
                    touchStartX = event.x
                    touchStartY = event.y
                    hasMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val rawDx = event.x - lastX
                    val rawDy = event.y - lastY

                    val totalDx = event.x - touchStartX
                    val totalDy = event.y - touchStartY
                    val totalDist = sqrt(totalDx * totalDx + totalDy * totalDy)
                    if (totalDist > clickThreshold) {
                        hasMoved = true
                    }

                    val dx = (rawDx * sensitivity).toInt()
                    val dy = (rawDy * sensitivity).toInt()

                    val mgr = (activity as? MainActivity)?.hidSender
                    if (isScrollMode) {
                        // Use Y movement as scroll wheel
                        val wheel = -dy // invert: finger up = scroll up
                        mgr?.sendMouseReport(0, 0, 0, wheel.coerceIn(-127, 127))
                    } else {
                        mgr?.sendMouseReport(0, dx, dy, 0)
                    }

                    lastX = event.x
                    lastY = event.y
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!hasMoved && isConnected()) {
                        // Single tap = left click
                        sendClick(button = 0x01)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun sendClick(button: Int) {
        val mgr = (activity as? MainActivity)?.hidSender ?: return
        mgr.sendMouseReport(button.toByte(), 0, 0, 0)
        // Short delay then release
        binding.root.postDelayed({
            mgr.sendMouseRelease()
        }, 80)
    }

    private fun isConnected(): Boolean {
        return (activity as? MainActivity)?.hidSender?.isConnected == true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val mgr = (activity as? MainActivity)?.hidSender
        mgr?.sendMouseRelease()
        _binding = null
    }
}
