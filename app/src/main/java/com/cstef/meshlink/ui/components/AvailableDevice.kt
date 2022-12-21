package com.cstef.meshlink.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cstef.meshlink.db.entities.Device
import com.cstef.meshlink.ui.theme.DarkColors
import com.cstef.meshlink.ui.theme.LightColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvailableDevice(
  device: Device,
  onDeviceClick: (deviceId: String) -> Unit = {}
) {
  Card(
    modifier = Modifier
      .padding(8.dp)
      .fillMaxWidth(),
    onClick = { onDeviceClick(device.userId) },
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(
      containerColor =
      if (isSystemInDarkTheme()) DarkColors.primaryContainer else LightColors.primaryContainer,
      contentColor =
      if (isSystemInDarkTheme()) DarkColors.onPrimaryContainer else LightColors.onPrimaryContainer
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
          imageVector = Icons.Rounded.Add,
          contentDescription = "Add device",
          tint = if (isSystemInDarkTheme()) DarkColors.onPrimaryContainer else LightColors.onPrimaryContainer
        )
      }
//        Icon(
//          imageVector =
//          if (device.rssi == 0) Icons.Rounded.SignalWifiBad
//          else if (!device.connected) Icons.Rounded.SignalWifiOff
//          else if (device.rssi >= -60) Icons.Rounded.NetworkWifi
//          else if (device.rssi >= -70) Icons.Rounded.NetworkWifi3Bar
//          else if (device.rssi >= -80) Icons.Rounded.NetworkWifi2Bar
//          else if (device.rssi >= -90) Icons.Rounded.NetworkWifi1Bar
//          else Icons.Rounded.SignalWifiStatusbarConnectedNoInternet4,
//          contentDescription = "Signal strength",
//        )
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

