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

  @Delete
  fun delete(device: Device)

  @Query("DELETE FROM devices")
  fun deleteAll()
}
