package com.cstef.meshlink

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cstef.meshlink.managers.isBleOn
import com.cstef.meshlink.screens.AddDeviceScreen
import com.cstef.meshlink.screens.ChatScreen
import com.cstef.meshlink.screens.ScanScreen
import com.cstef.meshlink.ui.theme.AppTheme
import com.cstef.meshlink.ui.theme.DarkColors
import com.cstef.meshlink.ui.theme.LightColors
import com.cstef.meshlink.util.RequestCode
import com.cstef.meshlink.util.generateFriendlyId

class MainActivity : AppCompatActivity() {

  var bleBinder: BleService.BleServiceBinder? = null
  var bleService: BleService? = null
  private val serviceConnection = BleServiceConnection()
  private var isServiceBound = false
  private var userId: String = ""

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    setContent {
      LoadingScreen()
    }
    val sharedPreference = getSharedPreferences("USER_SETTINGS", Context.MODE_PRIVATE)
    if (sharedPreference.getString("USER_ID", null) == null) {
      val editor = sharedPreference.edit()
      userId = generateFriendlyId()
      editor.putString("USER_ID", userId)
      editor.apply()
    } else {
      userId = sharedPreference.getString("USER_ID", null) ?: generateFriendlyId()
    }
    createNotificationChannels()
    bindService()
  }

  @Composable
  private fun LoadingScreen() {
    AppTheme {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(if (isSystemInDarkTheme()) DarkColors.background else LightColors.background)
      ) {
        CircularProgressIndicator()
      }
    }
  }

  @ExperimentalAnimationApi
  @ExperimentalMaterial3Api
  @Composable
  private fun App() {
    AppTheme {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            if (isSystemInDarkTheme()) DarkColors.background else LightColors.background,
          )
      ) {
        val started by bleBinder?.isBleStarted!!
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = "scan") {
          composable("scan") {
            Box(modifier = Modifier.fillMaxSize()) {
              ScanScreen(bleBinder, userId) { navController.navigate("chat/$it") }
              // Manually add a device via its ID
              FloatingActionButton(
                onClick = {
                  navController.navigate("add")
                },
                modifier = Modifier
                  .align(Alignment.BottomStart)
                  .padding(24.dp)
              ) {
                Icon(
                  imageVector = Icons.Rounded.Add,
                  contentDescription = "Add device",
                )
              }
              ExtendedFloatingActionButton(
                onClick = {
                  if (started) {
                    stopBle()
                  } else {
                    startBle()
                  }
                },
                icon = {
                  Icon(
                    imageVector = if (started) Icons.Rounded.Close else Icons.Filled.PlayArrow,
                    contentDescription = "Start/Stop"
                  )
                },
                text = { Text(text = if (started) "Stop" else "Start") },
                modifier = Modifier
                  .padding(24.dp)
                  .align(Alignment.BottomEnd),
              )
            }
          }
          composable(
            "chat/{deviceId}",
            arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
          ) { backStackEntry ->
            ChatScreen(
              bleBinder, backStackEntry.arguments?.getString("deviceId")
            ) {
              navController.popBackStack()
            }
          }
          composable("add") {
            AddDeviceScreen(bleBinder, userId) {
              navController.popBackStack()
            }
          }
        }
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    unbindService()
  }

  override fun onRequestPermissionsResult(
    requestCode: Int, permissions: Array<out String>, grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == RequestCode.ACCESS_COARSE_LOCATION) {
      if (grantResults.isNotEmpty() && grantResults.first() == PackageManager.PERMISSION_GRANTED) {
        startBle()
      } else {
        Toast.makeText(this, "Location Permission required to scan", Toast.LENGTH_SHORT).show()
      }
    }
  }

  private var requestBluetooth =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == RESULT_OK) {
        startBle()
      } else {
        Toast.makeText(this, "Bluetooth required to scan", Toast.LENGTH_SHORT).show()
      }
    }
  private val requestMultiplePermissions =
    registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
      permissions.entries.forEach {
        Log.d("test006", "${it.key} = ${it.value}")
      }
      if (permissions.filter { it.key != Manifest.permission.BLUETOOTH_ADMIN }
          .all { it.value } || permissions[Manifest.permission.BLUETOOTH_ADMIN] == true) {
        startBle()
      } else {
        Toast.makeText(this, "Not every permission granted, check logs", Toast.LENGTH_SHORT).show()
      }
    }

  fun startBle() {

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
      if (ContextCompat.checkSelfPermission(
          this, Manifest.permission.BLUETOOTH_ADMIN
        ) != PackageManager.PERMISSION_GRANTED
      ) {
        requestMultiplePermissions.launch(
          arrayOf(
            Manifest.permission.BLUETOOTH_ADMIN
          )
        )
        return
      }
    } else {
      if (ContextCompat.checkSelfPermission(
          this, Manifest.permission.BLUETOOTH_ADVERTISE
        ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
          this, Manifest.permission.BLUETOOTH_CONNECT
        ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
          this, Manifest.permission.BLUETOOTH_SCAN
        ) != PackageManager.PERMISSION_GRANTED
      ) {
        requestMultiplePermissions.launch(
          arrayOf(
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
          )
        )

        return
      }
    }
    val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
    if (!adapter.isBleOn) {
      val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
      requestBluetooth.launch(enableBtIntent)
      return
    }

    if (ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_COARSE_LOCATION
      ) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION
      ) != PackageManager.PERMISSION_GRANTED
    ) {
      requestMultiplePermissions.launch(
        arrayOf(
          Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
        )
      )
      return
    }
    bleService?.startBle(userId)
    Toast.makeText(this, "Ble Started", Toast.LENGTH_SHORT).show()
  }

  fun stopBle() {
    bleService?.stopBle()
    Toast.makeText(this, "Ble Stopped", Toast.LENGTH_SHORT).show()
  }

  private fun createNotificationChannels() {
    // Create the NotificationChannel, but only on API 26+ because
    // the NotificationChannel class is new and not in the support library
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val name = getString(R.string.channel_name)
      val descriptionText = getString(R.string.channel_description)
      val importance = NotificationManager.IMPORTANCE_DEFAULT
      val dataChannel = NotificationChannel("data", name, importance).apply {
        description = descriptionText
      }
      val messagesChannel = NotificationChannel("messages", name, importance).apply {
        description = descriptionText
      }
      // Register the channel with the system
      val notificationManager: NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      notificationManager.createNotificationChannel(dataChannel)
      notificationManager.createNotificationChannel(messagesChannel)
    }
  }

  private fun bindService() {
    val intent = Intent(this, BleService::class.java)
    intent.putExtra("user_id", userId)
    bindService(intent, serviceConnection as ServiceConnection, BIND_AUTO_CREATE)
    Log.d("test006", "bindService")
    isServiceBound = true
  }

  private fun unbindService() {
    if (isServiceBound) {
      unbindService(serviceConnection as ServiceConnection)
    }
    isServiceBound = false
  }

  inner class BleServiceConnection : ServiceConnection {
    @ExperimentalAnimationApi
    @ExperimentalMaterial3Api
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
      Log.d("test006", "onServiceConnected")
      bleService = (service as BleService.BleServiceBinder).service
      bleBinder = service
      startBle()
      setContent {
        App()
      }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
      bleService = null
    }
  }
}
