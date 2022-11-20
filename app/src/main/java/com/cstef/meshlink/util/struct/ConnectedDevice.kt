package com.cstef.meshlink.util.struct

import android.os.Parcel
import android.os.Parcelable

data class ConnectedDevice(
  val id: String,
  val name: String?,
  var rssi: Int,
  var connected: Boolean,
  var lastSeen: Long
) : java.io.Serializable, Parcelable {
  constructor(parcel: Parcel) : this(
    parcel.readString()!!,
    parcel.readString(),
    parcel.readInt(),
    parcel.readByte() != 0.toByte(),
    parcel.readLong()
  ) {
  }

  override fun writeToParcel(parcel: Parcel, flags: Int) {
    parcel.writeString(id)
    parcel.writeString(name)
    parcel.writeInt(rssi)
    parcel.writeByte(if (connected) 1 else 0)
    parcel.writeLong(lastSeen)
  }

  override fun describeContents(): Int {
    return 0
  }

  companion object CREATOR : Parcelable.Creator<ConnectedDevice> {
    override fun createFromParcel(parcel: Parcel): ConnectedDevice {
      return ConnectedDevice(parcel)
    }

    override fun newArray(size: Int): Array<ConnectedDevice?> {
      return arrayOfNulls(size)
    }
  }
}
