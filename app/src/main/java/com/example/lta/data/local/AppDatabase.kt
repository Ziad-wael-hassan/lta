package com.example.lta.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.lta.data.local.model.CallLogEntity
import com.example.lta.data.local.model.ContactEntity
import com.example.lta.data.local.model.NotificationEntity
import com.example.lta.data.local.model.SmsEntity

@Database(entities = [
    NotificationEntity::class,
    SmsEntity::class,
    CallLogEntity::class,
    ContactEntity::class
], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun notificationDao(): NotificationDao
    abstract fun smsDao(): SmsDao
    abstract fun callLogDao(): CallLogDao
    abstract fun contactDao(): ContactDao

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
                // Using destructive migration for simplicity. For production,
                // a proper migration strategy would be required.
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
