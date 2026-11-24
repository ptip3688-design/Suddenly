package com.example.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

private const val TAG = "VideoStudioVM"

data class VideoClipItem(
    val uri: Uri,
    val name: String,
    val durationMs: Long
)

enum class VideoAspectPreset(val label: String, val ratio: Float?) {
    ORIGINAL("Original", null),
    LANDSCAPE_16_9("16:9", 16f / 9f),
    PORTRAIT_9_16("9:16 Reels", 9f / 16f),
    SQUARE_1_1("1:1", 1f)
}

enum class VideoResolutionPreset(val label: String, val heightPx: Int) {
    RES_480P("480p", 480),
    RES_720P("720p HD", 720),
    RES_1080P("1080p FHD", 1080)
}

sealed interface VideoExportState {
    data object Idle : VideoExportState
    data class Exporting(val progressPercent: Int) : VideoExportState
    data class Completed(val outputPath: String, val savedMessage: String) : VideoExportState
    data class Error(val message: String) : VideoExportState
}

sealed interface CaptionGenState {
    data object Idle : CaptionGenState
    data object Loading : CaptionGenState
    data class Success(val srtAndSocialCopy: String) : CaptionGenState
    data class Error(val message: String) : CaptionGenState
}

@UnstableApi
class VideoStudioViewModel : ViewModel() {

    private val captionModel by lazy { Firebase.ai.generativeModel("gemini-3.8-flash") }

    private val _clips = MutableStateFlow<List<VideoClipItem>>(emptyList())
    val clips: StateFlow<List<VideoClipItem>> = _clips.asStateFlow()

    private val _trimStartMs = MutableStateFlow(0L)
    val trimStartMs: StateFlow<Long> = _trimStartMs.asStateFlow()

    private val _trimEndMs = MutableStateFlow(0L)
    val trimEndMs: StateFlow<Long> = _trimEndMs.asStateFlow()

    private val _rotationDegrees = MutableStateFlow(0f)
    val rotationDegrees: StateFlow<Float> = _rotationDegrees.asStateFlow()

    private val _muteAudio = MutableStateFlow(false)
    val muteAudio: StateFlow<Boolean> = _muteAudio.asStateFlow()

    private val _aspectPreset = MutableStateFlow(VideoAspectPreset.ORIGINAL)
    val aspectPreset: StateFlow<VideoAspectPreset> = _aspectPreset.asStateFlow()

    private val _resolutionPreset = MutableStateFlow(VideoResolutionPreset.RES_720P)
    val resolutionPreset: StateFlow<VideoResolutionPreset> = _resolutionPreset.asStateFlow()

    private val _exportState = MutableStateFlow<VideoExportState>(VideoExportState.Idle)
    val exportState: StateFlow<VideoExportState> = _exportState.asStateFlow()

    private val _captionState = MutableStateFlow<CaptionGenState>(CaptionGenState.Idle)
    val captionState: StateFlow<CaptionGenState> = _captionState.asStateFlow()

    private var activeTransformer: Transformer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun addSelectedVideos(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = uris.map { uri ->
                val duration = readDurationMs(context, uri)
                val name = resolveName(context, uri) ?: "Video Clip"
                VideoClipItem(uri = uri, name = name, durationMs = duration)
            }
            val updated = _clips.value + loaded
            _clips.value = updated
            if (updated.size == loaded.size && loaded.isNotEmpty()) {
                _trimStartMs.value = 0L
                _trimEndMs.value = loaded.first().durationMs
            }
        }
    }

    fun removeClip(index: Int) {
        val current = _clips.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _clips.value = current
            if (current.isNotEmpty()) {
                _trimStartMs.value = 0L
                _trimEndMs.value = current.first().durationMs
            } else {
                _trimStartMs.value = 0L
                _trimEndMs.value = 0L
            }
        }
    }

    fun updateTrimRange(startMs: Long, endMs: Long) {
        _trimStartMs.value = startMs.coerceAtLeast(0L)
        _trimEndMs.value = endMs.coerceAtLeast(startMs + 500L)
    }

    fun rotate90() {
        _rotationDegrees.value = (_rotationDegrees.value + 90f) % 360f
    }

    fun setMuteAudio(mute: Boolean) { _muteAudio.value = mute }
    fun setAspectPreset(preset: VideoAspectPreset) { _aspectPreset.value = preset }
    fun setResolutionPreset(preset: VideoResolutionPreset) { _resolutionPreset.value = preset }

    fun exportEditedVideo(context: Context) {
        val currentClips = _clips.value
        if (currentClips.isEmpty()) return

        activeTransformer?.cancel()
        val outFile = File(context.cacheDir, "jarvis_export_${System.currentTimeMillis()}.mp4")
        _exportState.value = VideoExportState.Exporting(0)

        val videoEffects = mutableListOf<Effect>()
        if (_rotationDegrees.value != 0f) {
            videoEffects.add(
                ScaleAndRotateTransformation.Builder()
                    .setRotationDegrees(_rotationDegrees.value)
                    .build()
            )
        }
        val ratio = _aspectPreset.value.ratio
        if (ratio != null) {
            videoEffects.add(
                Presentation.createForAspectRatio(ratio, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)
            )
        }
        videoEffects.add(Presentation.createForHeight(_resolutionPreset.value.heightPx))

        val editedItems = currentClips.mapIndexed { idx, clip ->
            val mediaBuilder = MediaItem.Builder().setUri(clip.uri)
            if (idx == 0 && _trimEndMs.value > _trimStartMs.value && clip.durationMs > 0L) {
                mediaBuilder.setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(_trimStartMs.value)
                        .setEndPositionMs(_trimEndMs.value.coerceAtMost(clip.durationMs))
                        .build()
                )
            }
            EditedMediaItem.Builder(mediaBuilder.build())
                .setRemoveAudio(_muteAudio.value)
                .setEffects(Effects(emptyList(), videoEffects))
                .build()
        }

        val sequence = EditedMediaItemSequence.withAudioAndVideoFrom(editedItems)
        val composition = Composition.Builder(sequence).build()

        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    viewModelScope.launch {
                        val savedNote = persistMp4ToGallery(context, outFile)
                        _exportState.value = VideoExportState.Completed(
                            outputPath = outFile.absolutePath,
                            savedMessage = savedNote
                        )
                    }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exception: ExportException
                ) {
                    Log.e(TAG, "Transformer export failed", exception)
                    _exportState.value = VideoExportState.Error(
                        exception.localizedMessage ?: "Video export failed."
                    )
                }
            })
            .build()

        activeTransformer = transformer
        transformer.start(composition, outFile.absolutePath)

        val progressHolder = ProgressHolder()
        mainHandler.post(object : Runnable {
            override fun run() {
                val state = transformer.getProgress(progressHolder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    _exportState.value = VideoExportState.Exporting(progressHolder.progress)
                }
                if (state != Transformer.PROGRESS_STATE_NOT_STARTED &&
                    _exportState.value is VideoExportState.Exporting
                ) {
                    mainHandler.postDelayed(this, 400L)
                }
            }
        })
    }

    fun cancelExport() {
        activeTransformer?.cancel()
        activeTransformer = null
        _exportState.value = VideoExportState.Idle
    }

    fun generateAiCaptions(context: Context, topicHint: String) {
        val firstClip = _clips.value.firstOrNull()
        viewModelScope.launch {
            _captionState.value = CaptionGenState.Loading
            try {
                val clipBytes = withContext(Dispatchers.IO) {
                    firstClip?.let {
                        context.contentResolver.openInputStream(it.uri)?.use { stream ->
                            val bytes = stream.readBytes()
                            if (bytes.size <= 18 * 1024 * 1024) bytes else null
                        }
                    }
                }

                val prompt = buildString {
                    append("Generate accurate timestamped SRT subtitles (00:00:00,000 --> 00:00:03,000 format) ")
                    append("plus a short punchy social media caption and 5 hashtags for this video. ")
                    if (topicHint.isNotBlank()) append("Context/topic: $topicHint. ")
                    if (clipBytes == null && firstClip != null) {
                        append("Video filename: ${firstClip.name}, duration: ${firstClip.durationMs / 1000}s. Note: video exceeded 18MB inline upload limit so base captions on the provided context.")
                    }
                }

                val response = withTimeout(60_000L) {
                    captionModel.generateContent(
                        content {
                            if (clipBytes != null) {
                                inlineData(clipBytes, "video/mp4")
                            }
                            text(prompt)
                        }
                    )
                }
                val text = response.text
                if (!text.isNullOrBlank()) {
                    _captionState.value = CaptionGenState.Success(text)
                } else {
                    _captionState.value = CaptionGenState.Error("Gemini returned empty caption text.")
                }
            } catch (e: TimeoutCancellationException) {
                _captionState.value = CaptionGenState.Error("Caption generation timed out after 60s.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Caption generation failed", e)
                _captionState.value = CaptionGenState.Error(e.localizedMessage ?: "Failed to generate captions.")
            }
        }
    }

    fun shareExportedFile(context: Context, path: String, mimeType: String) {
        try {
            val file = File(path)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Exported Video"))
        } catch (e: Exception) {
            _exportState.value = VideoExportState.Error("Share failed: ${e.localizedMessage}")
        }
    }

    private suspend fun persistMp4ToGallery(context: Context, sourceFile: File): String = withContext(Dispatchers.IO) {
        val filename = "JarvisVideo_${System.currentTimeMillis()}.mp4"
        return@withContext if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/JarvisAI")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext "Exported to ${sourceFile.absolutePath}"
            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(sourceFile).use { it.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            "Saved to Movies/JarvisAI/$filename"
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
            val dest = File(dir, filename)
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            "Saved to ${dest.absolutePath}"
        }
    }

    private fun readDurationMs(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 10_000L
        } catch (_: Exception) {
            10_000L
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun resolveName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }

    override fun onCleared() {
        super.onCleared()
        activeTransformer?.cancel()
    }
}
