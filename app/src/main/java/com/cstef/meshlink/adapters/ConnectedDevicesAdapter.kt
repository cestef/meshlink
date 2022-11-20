package com.cstef.meshlink.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cstef.meshlink.R
import com.cstef.meshlink.util.struct.ConnectedDevice

class ConnectedDevicesAdapter(
  private val devices: MutableList<ConnectedDevice> = mutableListOf(),
  private val onDeviceClick: (ConnectedDevice) -> Unit
) : RecyclerView.Adapter<ConnectedDevicesAdapter.ViewHolder>() {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val view = LayoutInflater.from(parent.context)
      .inflate(R.layout.item_connected_device, parent, false)
    return ViewHolder(view)
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    holder.bind(devices[position])
  }

  override fun getItemCount(): Int = devices.size

  fun updateData(newDevices: List<ConnectedDevice>) {
    devices.clear()
    devices.addAll(newDevices)
    notifyDataSetChanged()
  }

  inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val deviceName = itemView.findViewById<TextView>(R.id.tvDeviceId)
    private val deviceRssi = itemView.findViewById<TextView>(R.id.tvDeviceRssi)
    private val deviceConnected = itemView.findViewById<CheckBox>(R.id.cbDeviceConnected)

    init {
      deviceConnected.isEnabled = false
      itemView.setOnClickListener {
        onDeviceClick(devices[adapterPosition])
      }
    }

    fun bind(device: ConnectedDevice) {
      deviceName.text = device.id
      deviceRssi.text = (if (device.rssi == 0) "???" else device.rssi.toString()) + "dBm"
      deviceConnected.isChecked = device.connected
    }
  }
}
