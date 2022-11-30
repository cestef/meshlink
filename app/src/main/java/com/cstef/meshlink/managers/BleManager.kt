package com.cstef.meshlink.managers


import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.cstef.meshlink.BleService
import com.cstef.meshlink.util.struct.Chunk
import com.cstef.meshlink.util.struct.Message
import java.security.PublicKey

val BluetoothAdapter?.isBleOn get() = this != null && isEnabled

/**
 * Manages BLE connections and operations from this device to other devices with this
 * application installed. A single device can act as both a BLE client and server
 * at the same time.
 */
class BleManager(
  private val context: Context,
  dataExchangeManager: BleDataExchangeManager,
  encryptionManager: EncryptionManager,
  serviceHandler: Handler
) {

  var isStarted = mutableStateOf(false)
  private val tag = BleManager::class.java.canonicalName
  private val adapter get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

  // BLE Callbacks are executed on a binder thread. This handlers gets the work
  // off that binder thread as soon as possible and back onto the thread the
  // dataExchangeManager passed to this class expects.
  private val callbackHandler = Handler(Looper.getMainLooper())

  private val clientManager =
    ClientBleManager(
      context,
      dataExchangeManager,
      callbackHandler,
      encryptionManager,
      serviceHandler
    )
  private val serverManager =
    ServerBleManager(context, dataExchangeManager, callbackHandler, encryptionManager)

  private val canBeClient: Boolean = adapter != null &&
    context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

  // Bluetooth must currently be turned on for the check to succeed
  private val canBeServer: Boolean
    get() = adapter.bluetoothLeAdvertiser != null

  fun start(userId: String) {
    Log.d(tag, "BleManager started")
    Log.d(tag, "canBeClient: $canBeClient")
    Log.d(tag, "canBeServer: $canBeServer")
    if (canBeClient) clientManager.start(userId)
    if (canBeServer) serverManager.start(userId)
    isStarted.value = true
  }

  @SuppressLint("MissingPermission")
  fun stop() {
    Log.d(tag, "BleManager stopped")
    if (canBeClient) clientManager.stop()
    if (canBeServer) serverManager.stop()
    isStarted.value = false
  }

  fun sendData(message: Message) {
    val deviceAddress = clientManager.connectedServersIds[message.recipientId]
    if (deviceAddress != null && clientManager.connectedGattServers.containsKey(deviceAddress)) {
      Log.d(tag, clientManager.connectedGattServers[deviceAddress]?.device?.address!!)
      Log.d(tag, "Sending data to ${message.recipientId}")
      clientManager.sendData(message)
      val intent = Intent(BleService.ACTION_MESSAGES)
      context.sendBroadcast(intent)
    } else {
      Log.d(tag, "No connection to ${message.recipientId}, broadcasting data")
      if (message.type == Message.Type.TEXT) {
        clientManager.broadcastData(message)
        val intent = Intent(BleService.ACTION_MESSAGES)
        context.sendBroadcast(intent)
      } else {
        Log.e(tag, "Cannot broadcast other than text messages")
      }
    }
  }

  /**
   * Methods used for swapping data between a remote BLE client or server
   */
  interface BleDataExchangeManager {

    /**
     * @param chunk Chunk received from a remote BLE device
     * @param address Address of the remote device
     */
    fun onChunkReceived(chunk: Chunk, address: String) {}

    /**
     * @param userId ID of the remote BLE device
     */
    fun onUserConnected(userId: String) {}

    /**
     * @param userId ID of the remote BLE device
     */
    fun onUserDisconnected(userId: String) {}
    fun onUserPublicKeyReceived(userId: String, publicKey: PublicKey) {}
    fun getUsername(): String = ""
    fun getPublicKeyForUser(recipientId: String): PublicKey?
    fun onUserRssiReceived(userId: String, rssi: Int) {}
    fun onMessageSent(userId: String) {}
  }
}
