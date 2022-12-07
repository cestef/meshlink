package com.cstef.meshlink.util.struct

import android.os.Parcel
import android.os.Parcelable

data class SavedDevice(
  val id: String,
  val name: String?,
  var rssi: Int,
  var connected: Boolean,
  var lastSeen: Long,
  var writing: Boolean = false
) : java.io.Serializable, Parcelable {

  constructor(parcel: Parcel) : this(
    parcel.readString()!!,
    parcel.readString(),
    parcel.readInt(),
    parcel.readByte() != 0.toByte(),
    parcel.readLong(),
    parcel.readByte() != 0.toByte()
  )

  override fun writeToParcel(parcel: Parcel, flags: Int) {
    parcel.writeString(id)
    parcel.writeString(name)
    parcel.writeInt(rssi)
    parcel.writeByte(if (connected) 1 else 0)
    parcel.writeLong(lastSeen)
    parcel.writeByte(if (writing) 1 else 0)
  }

  override fun describeContents(): Int {
    return 0
  }

  companion object CREATOR : Parcelable.Creator<SavedDevice> {
    override fun createFromParcel(parcel: Parcel): SavedDevice {
      return SavedDevice(parcel)
    }

    override fun newArray(size: Int): Array<SavedDevice?> {
      return arrayOfNulls(size)
    }
  }
}
