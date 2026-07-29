package com.bluekey.ui

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bluekey.MainActivity
import com.bluekey.R
import com.bluekey.databinding.FragmentMenuBinding

class MenuFragment : Fragment() {

    private var _binding: FragmentMenuBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btHidManager = (activity as? MainActivity)?.btHidManager

        // Set up connection callbacks
        btHidManager?.onConnectionChanged = { device, state ->
            activity?.runOnUiThread {
                updateConnectionStatus(device, state)
            }
        }

        btHidManager?.onRegistered = { registered ->
            activity?.runOnUiThread {
                if (registered) {
                    binding.statusChip.text = "Ready — not connected"
                    binding.statusChip.setBackgroundColor(
                        requireContext().getColor(R.color.status_ready)
                    )
                } else {
                    binding.statusChip.text = "HID not registered"
                    binding.statusChip.setBackgroundColor(
                        requireContext().getColor(R.color.status_disconnected)
                    )
                }
            }
        }

        // Initial state
        if (btHidManager?.isConnected == true) {
            binding.statusChip.text = "Connected: ${btHidManager.connectedDeviceName}"
            binding.statusChip.setBackgroundColor(
                requireContext().getColor(R.color.status_connected)
            )
            binding.btnConnect.text = "Disconnect"
        } else {
            binding.statusChip.text = "Not connected"
            binding.statusChip.setBackgroundColor(
                requireContext().getColor(R.color.status_disconnected)
            )
            binding.btnConnect.text = "Connect"
        }

        // Card buttons
        binding.cardKeyboard.setOnClickListener {
            if (checkConnected()) {
                findNavController().navigate(R.id.action_menu_to_keyboard)
            }
        }

        binding.cardGamepad.setOnClickListener {
            if (checkConnected()) {
                findNavController().navigate(R.id.action_menu_to_gamepad)
            }
        }

        binding.cardMedia.setOnClickListener {
            if (checkConnected()) {
                findNavController().navigate(R.id.action_menu_to_media)
            }
        }

        binding.cardMouse.setOnClickListener {
            if (checkConnected()) {
                findNavController().navigate(R.id.action_menu_to_mouse)
            }
        }

        // Connect / Disconnect button
        binding.btnConnect.setOnClickListener {
            val mgr = btHidManager ?: return@setOnClickListener
            if (mgr.isConnected) {
                mgr.disconnect()
            } else {
                findNavController().navigate(R.id.action_menu_to_device_scan)
            }
        }
    }

    private fun checkConnected(): Boolean {
        val mgr = (activity as? MainActivity)?.btHidManager
        return if (mgr?.isConnected == true) {
            true
        } else {
            Toast.makeText(requireContext(), "Not connected", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun updateConnectionStatus(device: BluetoothDevice?, state: Int) {
        when (state) {
            BluetoothProfile.STATE_CONNECTED -> {
                binding.statusChip.text = "Connected: ${device?.name ?: "Unknown"}"
                binding.statusChip.setBackgroundColor(
                    requireContext().getColor(R.color.status_connected)
                )
                binding.btnConnect.text = "Disconnect"
            }
            BluetoothProfile.STATE_CONNECTING -> {
                binding.statusChip.text = "Connecting..."
                binding.statusChip.setBackgroundColor(
                    requireContext().getColor(R.color.status_connecting)
                )
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                binding.statusChip.text = "Disconnected"
                binding.statusChip.setBackgroundColor(
                    requireContext().getColor(R.color.status_disconnected)
                )
                binding.btnConnect.text = "Connect"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
