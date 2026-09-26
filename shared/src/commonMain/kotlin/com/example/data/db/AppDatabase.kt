package com.example.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.model.Layer
import com.example.model.Project

@Database(
    entities = [Project::class, Layer::class],
    version = 10,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun layerDao(): LayerDao
}

// Platform specific database builder will be handled in androidMain/desktopMain
