package com.example.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [LocalMissionPostEntity::class],
  version = 1,
  exportSchema = false,
)
abstract class VeyraLocalDatabase : RoomDatabase() {
  abstract fun localMissionPostDao(): LocalMissionPostDao

  companion object {
    @Volatile
    private var INSTANCE: VeyraLocalDatabase? = null

    fun getInstance(context: Context): VeyraLocalDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: Room.databaseBuilder(
          context.applicationContext,
          VeyraLocalDatabase::class.java,
          "veyra_local_missions.db",
        ).build().also { INSTANCE = it }
      }
    }
  }
}
