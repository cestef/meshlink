package com.cstef.meshlink.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cstef.meshlink.db.entities.Device

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AddedDevice(
  device: Device,
  onDeviceLongClick: (deviceID: String) -> Unit = {},
  onDeviceClick: (deviceId: String) -> Unit = {}
) {
  Card(
    modifier = Modifier
      .padding(8.dp)
      .fillMaxWidth()
      .clip(
        MaterialTheme.shapes.large,
      )
      .combinedClickable(
        onClick = { onDeviceClick(device.userId) },
        onLongClick = { onDeviceLongClick(device.userId) }),
    colors = CardDefaults.cardColors(
      containerColor = if (device.connected) {
        MaterialTheme.colorScheme.primaryContainer
      } else {
        MaterialTheme.colorScheme.secondaryContainer
      },
      contentColor = if (device.connected) {
        MaterialTheme.colorScheme.onPrimaryContainer
      } else {
        MaterialTheme.colorScheme.onSecondaryContainer
      }
    )
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(
        modifier = Modifier
          .padding(24.dp)
          .weight(1f)
      ) {
        Text(
          text = device.name ?: device.userId,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Column(
        modifier = Modifier
          .padding(24.dp),
        horizontalAlignment = Alignment.End
      ) {
        Icon(
          imageVector =
          if (device.rssi == 0) Icons.Rounded.SignalWifiBad
          else if (!device.connected) Icons.Rounded.SignalWifiOff
          else if (device.rssi >= -60) Icons.Rounded.NetworkWifi
          else if (device.rssi >= -70) Icons.Rounded.NetworkWifi3Bar
          else if (device.rssi >= -80) Icons.Rounded.NetworkWifi2Bar
          else if (device.rssi >= -90) Icons.Rounded.NetworkWifi1Bar
          else Icons.Rounded.SignalWifiStatusbarConnectedNoInternet4,
          contentDescription = "Signal strength",
        )
//        if (device.writing) {
//          Icon(
//            imageVector = Icons.Rounded.BluetoothSearching,
//            contentDescription = "Writing",
//            modifier = Modifier.padding(start = 8.dp)
//          )
//        }
      }
    }
  }
}
