package com.cstef.meshlink.screens

import android.util.Base64
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LiveData
import com.cstef.meshlink.db.entities.Device
import com.cstef.meshlink.ui.components.AvailableDevice
import com.cstef.meshlink.util.struct.QrData
import com.daveanthonythomas.moshipack.MoshiPack
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceScreen(
  allDevices: LiveData<List<Device>>,
  addDevice: (userId: String) -> Unit,
  onBack: (String?) -> Unit,
) {
  val moshi = remember { MoshiPack() }
  val (error, setError) = remember {
    mutableStateOf("")
  }
  val devices by allDevices.observeAsState(listOf())
  val noDevicesNearbyMessage = remember {
    val messages = listOf(
      "No devices nearby :(",
      "It's empty here...",
      "No devices found :(",
      "Try walking around a bit",
      "Is there anyone here ?",
    )
    messages.random()
  }
  Column(modifier = Modifier.fillMaxSize()) {
    TopAppBar(
      title = { Text(text = "Nearby devices") },
    )
    if (devices.any { !it.added && it.connected }) {
      LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        modifier = Modifier
          .fillMaxSize()
          .weight(1f)
      ) {
        items(
          devices.filter { it.connected && !it.added }
        ) { device ->
          AvailableDevice(device = device, onDeviceClick = {
            addDevice(device.userId)
            onBack(null)
          })
        }
      }
    } else {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .weight(1f)
      ) {
        Text(
          text = noDevicesNearbyMessage,
          style = MaterialTheme.typography.titleLarge,
          textAlign = TextAlign.Center,
          modifier = Modifier
            .align(Alignment.Center)
            .padding(vertical = 32.dp, horizontal = 16.dp),
          color = MaterialTheme.colorScheme.onBackground
        )
      }
    }
    val scanLauncher = rememberLauncherForActivityResult(
      contract = ScanContract(),
      onResult = { result ->
        if (result != null) {
          Log.d("AddDeviceScreen", "Scanned: ${result.contents}")
          try {
            val bytes = Base64.decode(result.contents, Base64.DEFAULT)
            val data = moshi.unpack<QrData>(bytes)
            val otherUserId = data.userId
//            val publicKeyString = data.publicKey
//            val publicKey = bleBinder?.getPublicKeyFromString(publicKeyString)
//            val address = data.address
            Log.d(
              "AddDeviceScreen",
              "userId: $otherUserId"
            )
            addDevice(otherUserId)
            onBack(otherUserId)
          } catch (e: Exception) {
            Log.e("AddDeviceScreen", "Error parsing QR code", e)
            setError("Invalid QR code")
          }
        }
      }
    )
    Button(
      onClick = {
        scanLauncher.launch(
          ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
            .setPrompt("Scan a QR code")
            .setBeepEnabled(false)
            .setBarcodeImageEnabled(false)
            .setOrientationLocked(false)
        )
      },
      modifier = Modifier
        .padding(16.dp)
        .height(48.dp)
        .fillMaxWidth(),
    ) {
      Text("Scan QR code", fontSize = 18.sp)
    }
    if (error.isNotEmpty()) {
      Text(
        text = error,
        modifier = Modifier.padding(16.dp),
        color = MaterialTheme.colorScheme.error
      )
    }
  }
}
