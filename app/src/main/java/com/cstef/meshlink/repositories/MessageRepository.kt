package com.cstef.meshlink.repositories

import androidx.lifecycle.LiveData
import com.cstef.meshlink.db.dao.MessageDao
import com.cstef.meshlink.db.entities.DatabaseMessage
import kotlinx.coroutines.*

class MessageRepository(private val messageDao: MessageDao) {
  val allMessages: LiveData<List<DatabaseMessage>> = messageDao.getAllMessages()
  private val coroutineScope = CoroutineScope(Dispatchers.Main)

  fun insert(databaseMessage: DatabaseMessage) {
    coroutineScope.launch(Dispatchers.IO) { messageDao.insert(databaseMessage) }
  }

  fun deleteAll() {
    coroutineScope.launch(Dispatchers.IO) { messageDao.deleteAll() }
  }

  fun delete(userId: String) {
    coroutineScope.launch(Dispatchers.IO) { messageDao.deleteFromUser(userId) }
  }
}

