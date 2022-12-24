package com.cstef.meshlink.db.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.cstef.meshlink.db.entities.Device

@Dao
interface DeviceDao {
  @Query("SELECT * FROM devices")
  fun getAllDevices(): LiveData<List<Device>>

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  fun insert(device: Device)

  @Update
  fun update(device: Device)

  @Query("DELETE FROM devices WHERE userId = :userId")
  fun delete(userId: String)

  @Query("DELETE FROM devices")
  fun deleteAll()

  @Query("UPDATE devices SET connected = :connected")
  fun updateAllConnected(connected: Boolean)

  @Query("SELECT 1")
  fun checkDatabaseWorking(): Boolean

}
