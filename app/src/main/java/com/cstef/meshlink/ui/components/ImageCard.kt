package com.cstef.meshlink.ui.components

import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ImageCard(isMine: Boolean, content: String) {
  val image = remember {
    try {
      Base64.decode(content, Base64.DEFAULT)
    } catch (e: Exception) {
      null
    }
  }
  val bitmap = remember { image?.let { BitmapFactory.decodeByteArray(image, 0, it.size) } }
  val imageBitmap = remember { bitmap?.asImageBitmap() }
  val (isFullScreen, setFullScreen) = remember { mutableStateOf(false) }
  val context = LocalContext.current
  if (!isFullScreen) {
    if (imageBitmap != null) {
      Image(
        bitmap = imageBitmap, contentDescription = null,
        modifier = Modifier
          .padding(8.dp)
          .fillMaxWidth()
          .height(300.dp)
          .padding(
            start = if (isMine) 64.dp else 0.dp, end = if (isMine) 0.dp else 64.dp
          )
          .clip(MaterialTheme.shapes.large)
          .clickable {
            setFullScreen(true)
          },
      )
    }
  } else {
    Dialog(
      onDismissRequest = { setFullScreen(false) },
      properties = DialogProperties(
        dismissOnBackPress = true, dismissOnClickOutside = false, usePlatformDefaultWidth = false
      ),
      content = {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(16.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            IconButton(
              onClick = { setFullScreen(false) },
              modifier = Modifier.padding(8.dp)
            ) {
              Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface
              )
            }
            IconButton(
              onClick = {
                val shareIntent = Intent().apply {
                  action = Intent.ACTION_SEND
                  putExtra(Intent.EXTRA_STREAM, image)
                  type = "image/*"
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share image"))
              },
              modifier = Modifier.padding(8.dp)
            ) {
              Icon(
                imageVector = Icons.Rounded.Share,
                contentDescription = "Share image",
                tint = MaterialTheme.colorScheme.onSurface
              )
            }
          }
          if (imageBitmap != null) {
            Image(
              bitmap = imageBitmap, contentDescription = null,
              modifier = Modifier
                .padding(8.dp)
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                  setFullScreen(false)
                },
            )
          }
        }
      },
    )
  }
}
