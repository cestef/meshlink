package com.cstef.meshlink.ui.components

import android.util.Log
import androidx.compose.runtime.Composable
import com.cstef.meshlink.util.struct.Message

@Composable
fun ChatMessage(type: String, content: String, timestamp: Long, isMine: Boolean) {
  Log.d("ChatMessage", "type: $type, content: $content, timestamp: $timestamp, isMine: $isMine")
  when (type) {
    Message.Type.TEXT -> {
      TextCard(isMine, content)
    }
    Message.Type.IMAGE -> {
      ImageCard(isMine, content)
    }
  }
}

