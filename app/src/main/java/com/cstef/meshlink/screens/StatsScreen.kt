package com.cstef.meshlink.screens

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun StatsScreen() {
  // Total number of messages sent, received
  // Total number of bytes sent, received
  // Total number of connections
  // Message delivery rate
  val context = LocalContext.current
  val sharedPrefs = context.getSharedPreferences("USER_STATS", Context.MODE_PRIVATE)
  val totalMessagesSent = sharedPrefs.getInt("TOTAL_MESSAGES_SENT", 0)
  val totalMessagesReceived = sharedPrefs.getInt("TOTAL_MESSAGES_RECEIVED", 0)
//  val totalBytesSent = sharedPrefs.getInt("TOTAL_BYTES_SENT", 0)
//  val totalBytesReceived = sharedPrefs.getInt("TOTAL_BYTES_RECEIVED", 0)
  val totalConnections = sharedPrefs.getInt("TOTAL_CONNECTIONS", 0)
  val totalMessagesFailed = sharedPrefs.getInt("TOTAL_MESSAGES_FAILED", 0)
  val totalMessagesDelivered = sharedPrefs.getInt("TOTAL_MESSAGES_DELIVERED", 0)
  Log.d("StatsScreen", "delivered: $totalMessagesDelivered, failed: $totalMessagesFailed")
  val messageDeliveryRate = if (totalMessagesDelivered + totalMessagesFailed == 0) {
    0f
  } else {
    totalMessagesDelivered / (totalMessagesDelivered + totalMessagesFailed) * 100
  }
  val colors = MaterialTheme.colorScheme
  Column(
    modifier = Modifier
      .padding(top = 64.dp)
      .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    Text(
      text = "Total messages sent: $totalMessagesSent",
      modifier = Modifier.padding(start = 16.dp),
      color = colors.onBackground,
      style = MaterialTheme.typography.bodyLarge
    )
    Text(
      text = "Total messages received: $totalMessagesReceived",
      modifier = Modifier.padding(start = 16.dp),
      color = colors.onBackground,
      style = MaterialTheme.typography.bodyLarge
    )
    Text(
      text = "Total connections: $totalConnections",
      modifier = Modifier.padding(start = 16.dp),
      color = colors.onBackground,
      style = MaterialTheme.typography.bodyLarge
    )
    Text(
      text = "Message delivery rate: $messageDeliveryRate%",
      modifier = Modifier.padding(start = 16.dp),
      color = colors.onBackground,
      style = MaterialTheme.typography.bodyLarge
    )
  }
}
