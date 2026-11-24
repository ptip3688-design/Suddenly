package com.example.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.MemoryFact
import com.example.data.model.NoteItem
import com.example.data.model.ScheduleItem
import com.example.data.model.TaskItem
import com.example.data.model.TaskPriority
import com.example.data.model.UserPreferences
import com.example.data.repository.JarvisFirestoreRepository
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private const val TAG = "WorkspaceVM"

sealed interface WorkspaceUiState<out T> {
    data object Loading : WorkspaceUiState<Nothing>
    data class Success<T>(val data: T) : WorkspaceUiState<T>
    data class Error(val message: String) : WorkspaceUiState<Nothing>
}

class WorkspaceViewModel(
    private val repository: JarvisFirestoreRepository,
    private val currentUserId: String
) : ViewModel() {

    private val noteAiModel by lazy { Firebase.ai.generativeModel("gemini-3.8-flash") }

    val tasksState: StateFlow<WorkspaceUiState<List<TaskItem>>> =
        repository.observeTasks(currentUserId).toWorkspaceState("Failed to load tasks")

    val notesState: StateFlow<WorkspaceUiState<List<NoteItem>>> =
        repository.observeNotes(currentUserId).toWorkspaceState("Failed to load notes")

    val schedulesState: StateFlow<WorkspaceUiState<List<ScheduleItem>>> =
        repository.observeSchedules(currentUserId).toWorkspaceState("Failed to load schedule")

    val memoriesState: StateFlow<WorkspaceUiState<List<MemoryFact>>> =
        repository.observeMemories(currentUserId).toWorkspaceState("Failed to load memory")

    val preferences: StateFlow<UserPreferences> = repository.observePreferences(currentUserId)
        .catch { err ->
            if (err is CancellationException) throw err
            emit(UserPreferences())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UserPreferences())

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private val _isSummarizingNote = MutableStateFlow(false)
    val isSummarizingNote: StateFlow<Boolean> = _isSummarizingNote.asStateFlow()

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun addTask(title: String, dueLabel: String, priority: TaskPriority) {
        viewModelScope.launch {
            repository.addTask(title = title, dueLabel = dueLabel.ifBlank { null }, priority = priority)
                .onFailure { _feedbackMessage.value = it.localizedMessage ?: "Failed to add task." }
        }
    }

    fun toggleTask(task: TaskItem) {
        viewModelScope.launch {
            repository.toggleTask(task.id, !task.done)
                .onFailure { _feedbackMessage.value = it.localizedMessage ?: "Failed to update task." }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            repository.deleteTask(taskId)
                .onFailure { _feedbackMessage.value = it.localizedMessage ?: "Failed to delete task." }
        }
    }

    fun addNote(title: String, content: String, tag: String) {
        viewModelScope.launch {
            repository.addNote(title, content, tag)
                .onFailure { _feedbackMessage.value = it.localizedMessage ?: "Failed to save note." }
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            repository.deleteNote(noteId)
                .onFailure { _feedbackMessage.value = it.localizedMessage ?: "Failed to delete note." }
        }
    }

    fun summarizeNoteWithAi(note: NoteItem) {
        if (note.content.isBlank()) return
        viewModelScope.launch {
            _isSummarizingNote.value = true
            try {
                val summary = withTimeout(45_000L) {
                    noteAiModel.generateContent(
                        "Summarize and organize these study/personal notes clearly with bullet points and key takeaways:\n\nTitle: ${note.title}\n${note.content}"
                    ).text.orEmpty()
                }
                if (summary.isNotBlank()) {
                    repository.updateNote(note.id, note.title, summary, note.tag)
                    _feedbackMessage.value = "Note summarized and polished by Jarvis AI."
                }
            } catch (e: TimeoutCancellationException) {
                _feedbackMessage.value = "AI note summary timed out."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _feedbackMessage.value = e.localizedMessage ?: "Failed to summarize note."
            } finally {
                _isSummarizingNote.value = false
            }
        }
    }

    fun addSchedule(title: String, timeSlot: String, dateLabel: String, category: String) {
        viewModelScope.launch {
            repository.addSchedule(title, timeSlot, dateLabel, category)
                .onFailure { _feedbackMessage.value = it.localizedMessage ?: "Failed to add schedule." }
        }
    }

    fun deleteSchedule(scheduleId: String) {
        viewModelScope.launch {
            repository.deleteSchedule(scheduleId)
                .onFailure { _feedbackMessage.value = it.localizedMessage ?: "Failed to delete schedule." }
        }
    }

    fun addMemory(fact: String) {
        viewModelScope.launch {
            repository.addMemory(fact)
                .onFailure { _feedbackMessage.value = it.localizedMessage ?: "Failed to save memory." }
        }
    }

    fun toggleMemory(memory: MemoryFact) {
        viewModelScope.launch {
            repository.toggleMemory(memory.id, !memory.active)
                .onFailure { _feedbackMessage.value = it.localizedMessage ?: "Failed to update memory." }
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            repository.deleteMemory(memoryId)
                .onFailure { _feedbackMessage.value = it.localizedMessage ?: "Failed to delete memory." }
        }
    }

    fun updatePreferences(
        memoryEnabled: Boolean = preferences.value.memoryEnabled,
        voiceAutoSpeak: Boolean = preferences.value.voiceAutoSpeak,
        preferredLanguage: String = preferences.value.preferredLanguage,
        preferredModel: String = preferences.value.preferredModel
    ) {
        viewModelScope.launch {
            repository.savePreferences(
                memoryEnabled = memoryEnabled,
                voiceAutoSpeak = voiceAutoSpeak,
                preferredLanguage = preferredLanguage,
                preferredModel = preferredModel
            ).onFailure {
                _feedbackMessage.value = it.localizedMessage ?: "Failed to save preferences."
            }
        }
    }

    private fun <T> Flow<T>.toWorkspaceState(fallbackMsg: String): StateFlow<WorkspaceUiState<T>> {
        return this
            .map<T, WorkspaceUiState<T>> { WorkspaceUiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, fallbackMsg, rootError)
                emit(WorkspaceUiState.Error(rootError.message ?: fallbackMsg))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = WorkspaceUiState.Loading
            )
    }

    companion object {
        fun provideFactory(
            repository: JarvisFirestoreRepository,
            currentUserId: String
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { WorkspaceViewModel(repository, currentUserId) }
        }
    }
}
