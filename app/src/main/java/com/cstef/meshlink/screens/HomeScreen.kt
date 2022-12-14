package com.cstef.meshlink.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cstef.meshlink.BleService
import com.cstef.meshlink.ui.components.AddedDevice
import com.cstef.meshlink.ui.components.DeviceID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  bleBinder: BleService.BleServiceBinder,
  userId: String,
  onSelfClick: () -> Unit,
  onDeviceLongClick: (deviceId: String) -> Unit,
  onDeviceSelected: (deviceId: String) -> Unit,
) {
  val devices by bleBinder.allDevices.observeAsState()
  val context = LocalContext.current
  Column(modifier = Modifier.fillMaxSize()) {
    TopAppBar(title = {
      Row(modifier = Modifier.fillMaxWidth()) {
        Avatar(
          deviceId = userId, modifier = Modifier
            .padding(start = 16.dp, top = 16.dp, bottom = 16.dp)
            .align(Alignment.CenterVertically)
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
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
      items(
        devices?.filter { it.added } ?: listOf(),
      ) { device ->
        AddedDevice(device = device, { onDeviceLongClick(it) }) { onDeviceSelected(it) }
      }
    }
  }
}
