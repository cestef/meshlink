package com.cstef.meshlink

import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import com.cstef.meshlink.adapters.ConnectedDevicesAdapter
import com.cstef.meshlink.chat.ChatFragment
import com.cstef.meshlink.util.struct.ConnectedDevice

class ScanFragment : Fragment() {

  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
  ): View? {
    return inflater.inflate(R.layout.fragment_scan, container, false)
  }

  private var rvConnectedDevices: androidx.recyclerview.widget.RecyclerView? = null
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val mainActivity = activity as MainActivity
    val connectedDevicesAdapter = ConnectedDevicesAdapter(mutableListOf()) {
      Log.i("MainActivity", "Device clicked: $it")
      // Switch to chat with this device, passing in the device id
      val chatFragment = ChatFragment()
      val bundle = Bundle()
      bundle.putString("device_id", it.id)
      chatFragment.arguments = bundle
      parentFragmentManager.beginTransaction().replace(R.id.fragment_container, chatFragment)
        .addToBackStack(null).commit()
    }
    rvConnectedDevices = view.findViewById(R.id.rvConnectedDevices)
    rvConnectedDevices?.adapter = connectedDevicesAdapter
    rvConnectedDevices?.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
    (rvConnectedDevices?.adapter as ConnectedDevicesAdapter).updateData(
      (requireActivity() as MainActivity).bleBinder?.getConnectedDevices() ?: mutableListOf()
    )
    val buttonTextRes =
      if ((requireActivity() as MainActivity).isBleStarted) R.string.stop_ble else R.string.start_ble
    val startButton = view.findViewById<Button>(R.id.startButton)
    startButton.text = getString(buttonTextRes)
    val userIDText = view.findViewById<TextView>(R.id.myUserIdTextView)
    val userId = (requireActivity() as MainActivity).userId
    Log.d("MainActivity", "User ID: $userId")
    userIDText.text = userId
    startButton.setOnClickListener {
      if (mainActivity.isBleStarted) {
        mainActivity.stopBle()
      } else {
        mainActivity.startBle()
      }
    }
  }

  override fun onResume() {
    super.onResume()
    requireActivity().registerReceiver(
      broadcastReceiver, IntentFilter(BleService.ACTION_USER.action)
    )
  }

  override fun onPause() {
    super.onPause()
    requireActivity().unregisterReceiver(broadcastReceiver)
  }

  private val broadcastReceiver = object : android.content.BroadcastReceiver() {
    override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
      when (intent?.action) {
        BleService.ACTION_USER.action -> {
          val devices =
            intent?.getParcelableArrayListExtra<ConnectedDevice>(BleService.EXTRA_DEVICES)
          //Log.d("ScanFragment", "Received devices: $devices")
          (rvConnectedDevices?.adapter as ConnectedDevicesAdapter).updateData(
            devices ?: mutableListOf()
          )
        }
        else -> {
          Log.d("ScanFragment", "Received unknown action: ${intent?.action}")
        }
      }
    }
  }
}
