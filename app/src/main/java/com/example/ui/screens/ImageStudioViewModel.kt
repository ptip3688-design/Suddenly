package com.example.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
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
import java.io.FileOutputStream

private const val TAG = "ImageStudioVM"

enum class ImageStylePreset(val label: String, val promptPrefix: String) {
    CUSTOM("Custom", ""),
    LOGO("Logo", "Minimalist vector brand logo design, clean bold geometry, crisp edges: "),
    WALLPAPER("Wallpaper", "Ultra-detailed atmospheric 4K wallpaper art, rich lighting: "),
    POSTER("Poster", "Graphic design promotional poster, balanced editorial layout: "),
    BANNER("Gaming Banner", "High-energy esports gaming banner, dynamic neon highlights: "),
    AVATAR("Profile Pic", "Centered high-contrast studio portrait avatar, crisp lighting: ")
}

enum class StudioAspectRatio(val label: String, val sdkValue: AspectRatio) {
    SQUARE("1:1", AspectRatio.SQUARE_1x1),
    LANDSCAPE("16:9", AspectRatio.LANDSCAPE_16x9),
    PORTRAIT("9:16", AspectRatio.PORTRAIT_9x16),
    CLASSIC("4:3", AspectRatio.LANDSCAPE_4x3),
    TALL("3:4", AspectRatio.PORTRAIT_3x4)
}

enum class StudioResolution(val label: String, val sdkValue: ImageSize) {
    RES_1K("1K HD", ImageSize.SIZE_1K),
    RES_2K("2K QHD", ImageSize.SIZE_2K),
    RES_4K("4K UHD", ImageSize.SIZE_4K)
}

sealed interface ImageGenUiState {
    data object Idle : ImageGenUiState
    data object Loading : ImageGenUiState
    data class Success(val bitmap: Bitmap) : ImageGenUiState
    data class Error(val message: String) : ImageGenUiState
}

class ImageStudioViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<ImageGenUiState>(ImageGenUiState.Idle)
    val uiState: StateFlow<ImageGenUiState> = _uiState.asStateFlow()

    private val _sourceBitmap = MutableStateFlow<Bitmap?>(null)
    val sourceBitmap: StateFlow<Bitmap?> = _sourceBitmap.asStateFlow()

    private val _selectedPreset = MutableStateFlow(ImageStylePreset.CUSTOM)
    val selectedPreset: StateFlow<ImageStylePreset> = _selectedPreset.asStateFlow()

    private val _selectedAspect = MutableStateFlow(StudioAspectRatio.SQUARE)
    val selectedAspect: StateFlow<StudioAspectRatio> = _selectedAspect.asStateFlow()

    private val _selectedResolution = MutableStateFlow(StudioResolution.RES_1K)
    val selectedResolution: StateFlow<StudioResolution> = _selectedResolution.asStateFlow()

    private val _useProModel = MutableStateFlow(false)
    val useProModel: StateFlow<Boolean> = _useProModel.asStateFlow()

    private val _exportStatus = MutableStateFlow<String?>(null)
    val exportStatus: StateFlow<String?> = _exportStatus.asStateFlow()

    fun selectPreset(preset: ImageStylePreset) { _selectedPreset.value = preset }
    fun selectAspect(aspect: StudioAspectRatio) { _selectedAspect.value = aspect }
    fun selectResolution(res: StudioResolution) { _selectedResolution.value = res }
    fun toggleProModel(enabled: Boolean) { _useProModel.value = enabled }
    fun clearExportStatus() { _exportStatus.value = null }

    fun loadInputImage(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val decoded = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                }
                if (decoded != null) {
                    val maxDim = 1536
                    val scaled = if (decoded.width > maxDim || decoded.height > maxDim) {
                        val ratio = minOf(maxDim.toFloat() / decoded.width, maxDim.toFloat() / decoded.height)
                        Bitmap.createScaledBitmap(
                            decoded,
                            (decoded.width * ratio).toInt().coerceAtLeast(1),
                            (decoded.height * ratio).toInt().coerceAtLeast(1),
                            true
                        )
                    } else decoded
                    _sourceBitmap.value = scaled
                }
            } catch (e: Exception) {
                _exportStatus.value = "Could not load selected image: ${e.localizedMessage}"
            }
        }
    }

    fun clearSourceImage() {
        _sourceBitmap.value = null
    }

    fun generateOrEdit(prompt: String) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank()) return
        val inputImg = _sourceBitmap.value
        val fullPrompt = if (inputImg == null) {
            "${_selectedPreset.value.promptPrefix}$cleanPrompt"
        } else {
            cleanPrompt
        }

        viewModelScope.launch {
            _uiState.value = ImageGenUiState.Loading
            try {
                val config = generationConfig {
                    responseModalities = listOf(ResponseModality.IMAGE)
                    imageConfig = ImageConfig.builder()
                        .setAspectRatio(_selectedAspect.value.sdkValue)
                        .setImageSize(_selectedResolution.value.sdkValue)
                        .build()
                }
                val modelName = if (_useProModel.value) "gemini-3-pro-image" else "gemini-nano-banana-2.1"
                val model = Firebase.ai.generativeModel(modelName = modelName, generationConfig = config)

                val bitmap = withTimeout(60_000L) {
                    val response = if (inputImg != null) {
                        model.generateContent(
                            content {
                                image(inputImg)
                                text(fullPrompt)
                            }
                        )
                    } else {
                        model.generateContent(fullPrompt)
                    }
                    response.candidates.firstOrNull()
                        ?.content?.parts?.filterIsInstance<ImagePart>()
                        ?.firstOrNull()
                        ?.image
                }

                if (bitmap != null) {
                    _uiState.value = ImageGenUiState.Success(bitmap)
                } else {
                    _uiState.value = ImageGenUiState.Error("No image was returned by Gemini.")
                }
            } catch (e: TimeoutCancellationException) {
                _uiState.value = ImageGenUiState.Error("Image generation timed out after 60 seconds.")
            } catch (e: QuotaExceededException) {
                _uiState.value = ImageGenUiState.Error("Rate limit or Blaze quota exceeded. Please try again later.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image generation failed", e)
                _uiState.value = ImageGenUiState.Error(e.localizedMessage ?: "Image generation failed.")
            }
        }
    }

    fun removeBackground() {
        if (_sourceBitmap.value == null) {
            _exportStatus.value = "Pick an image first to remove its background."
            return
        }
        generateOrEdit("Remove the background completely and isolate the main subject cleanly on a pure solid studio background with crisp edges.")
    }

    fun saveImageToGallery(context: Context, bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val filename = "JarvisAI_${System.currentTimeMillis()}.png"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/JarvisAI")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: throw IllegalStateException("Failed to create MediaStore entry.")
                    resolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    _exportStatus.value = "Saved PNG to Pictures/JarvisAI/$filename"
                } else {
                    val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
                    val outFile = File(dir, filename)
                    FileOutputStream(outFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    _exportStatus.value = "Saved PNG to ${outFile.absolutePath}"
                }
            } catch (e: Exception) {
                _exportStatus.value = "Save failed: ${e.localizedMessage}"
            }
        }
    }

    fun shareImage(context: Context, bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(context.cacheDir, "jarvis_share_${System.currentTimeMillis()}.png")
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                withContext(Dispatchers.Main) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share Jarvis Image"))
                }
            } catch (e: Exception) {
                _exportStatus.value = "Share failed: ${e.localizedMessage}"
            }
        }
    }
}
