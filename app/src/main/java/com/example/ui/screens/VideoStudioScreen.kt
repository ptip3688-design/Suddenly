package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.ui.theme.CardHudSurface
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.DeepHudSurface
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NeuralViolet
import com.example.ui.theme.ObsidianSpace
import com.example.ui.theme.OutlineGlow

@OptIn(UnstableApi::class)
@Composable
fun VideoStudioScreen(
    modifier: Modifier = Modifier,
    viewModel: VideoStudioViewModel = viewModel()
) {
    val context = LocalContext.current
    val clips by viewModel.clips.collectAsStateWithLifecycle()
    val trimStartMs by viewModel.trimStartMs.collectAsStateWithLifecycle()
    val trimEndMs by viewModel.trimEndMs.collectAsStateWithLifecycle()
    val rotationDegrees by viewModel.rotationDegrees.collectAsStateWithLifecycle()
    val muteAudio by viewModel.muteAudio.collectAsStateWithLifecycle()
    val aspectPreset by viewModel.aspectPreset.collectAsStateWithLifecycle()
    val resolutionPreset by viewModel.resolutionPreset.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val captionState by viewModel.captionState.collectAsStateWithLifecycle()

    var captionTopicHint by remember { mutableStateOf("") }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
    ) { uris ->
        viewModel.addSelectedVideos(context, uris)
    }

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build()
    }
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    LaunchedEffect(clips) {
        if (clips.isNotEmpty()) {
            exoPlayer.setMediaItems(clips.map { MediaItem.fromUri(it.uri) })
            exoPlayer.prepare()
        } else {
            exoPlayer.clearMediaItems()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianSpace)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "AI VIDEO STUDIO",
            style = MaterialTheme.typography.headlineMedium,
            color = ElectricCyan
        )
        Text(
            text = "Trim, crop, rotate, merge clips with Media3 Transformer, and generate AI SRT captions.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = {
                videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            },
            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianSpace),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("pick_video_button")
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (clips.isEmpty()) "Select Video(s)" else "Add More Clips to Merge")
        }

        // Player Preview
        if (clips.isNotEmpty()) {
            Surface(
                color = DeepHudSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, OutlineGlow, RoundedCornerShape(16.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(16.dp))
                )
            }

            // Timeline Clips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Timeline (${clips.size} clip${if (clips.size > 1) "s" else ""})", style = MaterialTheme.typography.labelLarge)
                clips.forEachIndexed { idx, clip ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CardHudSurface)
                            .border(1.dp, OutlineGlow, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Filled.VideoFile, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(clip.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${clip.durationMs / 1000}s", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { viewModel.removeClip(idx) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove clip")
                        }
                    }
                }
            }

            // Trim first clip
            val firstDuration = clips.first().durationMs.coerceAtLeast(1000L).toFloat()
            Surface(
                color = DeepHudSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, OutlineGlow, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Trim Primary Clip (${trimStartMs / 1000}s – ${trimEndMs / 1000}s)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    RangeSlider(
                        value = trimStartMs.toFloat().coerceIn(0f, firstDuration)..trimEndMs.toFloat().coerceIn(0f, firstDuration),
                        onValueChange = { range ->
                            viewModel.updateTrimRange(range.start.toLong(), range.endInclusive.toLong())
                        },
                        valueRange = 0f..firstDuration
                    )

                    Text("Crop / Aspect Ratio", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VideoAspectPreset.entries.forEach { preset ->
                            VideoChip(
                                label = preset.label,
                                selected = aspectPreset == preset,
                                onClick = { viewModel.setAspectPreset(preset) }
                            )
                        }
                    }

                    Text("Export Resolution", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VideoResolutionPreset.entries.forEach { res ->
                            VideoChip(
                                label = res.label,
                                selected = resolutionPreset == res,
                                onClick = { viewModel.setResolutionPreset(res) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = { viewModel.rotate90() }) {
                            Icon(Icons.Filled.RotateRight, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Rotate (${rotationDegrees.toInt()}°)")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Mute Audio", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.width(6.dp))
                            Switch(checked = muteAudio, onCheckedChange = { viewModel.setMuteAudio(it) })
                        }
                    }

                    when (val state = exportState) {
                        is VideoExportState.Exporting -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Exporting MP4 (${state.progressPercent}%)...", color = ElectricCyan)
                                LinearProgressIndicator(
                                    progress = { (state.progressPercent / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = ElectricCyan
                                )
                                OutlinedButton(onClick = { viewModel.cancelExport() }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Cancel Export")
                                }
                            }
                        }
                        is VideoExportState.Completed -> {
                            Surface(color = EmeraldPulse.copy(alpha = 0.14f), shape = RoundedCornerShape(12.dp)) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(state.savedMessage, color = EmeraldPulse, style = MaterialTheme.typography.bodySmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { viewModel.exportEditedVideo(context) },
                                            modifier = Modifier.weight(1f)
                                        ) { Text("Re-Export") }
                                        OutlinedButton(
                                            onClick = { viewModel.shareExportedFile(context, state.outputPath, "video/mp4") },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Share MP4")
                                        }
                                    }
                                }
                            }
                        }
                        is VideoExportState.Error -> {
                            Text("Export Error: ${state.message}", color = CrimsonError, style = MaterialTheme.typography.bodySmall)
                            Button(
                                onClick = { viewModel.exportEditedVideo(context) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Retry Export") }
                        }
                        VideoExportState.Idle -> {
                            Button(
                                onClick = { viewModel.exportEditedVideo(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPulse, contentColor = ObsidianSpace),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("export_video_button")
                            ) {
                                Icon(Icons.Filled.Movie, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Export Edited MP4")
                            }
                        }
                    }
                }
            }

            // AI Captions & Subtitles Card
            Surface(
                color = DeepHudSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, OutlineGlow, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("AI Captions & Subtitles (SRT)", style = MaterialTheme.typography.titleMedium, color = NeuralViolet)
                    OutlinedTextField(
                        value = captionTopicHint,
                        onValueChange = { captionTopicHint = it },
                        label = { Text("Optional topic or language (e.g. Urdu / English Reels)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeuralViolet,
                            unfocusedBorderColor = OutlineGlow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { viewModel.generateAiCaptions(context, captionTopicHint) },
                        enabled = captionState !is CaptionGenState.Loading,
                        colors = ButtonDefaults.buttonColors(containerColor = NeuralViolet),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("generate_captions_button")
                    ) {
                        Icon(Icons.Filled.ClosedCaption, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate SRT Subtitles & Social Copy")
                    }

                    when (val cState = captionState) {
                        is CaptionGenState.Loading -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = NeuralViolet, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Analyzing video & generating subtitles...", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        is CaptionGenState.Error -> {
                            Text("Error: ${cState.message}", color = CrimsonError, style = MaterialTheme.typography.bodySmall)
                        }
                        is CaptionGenState.Success -> {
                            Surface(color = ObsidianSpace, shape = RoundedCornerShape(12.dp)) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Generated Subtitles", style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
                                        IconButton(onClick = {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("SRT Captions", cState.srtAndSocialCopy))
                                        }) {
                                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy captions")
                                        }
                                    }
                                    Text(
                                        text = cState.srtAndSocialCopy,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily)
                                    )
                                }
                            }
                        }
                        CaptionGenState.Idle -> Unit
                    }
                }
            }
        } else {
            Surface(
                color = DeepHudSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, OutlineGlow, RoundedCornerShape(16.dp))
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = "Pick one or more videos from your device to trim, crop, rotate, merge, and generate AI captions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) ElectricCyan.copy(alpha = 0.2f) else CardHudSurface,
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, if (selected) ElectricCyan else OutlineGlow, RoundedCornerShape(50))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}
