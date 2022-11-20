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
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cstef.meshlink.ble.isBleOn
import com.cstef.meshlink.util.RequestCode
import com.cstef.meshlink.util.generateFriendlyId

class MainActivity : AppCompatActivity() {

  var bleBinder: BleService.BleServiceBinder? = null
  val isBleStarted get() = bleBinder?.isBleStarted ?: false
  var bleService: BleService? = null
  private val serviceConnection = BleServiceConnection()
  private var isServiceBound = false
  var userId: String = ""
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    @Suppress("DEPRECATION")
    window.setFlags(
      WindowManager.LayoutParams.FLAG_FULLSCREEN,
      WindowManager.LayoutParams.FLAG_FULLSCREEN
    )
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    setContentView(R.layout.activity_main)
    val sharedPreference = getSharedPreferences("USER_SETTINGS", Context.MODE_PRIVATE)
    if (sharedPreference.getString("USER_ID", null) == null) {
      val editor = sharedPreference.edit()
      userId = generateFriendlyId()
      editor.putString("USER_ID", userId)
      editor.apply()
    } else {
      userId = sharedPreference.getString("USER_ID", null) ?: generateFriendlyId()
    }
    createNotificationChannel()
    bindService()
    supportFragmentManager.beginTransaction()
      .replace(R.id.fragment_container, ScanFragment())
      .commit()
  }

  override fun onDestroy() {
    super.onDestroy()
    unbindService()
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == RequestCode.ACCESS_COARSE_LOCATION) {
      if (grantResults.isNotEmpty() &&
        grantResults.first() == PackageManager.PERMISSION_GRANTED
      ) {
        startBle()
      } else {
        Toast.makeText(this, "Location Permission required to scan", Toast.LENGTH_SHORT).show()
        val startButton = findViewById<Button>(R.id.startButton)
        startButton.text = getString(R.string.start_ble)
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
      if (permissions.all { it.value } || permissions[Manifest.permission.BLUETOOTH_ADMIN]!!) {
        startBle()
      } else {
        Toast.makeText(this, "Not every permission granted, check logs", Toast.LENGTH_SHORT).show()
      }
    }

  fun startBle() {
    val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
    if (!adapter.isBleOn) {
      val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
      requestBluetooth.launch(enableBtIntent)
      return
    }

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
          this, Manifest.permission.BLUETOOTH_ADMIN
        ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
          this, Manifest.permission.BLUETOOTH_CONNECT
        ) != PackageManager.PERMISSION_GRANTED
      ) {
        requestMultiplePermissions.launch(
          arrayOf(
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
          )
        )

        return
      }
    }

    if (ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_COARSE_LOCATION
      ) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION
      ) != PackageManager.PERMISSION_GRANTED
    ) {
      requestMultiplePermissions.launch(
        arrayOf(
          Manifest.permission.ACCESS_FINE_LOCATION,
          Manifest.permission.ACCESS_COARSE_LOCATION
        )
      )
      return
    }
    bleService?.startBle(userId)
    Toast.makeText(this, "Ble Started", Toast.LENGTH_SHORT).show()
    val startButton = findViewById<Button>(R.id.startButton)
    startButton.text = getString(R.string.stop_ble)
  }

  fun stopBle() {
    bleService?.stopBle()
    Toast.makeText(this, "Ble Stopped", Toast.LENGTH_SHORT).show()
    val startButton = findViewById<Button>(R.id.startButton)
    startButton.text = getString(R.string.start_ble)
  }

  private fun createNotificationChannel() {
    // Create the NotificationChannel, but only on API 26+ because
    // the NotificationChannel class is new and not in the support library
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val name = getString(R.string.channel_name)
      val descriptionText = getString(R.string.channel_description)
      val importance = NotificationManager.IMPORTANCE_DEFAULT
      val channel = NotificationChannel("data", name, importance).apply {
        description = descriptionText
      }
      // Register the channel with the system
      val notificationManager: NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      notificationManager.createNotificationChannel(channel)
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
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
      Log.d("test006", "onServiceConnected")
      bleService = (service as BleService.BleServiceBinder).service
      bleBinder = service
      startBle()
    }

    override fun onServiceDisconnected(name: ComponentName?) {
      bleService = null
    }
  }
}
