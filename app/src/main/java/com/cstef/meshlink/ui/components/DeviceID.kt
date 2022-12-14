package com.cstef.meshlink.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.cstef.meshlink.ui.theme.DarkColors
import com.cstef.meshlink.ui.theme.LightColors
import com.cstef.meshlink.util.struct.QrData
import com.daveanthonythomas.moshipack.MoshiPack

@Composable
fun DeviceID(modifier: Modifier = Modifier, userId: String, isMe: Boolean, blocked: Boolean) {
  val moshi = remember {
    MoshiPack()
  }
  val context = LocalContext.current
  val (isShowingQR, setIsShowingQR) = remember { mutableStateOf(false) }
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center
  ) {
    Text(
      text = "$userId ${if (isMe) "(me)" else if (blocked) "(blocked)" else ""}",
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier
        .padding(start = 16.dp)
        .align(Alignment.CenterVertically)
        .clickable {
          // Copy to clipboard
          val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
          val clip = android.content.ClipData.newPlainText("Device ID", userId)
          clipboard.setPrimaryClip(clip)
          Log.d("DeviceID", "Copied $userId to clipboard")
          Toast
            .makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT)
            .show()
        },
      color = if (isSystemInDarkTheme()) DarkColors.onBackground else LightColors.onBackground
    )
    IconButton(
      onClick = {
        // Generate a QR code with the user ID, public key and device address
        setIsShowingQR(true)
      },
      modifier = Modifier
        .align(Alignment.CenterVertically)
        .padding(top = 4.dp)
        .size(56.dp)
    ) {
      Icon(
        imageVector = Icons.Default.QrCode2,
        contentDescription = "Generate QR code",
        tint = if (isSystemInDarkTheme()) DarkColors.onBackground else LightColors.onBackground,
        modifier = Modifier.size(24.dp)
      )
    }
  }
  if (isShowingQR) {
    val qrData = QrData(
      userId = userId,
//          publicKey = if (isMe) Base64.encodeToString(
//            bleBinder.encryptionManager.publicKey.encoded,
//            Base64.DEFAULT
//          ) else device?.publicKey,
    )
    Log.d("UserInfoScreen", "QR data: $qrData")
    Dialog(onDismissRequest = { setIsShowingQR(false) }) {
      Column(
        modifier = Modifier
          .padding(16.dp)
          .fillMaxWidth()
          .wrapContentHeight()
      ) {
        QRCode(
          content = Base64.encodeToString(moshi.packToByteArray(qrData), Base64.DEFAULT),
          modifier = Modifier
            .padding(top = 16.dp, bottom = 16.dp)
            .align(Alignment.CenterHorizontally),
          size = 1000
        )
      }
    }
  }
}
