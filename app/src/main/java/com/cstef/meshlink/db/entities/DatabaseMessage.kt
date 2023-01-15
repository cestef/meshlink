package com.cstef.meshlink.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cstef.meshlink.util.struct.Message

@Entity(tableName = "messages")
data class DatabaseMessage(
  @PrimaryKey val messageId: String,
  @ColumnInfo(name = "sender_id") val senderId: String,
  @ColumnInfo(name = "recipient_id") val recipientId: String,
  @ColumnInfo(name = "content") val content: String,
  @ColumnInfo(name = "type") val type: String = Message.Type.TEXT,
  @ColumnInfo(name = "sent_timestamp") val sent_timestamp: Long,
  @ColumnInfo(name = "status") val status: String = Message.Status.SENT
)
