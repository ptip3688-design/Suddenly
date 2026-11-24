package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.MemoryFact
import com.example.data.model.NoteItem
import com.example.data.model.ScheduleItem
import com.example.data.model.TaskItem
import com.example.data.model.TaskPriority
import com.example.data.model.UserPreferences
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class JarvisFirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun <T> safeCollectionFlow(
        path: String,
        uid: String,
        flowProvider: () -> Flow<T>
    ): Flow<T> = flow {
        emitAll(
            flowProvider().catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                }
                throw error
            }
        )
    }

    // ---- Conversations & Messages ----

    fun observeConversations(userId: String): Flow<List<Conversation>> {
        if (userId.isBlank()) return flow { emit(emptyList()) }
        val path = "users/$userId/conversations"
        return safeCollectionFlow(path, userId) {
            db.collection("users").document(userId).collection("conversations")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { it.toObjects(Conversation::class.java, ServerTimestampBehavior.ESTIMATE) }
        }
    }

    fun observeMessages(userId: String, conversationId: String): Flow<List<ChatMessage>> {
        if (userId.isBlank() || conversationId.isBlank()) return flow { emit(emptyList()) }
        val path = "users/$userId/conversations/$conversationId/messages"
        return safeCollectionFlow(path, userId) {
            db.collection("users").document(userId)
                .collection("conversations").document(conversationId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { it.toObjects(ChatMessage::class.java, ServerTimestampBehavior.ESTIMATE) }
        }
    }

    suspend fun createConversation(title: String, modelName: String): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/conversations"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim().ifBlank { "New Conversation" }.take(120)
            val cleanModel = modelName.trim().ifBlank { "gemini-3.8-flash" }.take(60)
            val docRef = db.collection("users").document(uid).collection("conversations").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "modelName" to cleanModel,
                "lastMessagePreview" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun appendMessage(
        conversationId: String,
        role: String,
        text: String,
        attachmentLabel: String? = null,
        updateTitle: String? = null
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/conversations/$conversationId/messages"
        return try {
            val uid = requireUserId()
            require(conversationId.isNotBlank()) { "Conversation ID cannot be blank" }
            val cleanRole = if (role == "model") "model" else "user"
            val cleanText = text.take(32000)
            val convRef = db.collection("users").document(uid)
                .collection("conversations").document(conversationId)
            val msgRef = convRef.collection("messages").document()
            docPath = msgRef.path

            val msgPayload = mapOf(
                "userId" to uid,
                "role" to cleanRole,
                "text" to cleanText,
                "attachmentLabel" to attachmentLabel?.trim()?.take(120)?.ifBlank { null },
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            val convUpdates = mutableMapOf<String, Any>(
                "lastMessagePreview" to cleanText.replace("\n", " ").take(280),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!updateTitle.isNullOrBlank()) {
                convUpdates["title"] = updateTitle.trim().take(120)
            }

            val batch = db.batch()
            batch.set(msgRef, msgPayload)
            batch.update(convRef, convUpdates)
            batch.commit().await()
            Result.success(msgRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteConversation(conversationId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/conversations/$conversationId"
        return try {
            val uid = requireUserId()
            val convRef = db.collection("users").document(uid)
                .collection("conversations").document(conversationId)
            docPath = convRef.path
            val messages = convRef.collection("messages").get().await()
            val batch = db.batch()
            messages.documents.forEach { batch.delete(it.reference) }
            batch.delete(convRef)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // ---- Tasks ----

    fun observeTasks(userId: String): Flow<List<TaskItem>> {
        if (userId.isBlank()) return flow { emit(emptyList()) }
        val path = "users/$userId/tasks"
        return safeCollectionFlow(path, userId) {
            db.collection("users").document(userId).collection("tasks")
                .snapshots()
                .map { snap ->
                    snap.toObjects(TaskItem::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(compareBy<TaskItem> { it.done }.thenByDescending { it.createdAt ?: Timestamp(0, 0) })
                }
        }
    }

    suspend fun addTask(
        title: String,
        notes: String? = null,
        dueLabel: String? = null,
        priority: TaskPriority = TaskPriority.MEDIUM
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/tasks"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim().take(200)
            require(cleanTitle.isNotEmpty()) { "Task title is required" }
            val docRef = db.collection("users").document(uid).collection("tasks").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "notes" to notes?.trim()?.take(1000)?.ifBlank { null },
                "dueLabel" to dueLabel?.trim()?.take(80)?.ifBlank { null },
                "priority" to priority.name,
                "done" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleTask(taskId: String, done: Boolean): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/tasks/$taskId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("tasks").document(taskId)
            docPath = docRef.path
            docRef.update(mapOf("done" to done, "updatedAt" to FieldValue.serverTimestamp())).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteTask(taskId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/tasks/$taskId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("tasks").document(taskId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // ---- Notes ----

    fun observeNotes(userId: String): Flow<List<NoteItem>> {
        if (userId.isBlank()) return flow { emit(emptyList()) }
        val path = "users/$userId/notes"
        return safeCollectionFlow(path, userId) {
            db.collection("users").document(userId).collection("notes")
                .snapshots()
                .map { snap ->
                    snap.toObjects(NoteItem::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
        }
    }

    suspend fun addNote(title: String, content: String, tag: String = "General"): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/notes"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim().take(150)
            require(cleanTitle.isNotEmpty()) { "Note title is required" }
            val docRef = db.collection("users").document(uid).collection("notes").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "content" to content.trim().take(10000),
                "tag" to tag.trim().ifBlank { "General" }.take(40),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateNote(noteId: String, title: String, content: String, tag: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/notes/$noteId"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim().take(150)
            require(cleanTitle.isNotEmpty()) { "Note title is required" }
            val docRef = db.collection("users").document(uid).collection("notes").document(noteId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "title" to cleanTitle,
                    "content" to content.trim().take(10000),
                    "tag" to tag.trim().ifBlank { "General" }.take(40),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteNote(noteId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/notes/$noteId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("notes").document(noteId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // ---- Schedules ----

    fun observeSchedules(userId: String): Flow<List<ScheduleItem>> {
        if (userId.isBlank()) return flow { emit(emptyList()) }
        val path = "users/$userId/schedules"
        return safeCollectionFlow(path, userId) {
            db.collection("users").document(userId).collection("schedules")
                .snapshots()
                .map { snap ->
                    snap.toObjects(ScheduleItem::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
        }
    }

    suspend fun addSchedule(
        title: String,
        timeSlot: String,
        dateLabel: String = "Today",
        category: String = "Focus"
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/schedules"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim().take(150)
            val cleanTime = timeSlot.trim().ifBlank { "09:00 AM" }.take(60)
            require(cleanTitle.isNotEmpty()) { "Schedule title is required" }
            val docRef = db.collection("users").document(uid).collection("schedules").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "timeSlot" to cleanTime,
                "dateLabel" to dateLabel.trim().ifBlank { "Today" }.take(60),
                "category" to category.trim().ifBlank { "Focus" }.take(40),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteSchedule(scheduleId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/schedules/$scheduleId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("schedules").document(scheduleId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // ---- Memory & Preferences ----

    fun observeMemories(userId: String): Flow<List<MemoryFact>> {
        if (userId.isBlank()) return flow { emit(emptyList()) }
        val path = "users/$userId/memories"
        return safeCollectionFlow(path, userId) {
            db.collection("users").document(userId).collection("memories")
                .snapshots()
                .map { snap ->
                    snap.toObjects(MemoryFact::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
        }
    }

    suspend fun addMemory(fact: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/memories"
        return try {
            val uid = requireUserId()
            val cleanFact = fact.trim().take(400)
            require(cleanFact.isNotEmpty()) { "Memory fact cannot be empty" }
            val docRef = db.collection("users").document(uid).collection("memories").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "fact" to cleanFact,
                "active" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleMemory(memoryId: String, active: Boolean): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/memories/$memoryId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("memories").document(memoryId)
            docPath = docRef.path
            docRef.update(mapOf("active" to active, "updatedAt" to FieldValue.serverTimestamp())).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteMemory(memoryId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/memories/$memoryId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("memories").document(memoryId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    fun observePreferences(userId: String): Flow<UserPreferences> {
        if (userId.isBlank()) return flow { emit(UserPreferences()) }
        val path = "users/$userId/preferences/settings"
        return safeCollectionFlow(path, userId) {
            db.collection("users").document(userId)
                .collection("preferences").document("settings")
                .snapshots()
                .map { snap ->
                    snap.toObject(UserPreferences::class.java, ServerTimestampBehavior.ESTIMATE)
                        ?: UserPreferences(userId = userId)
                }
        }
    }

    suspend fun savePreferences(
        memoryEnabled: Boolean,
        voiceAutoSpeak: Boolean,
        preferredLanguage: String,
        preferredModel: String
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauth"}/preferences/settings"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
                .collection("preferences").document("settings")
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "memoryEnabled" to memoryEnabled,
                "voiceAutoSpeak" to voiceAutoSpeak,
                "preferredLanguage" to preferredLanguage.trim().ifBlank { "Auto" }.take(30),
                "preferredModel" to preferredModel.trim().ifBlank { "gemini-3.8-flash" }.take(60),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }
}
