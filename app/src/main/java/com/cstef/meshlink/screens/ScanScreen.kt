package com.cstef.meshlink.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
import com.cstef.meshlink.BleService
import com.cstef.meshlink.ui.components.AvailableDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
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
        Text(
          text = userId,
          modifier = Modifier
            .padding(top = 16.dp, bottom = 16.dp, start = 24.dp, end = 0.dp)
            .align(
              Alignment.CenterVertically
            )
            .clickable {
              // Copy to clipboard
              val clipboard =
                context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
              val clip = android.content.ClipData.newPlainText("MeshLink ID", userId)
              clipboard.setPrimaryClip(clip)
              Toast
                .makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT)
                .show()
            },
        )
        IconButton(
          modifier = Modifier
            .align(Alignment.CenterVertically)
            .padding(top = 16.dp, bottom = 16.dp, start = 0.dp, end = 0.dp),
          onClick = {
            val sendIntent: Intent = Intent().apply {
              action = Intent.ACTION_SEND
              putExtra(Intent.EXTRA_TEXT, userId)
              type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, null)
            startActivity(context, shareIntent, null)
          }) {
          Icon(
            imageVector = Icons.Filled.Share,
            contentDescription = "Share your ID",
            modifier = Modifier.size(24.dp)
          )
        }
      }
    }, modifier = Modifier.height(96.dp))
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
      items(
        devices ?: listOf(),
      ) { device ->
        AvailableDevice(device = device, { onDeviceLongClick(it) }) { onDeviceSelected(it) }
      }
    }
  }
}
