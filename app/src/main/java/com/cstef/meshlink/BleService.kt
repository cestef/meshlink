package com.cstef.meshlink

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.cstef.meshlink.managers.BleManager
import com.cstef.meshlink.managers.EncryptionManager
import com.cstef.meshlink.util.struct.BleData
import com.cstef.meshlink.util.struct.Chunk
import com.cstef.meshlink.util.struct.ConnectedDevice
import com.cstef.meshlink.util.struct.Message
import com.daveanthonythomas.moshipack.MoshiPack
import java.security.PublicKey
import java.util.*


class BleService : Service() {
  companion object {
    const val EXTRA_DEVICES = "com.cstef.meshlink.devices"
    val ACTION_USER = Intent("com.cstef.meshlink.ACTION_USER")
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
    val service: BleService
      get() = this@BleService

    val isBleStarted
      get() = bleManager.isStarted

    fun sendMessage(receiverId: String, message: String, type: String = Message.Type.TEXT) {
      this@BleService.sendMessage(BleData(userId, message, receiverId, type))
    }

    fun getMessages(receiverId: String): List<Message> {
      return messages[receiverId] ?: listOf()
    }

    fun getConnectedDevices(): List<ConnectedDevice> {
      return connectedDevices
    }

    fun addDevice(userId: String) {
      bleDataExchangeManager.onUserConnected(userId)
    }
  }

  private val connectedDevices = mutableListOf<ConnectedDevice>()
  val messages: MutableMap<String, MutableList<Message>> = mutableMapOf()
  val messagesHashes: MutableMap<String, MutableList<String>> = mutableMapOf()
  val publicKeys: MutableMap<String, PublicKey> = mutableMapOf()
  val chunks: MutableMap<String, MutableList<Chunk>> = mutableMapOf()

  private val bleDataExchangeManager = object : BleManager.BleDataExchangeManager {
    override fun onUserConnected(userId: String) {
      Log.d("BleService", "onUserConnected: $userId")
      if (!connectedDevices.any { it.id == userId }) {
        connectedDevices.add(ConnectedDevice(userId, null, 0, true, System.currentTimeMillis()))
        val intent = Intent(ACTION_USER)
        sendBroadcast(intent)
      } else {
        connectedDevices.find { it.id == userId }?.let {
          it.connected = true
          it.lastSeen = System.currentTimeMillis()
        }
      }
    }

    override fun onUserPublicKeyReceived(userId: String, publicKey: PublicKey) {
      Log.d("BleService", "onUserPublicKeyReceived: $userId")
      // if the public key is already known, ask the user if they want to update it
      if (publicKeys.containsKey(userId)) {
        Log.d("BleService", "public key already known")
      } else {
        Log.d("BleService", "public key unknown")
        publicKeys[userId] = publicKey
      }
    }

    override fun onMessageSent(userId: String) {
      Log.d("BleService", "onMessageSent: $userId")
      // Refresh chat fragment
      val intent = Intent(ACTION_MESSAGES)
      sendBroadcast(intent)
    }

    override fun onChunkReceived(chunk: Chunk, address: String) {
      Log.d(
        "BleService",
        "onChunkReceived: chunk.index: ${chunk.index}, chunk.data.size: ${chunk.data.size}"
      )
      if (chunks.containsKey(address)) {
        chunks[address]?.add(chunk)
      } else {
        chunks[address] = mutableListOf(chunk)
      }
      if (chunk.isLast) {
        Log.d("BleService", "onChunkReceived: Last chunk received")
        val chunks = chunks[address] ?: return
        val data = chunks.sortedBy { it.index }.map { it.data }.reduce { acc, bytes -> acc + bytes }
        Log.d("BleService", "onChunkReceived: data.size: ${data.size}")
        chunks.clear()
        val bleData = moshi.unpack<BleData>(data)
        //Log.d("BleService", "bleData: $bleData")
        val hash = encryptionManager.md5(moshi.packToByteArray(bleData))
        if (messagesHashes[bleData.senderId]?.contains(hash) == true) {
          Log.d("BleService", "Message in cache")
          return
        }
        if (bleData.recipientId == userId) {
          //Log.d("BleService", "onDataReceived: $bleData")
          messagesHashes[bleData.senderId]?.add(hash)
          bleData.content = encryptionManager.decrypt(bleData.content)
          val messagesForUser = messages.getOrPut(bleData.senderId) { mutableListOf() }
          val newMessage = Message.fromBleData(
            bleData, false
          )
          messagesForUser.add(
            newMessage
          )
          Log.d(
            "BleService", "onDataReceived: ${newMessage.senderId}: ${newMessage.content}"
          )
          // Send notification to user phone
          val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
          val notification = NotificationCompat.Builder(this@BleService, "messages")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("MeshLink")
            .setContentText(
              if (newMessage.type == Message.Type.TEXT) "${newMessage.senderId}: ${newMessage.content}" else "${newMessage.senderId}: ${
                newMessage.type.replaceFirstChar {
                  if (it.isLowerCase()) it.titlecase(
                    Locale.ROOT
                  ) else it.toString()
                }
              }"
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
          notificationManager.notify(
            newMessage.senderId.hashCode(), notification
          )
          messages[bleData.senderId] = messagesForUser
          val intent = Intent(ACTION_MESSAGES)
          sendBroadcast(intent)
        } else if (listOf(Message.Type.TEXT).contains(bleData.type) && bleData.ttl > 0) {
          Log.d("BleService", "onDataReceived: not for me")
          Toast.makeText(application, "Propagating a message", Toast.LENGTH_SHORT).show()
          bleData.ttl -= 1
          sendMessage(bleData)
        }
      }
    }

    override fun onUserDisconnected(userId: String) {
      Log.d("MainViewModel", "onUserDisconnected: $userId")
      connectedDevices.map { it.id }.indexOf(userId).let {
        if (it != -1) {
          connectedDevices[it].connected = false
          connectedDevices[it].lastSeen = System.currentTimeMillis()
          val intent = Intent(ACTION_USER)
          sendBroadcast(intent)
        }
      }
      val intent = Intent(ACTION_USER)
      sendBroadcast(intent)
    }

    override fun getPublicKeyForUser(recipientId: String): PublicKey? {
      return publicKeys[recipientId]
    }

    override fun onUserRssiReceived(userId: String, rssi: Int) {
      connectedDevices.find { it.id == userId }?.rssi = rssi
      val intent = Intent(ACTION_USER)
      intent.putParcelableArrayListExtra(EXTRA_DEVICES, connectedDevices.toCollection(ArrayList()))
      sendBroadcast(intent)
    }
  }

  lateinit var bleManager: BleManager
  private lateinit var encryptionManager: EncryptionManager

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

  fun startBle(userId: String) {
    Log.d("BleService", "startBle")
    this.userId = userId
    if (!bleManager.isStarted.value) bleManager.start(userId)
  }

  fun stopBle() {
    if (bleManager.isStarted.value) bleManager.stop()
  }

  fun sendMessage(bleData: BleData) {
    if (bleData.recipientId != null) {
      val messageData = Message.fromBleData(bleData, true)
      messages.getOrPut(bleData.recipientId) { mutableListOf() }.add(messageData)
      bleManager.sendData(messageData)
    }
  }
}
