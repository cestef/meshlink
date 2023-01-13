package com.cstef.meshlink.managers

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.ParcelUuid
import android.util.Base64
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cstef.meshlink.R
import com.cstef.meshlink.util.BleUuid
import com.cstef.meshlink.util.struct.*
import com.daveanthonythomas.moshipack.MoshiPack
import kotlinx.coroutines.*
import java.math.RoundingMode
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.text.DecimalFormat
import java.util.*
import kotlin.math.absoluteValue

class ClientBleManager(
  private val context: Context,
  private val dataExchangeManager: BleManager.BleDataExchangeManager,
  private val callbackHandler: Handler,
  private val encryptionManager: EncryptionManager,
  handler: Handler,
  private val parentManager: BleManager
) {

  private var rssiUpdateJob: Job? = null
  private var userId: String? = null
  private val moshi = MoshiPack()

  // Used to execute ble operation in sequence across one or
  // multiple device. All BLE callbacks should call OperationQueue#operationComplete
  private val operationQueue = OperationQueue(30000, handler)
  private val chunksOperationQueue = OperationQueue(30000, handler)

  // references of the connected servers are kept so they can by manually
  // disconnected if this manager is stopped
  val connectingGattServers = mutableSetOf<String>()
  val connectedGattServers = mutableMapOf<String, BluetoothGatt>()
  val connectedServersAddresses = mutableMapOf<String, String>()

  data class ChunkSendingState(
    var chunksSent: Int,
    val chunksTotal: Int,
    val userId: String,
    val notificationId: Int,
    val startTime: Long = System.currentTimeMillis(),
    val receiver: BroadcastReceiver,
    val messageId: String
  )

  private val sendingChunks = mutableMapOf<String, ChunkSendingState>()

  private val adapter: BluetoothAdapter? get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
  private val scanner get() = adapter?.bluetoothLeScanner

  private val scanFilters =
    ScanFilter.Builder()
      .setServiceUuid(ParcelUuid.fromString(BleUuid.SERVICE_UUID))
      // .setManufacturerData(0xDEAD, byteArrayOf(0x00))
      .build()
      .let { listOf(it) }

  private val scanSettings = ScanSettings.Builder()
    .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
    .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
    .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE).build()

  private val scanCallback = object : ScanCallback() {
    @SuppressLint("MissingPermission")
    override fun onScanResult(callbackType: Int, result: ScanResult?) {
      super.onScanResult(callbackType, result)
      val device = result?.device ?: return
      operationQueue.execute {
        if (connectedGattServers.containsKey(device.address)) {
          result.rssi.let { rssi ->
            connectedServersAddresses.entries.find { it.value == device.address }?.key.let { userId ->
//              Log.d("ClientBleManager", "onScanResult: $userId $rssi")
              if (userId != null) {
                dataExchangeManager.onUserRssiReceived(userId, rssi)
              }
            }
          }
          result.txPower.let { txPower ->
            if (txPower == ScanResult.TX_POWER_NOT_PRESENT) {
              Log.d("ClientBleManager", "onScanResult: txPower not present")
              return@execute
            }
            connectedServersAddresses.entries.find { it.value == device.address }?.key.let { userId ->
//              Log.d("ClientBleManager", "onScanResult: $userId $txPower")
              if (userId != null) {
                dataExchangeManager.onUserTxPowerReceived(userId, txPower)
              }
            }
          }
          operationQueue.operationComplete()
          return@execute
        } else {
          Log.d("ClientBleManager", "Connecting to ${device.address}")
          connectingGattServers.add(device.address)
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
                connectingGattServers.remove(gatt.device.address)
                val serverId =
                  connectedServersAddresses.entries.find { it.value == gatt.device.address }?.key
                if (serverId != null) {
                  if (sendingChunks.containsKey(gatt.device.address)) {
                    sendingChunks.remove(gatt.device.address)
                    val chunkSendingState = sendingChunks[gatt.device.address]
                    val notificationId = chunkSendingState?.notificationId ?: serverId.hashCode()
                    NotificationManagerCompat.from(context).cancel(notificationId)
                    dataExchangeManager.onMessageSendFailed(serverId, "Connection error")
                  }
                  connectedServersAddresses.remove(serverId)
                  dataExchangeManager.onUserDisconnected(serverId)
                }
                gatt.close()
              }
              else -> {
                Log.d("ClientBleManager", "onConnectionStateChange: disconnecting")
                connectedGattServers.remove(gatt.device.address)
                val serverId =
                  connectedServersAddresses.entries.find { it.value == gatt.device.address }?.key
                if (serverId != null) {
                  if (sendingChunks.containsKey(gatt.device.address)) {
                    sendingChunks.remove(gatt.device.address)
                    val chunkSendingState = sendingChunks[gatt.device.address]
                    val notificationId = chunkSendingState?.notificationId ?: serverId.hashCode()
                    NotificationManagerCompat.from(context).cancel(notificationId)
                    dataExchangeManager.onMessageSendFailed(serverId, "Connection error")
                  }
                  connectedServersAddresses.remove(serverId)
                  connectingGattServers.remove(gatt.device.address)
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
            connectingGattServers.remove(gatt.device.address)
            connectedGattServers[gatt.device.address] = gatt
            operationQueue.execute { gatt.requestMtu(512) }
          }
        }
        newState == BluetoothProfile.STATE_DISCONNECTED -> {
          if (gatt != null) {
            Log.i(
              "ClientBleManager",
              "onConnectionStateChange: disconnected from ${connectedServersAddresses[gatt.device.address]}"
            )
            connectedGattServers.remove(gatt.device.address)
            connectingGattServers.remove(gatt.device.address)
            val serverId =
              connectedServersAddresses.entries.find { it.value == gatt.device.address }?.key
            connectedServersAddresses.remove(serverId)
            if (sendingChunks.containsKey(gatt.device.address)) {
              sendingChunks.remove(gatt.device.address)
              val chunkSendingState = sendingChunks[gatt.device.address]
              val notificationId = chunkSendingState?.notificationId ?: serverId.hashCode()
              NotificationManagerCompat.from(context).cancel(notificationId)
              dataExchangeManager.onMessageSendFailed(serverId, "Connection error")
            }
            if (serverId != null) {
              dataExchangeManager.onUserDisconnected(serverId)
            }
            gatt.close()
          }
        }
        else -> {
          Log.w("ClientBleManager", "onConnectionStateChange: unknown state $newState")
        }
      }
    }

    @SuppressLint("MissingPermission")
    override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
      super.onMtuChanged(gatt, mtu, status)
      Log.d("ClientBleManager", "onMtuChanged: $mtu")
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
      Log.d(
        "ClientBleManager",
        "onServicesDiscovered: $status, success: ${status == BluetoothGatt.GATT_SUCCESS}"
      )
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

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    @Deprecated("Use onCharacteristicRead instead")
    override fun onCharacteristicRead(
      gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int
    ) {
      super.onCharacteristicRead(gatt, characteristic, status)
      Log.d(
        "ClientBleManager",
        "onCharacteristicRead: ${characteristic?.uuid.toString()}, from ${gatt?.device?.address}"
      )
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
              connectedServersAddresses[userId] = gatt.device.address
              operationQueue.execute { gatt.readPublicKey() }
            } else {
              Log.e("ClientBleManager", "onCharacteristicRead: gatt == null")
            }
          }
          BleUuid.USER_PUBLIC_KEY_UUID -> {
            val msg = moshi.unpack<KeyData>(characteristic.value)
            Log.d("ClientBleManager", "onCharacteristicRead: msg.key = ${msg.key}")
            val publicKey = KeyFactory.getInstance("RSA")
              .generatePublic(X509EncodedKeySpec(Base64.decode(msg.key, Base64.DEFAULT)))
            Log.d(
              "ClientBleManager",
              "onCharacteristicRead: publicKey = ${encryptionManager.getPublicKeySignature(publicKey)} gatt == null: ${gatt == null}"
            )
            if (gatt != null) {
              callbackHandler.post {
                dataExchangeManager.onUserConnected(
                  userId = msg.userId,
                  address = gatt.device.address,
                  publicKey = publicKey
                )
              }
            } else {
              Log.e("ClientBleManager", "onCharacteristicRead: gatt == null")
            }
          }
        }
      } else {
        Log.e("ClientBleManager", "onCharacteristicRead: failed to read characteristic")
        gatt?.disconnect()
      }
    }

    @SuppressLint("RestrictedApi")
    override fun onCharacteristicWrite(
      gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int
    ) {
      super.onCharacteristicWrite(gatt, characteristic, status)
      Log.d(
        "ClientBleManager",
        "onCharacteristicWrite: ${characteristic?.uuid.toString()} status == SUCCESS: ${status == BluetoothGatt.GATT_SUCCESS}, status=${status}, to: ${gatt?.device?.address}"
      )
      chunksOperationQueue.operationComplete()

      val chunkState = sendingChunks[gatt?.device?.address]
      val builder = NotificationCompat.Builder(context, "data")
        .setSmallIcon(R.drawable.ic_baseline_bluetooth_24)

      if (chunkState != null) {
        Log.d(
          "ClientBleManager",
          "onCharacteristicWrite: chunkState = ${chunkState.chunksSent + 1} / ${chunkState.chunksTotal}"
        )
        if (status == BluetoothGatt.GATT_SUCCESS) {
          chunkState.chunksSent++
          if (chunkState.chunksSent == chunkState.chunksTotal) {
            Log.d(
              "ClientBleManager",
              "onCharacteristicWrite: finished sending ${chunkState.chunksSent}/${chunkState.chunksTotal} chunks"
            )
            context.unregisterReceiver(chunkState.receiver)
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.cancel(chunkState.notificationId)
            sendingChunks.remove(gatt?.device?.address)
            callbackHandler.post {
              dataExchangeManager.onMessageSent(chunkState.userId, chunkState.messageId)
            }
          } else {
            val timeDiff = System.currentTimeMillis() - chunkState.startTime
            val speed = (chunkState.chunksSent * Chunk.CHUNK_SIZE / 1000.0) / (timeDiff / 1000.0)
            val df = DecimalFormat("#.##")
            df.roundingMode = RoundingMode.CEILING
            builder.setContentTitle("Sending data").setContentText(
              "Average speed: ${
                df.format(speed).toDouble()
              } kB/s"
            ).setProgress(
              chunkState.chunksTotal, chunkState.chunksSent, false
            ).setOngoing(true).setSilent(true).addAction(
              com.google.android.material.R.drawable.ic_m3_chip_close,
              "Cancel",
              PendingIntent.getBroadcast(
                context,
                0,
                Intent("cancel_sending"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
              )
            )
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(chunkState.notificationId, builder.build())
          }
        } else {
          Log.e("ClientBleManager", "onCharacteristicWrite: chunkState == null")
        }
      }
    }

    @SuppressLint("MissingPermission")
    override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
      super.onReadRemoteRssi(gatt, rssi, status)
      Log.d(
        "ClientBleManager",
        "onReadRemoteRssi: rssi = $rssi, status == SUCCESS: ${status == BluetoothGatt.GATT_SUCCESS}, status=${status}, from: ${gatt?.device?.address}"
      )
      if (status == BluetoothGatt.GATT_SUCCESS) {
        val userId =
          connectedServersAddresses.entries.find { it.value == gatt?.device?.address }?.key
        if (userId != null) {
          Log.d("ClientBleManager", "onReadRemoteRssi: userId = $userId")
          callbackHandler.post {
            dataExchangeManager.onUserRssiReceived(userId, rssi)
          }
        } else {
          Log.e("ClientBleManager", "onReadRemoteRssi: userId == null: ${gatt?.device?.address}")
          gatt?.disconnect()
        }
      } else {
        Log.e("ClientBleManager", "onReadRemoteRssi: failed to read rssi")
        gatt?.disconnect()
      }
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
        stopScanning()
        disconnectAll()
      }
    }
    operationQueue.clear()
    chunksOperationQueue.clear()
    rssiUpdateJob?.cancel()
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
  private fun BluetoothGatt.writeMessage(message: Message) {
    // Log.d("ClientBleManager", "writeData: $data")
    val publicKeyForUser = message.recipientId?.let { dataExchangeManager.getPublicKeyForUser(it) }
    message.content =
      if (message.recipientId != null && message.senderId == userId && publicKeyForUser != null && message.recipientId != "broadcast") (encryptionManager.encrypt(
        message.content, publicKeyForUser
      ))
      else message.content
    val value = moshi.packToByteArray(message)
    Log.d("ClientBleManager", "writeData: value.size = ${value.size}")

    val chunks = value.chunked(Chunk.CHUNK_SIZE)
    Log.d("ClientBleManager", "writeData: chunks.size = ${chunks.size}")
    // Create a notification
    val builder = NotificationCompat.Builder(context, "data")
      .setSmallIcon(R.drawable.ic_baseline_bluetooth_24).setContentTitle("Sending data")
      .setPriority(NotificationCompat.PRIORITY_DEFAULT).setSilent(true).setOngoing(true)
      .setProgress(chunks.size, 0, false).addAction(
        com.google.android.material.R.drawable.ic_m3_chip_close,
        "Cancel",
        PendingIntent.getBroadcast(
          context,
          0,
          Intent("cancel_sending"),
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
      )
    val notificationManager = NotificationManagerCompat.from(context)
    val notificationId = message.id.toByteArray().sum().absoluteValue
    notificationManager.notify(
      notificationId, builder.build()
    )
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("ClientBleManager", "onReceive: cancel_sending")
        chunksOperationQueue.clear()
        sendingChunks.remove(device.address)
        notificationManager.cancel(notificationId)
        context?.unregisterReceiver(this)
        dataExchangeManager.onMessageSendFailed(message.recipientId, "Cancelled by user")
      }
    }
    sendingChunks[device.address] = ChunkSendingState(
      chunksSent = 0,
      chunksTotal = chunks.size,
      userId = message.recipientId ?: "",
      notificationId = notificationId,
      startTime = System.currentTimeMillis(),
      receiver = receiver,
      messageId = message.id
    )
    context.registerReceiver(receiver, IntentFilter("cancel_sending"))
    chunks.forEachIndexed { index, chunk ->
      val chunkValue = Chunk(index == chunks.size - 1, index.toShort(), chunk, value.hashCode())
      chunksOperationQueue.execute(onTimeout = {
        Log.e(
          "ClientBleManager",
          "writeData: timeout (to ${message.recipientId})"
        )
        dataExchangeManager.onMessageSendFailed(message.recipientId, "Timeout")
        sendingChunks.remove(device.address)
        disconnect()
      }) {
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
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        writeCharacteristic(
          characteristic, chunk.toByteArray(), BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        )
      } else {
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        @Suppress("DEPRECATION")
        characteristic.value = chunk.toByteArray()
        @Suppress("DEPRECATION")
        writeCharacteristic(characteristic)
      }
      Log.d("ClientBleManager", "writeData: chunk ${chunk.index + 1} written")
    } else {
      Log.w("ClientBleManager", "sendChunk: characteristic is null")
      disconnect()
    }
  }

  fun broadcastData(message: Message) {
    connectedGattServers.values.filter { connectedServersAddresses.entries.find { entry -> entry.value == it.device.address }?.key != message.senderId }
      .forEach {
        it.writeMessage(message)
      }
  }

  fun sendData(message: Message) {
    val deviceAddress = connectedServersAddresses[message.recipientId]
    if (deviceAddress != null) {
      connectedGattServers[deviceAddress]?.writeMessage(message)
    } else {
      Log.w("ClientBleManager", "sendData: deviceAddress is null")
    }
  }

  @SuppressLint("MissingPermission")
  fun disconnectAll() {
    connectedGattServers.values.forEach { it.disconnect() }
    connectedGattServers.clear()
    connectedServersAddresses.clear()
  }

  @SuppressLint("MissingPermission")
  fun disconnect(userId: String) {
    val deviceAddress = connectedServersAddresses[userId]
    if (deviceAddress != null) {
      connectedGattServers[deviceAddress]?.disconnect()
      connectedGattServers.remove(deviceAddress)
      connectedServersAddresses.remove(userId)
    } else {
      Log.w("ClientBleManager", "disconnect: deviceAddress is null")
    }
  }

  @SuppressLint("MissingPermission")
  private fun startScanning() {
    if (adapter.isBleOn) {
      scanner?.startScan(scanFilters, scanSettings, scanCallback)
      Log.d("ClientBleManager", "startScanning: started")
    } else {
      Log.w("ClientBleManager", "startScanning: BLE is off")
    }
  }

  @SuppressLint("MissingPermission")
  private fun stopScanning() {
    scanner?.stopScan(scanCallback)
    Log.d("ClientBleManager", "stopScanning: stopped")
  }

  fun setUserId(id: String) {
    userId = id
  }

  @SuppressLint("MissingPermission")
  fun start() {
    startScanning()
    // Update remote rssi every 10 seconds
    rssiUpdateJob = CoroutineScope(Dispatchers.Main).launch(Dispatchers.IO) {
      while (true) {
        delay(10000)
        connectedGattServers.values.forEach {
          it.readRemoteRssi()
        }
      }
    }
  }

  @SuppressLint("MissingPermission")
  fun connect(address: String) {
    val device = adapter?.getRemoteDevice(address)
    if (device != null) {
      device.connectGatt(
        context, false, gattCallback, BluetoothDevice.TRANSPORT_LE
      )
    } else {
      Log.w("ClientBleManager", "connect: device is null")
    }
  }

//  @SuppressLint("MissingPermission")
//  @Suppress("DEPRECATION")
//  fun sendIsWriting(userId: String, writing: Boolean) {
//    val deviceAddress = connectedServersAddresses[userId]
//    val server = deviceAddress?.let { connectedGattServers[it] }
//    if (server != null) {
//      val characteristic =
//        server.getService(UUID.fromString(BleUuid.SERVICE_UUID))?.getCharacteristic(
//          UUID.fromString(
//            BleUuid.USER_WRITING_UUID
//          )
//        )
//      if (characteristic != null) {
//        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
//        characteristic.value = moshi.packToByteArray(WritingData(userId, writing))
//        server.writeCharacteristic(characteristic)
//      } else {
//        Log.w("ClientBleManager", "sendIsWriting: characteristic is null")
//      }
//    } else {
//      Log.w("ClientBleManager", "sendIsWriting: server is null")
//    }
//  }
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
