// AppDatabase.kt
package com.example.lta

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [
    NotificationEntity::class,
    SmsEntity::class,
    CallLogEntity::class,
    ContactEntity::class
], version = 2, exportSchema = false) // <-- INCREMENT VERSION
abstract class AppDatabase : RoomDatabase() {

    abstract fun notificationDao(): NotificationDao
    abstract fun smsDao(): SmsDao // <-- NEW
    abstract fun callLogDao(): CallLogDao // <-- NEW
    abstract fun contactDao(): ContactDao // <-- NEW

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lta_app_database"
                )
                .fallbackToDestructiveMigration() // <-- Add for easier schema changes during dev
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
