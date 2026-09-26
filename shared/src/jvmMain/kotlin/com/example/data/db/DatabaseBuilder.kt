package com.example.data.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File

actual fun getDatabaseBuilder(context: Any): RoomDatabase.Builder<AppDatabase> {
    val userHome = System.getProperty("user.home") ?: System.getProperty("java.io.tmpdir")
    val appDir = File(userHome, ".fusioncut").apply { mkdirs() }

    // Wipe ALL old database files in ~/.fusioncut/ and %TEMP%
    try {
        appDir.listFiles()?.filter { it.name.contains("fusion_cut") }?.forEach { it.delete() }
        val tempDir = File(System.getProperty("java.io.tmpdir"))
        tempDir.listFiles()?.filter { it.name.contains("fusion_cut") }?.forEach { it.delete() }
    } catch (e: Exception) {
        // Ignore
    }

    val dbFile = File(appDir, "fusion_cut_v10.db")

    return Room.databaseBuilder<AppDatabase>(
        name = dbFile.absolutePath,
    )
    .fallbackToDestructiveMigration(dropAllTables = true)
    .setDriver(BundledSQLiteDriver())
}
