package com.cstef.meshlink.ui.components

import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
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
import com.cstef.meshlink.ui.theme.DarkColors
import com.cstef.meshlink.ui.theme.LightColors

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ImageCard(isMine: Boolean, content: String) {
  val image = remember { Base64.decode(content, Base64.DEFAULT) }
  val bitmap = remember { BitmapFactory.decodeByteArray(image, 0, image.size) }
  val imageBitmap = remember { bitmap.asImageBitmap() }
  val (isFullScreen, setFullScreen) = remember { mutableStateOf(false) }
  val context = LocalContext.current
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
        dismissOnBackPress = true, dismissOnClickOutside = false, usePlatformDefaultWidth = false
      ),
      content = {
        Row(
          modifier = Modifier.fillMaxSize(),
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
              tint = if (isSystemInDarkTheme()) DarkColors.primary else LightColors.primary
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
              contentDescription = null,
              tint = if (isSystemInDarkTheme()) DarkColors.primary else LightColors.primary
            )
          }
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
        }
      },
    )
  }
}
