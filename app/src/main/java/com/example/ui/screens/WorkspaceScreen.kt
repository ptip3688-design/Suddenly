package com.example.ui.screens

import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.TaskPriority
import com.example.firebase.signOutUser
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CardHudSurface
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.DeepHudSurface
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.NeuralViolet
import com.example.ui.theme.ObsidianSpace
import com.example.ui.theme.OutlineGlow
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@Composable
fun WorkspaceScreen(
    viewModel: WorkspaceViewModel,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val tasksState by viewModel.tasksState.collectAsStateWithLifecycle()
    val notesState by viewModel.notesState.collectAsStateWithLifecycle()
    val schedulesState by viewModel.schedulesState.collectAsStateWithLifecycle()
    val memoriesState by viewModel.memoriesState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val feedback by viewModel.feedbackMessage.collectAsStateWithLifecycle()
    val isSummarizing by viewModel.isSummarizingNote.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Tasks", "Schedule", "Notes", "Memory")

    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianSpace)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DeepHudSurface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("PERSONAL WORKSPACE", style = MaterialTheme.typography.headlineSmall, color = ElectricCyan)
                Text(
                    text = Firebase.auth.currentUser?.email ?: "Synced Account",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = {
                    signOutUser(credentialManager, scope, onSignOutComplete = onSignedOut)
                },
                modifier = Modifier.testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Sign Out")
            }
        }

        if (!feedback.isNullOrBlank()) {
            Surface(color = EmeraldPulse.copy(alpha = 0.14f), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(feedback!!, color = EmeraldPulse, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.clearFeedback() }) { Text("OK") }
                }
            }
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = DeepHudSurface,
            contentColor = ElectricCyan
        ) {
            tabs.forEachIndexed { idx, title ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = { Text(title, style = MaterialTheme.typography.labelLarge) }
                )
            }
        }

        BoxActionHeader(
            tabTitle = tabs[selectedTab],
            onAddClick = { showAddDialog = true }
        )

        when (selectedTab) {
            0 -> { // Tasks
                when (val st = tasksState) {
                    is WorkspaceUiState.Loading -> LoadingBox()
                    is WorkspaceUiState.Error -> ErrorBox(st.message)
                    is WorkspaceUiState.Success -> {
                        if (st.data.isEmpty()) {
                            EmptyBox("No tasks yet. Tap + Add to create your to-do list.")
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(st.data, key = { it.id }) { task ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(CardHudSurface)
                                            .border(1.dp, OutlineGlow, RoundedCornerShape(14.dp))
                                            .padding(10.dp)
                                    ) {
                                        Checkbox(
                                            checked = task.done,
                                            onCheckedChange = { viewModel.toggleTask(task) }
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = task.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                textDecoration = if (task.done) TextDecoration.LineThrough else null
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(
                                                    text = task.priority,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = when (task.priority) {
                                                        "HIGH" -> CrimsonError
                                                        "LOW" -> EmeraldPulse
                                                        else -> AmberAlert
                                                    }
                                                )
                                                if (!task.dueLabel.isNullOrBlank()) {
                                                    Text(
                                                        text = "• ${task.dueLabel}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                        IconButton(onClick = {
                                            val alarmIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                                                putExtra(AlarmClock.EXTRA_MESSAGE, task.title)
                                                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                                            }
                                            try { context.startActivity(alarmIntent) } catch (_: Exception) {}
                                        }) {
                                            Icon(Icons.Filled.Alarm, contentDescription = "Set alarm", tint = ElectricCyan)
                                        }
                                        IconButton(onClick = { viewModel.deleteTask(task.id) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete task")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> { // Schedule
                when (val st = schedulesState) {
                    is WorkspaceUiState.Loading -> LoadingBox()
                    is WorkspaceUiState.Error -> ErrorBox(st.message)
                    is WorkspaceUiState.Success -> {
                        if (st.data.isEmpty()) {
                            EmptyBox("No schedule blocks yet. Tap + Add to plan your day.")
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(st.data, key = { it.id }) { item ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(CardHudSurface)
                                            .border(1.dp, OutlineGlow, RoundedCornerShape(14.dp))
                                            .padding(14.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.timeSlot, style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                                            Text("${item.dateLabel} • ${item.category}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        IconButton(onClick = { viewModel.deleteSchedule(item.id) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete schedule item")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> { // Notes
                when (val st = notesState) {
                    is WorkspaceUiState.Loading -> LoadingBox()
                    is WorkspaceUiState.Error -> ErrorBox(st.message)
                    is WorkspaceUiState.Success -> {
                        if (st.data.isEmpty()) {
                            EmptyBox("No notes saved yet. Tap + Add to create study or work notes.")
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(st.data, key = { it.id }) { note ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(CardHudSurface)
                                            .border(1.dp, OutlineGlow, RoundedCornerShape(14.dp))
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(note.tag.uppercase(), style = MaterialTheme.typography.labelSmall, color = NeuralViolet)
                                                Text(note.title, style = MaterialTheme.typography.titleMedium)
                                            }
                                            Row {
                                                IconButton(
                                                    onClick = { viewModel.summarizeNoteWithAi(note) },
                                                    enabled = !isSummarizing
                                                ) {
                                                    Icon(Icons.Filled.AutoAwesome, contentDescription = "AI Summarize", tint = ElectricCyan)
                                                }
                                                IconButton(onClick = { viewModel.deleteNote(note.id) }) {
                                                    Icon(Icons.Filled.Delete, contentDescription = "Delete note")
                                                }
                                            }
                                        }
                                        Text(note.content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            3 -> { // Memory & Preferences
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item("prefs") {
                        Surface(
                            color = DeepHudSurface,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, OutlineGlow, RoundedCornerShape(16.dp))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Assistant Preferences", style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Enable Long-Term Memory", style = MaterialTheme.typography.bodyLarge)
                                        Text("Allow Jarvis to remember active preferences across chats", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = preferences.memoryEnabled,
                                        onCheckedChange = { viewModel.updatePreferences(memoryEnabled = it) }
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Voice Auto-Speak Replies", style = MaterialTheme.typography.bodyLarge)
                                        Text("Read Gemini answers aloud in Voice Conversation Mode", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = preferences.voiceAutoSpeak,
                                        onCheckedChange = { viewModel.updatePreferences(voiceAutoSpeak = it) }
                                    )
                                }
                                Text("Default Language Style", style = MaterialTheme.typography.labelLarge)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("Auto", "English", "Urdu", "Roman Urdu", "Hindi").forEach { lang ->
                                        val selected = preferences.preferredLanguage == lang
                                        Surface(
                                            color = if (selected) ElectricCyan.copy(alpha = 0.2f) else CardHudSurface,
                                            shape = RoundedCornerShape(50),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .border(1.dp, if (selected) ElectricCyan else OutlineGlow, RoundedCornerShape(50))
                                                .clickable { viewModel.updatePreferences(preferredLanguage = lang) }
                                        ) {
                                            Text(
                                                text = lang,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (selected) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    when (val st = memoriesState) {
                        is WorkspaceUiState.Loading -> item("mem_loading") { LoadingBox() }
                        is WorkspaceUiState.Error -> item("mem_error") { ErrorBox(st.message) }
                        is WorkspaceUiState.Success -> {
                            if (st.data.isEmpty()) {
                                item("mem_empty") {
                                    EmptyBox("No saved memories yet. Tap + Add to store a preference for Jarvis.")
                                }
                            } else {
                                items(st.data, key = { it.id }) { mem ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(CardHudSurface)
                                            .border(1.dp, OutlineGlow, RoundedCornerShape(14.dp))
                                            .padding(12.dp)
                                    ) {
                                        Switch(
                                            checked = mem.active,
                                            onCheckedChange = { viewModel.toggleMemory(mem) }
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(mem.fact, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                        IconButton(onClick = { viewModel.deleteMemory(mem.id) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete memory")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        WorkspaceAddDialog(
            tabIndex = selectedTab,
            onDismiss = { showAddDialog = false },
            onAddTask = { title, due, priority ->
                viewModel.addTask(title, due, priority)
                showAddDialog = false
            },
            onAddSchedule = { title, timeSlot, dateLabel, cat ->
                viewModel.addSchedule(title, timeSlot, dateLabel, cat)
                showAddDialog = false
            },
            onAddNote = { title, content, tag ->
                viewModel.addNote(title, content, tag)
                showAddDialog = false
            },
            onAddMemory = { fact ->
                viewModel.addMemory(fact)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun BoxActionHeader(tabTitle: String, onAddClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(tabTitle, style = MaterialTheme.typography.titleLarge)
        Button(
            onClick = onAddClick,
            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianSpace),
            modifier = Modifier.testTag("workspace_add_button")
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add")
        }
    }
}

@Composable
private fun LoadingBox() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = ElectricCyan)
    }
}

@Composable
private fun ErrorBox(message: String) {
    Text(
        text = "Error: $message",
        color = CrimsonError,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(16.dp)
    )
}

@Composable
private fun EmptyBox(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(24.dp)
    )
}

@Composable
private fun WorkspaceAddDialog(
    tabIndex: Int,
    onDismiss: () -> Unit,
    onAddTask: (String, String, TaskPriority) -> Unit,
    onAddSchedule: (String, String, String, String) -> Unit,
    onAddNote: (String, String, String) -> Unit,
    onAddMemory: (String) -> Unit
) {
    var field1 by remember { mutableStateOf("") }
    var field2 by remember { mutableStateOf("") }
    var field3 by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(TaskPriority.MEDIUM) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeepHudSurface,
        title = {
            Text(
                when (tabIndex) {
                    0 -> "New Task"
                    1 -> "New Schedule Block"
                    2 -> "New Note"
                    else -> "New Jarvis Memory"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = field1,
                    onValueChange = { field1 = it },
                    label = {
                        Text(
                            when (tabIndex) {
                                0 -> "Task title"
                                1 -> "Event title"
                                2 -> "Note title"
                                else -> "Preference or fact to remember"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (tabIndex == 0) {
                    OutlinedTextField(
                        value = field2,
                        onValueChange = { field2 = it },
                        label = { Text("Due time/date (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TaskPriority.entries.forEach { p ->
                            OutlinedButton(
                                onClick = { priority = p },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(p.name, color = if (priority == p) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else if (tabIndex == 1) {
                    OutlinedTextField(
                        value = field2,
                        onValueChange = { field2 = it },
                        label = { Text("Time slot (e.g. 09:00 AM - 10:30 AM)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = field3,
                        onValueChange = { field3 = it },
                        label = { Text("Category (e.g. Study, Work)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (tabIndex == 2) {
                    OutlinedTextField(
                        value = field2,
                        onValueChange = { field2 = it },
                        label = { Text("Content") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = field3,
                        onValueChange = { field3 = it },
                        label = { Text("Tag (e.g. Study, Coding)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (field1.isBlank()) return@Button
                    when (tabIndex) {
                        0 -> onAddTask(field1, field2, priority)
                        1 -> onAddSchedule(field1, field2.ifBlank { "09:00 AM" }, "Today", field3.ifBlank { "Focus" })
                        2 -> onAddNote(field1, field2, field3.ifBlank { "General" })
                        else -> onAddMemory(field1)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianSpace)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
