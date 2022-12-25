package com.cstef.meshlink.ui.components

import android.util.Log
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cstef.meshlink.screens.Avatar
import com.cstef.meshlink.util.struct.Message

@Composable
fun ChatMessage(
  type: String,
  content: String,
  timestamp: Long,
  isMine: Boolean,
  showAvatar: Boolean,
  senderId: String,
  onUserClick: (String) -> Unit
) {
  Log.d(
    "ChatMessage",
    "type: $type, content: ${if (type == Message.Type.TEXT) content else "Binary data"}, timestamp: $timestamp, isMine: $isMine"
  )
  when (type) {
    Message.Type.TEXT -> {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(8.dp),
      ) {
        if (showAvatar && !isMine) {
          Avatar(
            deviceId = senderId, modifier = Modifier
              .padding(end = 8.dp)
              .size(40.dp),
            size = 40.dp
          ) {
            onUserClick(senderId)
          }
        }
        TextCard(isMine, content)
      }
    }
    Message.Type.IMAGE -> {
      ImageCard(isMine, content)
    }
  }
}

