package com.example.data.db

import androidx.room.RoomDatabase

expect fun getDatabaseBuilder(context: Any): RoomDatabase.Builder<AppDatabase>
