package com.cstef.meshlink.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cstef.meshlink.BleService
import com.cstef.meshlink.ui.theme.DarkColors
import com.cstef.meshlink.ui.theme.LightColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserInfoScreen(
  bleBinder: BleService.BleServiceBinder,
  userId: String?,
  isMe: Boolean,
  openSettings: () -> Unit
) {
  val context = LocalContext.current
  val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
  val devices by bleBinder.allDevices.observeAsState(listOf())
  val device = devices.find { it.userId == userId }
  // Display user info: Avatar, userID, public key, block/unblock button (if not me), delete data button (if me), rename text field (if not me)
  Column(modifier = Modifier.fillMaxSize()) {
    if (userId != null) {
      Avatar(
        deviceId = userId,
        modifier = Modifier
          .padding(top = 16.dp, bottom = 16.dp)
          .size(64.dp)
          .align(Alignment.CenterHorizontally)
      )
      if (device?.name != null) {
        Text(
          text = "${device.name} ${if (device.blocked) "(blocked)" else ""}",
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier
            .padding(top = 16.dp, bottom = 16.dp, start = 24.dp, end = 24.dp)
            .align(
              Alignment.CenterHorizontally
            ),
          color = colors.onBackground,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "",
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier
            .padding(bottom = 16.dp)
            .align(
              Alignment.CenterHorizontally
            ),
          color = colors.onBackground
        )
      } else {
        Text(
          text = "$userId ${if (isMe) "(me)" else if (device?.blocked == true) "(blocked)" else ""}",
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier
            .padding(top = 16.dp, bottom = 16.dp)
            .align(
              Alignment.CenterHorizontally
            ),
          color = colors.onBackground
        )
      }

      bleBinder.getPublicKeySignature(userId).let {
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
      val (newName, setNewName) = remember { mutableStateOf(device?.name ?: "") }
      OutlinedTextField(value = newName,
        onValueChange = {
          setNewName(it)
        },
        label = { Text("Nickname") },
        enabled = !isMe,
        modifier = Modifier
          .padding(top = 16.dp, bottom = 16.dp)
          .align(Alignment.CenterHorizontally),
        singleLine = true,
        trailingIcon = {
          if (!isMe) {
            IconButton(onClick = {
              bleBinder.updateDeviceName(userId, newName)
            }) {
              Icon(
                imageVector = Icons.Default.Done, contentDescription = "Done"
              )
            }
          }
        })

      if (!isMe) {
        val (chooseDeleteMode, setChooseDeleteMode) = remember { mutableStateOf(false) }
        if (chooseDeleteMode) {
          AlertDialog(onDismissRequest = { setChooseDeleteMode(false) },
            title = { Text("Delete data") },
            text = { Text("Are you sure you want to delete all data for this user?") },
            confirmButton = {
              Button(onClick = {
                if (device != null) {
                  bleBinder.deleteDataForUser(device)
                }
                setChooseDeleteMode(false)
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
                bleBinder.unblockUser(userId)
              } else {
                bleBinder.blockUser(userId)
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


