package com.cstef.meshlink.screens

import android.R.attr.data
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cstef.meshlink.BleService
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import com.patrykandpatrick.vico.compose.axis.horizontal.bottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.startAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.FloatEntry
import java.io.IOException
import java.io.OutputStreamWriter


@Composable
fun BenchmarkScreen(binder: BleService.BleServiceBinder) {
  val benchmarkResults = remember { mutableStateOf<BleService.BenchmarkResults?>(null) }
  val benchmarkRunning = remember {
    mutableStateOf(false)
  }
  val context = LocalContext.current
  Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Button(
      onClick = {
        benchmarkRunning.value = true
        binder.service.benchmark {
          benchmarkRunning.value = false
          benchmarkResults.value = it
        }
      },
      enabled = !benchmarkRunning.value,
      content = {
        Text("Run Benchmark")
      }
    )
    if (benchmarkResults.value != null) {
      val producer = ChartEntryModelProducer(benchmarkResults.value!!.results.map { it.value }
        .mapIndexed { index, value ->
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
      Button(onClick = {
        val csvOutput = csvWriter().writeAllAsString(benchmarkResults.value!!.results.map { listOf(it.messageId, it.value) })
        try {
          val outputStreamWriter =
            OutputStreamWriter(context.openFileOutput("benchmark_${System.currentTimeMillis()}.csv", Context.MODE_PRIVATE))
          outputStreamWriter.write(csvOutput)
          outputStreamWriter.close()
        } catch (e: IOException) {
          Log.e("BenchmarkScreen", "File write failed: $e")
        }
      }) {
        Text("Save results")
      }
    }
  }
}

