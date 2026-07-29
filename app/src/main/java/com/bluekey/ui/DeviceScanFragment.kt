package com.bluekey.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
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

        val btHidManager = (activity as? MainActivity)?.btHidManager
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        // Set up bonded devices list
        val deviceNames = mutableListOf<String>()
        listAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, deviceNames)
        binding.listBondedDevices.adapter = listAdapter

        // Populate bonded devices
        refreshBondedDevices(bluetoothAdapter, deviceNames)

        // Make discoverable button
        binding.btnMakeDiscoverable.setOnClickListener {
            val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
            }
            startActivity(discoverableIntent)
            startCountdown(300)
        }

        // Bonded device tap
        binding.listBondedDevices.setOnItemClickListener { _, _, position, _ ->
            if (position < bondedDevices.size) {
                val device = bondedDevices[position]
                binding.tvConnectionStatus.text = "Connecting to ${device.name}..."
                btHidManager?.connectToDevice(device)
            }
        }

        // Observe connection changes
        btHidManager?.onConnectionChanged = { device, state ->
            activity?.runOnUiThread {
                when (state) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        binding.tvConnectionStatus.text = "Connected: ${device?.name}"
                        Toast.makeText(
                            requireContext(),
                            "Connected to ${device?.name}",
                            Toast.LENGTH_SHORT
                        ).show()
                        countdownTimer?.cancel()
                        findNavController().navigateUp()
                    }
                    BluetoothProfile.STATE_CONNECTING -> {
                        binding.tvConnectionStatus.text = "Connecting to ${device?.name}..."
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        binding.tvConnectionStatus.text = "Not connected"
                    }
                }
            }
        }

        // Refresh bonded devices button
        binding.btnRefreshDevices.setOnClickListener {
            refreshBondedDevices(bluetoothAdapter, deviceNames)
        }

        // Initial status
        if (btHidManager?.isConnected == true) {
            binding.tvConnectionStatus.text = "Connected: ${btHidManager.connectedDeviceName}"
        } else {
            binding.tvConnectionStatus.text = "Not connected"
        }
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
        if (bondedDevices.isEmpty()) {
            binding.tvNoPairedDevices.visibility = View.VISIBLE
        } else {
            binding.tvNoPairedDevices.visibility = View.GONE
        }
    }

    private fun startCountdown(seconds: Int) {
        countdownTimer?.cancel()
        binding.tvCountdown.visibility = View.VISIBLE
        countdownTimer = object : CountDownTimer(seconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val s = millisUntilFinished / 1000
                binding.tvCountdown.text = "Discoverable for ${s}s"
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
