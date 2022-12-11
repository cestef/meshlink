package com.cstef.meshlink.screens

import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
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
import com.cstef.meshlink.BleService
import com.cstef.meshlink.ui.theme.DarkColors
import com.cstef.meshlink.ui.theme.LightColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  bleBinder: BleService.BleServiceBinder,
  startScanning: () -> Unit,
  stopScanning: () -> Unit,
  startAdvertising: () -> Unit,
  stopAdvertising: () -> Unit,
) {
  val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
  val devices by bleBinder.allDevices.observeAsState(listOf())
  val context = LocalContext.current
  val isAdvertising by bleBinder.isAdvertising
  val isScanning by bleBinder.isScanning
  TopAppBar(
    title = { Text(text = "Settings") },
    modifier = Modifier
      .height(56.dp)
      .padding(top = 16.dp),
  )
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(top = 64.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(
        text = "Discoverable",
        modifier = Modifier.padding(start = 16.dp),
        color = colors.onBackground,
        style = MaterialTheme.typography.bodyLarge
      )
      Switch(
        checked = isAdvertising,
        onCheckedChange = {
          if (it) {
            startAdvertising()
          } else {
            stopAdvertising()
          }
        },
        modifier = Modifier.padding(end = 16.dp)
      )
    }
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(
        text = "Scan for devices",
        modifier = Modifier.padding(start = 16.dp),
        color = colors.onBackground,
        style = MaterialTheme.typography.bodyLarge
      )
      Switch(
        checked = isScanning,
        onCheckedChange = {
          if (it) {
            startScanning()
          } else {
            stopScanning()
          }
        },
        modifier = Modifier.padding(end = 16.dp)
      )
    }
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      // Delete all data button
      val (confirmDelete, setConfirmDelete) = remember { mutableStateOf(false) }
      if (confirmDelete) {
        AlertDialog(onDismissRequest = {
          setConfirmDelete(false)
        },
          title = { Text("Delete data") },
          text = { Text("Are you sure you want to delete all data?") },
          confirmButton = {
            Button(onClick = {
              bleBinder.deleteAllData()
              setConfirmDelete(false)
            }) {
              Text("Delete")
            }
          },
          dismissButton = {
            TextButton(onClick = {
              setConfirmDelete(false)
            }) {
              Text("Cancel")
            }
          })
      }
      Button(
        onClick = {
          setConfirmDelete(true)
        }, modifier = Modifier
          .padding(start = 16.dp, end = 8.dp)
      ) {
        Text("Delete all data")
      }
      // Delete specific data button
      val (isDeleteSpecificDataEnabled, setDeleteSpecificDataEnabled) = remember {
        mutableStateOf(false)
      }
      if (isDeleteSpecificDataEnabled) {
        AlertDialog(onDismissRequest = { setDeleteSpecificDataEnabled(false) },
          title = { Text(text = "Delete specific data") },
          text = {
            DropdownMenu(expanded = true,
              onDismissRequest = { setDeleteSpecificDataEnabled(false) },
              content = {
                DropdownMenuItem(onClick = { /*TODO*/ }, text = { Text(text = "Device 1") })
                DropdownMenuItem(onClick = { /*TODO*/ }, text = { Text(text = "Device 2") })
                DropdownMenuItem(onClick = { /*TODO*/ }, text = { Text(text = "Device 3") })
              })
          },
          confirmButton = { Button(onClick = { /*TODO*/ }) { Text(text = "Yes") } },
          dismissButton = { Button(onClick = { setDeleteSpecificDataEnabled(false) }) { Text(text = "No") } })
      }
      Button(
        onClick = {
          setDeleteSpecificDataEnabled(true)
        },
        modifier = Modifier
          .padding(start = 8.dp, end = 16.dp)
      ) { Text(text = "Delete device data") }
    }

    // Change password
    val (passwordDialogVisible, setPasswordDialogVisible) = remember { mutableStateOf(false) }
    if (passwordDialogVisible) {
      val (newPassword, setNewPassword) = remember { mutableStateOf("") }
      AlertDialog(onDismissRequest = {
        setPasswordDialogVisible(false)
      }, title = { Text("Enter password") }, text = {
        Column {
          OutlinedTextField(value = newPassword,
            onValueChange = {
              setNewPassword(it)
            },
            label = { Text("New password") },
            modifier = Modifier
              .padding(top = 16.dp, bottom = 16.dp)
              .align(Alignment.CenterHorizontally),
            singleLine = true,
            trailingIcon = {
              IconButton(onClick = {
                setNewPassword("")
              }) {
                Icon(
                  imageVector = Icons.Default.Clear, contentDescription = "Clear"
                )
              }
            })
        }
      }, confirmButton = {
        Button(onClick = {
          val success = bleBinder.changeDatabasePassword(newPassword)
          if (success) {
            setPasswordDialogVisible(false)
          } else {
            Toast.makeText(context, "Wrong password", Toast.LENGTH_SHORT).show()
          }
        }) {
          Text("Set")
        }
      }, dismissButton = {
        TextButton(onClick = {
          setPasswordDialogVisible(false)
        }) {
          Text("Cancel")
        }
      })
    }
    Button(
      onClick = {
        setPasswordDialogVisible(true)
      },
      modifier = Modifier
        .padding(start = 16.dp, end = 16.dp)
        .align(Alignment.CenterHorizontally),
    ) {
      Text(text = "Change password")
    }
    // About button
    TextButton(
      onClick = { /*TODO*/ }, modifier = Modifier
        .padding(start = 16.dp, end = 16.dp)
        .align(
          Alignment.CenterHorizontally
        )
    ) { Text(text = "About") }
  }
}
