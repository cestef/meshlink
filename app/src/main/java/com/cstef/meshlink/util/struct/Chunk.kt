package com.cstef.meshlink.util.struct

import kotlin.experimental.or

// The first bit of the first byte is used to indicate if the chunk is the last one
// The rest of the first byte and the second byte are used to indicate the chunk index
// The remaining bytes of each chunk are used to store the actual data.
// The size of the resulting chunk's metadata is 2 bytes.

// if the MTU is 512 bytes, the Bluetooth header is 4 bytes, and the chunk metadata is 2 bytes,
// The maximum size of the data is 512-4-2=506 bytes.
// The maximum number of chunks is 2^15 = 32768
// The maximum size of the data is 32768 * 506 = 16'580'608 bytes = 16.5 MB
// A transfer of 16.5 MB with an average speed of 23 chunks per second would take 16.5 MB / (23 * 506) = ~8 minutes
class Chunk(val isLast: Boolean, val index: Short, val data: ByteArray, val messageId: Int) {
  fun toByteArray(): ByteArray {
    val byteArray = ByteArray(data.size + 6)
    byteArray[0] = if (isLast) 0b10000000.toByte() else 0b00000000.toByte()
    // the 7 last bits of the first byte are used to store the first 7 bits of the index
    byteArray[0] = byteArray[0] or ((index.toInt() shr 8) and 0b01111111).toByte()
    // the 8 bits of the second byte are used to store the last 8 bits of the index
    byteArray[1] = (index.toInt() and 0b11111111).toByte()
    // the 4 following bytes are used to store the message id
    byteArray[2] = (messageId shr 24).toByte()
    byteArray[3] = (messageId shr 16).toByte()
    byteArray[4] = (messageId shr 8).toByte()
    byteArray[5] = (messageId and 0xFF).toByte()
    // the remaining bytes are used to store the actual data
    data.copyInto(byteArray, 6)
    return byteArray
  }

  companion object {
    fun fromByteArray(byteArray: ByteArray): Chunk {
      val isLast = byteArray[0].toInt() and 0b10000000 != 0
      val index =
        ((byteArray[0].toInt() and 0b01111111) shl 8) or (byteArray[1].toInt() and 0b11111111)
      val messageId =
        (byteArray[2].toInt() shl 24) or (byteArray[3].toInt() shl 16) or (byteArray[4].toInt() shl 8) or (byteArray[5].toInt() and 0xFF)
      val data = byteArray.copyOfRange(6, byteArray.size)
      return Chunk(isLast, index.toShort(), data, messageId)
    }

    const val CHUNK_SIZE = 500
  }
}
