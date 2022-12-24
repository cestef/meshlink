package com.cstef.meshlink.ui.components

import android.util.Base64
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.cstef.meshlink.util.struct.QrData
import com.daveanthonythomas.moshipack.MoshiPack

@Composable
fun DeviceID(modifier: Modifier = Modifier, userId: String, isMe: Boolean, blocked: Boolean) {
  val moshi = remember {
    MoshiPack()
  }
//  val context = LocalContext.current
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
          setIsShowingQR(true)
        },
      color = MaterialTheme.colorScheme.onBackground
    )
    if (isShowingQR) {
      val qrData = QrData(
        userId = userId,
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
}
