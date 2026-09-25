package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.Project
import com.example.data.repository.ProjectRepository
import com.example.engine.MediaHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProjectItemUiModel(
    val project: Project,
    val firstMediaFilePath: String?,
    val isVideo: Boolean
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ProjectRepository

    val projects: StateFlow<List<Project>>

    private val _projectItems = MutableStateFlow<List<ProjectItemUiModel>>(emptyList())
    val projectItems: StateFlow<List<ProjectItemUiModel>> = _projectItems.asStateFlow()

    init {
        val db = AppDatabase.getInstance(application)
        repository = ProjectRepository(db.projectDao(), application)
        projects = repository.getAllProjects().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        viewModelScope.launch {
            projects.collect { list ->
                val uiModels = list.map { proj ->
                    val firstMedia = repository.getFirstMediaSync(proj.id)
                    val path = firstMedia?.filePath
                    val isVid = if (path != null) MediaHelper.isVideo(path) else false
                    ProjectItemUiModel(
                        project = proj,
                        firstMediaFilePath = path,
                        isVideo = isVid
                    )
                }
                _projectItems.value = uiModels
            }
        }
    }

    fun createProject(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.createProject(name)
            onCreated(id)
        }
    }

    fun deleteProject(projectId: Long) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
        }
    }
}
