package com.samsung.prism.teachable.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WorkflowEntity::class,
        WorkflowStepEntity::class,
        RunEntity::class,
        RunStepEntity::class,
        AppRegistryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PrismDatabase : RoomDatabase() {

    abstract fun workflowDao(): WorkflowDao
    abstract fun runDao(): RunDao
    abstract fun appRegistryDao(): AppRegistryDao

    companion object {
        @Volatile
        private var INSTANCE: PrismDatabase? = null

        fun getInstance(context: Context): PrismDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PrismDatabase::class.java,
                    "prism_teachable.db"
                ).fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun createInMemory(context: Context): PrismDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                PrismDatabase::class.java
            ).allowMainThreadQueries()
                .build()
        }
    }
}
