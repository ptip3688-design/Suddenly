package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.assistant.PendingPhoneAction
import com.example.assistant.VoiceAssistantManager
import com.example.data.model.ChatMessage
import com.example.ui.components.JarvisOrb
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CardHudSurface
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.DeepHudSurface
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NeuralViolet
import com.example.ui.theme.ObsidianSpace
import com.example.ui.theme.OutlineGlow
import com.example.ui.theme.SapphireBlue
import com.google.android.gms.location.LocationServices

@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val voiceManager = remember(context) { VoiceAssistantManager(context) }
    DisposableEffect(voiceManager) {
        onDispose { voiceManager.release() }
    }

    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val activeConversationId by viewModel.activeConversationId.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val webSearchEnabled by viewModel.webSearchEnabled.collectAsStateWithLifecycle()
    val streamState by viewModel.streamState.collectAsStateWithLifecycle()
    val pendingAttachment by viewModel.pendingAttachment.collectAsStateWithLifecycle()
    val pendingPhoneAction by viewModel.pendingPhoneAction.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    val isListening by voiceManager.isListening.collectAsStateWithLifecycle()
    val isSpeaking by voiceManager.isSpeaking.collectAsStateWithLifecycle()
    val partialTranscript by voiceManager.partialTranscript.collectAsStateWithLifecycle()
    val voiceError by voiceManager.voiceError.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var showAttachMenu by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showVoiceSheet by remember { mutableStateOf(false) }
    var wakeWordEnabled by remember { mutableStateOf(false) }
    var voiceLanguageTag by remember { mutableStateOf("en-US") }
    var previewHtmlCode by remember { mutableStateOf<String?>(null) }
    var showWeatherDialog by remember { mutableStateOf(false) }
    var weatherCityInput by remember { mutableStateOf("Karachi") }
    var showModelMenu by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.attachImageUri(context, it) } }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.attachDocumentUri(context, it) } }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showVoiceSheet = true
            voiceManager.startListening(voiceLanguageTag, wakeWordEnabled) { spoken ->
                viewModel.sendMessage(
                    rawPrompt = spoken,
                    voiceManager = voiceManager,
                    speakResponse = preferences.voiceAutoSpeak
                )
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            try {
                val fused = LocationServices.getFusedLocationProviderClient(context)
                fused.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) {
                        viewModel.fetchWeatherByCoordsIntoChat(
                            loc.latitude,
                            loc.longitude,
                            voiceManager,
                            preferences.voiceAutoSpeak
                        )
                    } else {
                        showWeatherDialog = true
                    }
                }.addOnFailureListener {
                    showWeatherDialog = true
                }
            } catch (_: SecurityException) {
                showWeatherDialog = true
            }
        } else {
            showWeatherDialog = true
        }
    }

    fun launchVoiceInput() {
        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            showVoiceSheet = true
            voiceManager.startListening(voiceLanguageTag, wakeWordEnabled) { spoken ->
                viewModel.sendMessage(
                    rawPrompt = spoken,
                    voiceManager = voiceManager,
                    speakResponse = preferences.voiceAutoSpeak
                )
            }
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, streamState) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem((messages.size - 1).coerceAtLeast(0))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianSpace)
    ) {
        // Top HUD Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DeepHudSurface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                JarvisOrb(
                    size = 38.dp,
                    isListening = isListening,
                    isSpeaking = isSpeaking,
                    isThinking = streamState is ChatStreamState.Thinking || streamState is ChatStreamState.Streaming
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "JARVIS AI",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Box {
                        Text(
                            text = "${selectedModel.label} ▾",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan,
                            modifier = Modifier
                                .clickable { showModelMenu = true }
                                .padding(vertical = 2.dp)
                        )
                        DropdownMenu(
                            expanded = showModelMenu,
                            onDismissRequest = { showModelMenu = false }
                        ) {
                            JarvisModelOption.entries.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text("${opt.label} (${opt.modelId})") },
                                    onClick = {
                                        viewModel.selectModel(opt)
                                        showModelMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    color = if (webSearchEnabled) ElectricCyan.copy(alpha = 0.2f) else CardHudSurface,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(
                            1.dp,
                            if (webSearchEnabled) ElectricCyan else OutlineGlow,
                            RoundedCornerShape(50)
                        )
                        .clickable { viewModel.toggleWebSearch() }
                        .testTag("web_search_chip")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Filled.Language,
                            contentDescription = "Toggle Web Research",
                            tint = if (webSearchEnabled) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (webSearchEnabled) "Web ON" else "Web",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (webSearchEnabled) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = { launchVoiceInput() },
                    modifier = Modifier.testTag("voice_link_button")
                ) {
                    Icon(
                        Icons.Filled.RecordVoiceOver,
                        contentDescription = "Voice Conversation Mode",
                        tint = if (isListening || isSpeaking) EmeraldPulse else ElectricCyan
                    )
                }

                IconButton(
                    onClick = { showHistorySheet = true },
                    modifier = Modifier.testTag("history_button")
                ) {
                    Icon(
                        Icons.Filled.History,
                        contentDescription = "Conversation History",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        if (!statusBanner.isNullOrBlank()) {
            Surface(
                color = SapphireBlue.copy(alpha = 0.16f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = statusBanner!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = ElectricCyan,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.clearStatusBanner() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Chat Stream
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty() && streamState is ChatStreamState.Idle) {
                EmptyAssistantHero(
                    onQuickPrompt = { prompt ->
                        inputText = prompt
                        viewModel.sendMessage(prompt, voiceManager, false)
                        inputText = ""
                    },
                    onOpenVoice = { launchVoiceInput() },
                    onWeather = {
                        val hasLoc = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasLoc) {
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        } else {
                            showWeatherDialog = true
                        }
                    }
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(messages, key = { it.id.ifBlank { "${it.role}_${it.text.hashCode()}" } }) { msg ->
                        ChatMessageBubble(
                            message = msg,
                            onSpeak = { voiceManager.speak(msg.text) },
                            onCopy = { copyToClipboard(context, msg.text) },
                            onSaveMemory = { viewModel.saveMemoryFact(msg.text.take(350)) },
                            onPreviewHtml = { html -> previewHtmlCode = html }
                        )
                    }

                    when (val state = streamState) {
                        is ChatStreamState.Thinking -> {
                            item("thinking") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(CardHudSurface)
                                        .border(1.dp, OutlineGlow, RoundedCornerShape(16.dp))
                                        .padding(14.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = ElectricCyan,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Jarvis is processing...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = ElectricCyan
                                    )
                                }
                            }
                        }
                        is ChatStreamState.Streaming -> {
                            item("streaming") {
                                ChatMessageBubble(
                                    message = ChatMessage(role = "model", text = state.partialText),
                                    onSpeak = {},
                                    onCopy = { copyToClipboard(context, state.partialText) },
                                    onSaveMemory = {},
                                    onPreviewHtml = { html -> previewHtmlCode = html }
                                )
                            }
                        }
                        is ChatStreamState.Error -> {
                            item("error") {
                                Surface(
                                    color = CrimsonError.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Error: ${state.message}",
                                        color = CrimsonError,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }
                            }
                        }
                        ChatStreamState.Idle -> Unit
                    }
                }
            }
        }

        // Pending Phone Action Confirmation Card
        pendingPhoneAction?.let { action ->
            Surface(
                color = CardHudSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .border(1.dp, AmberAlert, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Confirm Phone Action: ${action.title}",
                        style = MaterialTheme.typography.titleMedium,
                        color = AmberAlert
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = action.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.dismissPhoneAction() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = { viewModel.executePendingPhoneAction(context) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = ObsidianSpace
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("confirm_phone_action_button")
                        ) {
                            Text("Confirm & Run")
                        }
                    }
                }
            }
        }

        // Quick Phone & Tool Action Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickHudActionChip(Icons.Filled.WbSunny, "Weather") { showWeatherDialog = true }
            QuickHudActionChip(Icons.Filled.VideoLibrary, "YouTube") {
                viewModel.queuePhoneAction(PendingPhoneAction.OpenYouTube)
            }
            QuickHudActionChip(Icons.Filled.Phone, "Dialer") {
                viewModel.queuePhoneAction(PendingPhoneAction.OpenDialer())
            }
            QuickHudActionChip(Icons.Filled.Sms, "SMS") {
                viewModel.queuePhoneAction(PendingPhoneAction.CreateSmsDraft())
            }
            QuickHudActionChip(Icons.Filled.Alarm, "Alarm") {
                viewModel.queuePhoneAction(PendingPhoneAction.SetAlarm(7, 30, "Jarvis Alarm"))
            }
            QuickHudActionChip(Icons.Filled.Code, "HTML App") {
                viewModel.sendMessage("Create a sleek responsive HTML + CSS + JS portfolio landing page inside a single ```html code block.")
            }
        }

        // Attachment Preview Chip
        pendingAttachment?.let { att ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CardHudSurface)
                    .border(1.dp, ElectricCyan, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Filled.Image, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = att.label,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(onClick = { viewModel.clearAttachment() }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove attachment", modifier = Modifier.size(16.dp))
                }
            }
        }

        // Composer Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DeepHudSurface)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                IconButton(
                    onClick = { showAttachMenu = true },
                    modifier = Modifier.testTag("attach_button")
                ) {
                    Icon(
                        Icons.Filled.AttachFile,
                        contentDescription = "Attach image or file",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(expanded = showAttachMenu, onDismissRequest = { showAttachMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Analyze Photo (Photo Picker)") },
                        onClick = {
                            showAttachMenu = false
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Analyze Document / PDF / Code") },
                        onClick = {
                            showAttachMenu = false
                            documentPicker.launch(arrayOf("application/pdf", "text/*", "application/json"))
                        }
                    )
                }
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                    Text(
                        text = "Ask in English, Urdu, Roman Urdu, Hindi...",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                maxLines = 4,
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricCyan,
                    unfocusedBorderColor = OutlineGlow,
                    focusedContainerColor = CardHudSurface,
                    unfocusedContainerColor = CardHudSurface
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input")
            )

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = { launchVoiceInput() },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(CardHudSurface)
                    .testTag("mic_button")
            ) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = "Voice Input",
                    tint = if (isListening) EmeraldPulse else ElectricCyan
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            val isGenerating = streamState is ChatStreamState.Thinking || streamState is ChatStreamState.Streaming
            IconButton(
                onClick = {
                    if (isGenerating) {
                        viewModel.stopGeneration()
                    } else {
                        val toSend = inputText
                        inputText = ""
                        viewModel.sendMessage(toSend, voiceManager, false)
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isGenerating) CrimsonError else ElectricCyan)
                    .testTag("send_button")
            ) {
                Icon(
                    imageVector = if (isGenerating) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send,
                    contentDescription = if (isGenerating) "Stop generating" else "Send message",
                    tint = ObsidianSpace
                )
            }
        }
    }

    if (showVoiceSheet) {
        VoiceConversationSheet(
            isListening = isListening,
            isSpeaking = isSpeaking,
            isThinking = streamState is ChatStreamState.Thinking || streamState is ChatStreamState.Streaming,
            transcript = partialTranscript,
            voiceError = voiceError,
            wakeWordEnabled = wakeWordEnabled,
            selectedLangLabel = voiceLanguageTag,
            onToggleWakeWord = { wakeWordEnabled = it },
            onSelectLanguage = { voiceLanguageTag = it },
            onMicTap = {
                if (isListening) voiceManager.stopListening()
                else voiceManager.startListening(voiceLanguageTag, wakeWordEnabled) { spoken ->
                    viewModel.sendMessage(spoken, voiceManager, preferences.voiceAutoSpeak)
                }
            },
            onInterruptTap = {
                voiceManager.stopSpeaking()
                voiceManager.startListening(voiceLanguageTag, wakeWordEnabled) { spoken ->
                    viewModel.sendMessage(spoken, voiceManager, preferences.voiceAutoSpeak)
                }
            },
            onDismiss = {
                voiceManager.stopListening()
                showVoiceSheet = false
            }
        )
    }

    if (showHistorySheet) {
        ConversationHistorySheet(
            conversations = conversations,
            activeConversationId = activeConversationId,
            onSelectConversation = { viewModel.selectConversation(it) },
            onNewChat = { viewModel.startNewConversation() },
            onDeleteConversation = { viewModel.deleteConversation(it) },
            onDismiss = { showHistorySheet = false }
        )
    }

    previewHtmlCode?.let { html ->
        HtmlPreviewDialog(htmlCode = html, onDismiss = { previewHtmlCode = null })
    }

    if (showWeatherDialog) {
        AlertDialog(
            onDismissRequest = { showWeatherDialog = false },
            containerColor = DeepHudSurface,
            title = { Text("Live Weather Lookup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = weatherCityInput,
                        onValueChange = { weatherCityInput = it },
                        label = { Text("City Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(
                        onClick = {
                            showWeatherDialog = false
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    ) {
                        Text("Or use device approximate location", color = ElectricCyan)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWeatherDialog = false
                        if (weatherCityInput.isNotBlank()) {
                            viewModel.fetchWeatherIntoChat(weatherCityInput, voiceManager, preferences.voiceAutoSpeak)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianSpace)
                ) {
                    Text("Check Weather")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWeatherDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun EmptyAssistantHero(
    onQuickPrompt: (String) -> Unit,
    onOpenVoice: () -> Unit,
    onWeather: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        JarvisOrb(size = 110.dp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Systems Online. How can I help?",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Speak or type in English, Urdu, Roman Urdu, or Hindi.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.widthIn(max = 460.dp)
        ) {
            SuggestionPromptCard("Aaj ka plan Roman Urdu mein bana do") {
                onQuickPrompt("Aaj ka productive schedule Roman Urdu mein bana do.")
            }
            SuggestionPromptCard("Explain Quantum Computing simply + study quiz") {
                onQuickPrompt("Explain quantum computing simply and give me 3 quick practice questions.")
            }
            SuggestionPromptCard("Write a Python FastAPI starter & debug tips") {
                onQuickPrompt("Write a clean Python FastAPI starter server with health check and error handling.")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenVoice, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Voice Mode")
                }
                OutlinedButton(onClick = onWeather, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.WbSunny, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Live Weather")
                }
            }
        }
    }
}

@Composable
private fun SuggestionPromptCard(text: String, onClick: () -> Unit) {
    Surface(
        color = CardHudSurface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, OutlineGlow, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun QuickHudActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        color = CardHudSurface,
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, OutlineGlow, RoundedCornerShape(50))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessage,
    onSpeak: () -> Unit,
    onCopy: () -> Unit,
    onSaveMemory: () -> Unit,
    onPreviewHtml: (String) -> Unit
) {
    val isUser = message.role == "user"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isUser) SapphireBlue.copy(alpha = 0.24f) else CardHudSurface,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            modifier = Modifier
                .widthIn(max = 560.dp)
                .border(
                    width = 1.dp,
                    color = if (isUser) SapphireBlue.copy(alpha = 0.6f) else OutlineGlow,
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isUser) 18.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 18.dp
                    )
                )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (!message.attachmentLabel.isNullOrBlank()) {
                    Text(
                        text = "Attachment: ${message.attachmentLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                MessageFormattedBody(
                    text = message.text,
                    onCopyCode = onCopy,
                    onPreviewHtml = onPreviewHtml
                )

                if (!isUser) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onSpeak, modifier = Modifier.size(34.dp)) {
                            Icon(
                                Icons.Filled.VolumeUp,
                                contentDescription = "Read aloud",
                                tint = ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = onCopy, modifier = Modifier.size(34.dp)) {
                            Icon(
                                Icons.Filled.ContentCopy,
                                contentDescription = "Copy response",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        IconButton(onClick = onSaveMemory, modifier = Modifier.size(34.dp)) {
                            Icon(
                                Icons.Filled.BookmarkAdd,
                                contentDescription = "Remember this",
                                tint = NeuralViolet,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageFormattedBody(
    text: String,
    onCopyCode: () -> Unit,
    onPreviewHtml: (String) -> Unit
) {
    val context = LocalContext.current
    val parts = remember(text) { splitMarkdownCodeBlocks(text) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parts.forEach { segment ->
            if (segment.isCode) {
                Surface(
                    color = ObsidianSpace,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, OutlineGlow, RoundedCornerShape(10.dp))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = segment.lang.uppercase().ifBlank { "CODE" },
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (segment.lang.equals("html", ignoreCase = true) ||
                                    segment.content.contains("<html", ignoreCase = true)
                                ) {
                                    Text(
                                        text = "Preview",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldPulse,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clickable { onPreviewHtml(segment.content) }
                                            .padding(4.dp)
                                    )
                                }
                                Text(
                                    text = "Copy",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SapphireBlue,
                                    modifier = Modifier
                                        .clickable { copyToClipboard(context, segment.content) }
                                        .padding(4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = segment.content,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }
            } else if (segment.content.isNotBlank()) {
                Text(
                    text = segment.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private data class MessageSegment(val isCode: Boolean, val lang: String, val content: String)

private fun splitMarkdownCodeBlocks(raw: String): List<MessageSegment> {
    val regex = Regex("```([a-zA-Z0-9_-]*)\\n([\\s\\S]*?)```")
    val segments = mutableListOf<MessageSegment>()
    var cursor = 0
    for (match in regex.findAll(raw)) {
        if (match.range.first > cursor) {
            segments.add(MessageSegment(false, "", raw.substring(cursor, match.range.first).trim()))
        }
        segments.add(
            MessageSegment(
                isCode = true,
                lang = match.groupValues[1],
                content = match.groupValues[2].trimEnd()
            )
        )
        cursor = match.range.last + 1
    }
    if (cursor < raw.length) {
        segments.add(MessageSegment(false, "", raw.substring(cursor).trim()))
    }
    if (segments.isEmpty()) {
        segments.add(MessageSegment(false, "", raw))
    }
    return segments
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Jarvis AI", text))
}
