package com.cstef.meshlink.repositories

import androidx.lifecycle.LiveData
import com.cstef.meshlink.db.dao.DeviceDao
import com.cstef.meshlink.db.entities.Device
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DeviceRepository(private val deviceDao: DeviceDao) {
  val allDevices: LiveData<List<Device>> = deviceDao.getAllDevices()
  private val coroutineScope = CoroutineScope(Dispatchers.Main)

  fun insert(device: Device) {
    coroutineScope.launch(Dispatchers.IO) { deviceDao.insert(device) }
  }

  fun update(device: Device) {
    coroutineScope.launch(Dispatchers.IO) { deviceDao.update(device) }
  }

  fun delete(device: Device) {
    coroutineScope.launch(Dispatchers.IO) { deviceDao.delete(device) }
  }

  fun deleteAll() {
    coroutineScope.launch(Dispatchers.IO) { deviceDao.deleteAll() }
  }
}
