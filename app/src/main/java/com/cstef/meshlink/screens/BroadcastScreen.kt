package com.cstef.meshlink.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import com.cstef.meshlink.db.entities.DatabaseMessage
import com.cstef.meshlink.ui.components.ChatMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BroadcastScreen(
    myId: String,
    allMessages: LiveData<List<DatabaseMessage>>,
    sendMessage: (content: String, type: String) -> Unit,
    onUserClick: (String) -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxSize()
  ) {
    TopAppBar(title = {
      Row(modifier = Modifier.fillMaxWidth()) {
        Column(
          modifier = Modifier
            .padding(top = 16.dp, bottom = 16.dp, start = 24.dp)
            .align(
              Alignment.CenterVertically
            )
        ) {
          Text(
            text = "Broadcast",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
          )
        }
      }
    }, modifier = Modifier.height(64.dp))
    Messages(
      Modifier
        .weight(1f)
        .fillMaxSize(),
      allMessages,
      myId
    ) {
      onUserClick(it)
    }
    SendMessage(enableImages = false) { content, type ->
      sendMessage(content, type)
    }

  }
}

@Composable
fun Messages(
    modifier: Modifier = Modifier,
    allMessages: LiveData<List<DatabaseMessage>>,
    myId: String,
    onUserClick: (String) -> Unit = {},
) {
  val messages by allMessages.observeAsState(listOf())
  val scrollState = rememberLazyListState()
  // on messages change, scroll to the bottom of the list
  LaunchedEffect(messages) {
    if (messages.isNotEmpty()) {
      scrollState.animateScrollToItem(messages.size - 1)
    }
  }
  LazyColumn(
    modifier = modifier, contentPadding = PaddingValues(
      horizontal = 16.dp, vertical = 8.dp
    ), state = scrollState
  ) {
    items(
      messages.filter { it.recipientId == "broadcast" }
    )
    { message ->
      ChatMessage(
        type = message.type,
        content = message.content,
        timestamp = message.sent_timestamp,
        isMine = message.senderId == myId,
        showAvatar = true,
        senderId = message.senderId
      ) {
        onUserClick(it)
      }
    }
  }
}

