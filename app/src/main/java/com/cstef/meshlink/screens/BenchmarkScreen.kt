package com.cstef.meshlink.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cstef.meshlink.BleService
import com.patrykandpatrick.vico.compose.axis.horizontal.bottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.startAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.FloatEntry

@Composable
fun BenchmarkScreen(binder: BleService.BleServiceBinder) {
  val benchmarkResults = remember { mutableStateOf(emptyList<Long>()) }
  val benchmarkRunning = remember {
    mutableStateOf(false)
  }
  Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Button(
      onClick = {
        benchmarkRunning.value = true
        benchmarkResults.value = benchmark(binder)
        benchmarkRunning.value = false
      },
      enabled = !benchmarkRunning.value,
      content = {
        Text("Run Benchmark")
      }
    )
    if (benchmarkResults.value.isNotEmpty()) {
      val producer = ChartEntryModelProducer(benchmarkResults.value.mapIndexed { index, value ->
        FloatEntry(
          index.toFloat(),
          value.toFloat()
        )
      })
      Chart(
        chart = lineChart(),
        chartModelProducer = producer,
        modifier = Modifier
          .fillMaxWidth()
          .height(300.dp),
        startAxis = startAxis(),
        bottomAxis = bottomAxis(),
      )
    }
  }
}

fun benchmark(binder: BleService.BleServiceBinder): List<Long> {
  return emptyList()
}
