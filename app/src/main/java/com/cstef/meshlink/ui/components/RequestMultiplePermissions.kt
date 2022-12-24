package com.cstef.meshlink.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.google.accompanist.permissions.*

@ExperimentalPermissionsApi
@Composable
fun RequestMultiplePermissions(
  permissions: List<String>,
  deniedMessage: String = "Give this app a permission to proceed. If it doesn't work, then you'll have to do it manually from the settings.",
  content: (@Composable () -> Unit),
) {
  val multiplePermissionsState = rememberMultiplePermissionsState(permissions)

  HandleRequests(
    multiplePermissionsState = multiplePermissionsState,
    deniedContent = {
      PermissionDeniedContent(
        deniedMessage = deniedMessage,
        onRequestPermission = { multiplePermissionsState.launchMultiplePermissionRequest() }
      )
    },
    content = content
  )
}

@ExperimentalPermissionsApi
@Composable
private fun HandleRequests(
  multiplePermissionsState: MultiplePermissionsState,
  deniedContent: @Composable (Boolean) -> Unit,
  content: @Composable () -> Unit
) {
  var shouldShowRationale by remember { mutableStateOf(false) }
  val result = multiplePermissionsState.permissions.all {
    shouldShowRationale = it.status.shouldShowRationale
    it.status == PermissionStatus.Granted
  }
  if (result) {
    content()
  } else {
    deniedContent(shouldShowRationale)
  }
}

@ExperimentalPermissionsApi
@Composable
fun PermissionDeniedContent(
  deniedMessage: String,
  onRequestPermission: () -> Unit
) {
  AlertDialog(
    onDismissRequest = {},
    title = {
      Text(
        text = "Permissions Required",
        style = TextStyle(
          fontSize = MaterialTheme.typography.titleMedium.fontSize,
          fontWeight = FontWeight.Bold
        )
      )
    },
    text = {
      Text(deniedMessage)
    },
    confirmButton = {
      Button(onClick = onRequestPermission) {
        Text("Give Permission")
      }
    }
  )
}
