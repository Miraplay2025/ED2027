package com.example.data.repository

import android.content.Context
import com.example.data.dao.ProjectDao
import com.example.data.model.Project
import com.example.data.model.ProjectImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class ProjectRepository(
    private val projectDao: ProjectDao,
    private val context: Context
) {
    fun getAllProjects(): Flow<List<Project>> = projectDao.getAllProjects()

    fun getProject(id: Long): Flow<Project?> = projectDao.getProjectById(id)

    suspend fun getProjectSync(id: Long): Project? = withContext(Dispatchers.IO) {
        projectDao.getProjectSync(id)
    }

    suspend fun createProject(name: String): Long = withContext(Dispatchers.IO) {
        val project = Project(
            name = name.ifBlank { "Novo Projeto" },
            createdAt = System.currentTimeMillis(),
            imageCount = 0,
            syntaxConfig = ""
        )
        projectDao.insertProject(project)
    }

    suspend fun updateProject(project: Project) = withContext(Dispatchers.IO) {
        projectDao.updateProject(project)
    }

    suspend fun deleteProject(projectId: Long) = withContext(Dispatchers.IO) {
        projectDao.deleteProjectById(projectId)
        // Clean up project disk directory
        val projectFolder = File(context.filesDir, "projects/$projectId")
        if (projectFolder.exists()) {
            projectFolder.deleteRecursively()
        }
    }

    fun getImages(projectId: Long): Flow<List<ProjectImage>> =
        projectDao.getImagesForProject(projectId)

    suspend fun getImagesSync(projectId: Long): List<ProjectImage> = withContext(Dispatchers.IO) {
        projectDao.getImagesForProjectSync(projectId)
    }

    suspend fun getFirstMediaSync(projectId: Long): ProjectImage? = withContext(Dispatchers.IO) {
        projectDao.getFirstMediaForProjectSync(projectId)
    }

    suspend fun addImages(projectId: Long, imagePairs: List<Pair<String, String>>) = withContext(Dispatchers.IO) {
        val currentImages = projectDao.getImagesForProjectSync(projectId)
        var nextOrder = currentImages.size + 1

        val newEntities = imagePairs.map { (path, origName) ->
            ProjectImage(
                projectId = projectId,
                filePath = path,
                originalFileName = origName,
                orderIndex = nextOrder++
            )
        }
        projectDao.insertImages(newEntities)

        val totalCount = currentImages.size + newEntities.size
        val project = projectDao.getProjectSync(projectId)
        if (project != null) {
            projectDao.updateProject(project.copy(imageCount = totalCount))
        }
    }

    suspend fun deleteImage(projectId: Long, imageId: Long) = withContext(Dispatchers.IO) {
        val currentImages = projectDao.getImagesForProjectSync(projectId)
        val imageToDelete = currentImages.firstOrNull { it.id == imageId }
        if (imageToDelete != null) {
            // Delete file from storage
            val file = File(imageToDelete.filePath)
            if (file.exists()) {
                file.delete()
            }
            projectDao.deleteImageById(imageId)

            // Re-sequence remaining images 1, 2, 3...
            val remaining = currentImages.filter { it.id != imageId }
            val reordered = remaining.mapIndexed { index, img ->
                img.copy(orderIndex = index + 1)
            }
            projectDao.updateImages(reordered)

            val project = projectDao.getProjectSync(projectId)
            if (project != null) {
                projectDao.updateProject(project.copy(imageCount = reordered.size))
            }
        }
    }

    suspend fun updateProjectSyntax(projectId: Long, syntax: String) = withContext(Dispatchers.IO) {
        val project = projectDao.getProjectSync(projectId)
        if (project != null) {
            projectDao.updateProject(project.copy(syntaxConfig = syntax))
        }
    }

    suspend fun updateCustomOutputDir(projectId: Long, uriString: String?) = withContext(Dispatchers.IO) {
        val project = projectDao.getProjectSync(projectId)
        if (project != null) {
            projectDao.updateProject(project.copy(customOutputDirUri = uriString))
        }
    }

    suspend fun markLastRendered(projectId: Long) = withContext(Dispatchers.IO) {
        val project = projectDao.getProjectSync(projectId)
        if (project != null) {
            projectDao.updateProject(project.copy(lastRenderedAt = System.currentTimeMillis()))
        }
    }
}
