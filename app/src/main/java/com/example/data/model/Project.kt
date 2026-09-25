package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val imageCount: Int = 0,
    val syntaxConfig: String = "",
    val customOutputDirUri: String? = null,
    val lastRenderedAt: Long? = null
)
