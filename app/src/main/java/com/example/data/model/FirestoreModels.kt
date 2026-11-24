package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class TaskPriority {
    LOW,
    MEDIUM,
    HIGH;

    companion object {
        fun fromRaw(raw: String?): TaskPriority =
            entries.firstOrNull { it.name == raw } ?: MEDIUM
    }
}

@Keep
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "New Conversation",
    val modelName: String = "gemini-3.8-flash",
    val lastMessagePreview: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = "user",
    val text: String = "",
    val attachmentLabel: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class TaskItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val notes: String? = null,
    val dueLabel: String? = null,
    val priority: String = TaskPriority.MEDIUM.name,
    val done: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class NoteItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val content: String = "",
    val tag: String = "General",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ScheduleItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val timeSlot: String = "",
    val dateLabel: String = "Today",
    val category: String = "Focus",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MemoryFact(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fact: String = "",
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserPreferences(
    @DocumentId val id: String = "settings",
    val userId: String = "",
    val memoryEnabled: Boolean = true,
    val voiceAutoSpeak: Boolean = true,
    val preferredLanguage: String = "Auto",
    val preferredModel: String = "gemini-3.8-flash",
    @ServerTimestamp val updatedAt: Timestamp? = null
)
