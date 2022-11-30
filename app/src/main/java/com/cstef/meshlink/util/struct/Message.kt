package com.cstef.meshlink.util.struct

data class Message(
  val senderId: String,
  val recipientId: String?,
  val content: String,
  val type: String,
  val timestamp: Long,
  val isMe: Boolean,
  var ttl: Int = 12
) : java.io.Serializable {
  companion object {
    fun fromBleData(bleData: BleData, isMe: Boolean): Message {
      return Message(
        bleData.senderId,
        bleData.recipientId,
        bleData.content,
        bleData.type,
        System.currentTimeMillis(),
        isMe,
        bleData.ttl
      )
    }
  }

  class Type {
    companion object {
      const val TEXT = "text"
      const val IMAGE = "image"
      // const val FILE = "file"
    }
  }
}
