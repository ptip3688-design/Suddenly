package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.CardHudSurface
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.DeepHudSurface
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.ObsidianSpace
import com.example.ui.theme.OutlineGlow

@Composable
fun ImageStudioScreen(
    modifier: Modifier = Modifier,
    viewModel: ImageStudioViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sourceBitmap by viewModel.sourceBitmap.collectAsStateWithLifecycle()
    val selectedPreset by viewModel.selectedPreset.collectAsStateWithLifecycle()
    val selectedAspect by viewModel.selectedAspect.collectAsStateWithLifecycle()
    val selectedResolution by viewModel.selectedResolution.collectAsStateWithLifecycle()
    val useProModel by viewModel.useProModel.collectAsStateWithLifecycle()
    val exportStatus by viewModel.exportStatus.collectAsStateWithLifecycle()

    var prompt by remember { mutableStateOf("") }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.loadInputImage(context, it) } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianSpace)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "AI IMAGE STUDIO",
            style = MaterialTheme.typography.headlineMedium,
            color = ElectricCyan
        )
        Text(
            text = "Generate logos, wallpapers, posters, banners, or edit photos with Gemini Nano Banana.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!exportStatus.isNullOrBlank()) {
            Surface(
                color = EmeraldPulse.copy(alpha = 0.14f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = exportStatus!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldPulse,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.clearExportStatus() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Source Image Picker for Editing / Background Removal
        Surface(
            color = DeepHudSurface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, OutlineGlow, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Input Image (Optional Edit Mode)", style = MaterialTheme.typography.titleMedium)
                    if (sourceBitmap != null) {
                        IconButton(onClick = { viewModel.clearSourceImage() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear input image")
                        }
                    }
                }
                if (sourceBitmap != null) {
                    Image(
                        bitmap = sourceBitmap!!.asImageBitmap(),
                        contentDescription = "Input image to edit",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ObsidianSpace)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_edit_image_button")
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (sourceBitmap == null) "Pick Photo" else "Change Photo")
                    }
                    OutlinedButton(
                        onClick = { viewModel.removeBackground() },
                        enabled = sourceBitmap != null && uiState !is ImageGenUiState.Loading,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("remove_bg_button")
                    ) {
                        Icon(Icons.Filled.LayersClear, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Remove BG")
                    }
                }
            }
        }

        // Style Presets
        Text("Style Preset", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ImageStylePreset.entries.forEach { preset ->
                val selected = selectedPreset == preset
                StudioChip(
                    label = preset.label,
                    selected = selected,
                    onClick = { viewModel.selectPreset(preset) }
                )
            }
        }

        // Aspect Ratio & Resolution
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Aspect Ratio", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StudioAspectRatio.entries.forEach { ratio ->
                        StudioChip(
                            label = ratio.label,
                            selected = selectedAspect == ratio,
                            onClick = { viewModel.selectAspect(ratio) }
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Export Resolution", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StudioResolution.entries.forEach { res ->
                        StudioChip(
                            label = res.label,
                            selected = selectedResolution == res,
                            onClick = { viewModel.selectResolution(res) }
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Pro Model", style = MaterialTheme.typography.labelMedium)
                Switch(checked = useProModel, onCheckedChange = { viewModel.toggleProModel(it) })
            }
        }

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = {
                Text(
                    if (sourceBitmap == null) "Describe the image to generate..."
                    else "Describe the edit to apply..."
                )
            },
            minLines = 2,
            maxLines = 4,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricCyan,
                unfocusedBorderColor = OutlineGlow,
                focusedContainerColor = CardHudSurface,
                unfocusedContainerColor = CardHudSurface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("image_prompt_input")
        )

        Button(
            onClick = { viewModel.generateOrEdit(prompt) },
            enabled = prompt.isNotBlank() && uiState !is ImageGenUiState.Loading,
            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianSpace),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("generate_image_button")
        ) {
            Icon(Icons.Filled.AutoFixHigh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (sourceBitmap == null) "Generate Image" else "Apply AI Edit",
                style = MaterialTheme.typography.labelLarge
            )
        }

        // Result Canvas
        Surface(
            color = DeepHudSurface,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, OutlineGlow, RoundedCornerShape(18.dp))
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                when (val state = uiState) {
                    is ImageGenUiState.Idle -> {
                        Text(
                            text = "Generated 1K–4K artwork will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 48.dp)
                        )
                    }
                    is ImageGenUiState.Loading -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 48.dp)
                        ) {
                            CircularProgressIndicator(color = ElectricCyan)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Synthesizing high-res image (30–60s)...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ElectricCyan
                            )
                        }
                    }
                    is ImageGenUiState.Error -> {
                        Text(
                            text = "Error: ${state.message}",
                            color = CrimsonError,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    }
                    is ImageGenUiState.Success -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Image(
                                bitmap = state.bitmap.asImageBitmap(),
                                contentDescription = "Generated Jarvis Image",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(ObsidianSpace)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { viewModel.saveImageToGallery(context, state.bitmap) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldPulse,
                                        contentColor = ObsidianSpace
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("save_image_button")
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save PNG")
                                }
                                OutlinedButton(
                                    onClick = { viewModel.shareImage(context, state.bitmap) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("share_image_button")
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Share")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioChip(
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
