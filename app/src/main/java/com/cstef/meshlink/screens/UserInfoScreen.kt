package com.cstef.meshlink.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import com.cstef.meshlink.db.entities.Device
import com.cstef.meshlink.ui.components.DeviceID
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserInfoScreen(
  allDevices: LiveData<List<Device>>,
  userId: String?,
  publicKey: String?,
  isMe: Boolean,
  onBack: () -> Unit,
  openSettings: () -> Unit,
  blockUser: () -> Unit,
  unblockUser: () -> Unit,
  deleteDataForUser: () -> Unit,
  updateNickname: (String) -> Unit,
) {
  val context = LocalContext.current
  val colors = MaterialTheme.colorScheme
  val devices by allDevices.observeAsState(listOf())
  val device = devices.find { it.userId == userId }
  Column(modifier = Modifier.fillMaxSize()) {
    if (userId != null) {
      Avatar(
        deviceId = userId,
        modifier = Modifier
          .padding(top = 16.dp, bottom = 16.dp)
          .size(64.dp)
          .align(Alignment.CenterHorizontally)
      )
      DeviceID(
        userId = userId,
        isMe = isMe,
        blocked = device?.blocked ?: false,
        modifier = Modifier
          .padding(16.dp)
          .fillMaxWidth()
          .align(Alignment.CenterHorizontally)
      )
      if (device?.name != null) {
        Text(
          text = device.name,
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier
            .padding(bottom = 16.dp)
            .align(Alignment.CenterHorizontally),
          color = colors.onBackground
        )
      }
      // Formula: Distance = 10 ^ ((Measured Power - RSSI)/(10 * N)) where N = 2 (in free space) and N = 3 (in walls), Measured Power = -50 (at 1 meter)
      if (device?.connected == true && device.rssi != 0) {
        Log.d("UserInfoScreen", "RSSI: ${device.rssi}, TX Power: ${device.txPower}")
        val distance = 10.0.pow(((-59.0) - device.rssi.toDouble()) / (10.0 * 3.0))
        Text(
          text = "~%.2f meters away".format(distance),
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier
            .padding(bottom = 16.dp)
            .align(Alignment.CenterHorizontally),
          color = colors.onBackground
        )
      }
      publicKey?.let {
        Text(
          text = "Public key",
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier
            .padding(top = 16.dp, bottom = 16.dp)
            .align(Alignment.CenterHorizontally),
          color = colors.onBackground
        )
        Text(
          text = it,
          modifier = Modifier
            .padding(top = 16.dp, bottom = 16.dp)
            .align(Alignment.CenterHorizontally)
            .clickable {
              // Copy public key to clipboard
              val clipboard =
                context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
              val clip = android.content.ClipData.newPlainText("Public key for $userId", it)
              clipboard.setPrimaryClip(clip)
              Toast
                .makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT)
                .show()
            },
          color = colors.onBackground,
          style = MaterialTheme.typography.bodySmall
        )
      }
      if (!isMe) {
        val (newName, setNewName) = remember { mutableStateOf(device?.name ?: "") }
        OutlinedTextField(
          value = newName,
          onValueChange = {
            setNewName(it)
          },
          label = { Text("Nickname") },
          modifier = Modifier
            .padding(top = 16.dp, bottom = 16.dp)
            .align(Alignment.CenterHorizontally),
          singleLine = true,
          trailingIcon = {
            if (newName.isNotEmpty()) {
              IconButton(onClick = {
                updateNickname(newName)
              }) {
                Icon(
                  imageVector = Icons.Rounded.Check,
                  contentDescription = "Save nickname",
                )
              }
            }
          }
        )
        val (chooseDeleteMode, setChooseDeleteMode) = remember { mutableStateOf(false) }
        if (chooseDeleteMode) {
          AlertDialog(onDismissRequest = { setChooseDeleteMode(false) },
            title = { Text("Delete data") },
            text = { Text("Are you sure you want to delete all data for this user?") },
            confirmButton = {
              Button(onClick = {
                if (device != null) {
                  deleteDataForUser()
                }
                setChooseDeleteMode(false)
                onBack()
              }) {
                Text("Delete")
              }
            },
            dismissButton = {
              TextButton(onClick = {
                setChooseDeleteMode(false)
              }) {
                Text("Cancel")
              }
            })
        }
        Row(
          modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(top = 16.dp, bottom = 16.dp)
        ) {
          Button(onClick = {
            setChooseDeleteMode(true)
          }) {
            Text("Delete data")
          }
          Button(
            onClick = {
              if (device?.blocked == true) {
                unblockUser()
              } else {
                blockUser()
              }
            }, modifier = Modifier.padding(start = 16.dp)
          ) {
            Text(
              text = if (device?.blocked == true) "Unblock" else "Block",
            )
          }
        }
      } else {
        // Settings button
        Button(
          onClick = {
            openSettings()
          }, modifier = Modifier
            .padding(top = 16.dp, bottom = 16.dp)
            .align(Alignment.CenterHorizontally)
        ) {
          Text("Settings")
        }
      }
    }
  }
}


