package com.example.data.repository

import com.example.model.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

import com.example.data.db.getDatabaseBuilder

private val repositoryInstance: ProjectRepository by lazy {
    val builder = getDatabaseBuilder(Unit)
    val db = builder.fallbackToDestructiveMigration(dropAllTables = true).build()
    ProjectRepository(db.projectDao(), db.layerDao())
}

actual fun createProjectRepository(): ProjectRepository = repositoryInstance
