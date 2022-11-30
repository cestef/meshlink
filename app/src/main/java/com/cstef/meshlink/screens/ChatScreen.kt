package com.cstef.meshlink.screens

import android.graphics.RectF
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.caverock.androidsvg.SVG
import com.cstef.meshlink.BleService
import com.cstef.meshlink.ui.components.ChatMessage
import com.cstef.meshlink.util.SystemBroadcastReceiver
import com.cstef.meshlink.util.generateBeamSVG
import com.cstef.meshlink.util.struct.Message


@ExperimentalMaterial3Api
@Composable
fun ChatScreen(
  bleBinder: BleService.BleServiceBinder?,
  deviceId: String?,
  onBack: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
  ) {
    if (bleBinder != null && deviceId != null) {
      TopAppBar(title = {
        Row(modifier = Modifier.fillMaxWidth()) {
          Avatar(
            deviceId = deviceId,
            modifier = Modifier
              .padding(end = 16.dp, top = 16.dp, bottom = 16.dp)
              .align(Alignment.CenterVertically)
              .size(48.dp)
          )
          Text(
            text = "$deviceId",
            modifier = Modifier
              .padding(top = 16.dp, bottom = 16.dp, start = 24.dp)
              .align(
                Alignment.CenterVertically
              )
          )
        }
      }, modifier = Modifier.height(96.dp))
      Messages(
        Modifier
          .weight(1f)
          .fillMaxSize(), bleBinder, deviceId
      )
      SendMessage(bleBinder, deviceId)
    }
  }
}

@Composable
fun Avatar(deviceId: String, modifier: Modifier = Modifier.size(48.dp)) {
  Canvas(modifier = modifier) {
    drawIntoCanvas { canvas ->
      val svgString = generateBeamSVG(
        deviceId,
        listOf(
          "#F5DFDC",
          "#F2CDCC",
          "#F5C2E7",
          "#CBA6F6",
          "#F38BA8",
          "#ECA0AD",
          "#FBB387",
          "#F9E2AF",
          "#A8E3A1",
          "#93E2D5",
          "#89DCEB",
          "#74C7EC",
          "#89B4FB",
          "#B4BDFD",
          "#CDD6F4",
          "#BBC1DF",
          "#A6ACC7",
          "#9399B2",
          "#80849C",
          "#6C7086",
          "#595B71",
          "#45475B",
          "#313244",
          "#1E1E2E",
          "#181825",
          "#11111B"
        ),
        false,
        RectF(0f, 0f, 64.dp.toPx(), 64.dp.toPx()),
      )
      val svg = SVG.getFromString(svgString)
      svg.renderToCanvas(canvas.nativeCanvas)
    }
  }

}

@Composable
fun Messages(
  modifier: Modifier = Modifier, bleBinder: BleService.BleServiceBinder, deviceId: String
) {
  val messages = remember { mutableStateListOf<Message>() }
  val (hasNewMessage, setHasNewMessage) = remember { mutableStateOf(false) }
  val scrollState = rememberLazyListState()
  // on first composition, get the messages from the bleBinder
  LaunchedEffect(Unit) {
    val newMessages = bleBinder.getMessages(deviceId)
    messages.addAll(newMessages)
    if (newMessages.isNotEmpty()) {
      setHasNewMessage(true)
    }
  }
  if (hasNewMessage) {
    LaunchedEffect(Unit) {
      // Scroll to the bottom of the list, not with scrollState.maxValue because the list is infinite
      scrollState.scrollToItem(messages.size - 1)
      setHasNewMessage(false)
    }
  }
  SystemBroadcastReceiver(BleService.ACTION_MESSAGES.action!!) {
    // Add only new messages
    val newMessages = bleBinder.getMessages(deviceId).filter { !messages.contains(it) }
    messages.addAll(newMessages)
    if (newMessages.isNotEmpty()) {
      setHasNewMessage(true)
    }
  }
  LazyColumn(
    modifier = modifier,
    contentPadding = PaddingValues(
      horizontal = 16.dp, vertical = 8.dp
    ),
    state = scrollState
  ) {
    items(
      messages
    ) { message ->
      ChatMessage(
        type = message.type,
        content = message.content,
        timestamp = message.timestamp,
        isMine = message.isMe
      )
    }
  }
}

@ExperimentalMaterial3Api
@Composable
fun SendMessage(bleBinder: BleService.BleServiceBinder, deviceId: String) {
  val (text, setText) = remember { mutableStateOf("") }
  val galleryLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
      if (uri != null) {
        val stream = bleBinder.service.contentResolver.openInputStream(uri)
        val bytes = stream?.readBytes()
        if (bytes != null) {
          val base64 = Base64.encodeToString(bytes, Base64.DEFAULT)
          bleBinder.sendMessage(deviceId, base64, Message.Type.IMAGE)
        }
      }
    }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(16.dp)
  ) {
    OutlinedTextField(
      value = text,
      onValueChange = { setText(it) },
      label = { Text("Message") },
      modifier = Modifier
        .weight(1f)
        .padding(end = 8.dp)
        .align(Alignment.CenterVertically),
      shape = MaterialTheme.shapes.medium,
      trailingIcon = {
        IconButton(onClick = { galleryLauncher.launch("image/*") }) {
          Icon(Icons.Filled.AttachFile, contentDescription = "Attach file")
        }
      },

      )
    FloatingActionButton(
      onClick = {
        if (text.isNotEmpty()) {
          bleBinder.sendMessage(deviceId, text, Message.Type.TEXT)
          setText("")
        }
      },
      modifier = Modifier
        .size(56.dp)
        .align(Alignment.CenterVertically)
        .padding(top = 8.dp),
      elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp),
    ) {
      Icon(Icons.Filled.Send, contentDescription = "Send")
    }
  }
}
