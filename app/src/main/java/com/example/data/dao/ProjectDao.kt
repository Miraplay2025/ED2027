package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Project
import com.example.data.model.ProjectImage
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    fun getAllProjects(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun getProjectById(id: Long): Flow<Project?>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectSync(id: Long): Project?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: Project): Long

    @Update
    suspend fun updateProject(project: Project)

    @Delete
    suspend fun deleteProject(project: Project)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: Long)

    @Query("SELECT * FROM project_images WHERE projectId = :projectId ORDER BY orderIndex ASC")
    fun getImagesForProject(projectId: Long): Flow<List<ProjectImage>>

    @Query("SELECT * FROM project_images WHERE projectId = :projectId ORDER BY orderIndex ASC")
    suspend fun getImagesForProjectSync(projectId: Long): List<ProjectImage>

    @Query("SELECT * FROM project_images WHERE projectId = :projectId ORDER BY orderIndex ASC LIMIT 1")
    suspend fun getFirstMediaForProjectSync(projectId: Long): ProjectImage?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImages(images: List<ProjectImage>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: ProjectImage): Long

    @Delete
    suspend fun deleteImage(image: ProjectImage)

    @Query("DELETE FROM project_images WHERE id = :id")
    suspend fun deleteImageById(id: Long)

    @Query("DELETE FROM project_images WHERE projectId = :projectId")
    suspend fun deleteImagesForProject(projectId: Long)

    @Update
    suspend fun updateImages(images: List<ProjectImage>)
}
