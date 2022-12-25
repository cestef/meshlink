package com.cstef.meshlink.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import com.cstef.meshlink.db.entities.Device
import com.cstef.meshlink.ui.components.AddedDevice
import com.cstef.meshlink.ui.components.DeviceID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  allDevices: LiveData<List<Device>>,
  userId: String,
  onSelfClick: () -> Unit,
  onDeviceLongClick: (deviceId: String) -> Unit,
  onDeviceSelected: (deviceId: String) -> Unit,
) {
  val devices by allDevices.observeAsState(listOf())
  Column(modifier = Modifier.fillMaxSize()) {
    TopAppBar(title = {
      Row(modifier = Modifier.fillMaxWidth()) {
        Avatar(
          deviceId = userId, modifier = Modifier
            .padding(start = 8.dp, top = 16.dp, bottom = 16.dp)
            .align(Alignment.CenterVertically)
            .clip(CircleShape)
            .size(64.dp),
          onClick = onSelfClick
        )
        DeviceID(
          userId = userId, isMe = false, blocked = false, modifier = Modifier
//            .padding(16.dp)
            .padding(start = 8.dp)
            .align(Alignment.CenterVertically)
        )
      }
    }, modifier = Modifier.height(96.dp))
    if (devices.any { it.added }) {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
      ) {
        items(devices.filter { it.added }) { device ->
          AddedDevice(
            device = device,
            onDeviceLongClick = { onDeviceLongClick(device.userId) },
            onDeviceClick = { onDeviceSelected(device.userId) }
          )
        }
      }
    } else {
      Box(modifier = Modifier.fillMaxSize()) {
        Text(
          text = "No devices added yet",
          style = MaterialTheme.typography.titleLarge,
          textAlign = TextAlign.Center,
          modifier = Modifier
            .align(Alignment.Center)
            .padding(bottom = 64.dp),
          color = MaterialTheme.colorScheme.onBackground
        )
      }
    }
  }
}
