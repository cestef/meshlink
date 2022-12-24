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
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cstef.meshlink.screens.*
import com.cstef.meshlink.ui.theme.AppTheme
import com.cstef.meshlink.util.generateFriendlyId
import com.daveanthonythomas.moshipack.MoshiPack

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
      Column(
        modifier = Modifier
          .fillMaxSize()
          .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center
        ) {
          Text(
            text = "MeshLink",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
          )
        }
      }
    }
  }

  @ExperimentalAnimationApi
  @ExperimentalMaterial3Api
  @Composable
  private fun App(firstTime: Boolean) {
    val moshi = remember {
      MoshiPack()
    }
    AppTheme {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            MaterialTheme.colorScheme.background,
          )
      ) {
        val navController = rememberNavController()
        val isDatabaseOpen by bleBinder!!.isDatabaseOpen.observeAsState(false)
        val isDatabaseOpening by bleBinder!!.isDatabaseOpening.observeAsState(false)
        val databaseError by bleBinder!!.databaseError.observeAsState("")
        val context = LocalContext.current
        val sharedPreferences = context.getSharedPreferences("USER_SETTINGS", Context.MODE_PRIVATE)
        val isDefaultPassword by remember {
          mutableStateOf(sharedPreferences.getBoolean("is_default_password", false))
        }
        NavHost(navController = navController, startDestination = "home") {
          composable("home") {
            if (isDatabaseOpen) {
              Box(modifier = Modifier.fillMaxSize()) {
                if (bleBinder != null) {
                  HomeScreen(bleBinder!!,
                    userId,
                    { navController.navigate("user/$userId") },
                    { navController.navigate("user/$it") },
                    { navController.navigate("chat/$it") })
                  // Manually add a device via its ID
                  FloatingActionButton(
                    onClick = {
                      navController.navigate("add")
                    }, modifier = Modifier
                      .align(Alignment.BottomEnd)
                      .padding(24.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.Add,
                      contentDescription = "Add device",
                    )
                  }
                }
              }
            } else if (isDatabaseOpening) {
              Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(
                  modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
                )
              }
            } else {
              Box(modifier = Modifier.fillMaxSize()) {
                Text(
                  text = databaseError,
                  modifier = Modifier.align(Alignment.Center),
                  style = MaterialTheme.typography.titleLarge,
                  color = MaterialTheme.colorScheme.onBackground
                )
              }
            }
          }
          composable(
            "chat/{deviceId}",
            arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
          ) { backStackEntry ->
            bleBinder?.let { binder ->
              ChatScreen(
                binder, backStackEntry.arguments?.getString("deviceId")
              ) {
                // Navigate to user info screen
                navController.navigate("user/$it")
              }
            }
          }
          composable("add") {
            bleBinder?.let { binder ->
              AddDeviceScreen(binder, moshi) { user ->
                if (user != null) {
                  navController.navigate("user/${user}")
                } else {
                  navController.popBackStack()
                }
              }
            }
          }
          composable(
            "user/{deviceId}",
            arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
          ) { backStackEntry ->
            // User info screen
            bleBinder?.let {
              UserInfoScreen(it,
                backStackEntry.arguments?.getString("deviceId"),
                backStackEntry.arguments?.getString("deviceId") == userId,
                onBack = { navController.popBackStack() }) {
                navController.navigate("settings")
              }
            }
          }
          composable("settings") {
            bleBinder?.let { binder ->
              SettingsScreen(
                binder,
                startAdvertising = {
                  startAdvertising()
                },
                stopAdvertising = {
                  stopAdvertising()
                },
              ) {
                navController.navigate("about")
              }
            }
          }
          composable("about") {
            AboutScreen()
          }
          composable("password") {
            bleBinder?.let { binder ->
              PasswordScreen(
                binder,
                firstTime,
                isDatabaseOpening
              )
            }
          }
        }
        LaunchedEffect(databaseError) {
          if (databaseError.isNotEmpty()) {
            Toast.makeText(this@MainActivity, databaseError, Toast.LENGTH_LONG).show()
          }
        }
        if (!isDatabaseOpen && !isDatabaseOpening) {
          if (!isDefaultPassword) {
            navController.navigate("password") {
              popUpTo("password") { inclusive = true }
            }
          } else {
            LaunchedEffect(Unit) {
              bleBinder?.openDatabase(null)
            }
          }
        } else {
          LaunchedEffect(Unit) {
            if (sharedPreferences.getBoolean("first_time", true)) {
              val editor = sharedPreferences.edit()
              editor.putBoolean("first_time", false)
              editor.apply()
            }
            if (!checkBluetoothEnabled()) {
              Toast.makeText(this@MainActivity, "Bluetooth is disabled", Toast.LENGTH_LONG).show()
            } else {
              start()
            }
            navController.navigate("home") {
              popUpTo("home") { inclusive = true }
            }
          }
        }
      }
    }
  }

  private fun checkBluetoothEnabled(): Boolean {
    val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    return bluetoothManager.adapter.isEnabled
  }

  private fun start() {
    _requestPermissionsForBoth.launch(
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
          Manifest.permission.ACCESS_FINE_LOCATION,
          Manifest.permission.ACCESS_COARSE_LOCATION,
          Manifest.permission.BLUETOOTH,
          Manifest.permission.BLUETOOTH_ADVERTISE,
          Manifest.permission.BLUETOOTH_CONNECT,
          Manifest.permission.BLUETOOTH_SCAN,
        )
      } else {
        arrayOf(
          Manifest.permission.ACCESS_FINE_LOCATION,
          Manifest.permission.ACCESS_COARSE_LOCATION,
          Manifest.permission.BLUETOOTH,
          Manifest.permission.BLUETOOTH_ADMIN,
        )
      }
    )
  }

  override fun onDestroy() {
    super.onDestroy()
    unbindService()
  }

  private val _requestEnableBluetooth =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode != RESULT_OK) {
        Toast.makeText(this, "Bluetooth is required to scan", Toast.LENGTH_SHORT).show()
      }
    }

  private fun requestEnableBluetooth() {
    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
    _requestEnableBluetooth.launch(enableBtIntent)
  }

  private val _requestPermissionsForBoth =
    registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
      if (permissions.all { it.value }) {
        bleBinder?.startClient()
        bleBinder?.openServer()
        val sharedPreferences = getSharedPreferences("USER_SETTINGS", Context.MODE_PRIVATE)
        val advertise = sharedPreferences.getBoolean("is_advertising", true)
        if (advertise) {
          startAdvertising()
        }
      } else {
        Toast.makeText(this, "Permissions are required to scan", Toast.LENGTH_SHORT).show()
      }
    }

  private val checkLocation = {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_COARSE_LOCATION
      ) == PackageManager.PERMISSION_GRANTED
    }
  }

  private val checkBluetooth = {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
      ContextCompat.checkSelfPermission(
        this, Manifest.permission.BLUETOOTH_ADMIN
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      ContextCompat.checkSelfPermission(
        this, Manifest.permission.BLUETOOTH_ADVERTISE
      ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
        this, Manifest.permission.BLUETOOTH_SCAN
      ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
        this, Manifest.permission.BLUETOOTH_CONNECT
      ) == PackageManager.PERMISSION_GRANTED
    }
  }

  private fun startAdvertising() {
    val sharedPreferences = getSharedPreferences("USER_SETTINGS", Context.MODE_PRIVATE)
    val editor = sharedPreferences.edit()
    editor.putBoolean("is_advertising", true)
    editor.apply()
    bleBinder?.startAdvertising()
  }

  private fun stopAdvertising() {
    val sharedPreferences = getSharedPreferences("USER_SETTINGS", Context.MODE_PRIVATE)
    val editor = sharedPreferences.edit()
    editor.putBoolean("is_advertising", false)
    editor.apply()
    bleBinder?.stopAdvertising()
  }

  private fun createNotificationChannels() {
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
      bleBinder!!.setUserId(userId)
      // get isFirstTime from shared preferences
      val sharedPreferences = getSharedPreferences("USER_SETTINGS", Context.MODE_PRIVATE)
      val isFirstTime = sharedPreferences.getBoolean("first_time", true)

      setContent {
        App(isFirstTime)
      }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
      bleService = null
    }
  }
}
