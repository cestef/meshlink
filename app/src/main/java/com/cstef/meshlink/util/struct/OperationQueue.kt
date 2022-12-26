package com.cstef.meshlink.util.struct

import android.os.CountDownTimer
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import java.util.*
class OperationQueue(private val timeout: Long = 10000, private val handler: Handler = Handler(Looper.getMainLooper())) {

  private var currentOperation: Operation? = null
  private val queue = ArrayDeque<Operation>()

  fun execute(onTimeout: () -> Unit = {}, operation: () -> Unit) {
    queue.add(Operation(operation, timeout, onTimeout))
    if (currentOperation == null) executeNext()
  }

  fun operationComplete() {
    timeoutTimer.cancel()
    currentOperation = null
    if (queue.isNotEmpty()) executeNext()
  }

  fun clear() {
    timeoutTimer.cancel()
    currentOperation = null
    queue.clear()
  }

  private fun executeNext() {
    currentOperation = queue.poll()
    handler.post {
      timeoutTimer.start()
      currentOperation?.operation?.invoke()
    }
  }

  private val timeoutTimer = object : CountDownTimer(timeout, 1000) {
    override fun onFinish() = run {
      currentOperation?.onTimeout?.invoke()
      operationComplete()
    }
    override fun onTick(millisUntilFinished: Long) {}
  }
}

data class Operation(val operation: () -> Unit, val timeout: Long = 10000, val onTimeout: () -> Unit = {})
