package com.example.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Conversation
import com.example.ui.components.JarvisOrb
import com.example.ui.theme.CardHudSurface
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.DeepHudSurface
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianSpace
import com.example.ui.theme.OutlineGlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceConversationSheet(
    isListening: Boolean,
    isSpeaking: Boolean,
    isThinking: Boolean,
    transcript: String,
    voiceError: String?,
    wakeWordEnabled: Boolean,
    selectedLangLabel: String,
    onToggleWakeWord: (Boolean) -> Unit,
    onSelectLanguage: (String) -> Unit,
    onMicTap: () -> Unit,
    onInterruptTap: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DeepHudSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "JARVIS VOICE LINK",
                    style = MaterialTheme.typography.titleLarge,
                    color = ElectricCyan
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close voice mode")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            JarvisOrb(
                size = 150.dp,
                isListening = isListening,
                isSpeaking = isSpeaking,
                isThinking = isThinking
            )
            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = when {
                    isListening -> if (wakeWordEnabled) "Listening (Say 'Hey Jarvis' or speak)..." else "Listening..."
                    isThinking -> "Jarvis is thinking..."
                    isSpeaking -> "Speaking reply — tap Interrupt to speak"
                    else -> "Tap microphone to talk naturally"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            if (transcript.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = CardHudSurface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "\"$transcript\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ElectricCyan,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (!voiceError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = voiceError,
                    style = MaterialTheme.typography.bodySmall,
                    color = CrimsonError,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Language selector pills for SpeechRecognizer
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("en-US" to "English", "ur-PK" to "Urdu", "hi-IN" to "Hindi").forEach { (tag, label) ->
                    val selected = selectedLangLabel == tag
                    Surface(
                        color = if (selected) ElectricCyan.copy(alpha = 0.2f) else CardHudSurface,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .border(1.dp, if (selected) ElectricCyan else OutlineGlow, RoundedCornerShape(50))
                            .clickable { onSelectLanguage(tag) }
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardHudSurface)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "\"Hey Jarvis\" Wake Mode",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Hands-free prefix stripping while Voice Link is open",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = wakeWordEnabled, onCheckedChange = onToggleWakeWord)
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isSpeaking) {
                    OutlinedButton(
                        onClick = onInterruptTap,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("voice_interrupt_button")
                    ) {
                        Icon(Icons.Filled.VolumeOff, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Interrupt")
                    }
                }
                Button(
                    onClick = onMicTap,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isListening) CrimsonError else ElectricCyan,
                        contentColor = ObsidianSpace
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("voice_modal_mic_button")
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isListening) "Stop" else "Speak Now")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationHistorySheet(
    conversations: List<Conversation>,
    activeConversationId: String?,
    onSelectConversation: (String) -> Unit,
    onNewChat: () -> Unit,
    onDeleteConversation: (String) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DeepHudSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Conversation History",
                    style = MaterialTheme.typography.titleLarge,
                    color = ElectricCyan
                )
                Button(
                    onClick = {
                        onNewChat()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = ObsidianSpace
                    )
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Chat")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (conversations.isEmpty()) {
                Text(
                    text = "No saved conversations yet. Send a message to start one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(340.dp)
                ) {
                    items(conversations, key = { it.id }) { conv ->
                        val isActive = conv.id == activeConversationId
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isActive) ElectricCyan.copy(alpha = 0.14f) else CardHudSurface)
                                .border(
                                    width = 1.dp,
                                    color = if (isActive) ElectricCyan else OutlineGlow,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    onSelectConversation(conv.id)
                                    onDismiss()
                                }
                                .padding(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = conv.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (conv.lastMessagePreview.isNotBlank()) {
                                    Text(
                                        text = conv.lastMessagePreview,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(onClick = { onDeleteConversation(conv.id) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Delete conversation",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlPreviewDialog(
    htmlCode: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = ObsidianSpace,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DeepHudSurface)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Live Prototype Preview",
                        style = MaterialTheme.typography.titleLarge,
                        color = ElectricCyan
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close preview")
                    }
                }
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            loadDataWithBaseURL(null, htmlCode, "text/html", "UTF-8", null)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
