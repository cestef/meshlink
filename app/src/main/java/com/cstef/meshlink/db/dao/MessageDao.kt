package com.cstef.meshlink.db.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cstef.meshlink.db.entities.Message

@Dao
interface MessageDao {
  @Query("SELECT * FROM messages")
  fun getAllMessages(): LiveData<List<Message>>

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  fun insert(message: Message)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  fun insertAll(messages: List<Message>)

  @Query("DELETE FROM messages")
  fun deleteAll()

  @Query("DELETE FROM messages WHERE sender_id = :userId OR recipient_id = :userId")
  fun deleteFromUser(userId: String)
}
