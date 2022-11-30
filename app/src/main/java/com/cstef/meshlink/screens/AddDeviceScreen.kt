package com.cstef.meshlink.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cstef.meshlink.BleService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceScreen(
  bleBinder: BleService.BleServiceBinder?,
  myUserId: String,
  onBack: () -> Boolean
) {
  val (userId, setUserId) = remember {
    mutableStateOf("")
  }
  val (error, setError) = remember {
    mutableStateOf("")
  }
  Row(modifier = Modifier.fillMaxSize()) {
    OutlinedTextField(
      value = userId,
      onValueChange = {
        setUserId(it)
        setError("")
      },
      label = { Text("User ID") },
      modifier = Modifier
        .padding(16.dp)
        .align(Alignment.CenterVertically)
        .weight(1f)
        .fillMaxWidth(),
      isError = error.isNotEmpty(),
      singleLine = true,

    )
    FloatingActionButton(
      onClick = {
        // User Id format: 2 words and a 3 digits number separated by either a dot or a dash
        if (bleBinder != null && userId.isNotEmpty() && userId != myUserId && Regex(
            "^[a-zA-Z]+[\\.-]?[a-zA-Z]+[\\.-]?[0-9]{3}\$"
          ).matches(userId)
        ) {
          bleBinder.addDevice(userId)
          onBack()
        } else {
          setError("Please enter a valid user ID")
        }
      }, modifier = Modifier
        .align(Alignment.CenterVertically)
        .padding(end = 16.dp, top = 8.dp)
    ) {
      Icon(Icons.Rounded.Add, contentDescription = "Add device")
    }
  }
}
