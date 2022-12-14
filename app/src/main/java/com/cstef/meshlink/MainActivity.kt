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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cstef.meshlink.managers.isBleOn
import com.cstef.meshlink.screens.*
import com.cstef.meshlink.ui.theme.AppTheme
import com.cstef.meshlink.ui.theme.DarkColors
import com.cstef.meshlink.ui.theme.LightColors
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
          .background(if (isSystemInDarkTheme()) DarkColors.background else LightColors.background),
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
            color = if (isSystemInDarkTheme()) DarkColors.onBackground else LightColors.onBackground,
          )
        }
      }
    }
  }

  @ExperimentalAnimationApi
  @ExperimentalMaterial3Api
  @Composable
  private fun App(firstTime: Boolean, moshi: MoshiPack) {
    AppTheme {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            if (isSystemInDarkTheme()) DarkColors.background else LightColors.background,
          )
      ) {
        val navController = rememberNavController()
        val isDatabaseOpen by bleBinder!!.isDatabaseOpen.observeAsState(false)
        val isDatabaseOpening by bleBinder!!.isDatabaseOpening.observeAsState(false)
        val databaseError by bleBinder!!.databaseError.observeAsState("")

        LaunchedEffect(databaseError) {
          if (databaseError.isNotEmpty()) {
            Toast.makeText(this@MainActivity, databaseError, Toast.LENGTH_LONG).show()
          }
        }
        if (!isDatabaseOpen && !isDatabaseOpening) {
          // Prompt the user to enter the master database password
          val (masterPassword, setMasterPassword) = remember { mutableStateOf("") }
          AlertDialog(
            onDismissRequest = {},
            title = {
              Text(text = if (firstTime) "Set Master Password" else "Enter Master Password")
            },
            text = {
              var passwordVisible by rememberSaveable { mutableStateOf(false) }
              Column {
                if (firstTime) {
                  Text(
                    text = "This password will be used to encrypt your database.",
                    modifier = Modifier.padding(bottom = 16.dp)
                  )
                }
                OutlinedTextField(
                  value = masterPassword,
                  onValueChange = { setMasterPassword(it) },
                  label = { Text(text = "Master password") },
                  singleLine = true,
                  visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                  modifier = Modifier.fillMaxWidth(),
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                  trailingIcon = {
                    val image = if (passwordVisible)
                      Icons.Filled.Visibility
                    else Icons.Filled.VisibilityOff
                    val description = if (passwordVisible) "Hide password" else "Show password"
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                      Icon(imageVector = image, description)
                    }
                  }
                )
                if (firstTime) {
                  Text(
                    text = "You can change it later in your user profile.",
                    modifier = Modifier.padding(top = 16.dp)
                  )
                }
              }
            },
            confirmButton = {
              Button(
                onClick = {
                  if (masterPassword.isNotEmpty()) {
                    bleBinder?.openDatabase(masterPassword)
                  }
                },
                content = { Text(text = "Confirm") }
              )
            },
            dismissButton = {
              TextButton(
                onClick = { finish() },
                content = { Text(text = "Cancel") }
              )
            }
          )
        } else if (isDatabaseOpening) {
          AlertDialog(
            onDismissRequest = {},
            title = { Text(text = "Opening database...") },
            confirmButton = {})
        } else {
          LaunchedEffect(Unit) {
            val sharedPreferences = getSharedPreferences("USER_SETTINGS", Context.MODE_PRIVATE)
            if (sharedPreferences.getBoolean("first_time", true)) {
              val editor = sharedPreferences.edit()
              editor.putBoolean("first_time", false)
              editor.apply()
            }
            val scan = sharedPreferences.getBoolean("is_scanning", true)
            val advertise = sharedPreferences.getBoolean("is_advertising", true)

            openServer()
            startClient()

            if (advertise) {
              startAdvertising()
            }
            if (scan) {
              startScanning()
            }
          }
          NavHost(navController = navController, startDestination = "home") {
            composable("home") {
              Box(modifier = Modifier.fillMaxSize()) {
                if (bleBinder != null) {
                  HomeScreen(
                    bleBinder!!,
                    userId,
                    { navController.navigate("user/$userId") },
                    { navController.navigate("user/$it") },
                    { navController.navigate("chat/$it") }
                  )
                  // Manually add a device via its ID
                  FloatingActionButton(
                    onClick = {
                      navController.navigate("add")
                    },
                    modifier = Modifier
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
                UserInfoScreen(
                  it,
                  backStackEntry.arguments?.getString("deviceId"),
                  backStackEntry.arguments?.getString("deviceId") == userId,
                  onBack = { navController.popBackStack() }
                ) {
                  navController.navigate("settings")
                }
              }
            }
            composable("settings") {
              bleBinder?.let { binder ->
                SettingsScreen(
                  binder,
                  startScanning = {
                    startScanning()
                  },
                  stopScanning = {
                    stopScanning()
                  },
                  startAdvertising = {
                    startAdvertising()
                  },
                  stopAdvertising = {
                    stopAdvertising()
                  },
                )
              }
            }
          }
        }
      }
    }
  }

  private fun startClient() {
    requestPermissions {
      bleBinder?.startClient()
    }
  }

  private fun openServer() {
    requestPermissions {
      bleBinder?.openServer()
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    unbindService()
  }

  private val requestEnableBluetooth =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode != RESULT_OK) {
        Toast.makeText(this, "Bluetooth is required to scan", Toast.LENGTH_SHORT).show()
        requestPermissions()
      }
    }
  private val _requestLocation =
    registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
      // Check if all permissions are granted
      if (result.values.all { it }) {
        // TODO
      } else {
        Toast.makeText(this, "Location Permission required to scan", Toast.LENGTH_SHORT).show()
        requestPermissions()
      }
    }
  private val requestLocation = {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      _requestLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
    } else {
      _requestLocation.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION))
    }
  }

  private val checkLocation = {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_COARSE_LOCATION
      ) == PackageManager.PERMISSION_GRANTED
    }
  }

  private val _requestBluetooth =
    registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
      // Check if all permissions are granted
      if (result.values.all { it }) {
        // TODO
      } else {
        Toast.makeText(this, "Bluetooth Permission required to scan", Toast.LENGTH_SHORT).show()
        requestPermissions()
      }
    }

  private val requestBluetooth = {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
      _requestBluetooth.launch(
        arrayOf(
          Manifest.permission.BLUETOOTH_ADMIN
        )
      )
    } else {
      _requestBluetooth.launch(
        arrayOf(
          Manifest.permission.BLUETOOTH_ADVERTISE,
          Manifest.permission.BLUETOOTH_SCAN,
          Manifest.permission.BLUETOOTH_CONNECT
        )
      )
    }
  }

  private val checkBluetooth = {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
      ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.BLUETOOTH_ADMIN
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.BLUETOOTH_ADVERTISE
      ) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
          this,
          Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
          this,
          Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }
  }

  private fun requestPermissions(onSuccess: () -> Unit = {}) {
    if (!checkBluetooth()) {
      Log.d("MainActivity", "Requesting bluetooth permissions")
      requestBluetooth()
    }
    val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
    if (!adapter.isBleOn) {
      Log.d("MainActivity", "Requesting Bluetooth")
      requestEnableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }
    if (!checkLocation()) {
      Log.d("MainActivity", "Requesting location permission")
      requestLocation()
    }
    if (checkBluetooth() && adapter.isBleOn && checkLocation()) {
      Log.d("MainActivity", "All permissions granted")
      onSuccess()
    }
  }

  private fun startAdvertising() {
    requestPermissions {
      val sharedPreferences = getSharedPreferences("USER_SETTINGS", Context.MODE_PRIVATE)
      val editor = sharedPreferences.edit()
      editor.putBoolean("is_advertising", true)
      editor.apply()
      bleBinder?.startAdvertising()
    }
  }

  private fun stopAdvertising() {
    val sharedPreferences = getSharedPreferences("USER_SETTINGS", Context.MODE_PRIVATE)
    val editor = sharedPreferences.edit()
    editor.putBoolean("is_advertising", false)
    editor.apply()
    bleBinder?.stopAdvertising()
  }

  private fun startScanning() {
    requestPermissions {
      val sharedPreferences = getSharedPreferences("USER_SETTINGS", Context.MODE_PRIVATE)
      val editor = sharedPreferences.edit()
      editor.putBoolean("is_scanning", true)
      editor.apply()
      bleBinder?.startScanning()
    }
  }

  private fun stopScanning() {
    val sharedPreferences = getSharedPreferences("USER_SETTINGS", Context.MODE_PRIVATE)
    val editor = sharedPreferences.edit()
    editor.putBoolean("is_scanning", false)
    editor.apply()
    bleBinder?.stopScanning()
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
        App(isFirstTime, MoshiPack())
      }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
      bleService = null
    }
  }
}
