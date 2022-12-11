package com.cstef.meshlink

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.cstef.meshlink.db.AppDatabase
import com.cstef.meshlink.db.entities.Device
import com.cstef.meshlink.managers.BleManager
import com.cstef.meshlink.managers.EncryptionManager
import com.cstef.meshlink.repositories.DeviceRepository
import com.cstef.meshlink.repositories.MessageRepository
import com.cstef.meshlink.util.struct.Chunk
import com.cstef.meshlink.util.struct.Message
import com.daveanthonythomas.moshipack.MoshiPack
import java.security.PublicKey
import java.util.*


class BleService : Service() {
  companion object {
    val ACTION_DEVICE = Intent("com.cstef.meshlink.ACTION_USER")
    val ACTION_MESSAGES = Intent("com.cstef.meshlink.ACTION_MESSAGES")
  }

  private val moshi = MoshiPack()
  private var userId: String = ""
  private val mBinder: IBinder = BleServiceBinder()
  private val handlerThread = HandlerThread("BleService")
  private lateinit var handler: Handler

  override fun onBind(intent: Intent?): IBinder {
    return mBinder
  }

  inner class BleServiceBinder : Binder() {
    val isAdvertising
      get() = bleManager.isAdvertising
    val service: BleService
      get() = this@BleService

    val isBleStarted
      get() = bleManager.isStarted
    val isScanning
      get() = bleManager.isScanning
    val isDatabaseOpen: LiveData<Boolean>
      get() = this@BleService.isDatabaseOpen
    val isDatabaseOpening: LiveData<Boolean>
      get() = this@BleService.isDatabaseLoading
    val databaseError: LiveData<String>
      get() = this@BleService.databaseError
    val allMessages: LiveData<List<com.cstef.meshlink.db.entities.Message>>
      get() = this@BleService.allMessages ?: throw Exception("Database not open")
    val allDevices: LiveData<List<Device>>
      get() = this@BleService.allDevices ?: throw Exception("Database not open")

    fun setUserId(id: String) {
      userId = id
      bleManager.setUserId(id)
    }

    fun sendMessage(receiverId: String, message: String, type: String = Message.Type.TEXT) {
      this@BleService.sendMessage(
        Message(
          UUID.randomUUID().toString(),
          userId,
          receiverId,
          message,
          type,
          System.currentTimeMillis(),
          true
        )
      )
    }

    fun addDevice(userId: String, address: String, publicKey: PublicKey) {
      bleDataExchangeManager.onUserConnected(userId, address)
      bleDataExchangeManager.onUserPublicKeyReceived(userId, address, publicKey)
    }

//    fun sendIsWriting(userId: String, isWriting: Boolean) {
//      this@BleService.sendIsWriting(userId, isWriting)
//    }

    fun getPublicKeySignature(otherUserId: String): String {
      val device = allDevices.value?.find { it.userId == otherUserId }
      return if (otherUserId == userId) {
        encryptionManager.getPublicKeySignature(encryptionManager.publicKey)
      } else if (device != null) {
        encryptionManager.getPublicKeySignature(
          encryptionManager.getPublicKey(
            Base64.decode(
              device.publicKey,
              Base64.DEFAULT
            )
          )
        )
      } else {
        return "No public key for user $otherUserId"
      }
    }

    fun blockUser(userId: String) {
      val device = allDevices.value?.find { it.userId == userId }
      if (device != null) {
        deviceRepository?.update(device.copy(blocked = true))
      } else {
        Log.e("BleService", "Device not found")
        Toast.makeText(this@BleService, "Device not found", Toast.LENGTH_SHORT).show()
      }
    }

    fun unblockUser(userId: String) {
      val device = allDevices.value?.find { it.userId == userId }
      if (device != null) {
        deviceRepository?.update(device.copy(blocked = false))
      } else {
        Log.e("BleService", "Device not found")
        Toast.makeText(this@BleService, "Device not found", Toast.LENGTH_SHORT).show()
      }
    }

    fun updateDeviceName(userId: String, name: String) {
      val device = allDevices.value?.find { it.userId == userId }
      if (device != null) {
        deviceRepository?.update(device.copy(name = name))
      } else {
        Log.e("BleService", "Device not found")
        Toast.makeText(this@BleService, "Device not found", Toast.LENGTH_SHORT).show()
      }
    }

    fun deleteAllData() {
      deviceRepository?.deleteAll()
      messageRepository?.deleteAll()
      // Disconnect from all devices
      bleManager.disconnectAll()

    }

    fun deleteDataForUser(device: Device) {
      deviceRepository?.delete(device)
      messageRepository?.delete(device.userId)
      // Disconnect from device
      bleManager.disconnect(device.userId)
    }

    fun changeDatabasePassword(newPassword: String): Boolean {
      return AppDatabase.updatePassword(newPassword)
    }

    fun openDatabase(masterPassword: String) {
      this@BleService.openDatabase(masterPassword)
    }

    fun openServer() {
      bleManager.openServer()
    }

    fun closeServer() {
      bleManager.closeServer()
    }

    fun startScanning() {
      // check permissions
      bleManager.startScanning()
    }

    fun stopScanning() {
      bleManager.stopScanning()
    }

    fun startAdvertising() {
      bleManager.startAdvertising()
    }

    fun stopAdvertising() {
      bleManager.stopAdvertising()
    }

    fun startConnectOrUpdateKnownDevices() {
      bleManager.startConnectOrUpdateKnownDevices()
    }

    fun stopConnectOrUpdateKnownDevices() {
      bleManager.stopConnectOrUpdateKnownDevices()
    }
  }

  val messagesHashes: MutableMap<String, MutableList<String>> = mutableMapOf()
  val chunks: MutableMap<String, MutableMap<Int, MutableList<Chunk>>> =
    mutableMapOf() // userId -> messageId -> chunks

  private val bleDataExchangeManager = object : BleManager.BleDataExchangeManager {
    override fun getKnownDevices(): List<Device> {
      return allDevices?.value ?: emptyList()
    }

    override fun getAddressForUserId(userId: String): String {
      return allDevices?.value?.find { it.userId == userId }?.address ?: ""
    }

    override fun onUserConnected(userId: String, address: String) {
      Log.d("BleService", "onUserConnected: $userId")
      val devices = allDevices?.value ?: emptyList()
      if (!devices.any { it.userId == userId }) {
        deviceRepository?.insert(Device(userId, address, 0, System.currentTimeMillis(), true))
        val intent = Intent(ACTION_DEVICE)
        sendBroadcast(intent)
      } else {
        devices.find { it.userId == userId }?.let {
          deviceRepository?.update(
            it.copy(
              lastSeen = System.currentTimeMillis(),
              connected = true
            )
          )
        }
      }
    }

    override fun onUserPublicKeyReceived(userId: String, address: String, publicKey: PublicKey) {
      Log.d("BleService", "onUserPublicKeyReceived: $userId")
      // if the public key is already known, ask the user if they want to update it
      val devices = allDevices?.value ?: emptyList()
      val device = devices.find { it.userId == userId }
      if (device != null) {
        deviceRepository?.update(
          device.copy(
            publicKey = Base64.encodeToString(
              publicKey.encoded,
              Base64.DEFAULT
            )
          )
        )
      } else {
        deviceRepository?.insert(
          Device(
            userId,
            address,
            publicKey = Base64.encodeToString(
              publicKey.encoded,
              Base64.DEFAULT
            ),
            lastSeen = System.currentTimeMillis(),
            connected = true
          )
        )
      }
    }

    override fun onMessageSent(userId: String) {
      Log.d("BleService", "onMessageSent: $userId")
      // Refresh chat fragment
      val intent = Intent(ACTION_MESSAGES)
      sendBroadcast(intent)
    }

    override fun onChunkReceived(chunk: Chunk, address: String) {
      Log.d("BleService", "onChunkReceived: $address")
      Log.d(
        "BleService",
        "onChunkReceived: chunk.index: ${chunk.index}, chunk.data.size: ${chunk.data.size}, chunk.messageId: ${chunk.messageId}"
      )
      // is user blocked?
      val devices = allDevices?.value ?: emptyList()
      val device = devices.find { it.userId == getUserIdForAddress(address) }
      if (device != null && device.blocked) {
        Log.d("BleService", "onChunkReceived: user is blocked")
        return
      }
      if (chunks.containsKey(address)) {
        chunks[address]?.let { chunks ->
          if (chunks.containsKey(chunk.messageId)) {
            chunks[chunk.messageId]?.add(chunk)
          } else {
            chunks[chunk.messageId] = mutableListOf(chunk)
          }
        }
      } else {
        chunks[address] = mutableMapOf(chunk.messageId to mutableListOf(chunk))
      }
      if (chunk.isLast) {
        Log.d("BleService", "onChunkReceived: Last chunk received")
        val chunks = chunks[address]?.get(chunk.messageId) ?: return
        // Check if a chunk is missing (all indexes are present)
        val indexes = chunks.map { it.index }
        val missingIndexes = (0 until chunks.size).filter { !indexes.contains(it.toShort()) }
        if (missingIndexes.isNotEmpty()) {
          Log.e("BleService", "onChunkReceived: Missing chunks: $missingIndexes")
          return
        }
        val data = chunks.sortedBy { it.index }.map { it.data }.reduce { acc, bytes -> acc + bytes }
        Log.d("BleService", "onChunkReceived: data.size: ${data.size}")
        chunks.clear()
        val message = moshi.unpack<Message>(data)
        //Log.d("BleService", "bleData: $bleData")
        val hash = encryptionManager.md5(data)
        if (messagesHashes[message.senderId]?.contains(hash) == true) {
          Log.d("BleService", "Message in cache")
          return
        }
        if (message.recipientId == userId) {
          //Log.d("BleService", "onDataReceived: $bleData")
          messagesHashes[message.senderId]?.add(hash)
          // Check if the user is blocked
          message.content = encryptionManager.decrypt(message.content)

          messageRepository?.insert(
            com.cstef.meshlink.db.entities.Message(
              message.id,
              message.senderId,
              message.recipientId,
              message.content,
              message.type,
              message.timestamp
            )
          )
          // Send notification to user phone
          val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
//          // Go to the chat screen when the user clicks on the notification
//          val clickIntent = Intent(this@BleService, MainActivity::class.java)
//          clickIntent.putExtra("userId", message.senderId)
//          val pendingIntent = PendingIntent.getActivity(
//            this@BleService, 0, clickIntent,
//            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
//          )
          val notification = NotificationCompat.Builder(this@BleService, "messages")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("MeshLink")
            .setContentText(
              if (message.type == Message.Type.TEXT) "${message.senderId}: ${message.content}" else "${message.senderId}: ${
                message.type.replaceFirstChar {
                  if (it.isLowerCase()) it.titlecase(
                    Locale.ROOT
                  ) else it.toString()
                }
              }"
            )
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
//            .setContentIntent(pendingIntent)
            .build()
          notificationManager.notify(
            message.senderId.hashCode(), notification
          )

          val intent = Intent(ACTION_MESSAGES)
          sendBroadcast(intent)
        } else if (listOf(Message.Type.TEXT).contains(message.type) && message.ttl > 0) {
          Log.d("BleService", "onDataReceived: not for me")
          Toast.makeText(application, "Propagating a message", Toast.LENGTH_SHORT).show()
          message.ttl -= 1
          sendMessage(message)
        }
      }
    }

    override fun onMessageSendFailed(userId: String?, reason: String?) {
      Log.d("BleService", "onMessageSendFailed: $userId")
      Toast.makeText(application, "Message to $userId failed: $reason", Toast.LENGTH_LONG).show()
    }

    override fun onUserDisconnected(userId: String) {
      Log.d("MainViewModel", "onUserDisconnected: $userId")
      val devices = allDevices?.value ?: return
      devices.map { it.userId }.indexOf(userId).let {
        if (it != -1) {
          deviceRepository?.update(devices[it].copy(connected = false))
        }
      }
      val intent = Intent(ACTION_DEVICE)
      sendBroadcast(intent)
    }

    override fun getPublicKeyForUser(recipientId: String): PublicKey? {
      return allDevices?.value?.find { it.userId == recipientId }?.publicKey?.let {
        encryptionManager.getPublicKey(
          Base64.decode(
            it,
            Base64.DEFAULT
          )
        )
      }
    }

    override fun onUserRssiReceived(userId: String, rssi: Int) {
      val devices = allDevices?.value ?: return
      if (devices.isNotEmpty()) {
        val device = devices[0]
        if (device.rssi != rssi) {
          deviceRepository?.update(device.copy(rssi = rssi))
          val intent = Intent(ACTION_DEVICE)
          sendBroadcast(intent)
        }
      }
    }

    override fun getUserIdForAddress(address: String): String? {
      return bleManager.getUserIdForAddress(address)
    }

    override fun onUserWriting(userId: String, isWriting: Boolean) {
//      Log.d("BleService", "onUserWriting: $userId, $isWriting")
//      val deviceDao = db.deviceDao()
//      val devices = deviceDao.getDevice(userId)
//      if (devices.isNotEmpty()) {
//        val device = devices[0]
//        if (device.isWriting != isWriting) {
//          deviceDao.update(device.copy(isWriting = isWriting))
//          val intent = Intent(ACTION_USER)
//          sendBroadcast(intent)
//        }
//      }
//      val intent = Intent(ACTION_USER)
//      sendBroadcast(intent)
    }
  }
  lateinit var bleManager: BleManager
  private lateinit var encryptionManager: EncryptionManager

  var allMessages: LiveData<List<com.cstef.meshlink.db.entities.Message>>? = null
  var allDevices: LiveData<List<Device>>? = null
  val isDatabaseOpen = MutableLiveData(false)
  val isDatabaseLoading = MutableLiveData(false)
  val databaseError = MutableLiveData("")
  var messageRepository: MessageRepository? = null
  var deviceRepository: DeviceRepository? = null

  override fun onDestroy() {
    super.onDestroy()
    bleManager.stop()
  }

  override fun onCreate() {
    super.onCreate()
    handlerThread.start()
    handler = Handler(handlerThread.looper)
    encryptionManager = EncryptionManager()
    bleManager = BleManager(applicationContext, bleDataExchangeManager, encryptionManager, handler)
  }


  fun openDatabase(masterPassword: String) {
    AppDatabase.destroyInstance()
    val db = AppDatabase.getInstance(application, masterPassword)
    val messageDao = db.messageDao()
    val deviceDao = db.deviceDao()
    messageRepository = MessageRepository(messageDao)
    deviceRepository = DeviceRepository(deviceDao, isDatabaseOpen, isDatabaseLoading, databaseError)
    deviceRepository?.checkDatabaseWorking()
    allMessages = messageRepository?.allMessages
    allDevices = deviceRepository?.allDevices
  }

  fun sendMessage(message: Message) {
    if (message.recipientId != null) {
      messageRepository?.insert(
        com.cstef.meshlink.db.entities.Message(
          message.id,
          message.senderId,
          message.recipientId,
          message.content,
          message.type,
          message.timestamp
        )
      )
      bleManager.sendMessage(message)
    }
  }

//  private fun sendIsWriting(userId: String, writing: Boolean) {
//    bleManager.sendIsWriting(userId, writing)
//  }
}
