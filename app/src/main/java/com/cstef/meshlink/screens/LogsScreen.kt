package com.cstef.meshlink.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.MutableLiveData
import com.cstef.meshlink.managers.LogcatMessage

@Composable
fun LogsScreen(logcatLogs: MutableLiveData<List<LogcatMessage>>, clearLogs: () -> Unit) {
  val logs by logcatLogs.observeAsState(listOf())
  LogsList(logs = logs, clearLogs = clearLogs)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsList(logs: List<LogcatMessage>, clearLogs: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  // go to the bottom of the list when new logs are added
  val scrollState = rememberScrollState()
  LaunchedEffect(logs.size) {
    scrollState.animateScrollTo(logs.size * 100)
  }
  val (filter, setFilter) = remember { mutableStateOf("") }
  Column(modifier = Modifier.fillMaxSize()) {
    TopAppBar(
      title = {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          OutlinedTextField(
            value = filter,
            onValueChange = setFilter,
            label = { Text("Filter") },
            modifier = Modifier
              .weight(1f)
              .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
              .align(Alignment.CenterVertically),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
          )
          IconButton(
            onClick = {
              clearLogs()
            },
            modifier = Modifier
              .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
              .align(Alignment.CenterVertically)
          ) {
            Icon(
              imageVector = Icons.Default.ClearAll,
              contentDescription = "Clear logs",
              tint = colors.onSurface
            )
          }
        }
      },
      modifier = Modifier.height(96.dp),
      colors = TopAppBarDefaults.topAppBarColors(
        containerColor = colors.background,
      ),
    )
    LazyColumn(
      modifier = Modifier.fillMaxSize()
    ) {
      items(logs.filter {
        it.message.contains(filter, true) || it.tag.contains(
          filter, true
        )
      }) { log ->
        Spacer(
          modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.Gray)
        )
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = log.tag, modifier = Modifier.padding(start = 8.dp), color = when (log.priority) {
              "V" -> colors.onSurface
              "D" -> colors.primary
              "I" -> colors.secondary
              "W" -> colors.tertiary
              "E" -> colors.error
              else -> colors.onSurface
            }, style = MaterialTheme.typography.bodyMedium
          )
          Text(
            text = log.message,
            modifier = Modifier.padding(start = 8.dp),
            color = colors.onBackground,
            style = MaterialTheme.typography.bodyMedium
          )
        }
      }
    }
  }
}
