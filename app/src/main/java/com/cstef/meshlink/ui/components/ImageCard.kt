package com.cstef.meshlink.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ImageCard(isMine: Boolean, content: String) {
  val image = remember { Base64.decode(content, Base64.DEFAULT) }
  val bitmap = remember { BitmapFactory.decodeByteArray(image, 0, image.size) }
  val imageBitmap = remember { bitmap.asImageBitmap() }
  val (isFullScreen, setFullScreen) = remember { mutableStateOf(false) }
  if (!isFullScreen) {
    Image(
      bitmap = imageBitmap, contentDescription = null,
      modifier = Modifier
        .padding(8.dp)
        .fillMaxWidth()
        .height(300.dp)
        .padding(
          start = if (isMine) 64.dp else 0.dp, end = if (isMine) 0.dp else 64.dp
        )
        .clip(MaterialTheme.shapes.medium)
        .clickable {
          setFullScreen(true)
        },
    )
  } else {
    Dialog(
      onDismissRequest = { setFullScreen(false) },
      properties = DialogProperties(
        dismissOnBackPress = true,
        dismissOnClickOutside = false,
        usePlatformDefaultWidth = false
      ),
      content = {
        Image(
          bitmap = imageBitmap, contentDescription = null,
          modifier = Modifier
            .padding(8.dp)
            .fillMaxSize()
            .clip(MaterialTheme.shapes.medium)
            .clickable {
              setFullScreen(false)
            },
        )
      })
  }
}
