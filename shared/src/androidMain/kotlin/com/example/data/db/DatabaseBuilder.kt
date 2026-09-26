package com.example.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

actual fun getDatabaseBuilder(context: Any): RoomDatabase.Builder<AppDatabase> {
    val ctx = context as Context
    val dbFile = ctx.getDatabasePath("fusion_cut_v10.db")
    try {
        ctx.databaseList().filter { it.contains("fusion_cut") && !it.contains("v10") }.forEach {
            ctx.deleteDatabase(it)
        }
    } catch (e: Exception) {
        // Ignore
    }
    return Room.databaseBuilder<AppDatabase>(
        context = ctx,
        name = dbFile.absolutePath
    )
    .fallbackToDestructiveMigration(dropAllTables = true)
    .setDriver(BundledSQLiteDriver())
}

