package com.cstef.meshlink.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TextCard(
  isMine: Boolean, content: String
) {
  Card(
    modifier = Modifier
      .padding(8.dp)
      .fillMaxWidth()
      .padding(
        start = if (isMine) 64.dp else 0.dp, end = if (isMine) 0.dp else 64.dp
      ),
    shape = MaterialTheme.shapes.large,
    elevation = CardDefaults.cardElevation(0.dp),
    colors = CardDefaults.outlinedCardColors(
      containerColor = if (isMine) {
        MaterialTheme.colorScheme.primary
      } else {
        MaterialTheme.colorScheme.secondary
      },
    )
  ) {
    Row {
      Column(
        modifier = Modifier
          .padding(16.dp)
          .fillMaxWidth()
          .align(alignment = Alignment.CenterVertically)
      ) {
        Text(text = content, style = MaterialTheme.typography.bodyLarge)
      }
    }
  }
}
