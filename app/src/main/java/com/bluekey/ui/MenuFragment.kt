package com.bluekey.ui

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

        val mainActivity = activity as? MainActivity ?: return

        // ── Bluetooth callbacks ────────────────────────────────────────────

        mainActivity.btHidManager.onConnectionChanged = { device, state ->
            activity?.runOnUiThread { refreshStatus() }
        }

        mainActivity.btHidManager.onRegistered = { registered ->
            activity?.runOnUiThread {
                if (registered && mainActivity.hidSender == null) {
                    setChip("Ready — not connected", R.color.status_ready)
                }
            }
        }

        // ── WiFi callbacks ─────────────────────────────────────────────────

        mainActivity.wifiHidManager.onConnectionChanged = { _, _ ->
            activity?.runOnUiThread { refreshStatus() }
        }

        // ── Mode buttons ───────────────────────────────────────────────────

        binding.cardKeyboard.setOnClickListener {
            if (checkConnected()) findNavController().navigate(R.id.action_menu_to_keyboard)
        }
        binding.cardGamepad.setOnClickListener {
            if (checkConnected()) findNavController().navigate(R.id.action_menu_to_gamepad)
        }
        binding.cardMedia.setOnClickListener {
            if (checkConnected()) findNavController().navigate(R.id.action_menu_to_media)
        }
        binding.cardMouse.setOnClickListener {
            if (checkConnected()) findNavController().navigate(R.id.action_menu_to_mouse)
        }

        // ── Connect / Disconnect button ────────────────────────────────────

        binding.btnConnect.setOnClickListener {
            val sender = mainActivity.hidSender
            if (sender != null) {
                // Disconnect whichever is active
                if (mainActivity.wifiHidManager.isConnected) {
                    mainActivity.wifiHidManager.disconnect()
                } else {
                    mainActivity.btHidManager.disconnect()
                }
            } else {
                findNavController().navigate(R.id.action_menu_to_device_scan)
            }
        }

        refreshStatus()
    }

    private fun refreshStatus() {
        val mainActivity = activity as? MainActivity ?: return
        val sender = mainActivity.hidSender
        if (sender != null) {
            setChip("● ${sender.connectedLabel}", R.color.status_connected)
            binding.btnConnect.text = "Disconnect"
        } else {
            setChip("Not connected", R.color.status_disconnected)
            binding.btnConnect.text = "Connect"
        }
    }

    private fun setChip(text: String, colorRes: Int) {
        binding.statusChip.text = text
        binding.statusChip.setBackgroundColor(requireContext().getColor(colorRes))
    }

    private fun checkConnected(): Boolean {
        return if ((activity as? MainActivity)?.hidSender != null) {
            true
        } else {
            Toast.makeText(requireContext(), "Not connected — tap Connect first", Toast.LENGTH_SHORT).show()
            false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
