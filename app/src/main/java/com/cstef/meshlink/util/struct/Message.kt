package com.cstef.meshlink.util.struct

data class Message(
  val id: String,
  val senderId: String,
  val recipientId: String?,
  var content: String,
  val type: String,
  val timestamp: Long,
  val isMe: Boolean,
  var ttl: Int = 12
) : java.io.Serializable {
  class Type {
    companion object {
      const val BENCHMARK = "benchmark"
      const val TEXT = "text"
      const val IMAGE = "image"
      // const val FILE = "file"
    }
  }

  class Status {
    companion object {
      const val SENT = "sent"
      const val RECEIVED = "received"
      const val FAILED = "failed"
    }
  }
}
