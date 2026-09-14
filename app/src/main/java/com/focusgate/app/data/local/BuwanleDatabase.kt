package com.focusgate.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SessionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class BuwanleDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile
        private var instance: BuwanleDatabase? = null

        fun get(context: Context): BuwanleDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                BuwanleDatabase::class.java,
                "buwanle.db"
            )
                // Existing screens use a synchronous repository contract. Queries are small and
                // the service never performs network work on the accessibility callback path.
                .allowMainThreadQueries()
                .build()
                .also { instance = it }
        }
    }
}
