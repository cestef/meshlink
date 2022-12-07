package com.cstef.meshlink.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class Message(
  @PrimaryKey val messageId: String,
  @ColumnInfo(name = "sender_id") val senderId: String,
  @ColumnInfo(name = "recipient_id") val recipientId: String,
  @ColumnInfo(name = "content") val content: String,
  @ColumnInfo(name = "type") val type: String = com.cstef.meshlink.util.struct.Message.Type.TEXT,
  @ColumnInfo(name = "timestamp") val timestamp: Long,
)
