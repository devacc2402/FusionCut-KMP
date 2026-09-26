package com.example.data.repository

import android.content.Context
import com.example.data.db.AppDatabase
import com.example.data.db.getDatabaseBuilder

private lateinit var appContext: Context

fun initRepository(context: Context) {
    appContext = context.applicationContext
}

private val repositoryInstance: ProjectRepository by lazy {
    val builder = getDatabaseBuilder(appContext)
    val db = builder.fallbackToDestructiveMigration(dropAllTables = true).build()
    ProjectRepository(db.projectDao(), db.layerDao())
}

actual fun createProjectRepository(): ProjectRepository = repositoryInstance
