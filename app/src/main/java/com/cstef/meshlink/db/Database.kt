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
  version = 16,
  exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun deviceDao(): DeviceDao
  abstract fun messageDao(): MessageDao

  companion object {

    private var INSTANCE: AppDatabase? = null

    fun getInstance(context: Context, passphrase: String?): AppDatabase {
      if (INSTANCE == null) {
        val bytes = SQLiteDatabase.getBytes(passphrase?.toCharArray() ?: "MeshLink".toCharArray())
        val factory = SupportFactory(bytes)
        INSTANCE = Room.databaseBuilder(
          context,
          AppDatabase::class.java, "meshlink.db"
        ).openHelperFactory(factory).fallbackToDestructiveMigration().build()
      }
      return INSTANCE!!
    }

    fun destroyInstance() {
      INSTANCE = null
    }

    fun updatePassword(password: String): Boolean {
      if (INSTANCE == null) {
        return false
      } else {
        val db = INSTANCE!!.openHelper.writableDatabase as SQLiteDatabase
        try {
          db.changePassword(password)
        } catch (e: Exception) {
          e.printStackTrace()
          return false
        }
        return true
      }
    }
  }
}
