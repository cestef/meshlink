package com.cstef.meshlink.repositories

import androidx.lifecycle.LiveData
import com.cstef.meshlink.db.dao.MessageDao
import com.cstef.meshlink.db.entities.Message
import kotlinx.coroutines.*

class MessageRepository(private val messageDao: MessageDao) {
  val allMessages: LiveData<List<Message>> = messageDao.getAllMessages()
  private val coroutineScope = CoroutineScope(Dispatchers.Main)

  fun insert(message: Message) {
    coroutineScope.launch(Dispatchers.IO) { messageDao.insert(message) }
  }

  fun deleteAll() {
    coroutineScope.launch(Dispatchers.IO) { messageDao.deleteAll() }
  }

  fun delete(userId: String) {
    coroutineScope.launch(Dispatchers.IO) { messageDao.delete(userId) }
  }
}

