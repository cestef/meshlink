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
import com.cstef.meshlink.db.entities.Device
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

  val isAdvertising = mutableStateOf(false)
  val isStarted = mutableStateOf(false)
  val isScanning = mutableStateOf(false)
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
      serviceHandler,
      this
    )
  private val serverManager =
    ServerBleManager(context, dataExchangeManager, callbackHandler, encryptionManager, this)

  private val canBeClient: Boolean = adapter != null &&
    context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

  // Bluetooth must currently be turned on for the check to succeed
  private val canBeServer: Boolean
    get() = adapter.bluetoothLeAdvertiser != null

  @SuppressLint("MissingPermission")
  fun stop() {
    Log.d(tag, "BleManager stopped")
    if (canBeClient) clientManager.stop()
    if (canBeServer) serverManager.stopAdvertising()
    isStarted.value = false
  }

  fun sendMessage(message: Message) {
    val deviceAddress = clientManager.connectedServersAddresses[message.recipientId]
    if (deviceAddress != null && clientManager.connectedGattServers.containsKey(deviceAddress)) {
      Log.d(tag, clientManager.connectedGattServers[deviceAddress]?.device?.address!!)
      Log.d(tag, "Sending data to ${message.recipientId}")
      clientManager.sendData(message)
    } else {
      Log.d(tag, "No connection to ${message.recipientId}, broadcasting data")
      if (message.type == Message.Type.TEXT) {
        clientManager.broadcastData(message)
      } else {
        Log.e(tag, "Cannot broadcast other than text messages")
      }
    }
  }

  fun getUserIdForAddress(address: String): String? {
    Log.d(
      tag,
      "Getting user id for address $address, connected servers: ${clientManager.connectedServersAddresses}"
    )
    return clientManager.connectedServersAddresses.entries.find { it.value == address }?.key
  }

  fun disconnectAll() {
    clientManager.disconnectAll()
  }

  fun disconnect(userId: String) {
    clientManager.disconnect(userId)
  }

  fun startScanning() {
    if (canBeClient) clientManager.startScanning()
  }

  fun stopScanning() {
    clientManager.stopScanning()
  }

  fun startAdvertising() {
    if (canBeServer) serverManager.startAdvertising()
  }

  fun stopAdvertising() {
    serverManager.stopAdvertising()
  }

  fun setUserId(id: String) {
    clientManager.setUserId(id)
    serverManager.setUserId(id)
  }

  fun openServer() {
    if (canBeServer) serverManager.openServer()
  }

  fun closeServer() {
    serverManager.closeServer()
  }

  fun startClient() {
    if (canBeClient) clientManager.start()
  }
//  fun sendIsWriting(userId: String, writing: Boolean) {
//    val deviceAddress = clientManager.connectedServersAddresses[userId]
//    if (deviceAddress != null && clientManager.connectedGattServers.containsKey(deviceAddress)) {
//      Log.d(tag, "Sending isWriting to $userId")
//      clientManager.sendIsWriting(userId, writing)
//    }
//  }

//  fun isConnected(deviceId: String): Boolean {
//    return clientManager.connectedServersAddresses.containsKey(deviceId)
//  }

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
    fun onUserConnected(userId: String, serverAddress: String?, publicKey: PublicKey?) {}

    /**
     * @param userId ID of the remote BLE device
     */
    fun onUserDisconnected(userId: String) {}
    fun onUserPublicKeyReceived(userId: String, serverAddress: String, publicKey: PublicKey) {}
    fun getUsername(): String = ""
    fun getPublicKeyForUser(recipientId: String): PublicKey?
    fun onUserRssiReceived(userId: String, rssi: Int) {}
    fun onMessageSent(userId: String) {}
    fun onUserWriting(userId: String, isWriting: Boolean) {}
    fun getUserIdForAddress(address: String): String? = ""
    fun onMessageSendFailed(userId: String?, reason: String?) {}
    fun getKnownDevices(): List<Device> = emptyList()
    fun getAddressForUserId(userId: String): String = ""
    fun onUserAdded(userId: String)
  }
}
