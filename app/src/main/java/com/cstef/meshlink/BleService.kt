package com.cstef.meshlink

import android.app.Service
import android.content.Intent
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.cstef.meshlink.ble.BleData
import com.cstef.meshlink.ble.BleManager
import com.cstef.meshlink.ble.Chunk
import com.cstef.meshlink.chat.Message
import com.cstef.meshlink.util.struct.ConnectedDevice
import com.daveanthonythomas.moshipack.MoshiPack
import java.security.PublicKey


class BleService : Service() {
  companion object {
    val EXTRA_DEVICES = "com.cstef.meshlink.devices"
    val EXTRA_MESSAGES = "com.cstef.meshlink.messages"
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

    fun sendMessage(receiverId: String, message: String, type: String = "text") {
      this@BleService.sendMessage(receiverId, message, type)
    }

    fun getMessages(receiverId: String): List<Message> {
      return messages[receiverId] ?: listOf()
    }

    fun getConnectedDevices(): List<ConnectedDevice> {
      return connectedDevices
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
        val devices = connectedDevices.toCollection(ArrayList())
        Log.d("BleService", "Sending devices: $devices")
        intent.putParcelableArrayListExtra(EXTRA_DEVICES, devices)
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
          messagesForUser.add(
            Message.fromBleData(
              bleData, false
            )
          )
          messages[bleData.senderId] = messagesForUser
          val intent = Intent(ACTION_MESSAGES)
          intent.putExtra(EXTRA_MESSAGES, HashMap(messages))
          sendBroadcast(intent)
          Toast.makeText(application, "Received: ${bleData.content}", Toast.LENGTH_SHORT).show()
        } else {
          Log.d("BleService", "onDataReceived: not for me")
          Toast.makeText(application, "Propagating a message", Toast.LENGTH_SHORT).show()
          sendMessage(bleData.recipientId, bleData.content)
        }
      }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onUserDisconnected(userId: String) {
      Log.d("MainViewModel", "onUserDisconnected: $userId")
      connectedDevices.map { it.id }.indexOf(userId).let {
        if (it != -1) {
          connectedDevices[it].connected = false
          connectedDevices[it].lastSeen = System.currentTimeMillis()
          val intent = Intent(ACTION_USER)
          val devices = connectedDevices.toCollection(ArrayList())
          Log.d("BleService", "Sending devices: $devices")
          intent.putParcelableArrayListExtra(EXTRA_DEVICES, devices)
          sendBroadcast(intent)
        }
      }
      val intent = Intent(ACTION_USER)
      intent.putParcelableArrayListExtra(EXTRA_DEVICES, connectedDevices.toCollection(ArrayList()))
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
    if (!bleManager.isStarted) bleManager.start(userId)
  }

  fun stopBle() {
    if (bleManager.isStarted) bleManager.stop()
  }

  fun sendMessage(recipientId: String?, content: String, type: String = "text") {
    val messageData = Message(userId, recipientId, content, type, System.currentTimeMillis(), true)
    messages.getOrPut(recipientId ?: "broadcast") { mutableListOf() }.add(messageData)
    val intent = Intent(ACTION_MESSAGES)
    intent.putExtra(EXTRA_MESSAGES, HashMap(messages))
    sendBroadcast(intent)
    bleManager.sendData(messageData)
  }
}
