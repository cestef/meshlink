package com.cstef.meshlink.util.struct


/**
 * Wrapper for data sent and received via BLE
 */
data class BleData(
  val senderId: String,
  var content: String,
  val recipientId: String? = null,
  val type: String = Message.Type.TEXT,
  var ttl: Int = 12
) {
  companion object {
    fun fromMessage(message: Message): BleData {
      return BleData(
        message.senderId,
        message.content,
        message.recipientId,
        message.type,
        message.ttl
      )
    }
  }
}
