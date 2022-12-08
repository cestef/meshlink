package com.cstef.meshlink.repositories

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.cstef.meshlink.db.dao.DeviceDao
import com.cstef.meshlink.db.entities.Device
import kotlinx.coroutines.*

class DeviceRepository(
  private val deviceDao: DeviceDao,
  private val isDatabaseOpen: MutableLiveData<Boolean>,
  private val isDatabaseOpening: MutableLiveData<Boolean>,
  private val databaseError: MutableLiveData<String>
) {
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

  fun updateAllConnected(connected: Boolean) {
    coroutineScope.launch(Dispatchers.IO) { deviceDao.updateAllConnected(connected) }
  }

  fun checkDatabaseWorking() {
    isDatabaseOpening.value = true
    coroutineScope.launch(Dispatchers.Main) {
      isDatabaseOpen.value = asyncCheckDatabaseWorking().await()
      if (isDatabaseOpen.value == true) {
        databaseError.value = ""
      } else {
        databaseError.value = "Incorrect password"
      }
      isDatabaseOpening.value = false
    }
  }

  private fun asyncCheckDatabaseWorking(): Deferred<Boolean> {
    try {
      return coroutineScope.async(Dispatchers.IO) {
        try {
          return@async deviceDao.checkDatabaseWorking()
        } catch (e: Exception) {
          e.printStackTrace()
          return@async false
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
      return coroutineScope.async { return@async false }
    }
  }
}
