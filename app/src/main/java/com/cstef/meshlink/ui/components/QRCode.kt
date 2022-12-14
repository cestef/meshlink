package com.cstef.meshlink.ui.components

import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder

@Composable
fun QRCode(modifier: Modifier = Modifier, content: String, size: Int = 500) {
  BarcodeEncoder().encodeBitmap(content, BarcodeFormat.QR_CODE, size, size)?.let {
    Image(
      bitmap = it.asImageBitmap(), contentDescription = "QR Code", modifier = modifier.clip(
        MaterialTheme.shapes.medium
      )
    )
  }
}
