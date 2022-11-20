package com.cstef.meshlink.chat

import com.cstef.meshlink.ble.BleData

data class Message(
  val senderId: String,
  val receiverId: String?,
  val content: String,
  val type: String,
  val timestamp: Long,
  val isMe: Boolean
) : java.io.Serializable {
  companion object {
    fun fromBleData(bleData: BleData, isMe: Boolean): Message {
      return Message(
        bleData.senderId,
        bleData.recipientId,
        bleData.content,
        bleData.type,
        System.currentTimeMillis(),
        isMe
      )
    }
  }
}
