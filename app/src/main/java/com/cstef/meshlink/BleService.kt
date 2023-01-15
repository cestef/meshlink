package com.cstef.meshlink

import android.app.NotificationManager
import android.app.Service
import android.content.Context
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
import com.cstef.meshlink.db.entities.DatabaseMessage
import com.cstef.meshlink.db.entities.Device
import com.cstef.meshlink.managers.BleManager
import com.cstef.meshlink.managers.EncryptionManager
import com.cstef.meshlink.managers.LogcatMessage
import com.cstef.meshlink.managers.LogsManager
import com.cstef.meshlink.repositories.DeviceRepository
import com.cstef.meshlink.repositories.MessageRepository
import com.cstef.meshlink.util.struct.Chunk
import com.cstef.meshlink.util.struct.Message
import com.daveanthonythomas.moshipack.MoshiPack
import java.security.PublicKey
import java.util.*
import kotlin.random.Random


class BleService : Service() {
  private val moshi = MoshiPack()
  private var userId: String = ""
  private val mBinder: IBinder = BleServiceBinder()
  private val handlerThread = HandlerThread("BleService")
  private lateinit var handler: Handler

  override fun onBind(intent: Intent?): IBinder {
    return mBinder
  }

  inner class BleServiceBinder : Binder() {
    val logcatLogs: MutableLiveData<List<LogcatMessage>>
      get() = logsManager.logcatMessages
    val isAdvertising
      get() = bleManager.isAdvertising
    val service: BleService
      get() = this@BleService
    val isDatabaseOpen: LiveData<Boolean>
      get() = this@BleService.isDatabaseOpen
    val isDatabaseOpening: LiveData<Boolean>
      get() = this@BleService.isDatabaseLoading
    val databaseError: LiveData<String>
      get() = this@BleService.databaseError
    val allMessages: LiveData<List<DatabaseMessage>>
      get() = this@BleService.allMessages ?: throw Exception("Database not open")
    val allDevices: LiveData<List<Device>>
      get() = this@BleService.allDevices ?: throw Exception("Database not open")

    fun setUserId(id: String) {
      userId = id
      bleManager.setUserId(id)
    }

    fun sendMessage(
      recipientId: String, message: String, type: String = Message.Type.TEXT
    ): String {
      val id = UUID.randomUUID().toString()
      this@BleService.sendMessage(
        Message(
          id, userId, recipientId, message, type, System.currentTimeMillis(), true
        )
      )
      return id
    }

    fun addDevice(userId: String) {
      bleDataExchangeManager.onUserAdded(userId)
    }

//    fun sendIsWriting(userId: String, isWriting: Boolean) {
//      this@BleService.sendIsWriting(userId, isWriting)
//    }

    fun getPublicKeySignature(otherUserId: String): String {
      val device = allDevices.value?.find { it.userId == otherUserId }
      return if (otherUserId == userId) {
        encryptionManager.getPublicKeySignature(encryptionManager.publicKey)
      } else if (device?.publicKey != null) {
        encryptionManager.getPublicKeySignature(
          encryptionManager.getPublicKey(
            Base64.decode(
              device.publicKey, Base64.DEFAULT
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

    fun deleteDataForUser(userId: String) {
      deviceRepository?.delete(userId)
      messageRepository?.delete(userId)
      // Disconnect from device
      bleManager.disconnect(userId)
    }

    fun changeDatabasePassword(newPassword: String): Boolean {
      val sharedPreferences = getSharedPreferences("USER_SETTINGS", MODE_PRIVATE)
      if (newPassword.isEmpty()) {
        sharedPreferences.edit().putBoolean("is_default_password", true).apply()
      } else {
        sharedPreferences.edit().putBoolean("is_default_password", false).apply()
      }
      return AppDatabase.updatePassword(newPassword)
    }

    fun openDatabase(masterPassword: String?) {
      this@BleService.openDatabase(masterPassword)
    }

    fun openServer() {
      bleManager.openServer()
    }

    fun startAdvertising() {
      bleManager.startAdvertising()
    }

    fun stopAdvertising() {
      bleManager.stopAdvertising()
    }

    fun startClient() {
      bleManager.startClient()
    }
  }

  val messagesHashes: MutableMap<String, MutableList<String>> = mutableMapOf()
  val chunks: MutableMap<String, MutableMap<Int, MutableList<Chunk>>> =
    mutableMapOf() // userId -> messageId -> chunks

  data class SentAndReceived(
    val sent: Long, val received: Long
  )

  var currentBenchmarkSentMessagesTimestamps: MutableMap<String, SentAndReceived> = mutableMapOf()

  private val bleDataExchangeManager = object : BleManager.BleDataExchangeManager {
    override fun connect(address: String) {
      bleManager.connect(address)
    }

    override fun getKnownDevices(): List<Device> {
      return allDevices?.value ?: emptyList()
    }

    override fun getAddressForUserId(userId: String): String {
      return allDevices?.value?.find { it.userId == userId }?.address ?: ""
    }

    override fun onUserAdded(userId: String) {
      val existingDevice = allDevices?.value?.find { it.userId == userId }
      if (existingDevice == null) {
        val device = Device(userId = userId, added = true)
        deviceRepository?.insert(device)
      } else {
        deviceRepository?.update(existingDevice.copy(added = true))
      }
    }

    override fun onUserConnected(userId: String, address: String?, publicKey: PublicKey?) {
      Log.d("BleService", "onUserConnected: $userId")
      val devices = allDevices?.value ?: emptyList()
      if (!devices.any { it.userId == userId }) {
        val sharedPreferences = getSharedPreferences("USER_STATS", MODE_PRIVATE)
        val totalConnections = sharedPreferences.getInt("TOTAL_CONNECTIONS", 0)
        sharedPreferences.edit().putInt("TOTAL_CONNECTIONS", totalConnections + 1).apply()
        deviceRepository?.insert(
          Device(
            userId = userId,
            address = address,
            rssi = 0,
            lastSeen = System.currentTimeMillis(),
            connected = true,
            name = null,
            blocked = false,
            publicKey = publicKey?.encoded?.let { Base64.encodeToString(it, Base64.DEFAULT) },
            added = false,
            txPower = 0
          )
        )
      } else {
        devices.find { it.userId == userId }?.let { device ->
          deviceRepository?.update(
            device.copy(address = address,
              rssi = 0,
              lastSeen = System.currentTimeMillis(),
              connected = true,
              publicKey = device.publicKey ?: publicKey?.encoded?.let {
                Base64.encodeToString(
                  it, Base64.DEFAULT
                )
              })
          )
        }
      }
    }

    override fun onMessageSent(userId: String, messageId: String) {
      val sharedPreferences = getSharedPreferences("USER_STATS", MODE_PRIVATE)
      val deliveredMessages = sharedPreferences.getInt("TOTAL_MESSAGES_DELIVERED", 0)
      sharedPreferences.edit().putInt("TOTAL_MESSAGES_DELIVERED", deliveredMessages + 1).apply()
      currentBenchmarkSentMessagesTimestamps[messageId] = SentAndReceived(
        currentBenchmarkSentMessagesTimestamps[messageId]?.sent ?: 0, System.currentTimeMillis()
      )
      Log.d("BleService", "onMessageSent: ${currentBenchmarkSentMessagesTimestamps.size}")
      Log.d("BleService", "onMessageSent: $userId, $messageId")
    }

    override fun onChunkReceived(chunk: Chunk, address: String) {
      Log.d("BleService", "onChunkReceived: $address")
      Log.d(
        "BleService",
        "onChunkReceived: chunk.index: ${chunk.index}, chunk.data.size: ${chunk.data.size}, chunk.messageId: ${chunk.messageId}"
      )
      // check if we are already connected as a client to this address, if not, connect
      if (!bleManager.isConnected(address) && !bleManager.isConnecting(address)) {
        bleManager.connect(address)
      }
      val devices = allDevices?.value ?: emptyList()
      val device = devices.find { it.userId == getUserIdForAddress(address) }
      if (device?.connected == false && bleManager.isConnected(address)) {
        deviceRepository?.update(device.copy(connected = true))
      }
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
        if (message.type == Message.Type.BENCHMARK) return
        if (message.recipientId == userId || message.recipientId == "broadcast") {
          val sharedPreferences = getSharedPreferences("USER_STATS", MODE_PRIVATE)
          val receivedMessages = sharedPreferences.getInt("TOTAL_MESSAGES_RECEIVED", 0)
          sharedPreferences.edit().putInt("TOTAL_MESSAGES_RECEIVED", receivedMessages + 1).apply()
          //Log.d("BleService", "onDataReceived: $bleData")
          messagesHashes[message.senderId]?.add(hash)
          // Check if the user is blocked
          if (message.recipientId != "broadcast") message.content =
            encryptionManager.decrypt(message.content)

          messageRepository?.insert(
            DatabaseMessage(
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
            .setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle("MeshLink")
            .setContentText(if (message.type == Message.Type.TEXT) "${message.senderId}: ${message.content}" else "${message.senderId}: ${
              message.type.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(
                  Locale.ROOT
                ) else it.toString()
              }
            }").setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT)
//            .setContentIntent(pendingIntent)
            .build()
          notificationManager.notify(
            message.senderId.hashCode(), notification
          )
        } else if (listOf(Message.Type.TEXT).contains(message.type) && message.ttl > 0) {
          Log.d("BleService", "onDataReceived: not for me")
          Toast.makeText(application, "Propagating a message", Toast.LENGTH_SHORT).show()
          message.ttl -= 1
          sendMessage(message)
        }
      }
    }

    override fun onMessageSendFailed(userId: String?, messageId: String, reason: String?) {
      Log.d("BleService", "onMessageSendFailed: $userId")
      val sharedPreferences = getSharedPreferences("USER_STATS", Context.MODE_PRIVATE)
      val failedMessages = sharedPreferences.getInt("TOTAL_MESSAGES_FAILED", 0)
      sharedPreferences.edit().putInt("TOTAL_MESSAGES_FAILED", failedMessages + 1).apply()
//      val message = messageRepository?.allMessages?.value?.find { it.messageId == messageId }
//      if (message != null) {
//        messageRepository?.updateStatus(messageId, Message.Status.FAILED)
//      }
      currentBenchmarkSentMessagesTimestamps[messageId] = SentAndReceived(
        currentBenchmarkSentMessagesTimestamps[messageId]?.sent ?: 0,
        -1
      )
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
    }

    override fun getPublicKeyForUser(recipientId: String): PublicKey? {
      return allDevices?.value?.find { it.userId == recipientId }?.publicKey?.let {
        encryptionManager.getPublicKey(
          Base64.decode(
            it, Base64.DEFAULT
          )
        )
      }
    }

    override fun onUserRssiReceived(userId: String, rssi: Int) {
      val devices = allDevices?.value ?: return
      if (devices.isNotEmpty()) {
        devices.find { it.userId == userId }?.let { device ->
          if (device.rssi != rssi) {
            deviceRepository?.update(device.copy(rssi = rssi, connected = true))
          }
        }
      }
    }

    override fun onUserTxPowerReceived(userId: String, txPower: Int) {
      val devices = allDevices?.value ?: return
      if (devices.isNotEmpty()) {
        devices.find { it.userId == userId }?.let { device ->
          if (device.txPower != txPower) {
            deviceRepository?.update(device.copy(txPower = txPower, connected = true))
          }
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
  lateinit var logsManager: LogsManager

  var allMessages: LiveData<List<DatabaseMessage>>? = null
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
    logsManager = LogsManager()
    encryptionManager = EncryptionManager()
    bleManager = BleManager(applicationContext, bleDataExchangeManager, encryptionManager, handler)
  }


  fun openDatabase(masterPassword: String?) {
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
    val sharedPreferences = getSharedPreferences("USER_STATS", Context.MODE_PRIVATE)
    val sentMessages = sharedPreferences.getInt("TOTAL_MESSAGES_SENT", 0)
    sharedPreferences.edit().putInt("TOTAL_MESSAGES_SENT", sentMessages + 1).apply()
    if (message.recipientId != null && message.recipientId.isNotEmpty() && message.recipientId != "broadcast" && message.type != Message.Type.BENCHMARK) {
      messageRepository?.insert(
        DatabaseMessage(
          messageId = message.id,
          senderId = message.senderId,
          recipientId = message.recipientId,
          content = message.content,
          type = message.type,
          sent_timestamp = message.timestamp
        )
      )
      currentBenchmarkSentMessagesTimestamps[message.id] = SentAndReceived(
        sent = message.timestamp, received = 0L
      )
      bleManager.sendMessage(message)
    } else if (message.recipientId == "broadcast") {
      messageRepository?.insert(
        DatabaseMessage(
          messageId = message.id,
          senderId = message.senderId,
          recipientId = message.recipientId,
          content = message.content,
          type = message.type,
          sent_timestamp = message.timestamp
        )
      )
      bleManager.broadcastMessage(message)
    }
  }

  fun benchmark(
    onFinish: (results: BenchmarkResults) -> Unit = {}
  ) {
    Thread {
      val device = allDevices?.value?.first() ?: return@Thread onFinish(
        BenchmarkResults(
          status = BenchmarkResults.Status.FAILURE, results = emptyList()
        )
      )
      val benchmarkMessageIds = mutableListOf<String>()
      currentBenchmarkSentMessagesTimestamps.clear()
      for (i in 0..50) {
        val content = Random.nextBytes(1000).toString()
        val messageId = UUID.randomUUID().toString()
        sendMessage(
          Message(
            id = messageId,
            senderId = device.userId,
            recipientId = device.userId,
            content = content,
            type = Message.Type.BENCHMARK,
            timestamp = System.currentTimeMillis(),
            isMe = true
          )
        )
        benchmarkMessageIds += messageId
      }
      val started = System.currentTimeMillis()
      while (currentBenchmarkSentMessagesTimestamps.filter {
          benchmarkMessageIds.contains(it.key) && it.value.received != 0L
        }.size != benchmarkMessageIds.size || System.currentTimeMillis() - started > 10000) {
        Thread.sleep(100)
      }
      val messages =
        currentBenchmarkSentMessagesTimestamps.filter { benchmarkMessageIds.contains(it.key) }
      val results = messages.map { message ->
        BenchmarkResults.Result(
          status = if (message.value.received != 0L) BenchmarkResults.Status.SUCCESS else BenchmarkResults.Status.FAILURE,
          value = message.value.received - message.value.sent,
          messageId = message.key
        )
      }
      onFinish(
        BenchmarkResults(
          status = BenchmarkResults.Status.SUCCESS, results = results.slice(1..49)
        )
      )
    }.start()
  }

  class BenchmarkResults(
    val status: Status, val results: List<Result>
  ) {
    enum class Status {
      SUCCESS, FAILURE,
    }

    data class Result(
      val status: Status, val value: Long, val messageId: String
    )
  }


//  private fun sendIsWriting(userId: String, writing: Boolean) {
//    bleManager.sendIsWriting(userId, writing)
//  }
}
