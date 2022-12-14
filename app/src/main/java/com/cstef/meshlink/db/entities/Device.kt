package com.cstef.meshlink.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class Device(
  @PrimaryKey val userId: String,
  @ColumnInfo(name = "address") val address: String? = null,
  @ColumnInfo(name = "rssi") val rssi: Int = 0,
  @ColumnInfo(name = "last_seen") val lastSeen: Long = 0,
  @ColumnInfo(name = "connected") val connected: Boolean = false,
  @ColumnInfo(name = "name") val name: String? = null,
  @ColumnInfo(name = "blocked") val blocked: Boolean = false,
  @ColumnInfo(name = "public_key") val publicKey: String? = null,
  @ColumnInfo(name = "added") val added: Boolean = false,
)
