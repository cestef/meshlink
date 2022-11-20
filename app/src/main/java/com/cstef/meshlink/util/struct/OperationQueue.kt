package com.cstef.meshlink.util.struct

import android.os.CountDownTimer
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import java.util.*

/**
 * Execute operations in series. If an operation B is executed while operation A
 * is in progress. Operations B will not be executed until operation A is marked
 * as complete.
 * Runs operations on a separate thread.
 */
class OperationQueue(timeout: Long = 10000, private val handler: Handler = Handler(Looper.getMainLooper())) {

  private var currentOperation: (() -> Unit)? = null
  private val queue = ArrayDeque<() -> Unit>()

  fun execute(operation: () -> Unit) {
    queue.add(operation)
    if (currentOperation == null) executeNext()
  }

  fun operationComplete() {
    timeout.cancel()
    currentOperation = null
    if (queue.isNotEmpty()) executeNext()
  }

  fun clear() {
    timeout.cancel()
    currentOperation = null
    queue.clear()
  }

  private fun executeNext() {
    currentOperation = queue.poll()
    handler.post {
      timeout.start()
      currentOperation?.invoke()
    }
  }

  private val timeout = object : CountDownTimer(timeout, 1000) {
    override fun onFinish() = operationComplete()
    override fun onTick(millisUntilFinished: Long) {
    }
  }
}
