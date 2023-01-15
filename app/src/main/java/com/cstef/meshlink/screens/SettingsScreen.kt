package com.cstef.meshlink.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Feed
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  isAdvertising: MutableState<Boolean>,
  startAdvertising: () -> Unit,
  stopAdvertising: () -> Unit,
  goToAbout: () -> Unit,
  goToStats: () -> Unit,
  goToLogs: () -> Unit,
  goToBenchmark: () -> Unit,
  deleteAllData: () -> Unit,
  changeDatabasePassword: (password: String) -> Boolean,
) {
  val colors = MaterialTheme.colorScheme
  val context = LocalContext.current
  val advertising by isAdvertising
  TopAppBar(
    title = { Text(text = "Settings") },
    modifier = Modifier
      .height(56.dp)
      .padding(top = 16.dp),
    actions = {
      Row(
        modifier = Modifier
          .fillMaxHeight()
          .padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
      ) {
        IconButton(onClick = goToLogs) {
          Icon(
            imageVector = Icons.Filled.Feed,
            contentDescription = "Go to logs screen",
            tint = colors.onSurface
          )
        }
        IconButton(onClick = goToBenchmark) {
          Icon(
            imageVector = Icons.Outlined.TrendingUp,
            contentDescription = "Go to benchmark screen",
            tint = colors.onSurface
          )
        }
      }
    }
  )
  Column {
    Column(
      modifier = Modifier
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
          checked = advertising,
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
//    Row(
//      modifier = Modifier.fillMaxWidth(),
//      verticalAlignment = Alignment.CenterVertically,
//      horizontalArrangement = Arrangement.SpaceBetween
//    ) {
//      Text(
//        text = "Scan for devices",
//        modifier = Modifier.padding(start = 16.dp),
//        color = colors.onBackground,
//        style = MaterialTheme.typography.bodyLarge
//      )
//      Switch(
//        checked = isScanning,
//        onCheckedChange = {
//          if (it) {
//            startScanning()
//          } else {
//            stopScanning()
//          }
//        },
//        modifier = Modifier.padding(end = 16.dp)
//      )
//    }
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
              deleteAllData()
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
          .padding(top = 16.dp)
          .align(Alignment.CenterHorizontally)
      ) {
        Text("Delete all data")
      }

      // Change password
      val (passwordDialogVisible, setPasswordDialogVisible) = remember { mutableStateOf(false) }
      val (confirmDeletePassword, setConfirmDeletePassword) = remember { mutableStateOf(false) }
      if (confirmDeletePassword) {
        AlertDialog(onDismissRequest = {
          setConfirmDeletePassword(false)
        },
          title = { Text("Delete password") },
          text = { Text("Are you sure you want to delete the password?") },
          confirmButton = {
            Button(onClick = {
              val success = changeDatabasePassword("")
              if (success) {
                setConfirmDeletePassword(false)
              } else {
                Toast.makeText(context, "Failed to delete password", Toast.LENGTH_SHORT).show()
              }
            }) {
              Text("Delete")
            }
          },
          dismissButton = {
            TextButton(onClick = {
              setConfirmDeletePassword(false)
            }) {
              Text("Cancel")
            }
          })
      }
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
            if (newPassword.isEmpty()) {
              // Confirm if the user wants to remove the password
              setPasswordDialogVisible(false)
              setConfirmDeletePassword(true)
            } else {
              val success = changeDatabasePassword(newPassword)
              if (success) {
                setPasswordDialogVisible(false)
              } else {
                Toast.makeText(context, "Wrong password", Toast.LENGTH_SHORT).show()
              }
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
    }
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      TextButton(
        onClick = { goToStats() }, modifier = Modifier
          .padding(start = 16.dp, end = 16.dp, top = 16.dp)
          .align(
            Alignment.CenterHorizontally
          )
      ) { Text(text = "Stats / Metrics") }
      // About button
      TextButton(
        onClick = { goToAbout() },
        modifier = Modifier
          .padding(start = 16.dp, end = 16.dp)
          .align(
            Alignment.CenterHorizontally
          ),
      ) { Text(text = "About") }
    }
  }
}
