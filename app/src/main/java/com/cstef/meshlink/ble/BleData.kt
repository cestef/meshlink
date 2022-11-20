package com.cstef.meshlink.ble


/**
 * Wrapper for data sent and received via BLE
 */
data class BleData(
  val senderId: String,
  var content: String,
  val recipientId: String? = null,
  val type: String = "text",
)
