package com.bluekey.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bluekey.MainActivity
import com.bluekey.R
import com.bluekey.databinding.FragmentDeviceScanBinding

@SuppressLint("MissingPermission")
class DeviceScanFragment : Fragment() {

    private var _binding: FragmentDeviceScanBinding? = null
    private val binding get() = _binding!!

    private var countdownTimer: CountDownTimer? = null
    private val bondedDevices = mutableListOf<BluetoothDevice>()
    private var listAdapter: ArrayAdapter<String>? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDeviceScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = activity as? MainActivity ?: return
        val btHidManager = activity.btHidManager
        val wifiHidManager = activity.wifiHidManager
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        // ── Bluetooth section ──────────────────────────────────────────────

        val deviceNames = mutableListOf<String>()
        listAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, deviceNames)
        binding.listBondedDevices.adapter = listAdapter

        refreshBondedDevices(bluetoothAdapter, deviceNames)

        binding.btnMakeDiscoverable.setOnClickListener {
            val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
            }
            startActivity(discoverableIntent)
            startCountdown(300)
        }

        binding.listBondedDevices.setOnItemClickListener { _, _, position, _ ->
            if (position < bondedDevices.size) {
                val device = bondedDevices[position]
                updateStatus("Connecting to ${device.name}…")
                btHidManager.connectToDevice(device)
            }
        }

        binding.btnRefreshDevices.setOnClickListener {
            refreshBondedDevices(bluetoothAdapter, deviceNames)
        }

        btHidManager.onConnectionChanged = { device, state ->
            activity.runOnUiThread {
                when (state) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        updateStatus("Connected (BT): ${device?.name}")
                        Toast.makeText(requireContext(), "Connected to ${device?.name}", Toast.LENGTH_SHORT).show()
                        countdownTimer?.cancel()
                        findNavController().navigateUp()
                    }
                    BluetoothProfile.STATE_CONNECTING -> updateStatus("Connecting to ${device?.name}…")
                    BluetoothProfile.STATE_DISCONNECTED -> updateStatus("Not connected")
                }
            }
        }

        // ── WiFi section ───────────────────────────────────────────────────

        binding.btnWifiConnect.setOnClickListener {
            showWifiConnectDialog()
        }

        binding.btnWifiDisconnect.setOnClickListener {
            wifiHidManager.disconnect()
        }

        wifiHidManager.onConnectionChanged = { connected, info ->
            activity.runOnUiThread {
                if (connected) {
                    updateStatus("Connected (WiFi): $info")
                    binding.btnWifiDisconnect.visibility = View.VISIBLE
                    binding.btnWifiConnect.text = "Change Host"
                    Toast.makeText(requireContext(), "WiFi connected to $info", Toast.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                } else {
                    binding.btnWifiDisconnect.visibility = View.GONE
                    binding.btnWifiConnect.text = "Connect to Host IP…"
                    updateStatus(if (btHidManager.isConnected) "Connected (BT): ${btHidManager.connectedDeviceName}" else "Not connected")
                    if (info.isNotBlank() && info != "Disconnected") {
                        Toast.makeText(requireContext(), "WiFi error: $info", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        // ── Initial state ──────────────────────────────────────────────────

        updateStatus(
            when {
                wifiHidManager.isConnected -> "Connected (WiFi): ${wifiHidManager.connectedLabel}"
                btHidManager.isConnected   -> "Connected (BT): ${btHidManager.connectedDeviceName}"
                else                       -> "Not connected"
            }
        )
        binding.btnWifiDisconnect.visibility = if (wifiHidManager.isConnected) View.VISIBLE else View.GONE
        if (wifiHidManager.isConnected) binding.btnWifiConnect.text = "Change Host"
    }

    private fun showWifiConnectDialog() {
        val input = EditText(requireContext()).apply {
            hint = "192.168.x.x"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setPadding(48, 32, 48, 16)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Connect via WiFi")
            .setMessage("Enter the IP address of the PC or device running the BlueKey companion server (port 8080).")
            .setView(input)
            .setPositiveButton("Connect") { _, _ ->
                val host = input.text.toString().trim()
                if (host.isNotEmpty()) {
                    updateStatus("Connecting to $host…")
                    (activity as? MainActivity)?.wifiHidManager?.connect(host)
                } else {
                    Toast.makeText(requireContext(), "Please enter an IP address", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshBondedDevices(
        bluetoothAdapter: BluetoothAdapter?,
        deviceNames: MutableList<String>
    ) {
        bondedDevices.clear()
        deviceNames.clear()
        val bonded = bluetoothAdapter?.bondedDevices ?: emptySet()
        for (device in bonded) {
            bondedDevices.add(device)
            deviceNames.add("${device.name} (${device.address})")
        }
        listAdapter?.notifyDataSetChanged()
        binding.tvNoPairedDevices.visibility = if (bondedDevices.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateStatus(text: String) {
        binding.tvConnectionStatus.text = text
    }

    private fun startCountdown(seconds: Int) {
        countdownTimer?.cancel()
        binding.tvCountdown.visibility = View.VISIBLE
        countdownTimer = object : CountDownTimer(seconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                binding.tvCountdown.text = "Discoverable for ${millisUntilFinished / 1000}s"
            }
            override fun onFinish() {
                binding.tvCountdown.text = "Discoverable time expired"
            }
        }.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countdownTimer?.cancel()
        _binding = null
    }
}
