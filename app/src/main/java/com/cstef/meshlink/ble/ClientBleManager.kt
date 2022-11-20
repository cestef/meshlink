package com.cstef.meshlink.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.ParcelUuid
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cstef.meshlink.EncryptionManager
import com.cstef.meshlink.R
import com.cstef.meshlink.chat.Message
import com.cstef.meshlink.util.BleUuid
import com.cstef.meshlink.util.struct.KeyData
import com.cstef.meshlink.util.struct.OperationQueue
import com.daveanthonythomas.moshipack.MoshiPack
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.*

class ClientBleManager(
  private val context: Context,
  private val dataExchangeManager: BleManager.BleDataExchangeManager,
  private val callbackHandler: Handler,
  private val encryptionManager: EncryptionManager,
  handler: Handler
) {

  private var notification: NotificationCompat.Builder? = null
  private var userId: String? = null
  private val moshi = MoshiPack()

  // Used to execute ble operation in sequence across one or
  // multiple device. All BLE callbacks should call OperationQueue#operationComplete
  private val operationQueue = OperationQueue(10000, handler)
  private val chunksOperationQueue = OperationQueue(30000, handler)

  // references of the connected servers are kept so they can by manually
  // disconnected if this manager is stopped
  val connectedGattServers = mutableMapOf<String, BluetoothGatt>()
  val connectedServersIds = mutableMapOf<String, String>()

  data class ChunkSendingState(
    var chunksSent: Int,
    val chunksTotal: Int,
    val builder: NotificationCompat.Builder,
    val userId: String,
    val notificationId: Int
  )

  private val sendingChunks = mutableMapOf<String, ChunkSendingState>()

  private val adapter: BluetoothAdapter? get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
  private val scanner get() = adapter?.bluetoothLeScanner

  private val scanFilters =
    ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(BleUuid.SERVICE_UUID)).build()
      .let { listOf(it) }

  private val scanSettings = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
      .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
      .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE).build()
  } else {
    TODO("VERSION.SDK_INT < M")
  }

  private val scanCallback = object : ScanCallback() {
    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.M)
    override fun onScanResult(callbackType: Int, result: ScanResult?) {
      super.onScanResult(callbackType, result)
      val device = result?.device ?: return
      operationQueue.execute {
        if (connectedGattServers.containsKey(device.address)) {
          result.rssi.let { rssi ->
            val userId = connectedServersIds.entries.find { it.value == device.address }?.key
            if (userId != null) {
              callbackHandler.post { dataExchangeManager.onUserRssiReceived(userId, rssi) }
            } else {
              Log.w(
                "ClientBleManager", "onScanResult: userId not found for device ${device.address}"
              )
            }
          }
          operationQueue.operationComplete()
          return@execute
        } else {
          Log.d("ClientBleManager", "Connecting to ${device.address}")
          device.connectGatt(
            context, false, gattCallback, BluetoothDevice.TRANSPORT_LE
          )
        }
      }
    }
  }
  private val gattCallback = object : BluetoothGattCallback() {
    @SuppressLint("MissingPermission")
    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
      super.onConnectionStateChange(gatt, status, newState)
      operationQueue.operationComplete()
      when {
        status != BluetoothGatt.GATT_SUCCESS -> {
          if (gatt?.device != null) {
            when (newState) {
              BluetoothProfile.STATE_DISCONNECTED -> {
                Log.d(
                  "ClientBleManager",
                  "onConnectionStateChange: Disconnected from ${gatt.device.address}"
                )
                connectedGattServers.remove(gatt.device.address)
                val serverId =
                  connectedServersIds.entries.find { it.value == gatt.device.address }?.key
                if (serverId != null) {
                  connectedServersIds.remove(serverId)
                  dataExchangeManager.onUserDisconnected(serverId)
                }
                gatt.close()
              }
              else -> {
                Log.d("ClientBleManager", "onConnectionStateChange: disconnecting")
                connectedGattServers.remove(gatt.device.address)
                val serverId =
                  connectedServersIds.entries.find { it.value == gatt.device.address }?.key
                if (serverId != null) {
                  connectedServersIds.remove(serverId)
                  dataExchangeManager.onUserDisconnected(serverId)
                }
                gatt.disconnect()
              }
            }
          }
        }
        newState == BluetoothProfile.STATE_CONNECTED -> {
          if (gatt != null) {
            Log.d(
              "ClientBleManager", "onConnectionStateChange: Connected to ${gatt.device.address}"
            )
            connectedGattServers[gatt.device.address] = gatt
            operationQueue.execute { gatt.requestMtu(512) }
          }
        }
        newState == BluetoothProfile.STATE_DISCONNECTED -> {
          if (gatt != null) {
            Log.i(
              "ClientBleManager",
              "onConnectionStateChange: disconnected from ${connectedServersIds[gatt.device.address]}"
            )
            connectedGattServers.remove(gatt.device.address)
            connectedServersIds.remove(gatt.device.address)
            gatt.close()
          }
        }
      }
    }

    @SuppressLint("MissingPermission")
    override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
      super.onMtuChanged(gatt, mtu, status)
      operationQueue.operationComplete()
      if (status == BluetoothGatt.GATT_SUCCESS) {
        if (gatt != null) operationQueue.execute {
          gatt.discoverServices()
        }
      } else {
        Log.e("ClientBleManager", "onMtuChanged: failed to set MTU")
        gatt?.disconnect()
      }
    }

    @SuppressLint("MissingPermission")
    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
      super.onServicesDiscovered(gatt, status)
      operationQueue.operationComplete()
      if (status == BluetoothGatt.GATT_SUCCESS) {
        // request data from remote BLE server
        if (gatt != null) {
          operationQueue.execute { gatt.readUserId() }
        }
      } else {
        Log.e("ClientBleManager", "onServicesDiscovered: failed to discover services")
        gatt?.disconnect()
      }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("MissingPermission")
    override fun onCharacteristicRead(
      gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int
    ) {
      super.onCharacteristicRead(gatt, characteristic, status)
      Log.d("ClientBleManager", "onCharacteristicRead: ${characteristic?.uuid.toString()}")
      operationQueue.operationComplete()
      if (status == BluetoothGatt.GATT_SUCCESS) {
        when (characteristic?.uuid?.toString()) {
          BleUuid.USER_ID_UUID -> {
            val userId = characteristic.getStringValue(0)
            Log.d(
              "ClientBleManager",
              "onCharacteristicRead: userId = $userId gatt == null: ${gatt == null}"
            )

            if (gatt != null) {
              connectedServersIds[userId] = gatt.device.address
              operationQueue.execute { gatt.readPublicKey() }
            }
            callbackHandler.post {
              userId?.let { dataExchangeManager.onUserConnected(it) }
            }
          }
          BleUuid.USER_PUBLIC_KEY_UUID -> {
            val msg = moshi.unpack<KeyData>(characteristic.value)
            Log.d("ClientBleManager", "onCharacteristicRead: msg.key = ${msg.key}")
            val publicKey = KeyFactory.getInstance("RSA")
              .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(msg.key)))
            Log.d(
              "ClientBleManager",
              "onCharacteristicRead: publicKey = $publicKey gatt == null: ${gatt == null}"
            )
            callbackHandler.post {
              dataExchangeManager.onUserPublicKeyReceived(
                msg.userId, publicKey
              )
            }
          }
        }

      } else {
        Log.e("ClientBleManager", "onCharacteristicRead: failed to read characteristic")
        gatt?.disconnect()
      }
    }

    override fun onCharacteristicWrite(
      gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int
    ) {
      super.onCharacteristicWrite(gatt, characteristic, status)
      Log.d(
        "ClientBleManager",
        "onCharacteristicWrite: ${characteristic?.uuid.toString()} status == SUCCESS: ${status == BluetoothGatt.GATT_SUCCESS}, status=${status}"
      )
      chunksOperationQueue.operationComplete()

      val chunkState = sendingChunks[gatt?.device?.address]
      if (chunkState != null) {
        Log.d(
          "ClientBleManager",
          "onCharacteristicWrite: chunkState = ${chunkState.chunksSent} / ${chunkState.chunksTotal}"
        )
        chunkState.builder.setProgress(
          chunkState.chunksTotal, chunkState.chunksSent, false
        )
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.notify(chunkState.notificationId, chunkState.builder.build())
        if (status == BluetoothGatt.GATT_SUCCESS) {
          chunkState.chunksSent++
          if (chunkState.chunksSent == chunkState.chunksTotal) {
            sendingChunks.remove(gatt?.device?.address)
            operationQueue.operationComplete()
            callbackHandler.post {
              dataExchangeManager.onMessageSent(chunkState.userId)
            }
            chunkState.builder.setContentText("Upload complete").setProgress(0, 0, false)
              .setOngoing(false)

            notificationManager.notify(chunkState.notificationId, chunkState.builder.build())
          }
        }
      }
    }
  }

  fun start(myUserId: String) {
    userId = myUserId
    if (adapter.isBleOn) {
      if (ActivityCompat.checkSelfPermission(
          context, Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED || (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(
          context, Manifest.permission.BLUETOOTH_ADMIN
        ) == PackageManager.PERMISSION_GRANTED)
      ) {
        scanner?.startScan(scanFilters, scanSettings, scanCallback)
        Log.d("ClientBleManager", "Started scanning")
      } else {
        Log.e("ClientBleManager", "Permission not granted")
        Toast.makeText(context, "Permission not granted", Toast.LENGTH_SHORT).show()
      }
    } else {
      Toast.makeText(context, "Bluetooth LE is not enabled", Toast.LENGTH_SHORT).show()
      Log.d(
        "ClientBleManager",
        "start: Bluetooth is not on: adapter=$adapter isEnabled=${adapter?.isEnabled}"
      )
    }
  }

  fun stop() {
    if (adapter.isBleOn) {
      if (ActivityCompat.checkSelfPermission(
          context, Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED || (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(
          context, Manifest.permission.BLUETOOTH_ADMIN
        ) == PackageManager.PERMISSION_GRANTED)
      ) {
        scanner?.stopScan(scanCallback)
        connectedGattServers.values.forEach { it.disconnect() }
      }
    }
    operationQueue.clear()
    chunksOperationQueue.clear()
  }

  @SuppressLint("MissingPermission")
  private fun BluetoothGatt.readUserId() {
    val characteristic =
      getService(UUID.fromString(BleUuid.SERVICE_UUID))?.getCharacteristic(UUID.fromString(BleUuid.USER_ID_UUID))
    if (characteristic != null) {
      readCharacteristic(characteristic)
    } else {
      Log.w("ClientBleManager", "readUserId: characteristic is null")
      disconnect()
    }
  }

  @SuppressLint("MissingPermission")
  private fun BluetoothGatt.readPublicKey() {
    val characteristic =
      getService(UUID.fromString(BleUuid.SERVICE_UUID))?.getCharacteristic(UUID.fromString(BleUuid.USER_PUBLIC_KEY_UUID))
    if (characteristic != null) {
      readCharacteristic(characteristic)
    } else {
      Log.w("ClientBleManager", "readPublicKey: characteristic is null")
      disconnect()
    }
  }

  @SuppressLint("MissingPermission")
  private fun BluetoothGatt.writeData(data: BleData) {
    // Log.d("ClientBleManager", "writeData: $data")
    val newBleData = BleData(
      data.senderId,
      if (data.recipientId != null && data.senderId == userId) (encryptionManager.encrypt(
        data.content, dataExchangeManager.getPublicKeyForUser(data.recipientId)
      )) else data.content,
      data.recipientId
    )
    val value = moshi.packToByteArray(newBleData)
    Log.d("ClientBleManager", "writeData: value.size = ${value.size}")

    val chunks = value.chunked(506) // MTU (512) - Header (4) - Chunk metadata (2) = 512-4-2 = 506
    Log.d("ClientBleManager", "writeData: chunks.size = ${chunks.size}")
    // Create a notification
    if (chunks.size > 1) {
      notification =
        NotificationCompat.Builder(context, "data")
          .setSmallIcon(R.drawable.ic_baseline_bluetooth_24)
          .setContentTitle("Sending data")
          .setContentText("Sending data to ${newBleData.recipientId}")
          .setPriority(NotificationCompat.PRIORITY_DEFAULT)
          .setOngoing(true)
          .setProgress(chunks.size, 0, false)
      val notificationManager = NotificationManagerCompat.from(context)
      val notificationId = newBleData.recipientId.hashCode()
      notificationManager.notify(
        notificationId, notification!!.build()
      )
      sendingChunks[device.address] = ChunkSendingState(
        0, chunks.size, notification!!, newBleData.recipientId!!, notificationId
      )
    }
    chunks.forEachIndexed { index, chunk ->
      val chunkValue = Chunk(index == chunks.size - 1, index.toShort(), chunk)
      chunksOperationQueue.execute {
        sendChunk(chunkValue)
      }
    }
  }

  @SuppressLint("MissingPermission")
  private fun BluetoothGatt.sendChunk(chunk: Chunk) {
    val characteristic = getService(UUID.fromString(BleUuid.SERVICE_UUID))?.getCharacteristic(
      UUID.fromString(
        BleUuid.WRITE_UUID
      )
    )
    if (characteristic != null) {
      characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
      characteristic.value = chunk.toByteArray()
      writeCharacteristic(characteristic)
      Log.d("ClientBleManager", "writeData: chunk ${chunk.index} written")
    } else {
      Log.w("ClientBleManager", "sendChunk: characteristic is null")
      disconnect()
    }
  }

  fun broadcastData(message: Message) {
    val bleData = BleData(message.senderId, message.content, message.receiverId)
    connectedGattServers.values.forEach { it.writeData(bleData) }
  }

  fun sendData(message: Message) {
    val bleData = BleData(message.senderId, message.content, message.receiverId, message.type)
    val deviceAddress = connectedServersIds[message.receiverId]
    if (deviceAddress != null) {
      connectedGattServers[deviceAddress]?.writeData(bleData)
    } else {
      Log.w("ClientBleManager", "sendData: deviceAddress is null")
    }
  }
}

private fun ByteArray.chunked(i: Int): List<ByteArray> {
  if (i >= size) return listOf(this)
  val list = mutableListOf<ByteArray>()
  var index = 0
  while (index < this.size) {
    if (index + i > this.size) {
      list.add(this.copyOfRange(index, this.size))
    } else {
      list.add(this.copyOfRange(index, index + i))
    }
    index += i
  }
  return list
}
