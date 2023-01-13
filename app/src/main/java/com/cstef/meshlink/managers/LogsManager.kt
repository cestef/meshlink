package com.cstef.meshlink.managers

import android.icu.util.GregorianCalendar
import androidx.lifecycle.MutableLiveData
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

// A class that listens to logcat on a separate thread and formats them into a LogcatMessage object

data class LogcatMessage(
  val pid: String,
  val tid: String,
  val priority: String,
  val tag: String,
  val message: String,
  val time: Long
)

class LogsManager {
  val logcatMessages = MutableLiveData<List<LogcatMessage>>()
  private val logcatMessagesLock = ReentrantLock()
  private val logcatMessagesCondition = logcatMessagesLock.newCondition()
  private val logcatMessagesThread = Thread {
    // Clear the logcat
    Runtime.getRuntime().exec("logcat -c").waitFor()
    val process = Runtime.getRuntime().exec("logcat -v threadtime")
    val reader = BufferedReader(InputStreamReader(process.inputStream))
    var line: String?
    while (true) {
      line = reader.readLine()
      if (line != null) {
        val logcatMessage = parseLogcatMessage(line)
        if (logcatMessage != null) {
          // if the tag is included in the list of tags not to be displayed, skip it
          val tagsToSkip =
            listOf(
              "Quality",
              "AutofillManager",
              "InputMethodManager",
              "InsetsController",
              "ProfileInstaller",
              "CompatibilityChangeReporter",
              "Activity",
              "Choreographer",
              "Looper",
              "OpenGLRenderer",
              "OplusSystemUINavigationGesture",
              "System",
              "BLASTBufferQueue",
            )
          if (logcatMessage.tag !in tagsToSkip) {
            logcatMessagesLock.withLock {
              logcatMessages.postValue(
                logcatMessages.value?.plus(logcatMessage) ?: listOf(
                  logcatMessage
                )
              )
              logcatMessagesCondition.signal()
            }
          }
        }
      }
    }
  }

  init {
    logcatMessagesThread.start()
  }

  private fun parseLogcatMessage(line: String): LogcatMessage? {
    val regex = Regex("([0-9-]+ [0-9:.]+) +([0-9]+) +([0-9]+) +([A-Z]) +([^:]+): (.*)")
    val matchResult = regex.find(line)
    if (matchResult != null) {
      val groups = matchResult.groups
      val time = GregorianCalendar().apply {
        val date = groups[1]!!.value.split(" ")
        val dateParts = date[0].split("-")
        val timeParts = date[1].split(":")
        set(
          /* Current year */ get(GregorianCalendar.YEAR),
          dateParts[0].toInt(),
          dateParts[1].toInt(),
          timeParts[0].toInt(),
          timeParts[1].toInt(),
          timeParts[2].split(".")[0].toInt()
        )
      }.timeInMillis
      return LogcatMessage(
        pid = groups[2]!!.value.trim(),
        tid = groups[3]!!.value.trim(),
        priority = groups[4]!!.value.trim(),
        tag = groups[5]!!.value.trim(),
        message = groups[6]!!.value.trim(),
        time = time
      )
    }
    return null
  }
}
