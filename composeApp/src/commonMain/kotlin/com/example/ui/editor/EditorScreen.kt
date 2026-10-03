package com.example.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.LayerType
import com.example.ui.theme.*
import com.example.ui.viewmodel.EditorViewModel

import com.example.di.KmpViewModelFactory
import com.example.data.repository.createProjectRepository
import com.example.util.PickerType
import com.example.util.rememberFilePicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    projectId: Long,
    module: com.example.di.AppModule,
    onNavigateBack: () -> Unit,
    viewModel: EditorViewModel = viewModel(
        key = "editor_vm_$projectId",
        factory = KmpViewModelFactory(
            repository = module.repository,
            audioPlayer = module.audioPlayer,
            mediaProvider = module.mediaProvider,
            previewCache = module.previewCache,
            projectId = projectId,
        )
    )
) {
    val project by viewModel.project.collectAsStateWithLifecycle()
    val layers by viewModel.layers.collectAsStateWithLifecycle()
    val currentTime by viewModel.currentTime.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val selectedLayerId by viewModel.selectedLayerId.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val loadedBitmaps by viewModel.loadedBitmaps.collectAsStateWithLifecycle()
    val frameVersion by viewModel.frameVersion.collectAsStateWithLifecycle()

    var showAddLayerSheet by remember { mutableStateOf(false) }
    var showTransformSheet by remember { mutableStateOf(false) }
    var inspectorInitialTab by remember { mutableIntStateOf(0) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    // Platform-specific launchers
    val imagePickerLauncher = rememberFilePicker(PickerType.IMAGE) { uri ->
        uri?.let { viewModel.addMediaLayer(it, isVideo = false) }
    }
    val videoPickerLauncher = rememberFilePicker(PickerType.VIDEO) { uri ->
        uri?.let { viewModel.addMediaLayer(it, isVideo = true) }
    }
    val audioPickerLauncher = rememberFilePicker(PickerType.AUDIO) { uri ->
        uri?.let { viewModel.addAudioLayer(it) }
    }
    val extractAudioPickerLauncher = rememberFilePicker(PickerType.VIDEO) { uri ->
        uri?.let { viewModel.extractAudioFromVideo(it) }
    }

    val currentProject = project

    if (currentProject == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = NeonCyan)
        }
        return
    }

    val selectedLayer = layers.find { it.id == selectedLayerId }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        val isDesktop = maxWidth > 800.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = BackgroundDark,
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                // 1. Top Editor Header
                EditorTopBar(
                    projectTitle = currentProject.title,
                    resolution = "${currentProject.width}x${currentProject.height}",
                    fps = currentProject.fps,
                    canUndo = canUndo,
                    canRedo = canRedo,
                    onBack = onNavigateBack,
                    onUndo = { viewModel.undo() },
                    onRedo = { viewModel.redo() },
                    onOpenSettings = { showSettingsDialog = true },
                    onOpenExport = { showExportDialog = true }
                )

                if (isDesktop) {
                    // DESKTOP LAYOUT
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        // Left Sidebar: Assets
                        Box(
                            modifier = Modifier
                                .width(300.dp)
                                .fillMaxHeight()
                                .background(SurfaceDark)
                                .border(1.dp, SurfaceBorderDark)
                        ) {
                            AddLayerPanel(
                                onAddShape = { shapeType, color -> viewModel.addShapeLayer(shapeType, color) },
                                onPickImage = { imagePickerLauncher() },
                                onPickVideo = { videoPickerLauncher() },
                                onPickAudio = { audioPickerLauncher() },
                                onExtractAudioFromVideo = { extractAudioPickerLauncher() },
                                onAddSampleMedia = { uri -> viewModel.addMediaLayer(uri) },
                                onAddSampleAudio = { uri, name -> viewModel.addAudioLayer(uri, name) },
                                onAddText = { text -> viewModel.addTextLayer(text) },
                                onAddSolid = { color -> viewModel.addSolidLayer(color) },
                                showHeader = true
                            )
                        }

                        // Center Area: Preview + Transport + Timeline
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                CanvasViewport(
                                    project = currentProject,
                                    layers = layers,
                                    currentTime = currentTime,
                                    selectedLayerId = selectedLayerId,
                                    onSelectLayer = { viewModel.selectLayer(it) },
                                    bitmaps = loadedBitmaps,
                                    previewCache = module.previewCache,
                                    frameVersion = frameVersion,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            TransportBar(
                                project = currentProject,
                                currentTime = currentTime,
                                isPlaying = isPlaying,
                                selectedLayer = selectedLayer,
                                onTogglePlay = { viewModel.togglePlayPause() },
                                onStepFrame = { viewModel.stepFrame(it) },
                                onSplitLayer = {
                                    if (selectedLayer != null) {
                                        viewModel.splitLayerAtPlayhead(selectedLayer.id)
                                    }
                                },
                                onOpenAddLayer = { showAddLayerSheet = true }
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
                                    .border(1.dp, SurfaceBorderDark)
                            ) {
                                TimelineView(
                                    project = currentProject,
                                    layers = layers,
                                    currentTime = currentTime,
                                    selectedLayerId = selectedLayerId,
                                    onSelectLayer = { viewModel.selectLayer(it) },
                                    onSeek = { viewModel.seekTo(it) },
                                    onToggleVisibility = { viewModel.toggleVisibility(it) },
                                    onToggleLock = { viewModel.toggleLock(it) },
                                    onUpdateTiming = { id, start, end -> viewModel.updateLayerTiming(id, start, end) },
                                    onDeleteLayer = { viewModel.deleteLayer(it) },
                                    onOpenAddLayer = { showAddLayerSheet = true },
                                    onCommitTiming = { viewModel.commitLayer(it) },
                                    previewCache = module.previewCache,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        // Right Sidebar: Inspector
                        Box(
                            modifier = Modifier
                                .width(320.dp)
                                .fillMaxHeight()
                                .background(SurfaceDark)
                                .border(1.dp, SurfaceBorderDark)
                        ) {
                            if (selectedLayer != null) {
                                TransformInspector(
                                    layer = selectedLayer,
                                    currentTime = currentTime,
                                    initialTab = inspectorInitialTab,
                                    onClose = { viewModel.selectLayer(null) },
                                    onResetTransform = { viewModel.resetTransform(selectedLayer.id) },
                                    onCenterLayer = { viewModel.centerLayer(selectedLayer.id) },
                                    onDeleteLayer = { viewModel.deleteLayer(selectedLayer.id) },
                                    onUpdateTransform = { posX, posY, sX, sY, rot, opac, aX, aY, blend, flipH, flipV ->
                                        viewModel.updateLayerTransform(
                                            layerId = selectedLayer.id,
                                            positionX = posX,
                                            positionY = posY,
                                            scaleX = sX,
                                            scaleY = sY,
                                            rotation = rot,
                                            opacity = opac,
                                            anchorX = aX,
                                            anchorY = aY,
                                            blendMode = blend,
                                            isFlippedH = flipH,
                                            isFlippedV = flipV
                                        )
                                    },
                                    onUpdateShape = { shapeType, fillCol, strokeCol, strokeW, cr ->
                                        viewModel.updateLayerShape(
                                            layerId = selectedLayer.id,
                                            shapeType = shapeType,
                                            fillColor = fillCol,
                                            strokeColor = strokeCol,
                                            strokeWidth = strokeW,
                                            cornerRadius = cr
                                        )
                                    },
                                    onUpdateText = { text, fontSize, textCol, isBold, isItalic, align ->
                                        viewModel.updateLayerText(
                                            layerId = selectedLayer.id,
                                            text = text,
                                            fontSize = fontSize,
                                            textColor = textCol,
                                            isBold = isBold,
                                            isItalic = isItalic,
                                            alignment = align
                                        )
                                    },
                                    onUpdateMedia = { uri, fit ->
                                        viewModel.updateLayerMedia(
                                            layerId = selectedLayer.id,
                                            uri = uri,
                                            fitMode = fit
                                        )
                                    },
                                    onPickMedia = {
                                        if (selectedLayer.type == LayerType.VIDEO) {
                                            videoPickerLauncher()
                                        } else {
                                            imagePickerLauncher()
                                        }
                                    },
                                    onUpdateTiming = { start, end ->
                                        viewModel.setLayerTiming(selectedLayer.id, start, end)
                                    },
                                    onExtendDuration = { delta ->
                                        viewModel.extendLayerDuration(selectedLayer.id, delta)
                                    },
                                    onMatchProjectDuration = {
                                        viewModel.matchLayerToProject(selectedLayer.id)
                                    },
                                    onTrimStartToPlayhead = {
                                        viewModel.trimLayerStartToPlayhead(selectedLayer.id)
                                    },
                                    onTrimEndToPlayhead = {
                                        viewModel.trimLayerEndToPlayhead(selectedLayer.id)
                                    },
                                    onSplitAtPlayhead = {
                                        viewModel.splitLayerAtPlayhead(selectedLayer.id)
                                    },
                                    isSidebar = true,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Select a layer to inspect",
                                        color = TextMuted,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // MOBILE LAYOUT (Original)
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1.1f)
                        ) {
                            CanvasViewport(
                                project = currentProject,
                                layers = layers,
                                currentTime = currentTime,
                                selectedLayerId = selectedLayerId,
                                onSelectLayer = { viewModel.selectLayer(it) },
                                bitmaps = loadedBitmaps,
                                previewCache = module.previewCache,
                                frameVersion = frameVersion,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        TransportBar(
                            project = currentProject,
                            currentTime = currentTime,
                            isPlaying = isPlaying,
                            selectedLayer = selectedLayer,
                            onTogglePlay = { viewModel.togglePlayPause() },
                            onStepFrame = { viewModel.stepFrame(it) },
                            onSplitLayer = {
                                if (selectedLayer != null) {
                                    viewModel.splitLayerAtPlayhead(selectedLayer.id)
                                }
                            },
                            onOpenAddLayer = { showAddLayerSheet = true }
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.9f)
                        ) {
                            TimelineView(
                                project = currentProject,
                                layers = layers,
                                currentTime = currentTime,
                                selectedLayerId = selectedLayerId,
                                onSelectLayer = { viewModel.selectLayer(it) },
                                onSeek = { viewModel.seekTo(it) },
                                onToggleVisibility = { viewModel.toggleVisibility(it) },
                                onToggleLock = { viewModel.toggleLock(it) },
                                onUpdateTiming = { id, start, end -> viewModel.updateLayerTiming(id, start, end) },
                                onDeleteLayer = { viewModel.deleteLayer(it) },
                                onOpenAddLayer = { showAddLayerSheet = true },
                                onCommitTiming = { viewModel.commitLayer(it) },
                                previewCache = module.previewCache,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        if (showTransformSheet && selectedLayer != null) {
                            TransformInspector(
                                layer = selectedLayer,
                                currentTime = currentTime,
                                initialTab = inspectorInitialTab,
                                onClose = { showTransformSheet = false },
                                onResetTransform = { viewModel.resetTransform(selectedLayer.id) },
                                onCenterLayer = { viewModel.centerLayer(selectedLayer.id) },
                                onDeleteLayer = {
                                    viewModel.deleteLayer(selectedLayer.id)
                                    showTransformSheet = false
                                },
                                onUpdateTransform = { posX, posY, sX, sY, rot, opac, aX, aY, blend, flipH, flipV ->
                                    viewModel.updateLayerTransform(
                                        layerId = selectedLayer.id,
                                        positionX = posX,
                                        positionY = posY,
                                        scaleX = sX,
                                        scaleY = sY,
                                        rotation = rot,
                                        opacity = opac,
                                        anchorX = aX,
                                        anchorY = aY,
                                        blendMode = blend,
                                        isFlippedH = flipH,
                                        isFlippedV = flipV
                                    )
                                },
                                onUpdateShape = { shapeType, fillCol, strokeCol, strokeW, cr ->
                                    viewModel.updateLayerShape(
                                        layerId = selectedLayer.id,
                                        shapeType = shapeType,
                                        fillColor = fillCol,
                                        strokeColor = strokeCol,
                                        strokeWidth = strokeW,
                                        cornerRadius = cr
                                    )
                                },
                                onUpdateText = { text, fontSize, textCol, isBold, isItalic, align ->
                                    viewModel.updateLayerText(
                                        layerId = selectedLayer.id,
                                        text = text,
                                        fontSize = fontSize,
                                        textColor = textCol,
                                        isBold = isBold,
                                        isItalic = isItalic,
                                        alignment = align
                                    )
                                },
                                onUpdateMedia = { uri, fit ->
                                    viewModel.updateLayerMedia(
                                        layerId = selectedLayer.id,
                                        uri = uri,
                                        fitMode = fit
                                    )
                                },
                                onPickMedia = {
                                    if (selectedLayer.type == LayerType.VIDEO) {
                                        videoPickerLauncher()
                                    } else {
                                        imagePickerLauncher()
                                    }
                                },
                                onUpdateTiming = { start, end ->
                                    viewModel.setLayerTiming(selectedLayer.id, start, end)
                                },
                                onExtendDuration = { delta ->
                                    viewModel.extendLayerDuration(selectedLayer.id, delta)
                                },
                                onMatchProjectDuration = {
                                    viewModel.matchLayerToProject(selectedLayer.id)
                                },
                                onTrimStartToPlayhead = {
                                    viewModel.trimLayerStartToPlayhead(selectedLayer.id)
                                },
                                onTrimEndToPlayhead = {
                                    viewModel.trimLayerEndToPlayhead(selectedLayer.id)
                                },
                                onSplitAtPlayhead = {
                                    viewModel.splitLayerAtPlayhead(selectedLayer.id)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp)
                            )
                        } else {
                            CapCutBottomNavBar(
                                selectedLayer = selectedLayer,
                                onDeselectLayer = {
                                    viewModel.selectLayer(null)
                                    showTransformSheet = false
                                },
                                onOpenAddLayer = { showAddLayerSheet = true },
                                onOpenTransform = {
                                    inspectorInitialTab = 0
                                    showTransformSheet = true
                                },
                                onSplitClip = {
                                    if (selectedLayer != null) {
                                        viewModel.splitLayerAtPlayhead(selectedLayer.id)
                                    }
                                },
                                onDeleteClip = {
                                    if (selectedLayer != null) {
                                        viewModel.deleteLayer(selectedLayer.id)
                                        showTransformSheet = false
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Add Layer Modal Bottom Sheet (Mobile only)
    if (showAddLayerSheet) {
        AddLayerSheet(
            onDismiss = { showAddLayerSheet = false },
            onAddShape = { shapeType, color -> viewModel.addShapeLayer(shapeType, color) },
            onPickImage = { imagePickerLauncher() },
            onPickVideo = { videoPickerLauncher() },
            onPickAudio = { audioPickerLauncher() },
            onExtractAudioFromVideo = { extractAudioPickerLauncher() },
            onAddSampleMedia = { uri -> viewModel.addMediaLayer(uri) },
            onAddSampleAudio = { uri, name -> viewModel.addAudioLayer(uri, name) },
            onAddText = { text -> viewModel.addTextLayer(text) },
            onAddSolid = { color -> viewModel.addSolidLayer(color) }
        )
    }

    // Project Settings Dialog
    if (showSettingsDialog) {
        ProjectSettingsDialog(
            project = currentProject,
            onDismiss = { showSettingsDialog = false },
            onSave = { title, fps, bg, duration ->
                viewModel.updateProjectSettings(title, fps, bg, duration)
            }
        )
    }

    // Export Dialog
    if (showExportDialog) {
        ExportDialog(
            project = currentProject,
            layers = layers,
            currentTime = currentTime,
            loadedBitmaps = loadedBitmaps,
            onDismiss = { showExportDialog = false }
        )
    }
}

@Composable
private fun EditorTopBar(
    projectTitle: String,
    resolution: String,
    fps: Int,
    canUndo: Boolean,
    canRedo: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenExport: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Back \u0026 Title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp).testTag("editor_back_btn")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to projects",
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = projectTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$resolution \u2022 ${fps}FPS",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = NeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
        }

        // Action Buttons: Undo, Redo, Settings, Export
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = onUndo,
                enabled = canUndo,
                modifier = Modifier.size(36.dp).testTag("undo_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    tint = if (canUndo) TextPrimary else TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onRedo,
                enabled = canRedo,
                modifier = Modifier.size(36.dp).testTag("redo_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Redo",
                    tint = if (canRedo) TextPrimary else TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(36.dp).testTag("project_settings_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Project Settings",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Export Button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(NeonCyan)
                    .clickable { onOpenExport() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("export_btn"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Export",
                    tint = Color(0xFF090B10.toInt()),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Export",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF090B10.toInt()),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                )
            }
        }
    }
}
