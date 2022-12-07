package com.cstef.meshlink.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.cstef.meshlink.db.dao.DeviceDao
import com.cstef.meshlink.db.dao.MessageDao
import com.cstef.meshlink.db.entities.Device
import com.cstef.meshlink.db.entities.Message
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(
  entities = [
    Device::class,
    Message::class,
  ],
  version = 1,
  exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun deviceDao(): DeviceDao
  abstract fun messageDao(): MessageDao

  companion object {

    private var INSTANCE: AppDatabase? = null

    fun getInstance(context: Context, passphrase: String? = null): AppDatabase {
      if (INSTANCE == null) {
        val bytes = SQLiteDatabase.getBytes(passphrase?.toCharArray())
        val factory = SupportFactory(bytes)
        INSTANCE = Room.databaseBuilder(
          context,
          AppDatabase::class.java, "meshlink.db"
        ).openHelperFactory(factory).build()
      }
      return INSTANCE!!
    }

    fun destroyInstance() {
      INSTANCE = null
    }
  }
}
