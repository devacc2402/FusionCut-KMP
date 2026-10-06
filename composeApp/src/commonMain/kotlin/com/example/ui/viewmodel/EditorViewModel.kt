package com.example.ui.viewmodel

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ProjectRepository
import com.example.engine.FusionEngineProvider
import com.example.engine.IFusionEngine
import com.example.model.EffectType
import com.example.model.KeyframeEasing
import com.example.model.Layer
import com.example.model.LayerEffect
import com.example.model.LayerType
import com.example.model.MediaFitMode
import com.example.model.Project
import com.example.model.PropertyKeyframe
import com.example.model.ShapeType
import com.example.util.AudioPlayer
import com.example.util.MediaProvider
import com.example.util.PreviewCache
import com.example.util.randomUUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.TimeSource

class EditorViewModel(
    private val repository: ProjectRepository,
    private val audioPlayer: AudioPlayer,
    private val mediaProvider: MediaProvider,
    private val previewCache: PreviewCache,
    private val projectId: Long
) : ViewModel() {

    private val engine: IFusionEngine = FusionEngineProvider.getEngine()

    private val _project = MutableStateFlow<Project?>(null)
    val project: StateFlow<Project?> = _project.asStateFlow()

    private val _layers = MutableStateFlow<List<Layer>>(emptyList())
    val layers: StateFlow<List<Layer>> = _layers.asStateFlow()

    private val _currentTime = MutableStateFlow(0.0f)
    val currentTime = _currentTime.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _selectedLayerId = MutableStateFlow<String?>(null)
    val selectedLayerId = _selectedLayerId.asStateFlow()

    private val _snappingGuideX = MutableStateFlow<Float?>(null)
    val snappingGuideX = _snappingGuideX.asStateFlow()

    private val _snappingGuideY = MutableStateFlow<Float?>(null)
    val snappingGuideY = _snappingGuideY.asStateFlow()

    private var playbackJob: Job? = null
    private var pendingSaveJob: Job? = null
    private var prefetchJob: Job? = null

    // Undo / Redo history
    private val undoStack = mutableListOf<List<Layer>>()
    private val redoStack = mutableListOf<List<Layer>>()
    private var historySnapshotBeforeEdit: List<Layer>? = null
    private val _canUndo = MutableStateFlow(false)
    val canUndo = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo = _canRedo.asStateFlow()

    private val _loadedBitmaps = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    val loadedBitmaps: StateFlow<Map<String, ImageBitmap>> = _loadedBitmaps.asStateFlow()

    private val _frameVersion = MutableStateFlow(0L)
    val frameVersion: StateFlow<Long> = _frameVersion.asStateFlow()

    init {
        // Fetch project immediately and collect updates
        viewModelScope.launch {
            val direct = repository.getProjectDirect(projectId)
            if (direct != null) {
                _project.value = direct
            }
            repository.getProject(projectId).collect { dbProj ->
                if (dbProj != null) {
                    _project.value = dbProj
                }
            }
        }

        // Fetch layers immediately and collect updates
        viewModelScope.launch {
            val directLayers = repository.getLayersDirect(projectId)
            if (directLayers.isNotEmpty() && _layers.value.isEmpty()) {
                _layers.value = directLayers
            }
            repository.getLayers(projectId).collect { dbLayers ->
                if (_layers.value.isEmpty() || pendingSaveJob?.isActive != true) {
                    _layers.value = dbLayers
                }
            }
        }

        // Preload any sample or media textures
        viewModelScope.launch {
            _layers.collect { currentLayers ->
                for (l in currentLayers) {
                    val uri = l.mediaUri
                    if (uri != null && !_loadedBitmaps.value.containsKey(uri)) {
                        val platformBitmap = mediaProvider.loadBitmap(uri)
                        if (platformBitmap != null) {
                            _loadedBitmaps.value = _loadedBitmaps.value + (uri to platformBitmap.asImageBitmap())
                        }
                    }
                }
            }
        }
    }

    private fun invalidateLayerArea(layer: Layer) {
        val currentFps = project.value?.fps ?: 30
        previewCache.invalidateTimeRange(projectId, layer.startTime, layer.endTime, currentFps)
    }

    private fun invalidateTimeRange(startTime: Float, endTime: Float) {
        val currentFps = project.value?.fps ?: 30
        previewCache.invalidateTimeRange(projectId, startTime, endTime, currentFps)
    }

    fun selectLayer(id: String?) {
        _selectedLayerId.value = id
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            pause()
        } else {
            play()
        }
    }

    fun play() {
        val currentProj = project.value ?: return
        _isPlaying.value = true
        audioPlayer.onPlay(_currentTime.value, _layers.value)
        playbackJob?.cancel()

        playbackJob = viewModelScope.launch(Dispatchers.Main) {
            val fps = currentProj.fps.coerceIn(15, 60)
            val frameStep = 1.0f / fps
            val timeSource = TimeSource.Monotonic
            var lastRealtimeMark = timeSource.markNow()

            while (isActive && _isPlaying.value) {
                val projSnapshot = project.value ?: break
                val layersSnapshot = _layers.value
                val curTime = _currentTime.value
                val frameIndex = (curTime * fps).toInt()

                // Fusion 2 Ultra: Direct Canvas Rendering Pipeline
                // We advance the playhead, and CanvasViewport handles the GPU rendering in real-time.
                val nextTime = curTime + frameStep
                if (nextTime >= projSnapshot.durationSeconds) {
                    _currentTime.value = 0.0f
                    audioPlayer.onSeek(0.0f, _layers.value)
                } else {
                    _currentTime.value = nextTime
                }

                // Optimization: Skip heavy background pre-caching during active playback
                // only prefetch video frames which is relatively fast
                prefetchVideoFramesForTime(nextTime + frameStep * 4)

                val elapsed = lastRealtimeMark.elapsedNow()
                val target = (1000L / fps).milliseconds
                val sleep = target - elapsed
                if (sleep > 2.milliseconds) {
                    delay(sleep.inWholeMilliseconds)
                } else {
                    yield()
                }
                lastRealtimeMark = timeSource.markNow()
            }
        }
    }

    fun pause() {
        _isPlaying.value = false
        audioPlayer.onPause()
        playbackJob?.cancel()
        playbackJob = null
        prefetchJob?.cancel()
        prefetchJob = null
    }

    fun seekTo(timeSeconds: Float) {
        val currentProj = project.value ?: return
        val clamped = timeSeconds.coerceIn(0.0f, currentProj.durationSeconds)
        _currentTime.value = clamped
        audioPlayer.onSeek(clamped, _layers.value)
        prefetchVideoFramesForTime(clamped)
    }

    fun stepFrame(forward: Boolean) {
        pause()
        val currentProj = project.value ?: return
        val fps = currentProj.fps.coerceAtLeast(1)
        val step = 1.0f / fps
        val newTime = if (forward) _currentTime.value + step else _currentTime.value - step
        seekTo(newTime)
    }

    fun jumpToStart() {
        pause()
        seekTo(0.0f)
    }

    fun jumpToEnd() {
        pause()
        project.value?.let { seekTo(it.durationSeconds) }
    }

    fun pushHistory() {
        historySnapshotBeforeEdit = null
        val current = _layers.value
        undoStack.add(current)
        if (undoStack.size > 50) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = false
        previewCache.invalidate(projectId)
    }

    fun undo() {
        historySnapshotBeforeEdit = null
        pendingSaveJob?.cancel()
        if (undoStack.isNotEmpty()) {
            val prev = undoStack.removeAt(undoStack.size - 1)
            redoStack.add(_layers.value)
            _canUndo.value = undoStack.isNotEmpty()
            _canRedo.value = true
            _layers.value = prev
            if (_selectedLayerId.value != null && prev.none { it.id == _selectedLayerId.value }) {
                _selectedLayerId.value = prev.firstOrNull()?.id
            }
            previewCache.invalidate(projectId)
            viewModelScope.launch {
                repository.replaceAllLayers(projectId, prev)
            }
        }
    }

    fun redo() {
        historySnapshotBeforeEdit = null
        pendingSaveJob?.cancel()
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.size - 1)
            undoStack.add(_layers.value)
            _canUndo.value = true
            _canRedo.value = redoStack.isNotEmpty()
            _layers.value = next
            if (_selectedLayerId.value != null && next.none { it.id == _selectedLayerId.value }) {
                _selectedLayerId.value = next.firstOrNull()?.id
            }
            previewCache.invalidate(projectId)
            viewModelScope.launch {
                repository.replaceAllLayers(projectId, next)
            }
        }
    }

    fun updateLayerTransform(
        layerId: String,
        positionX: Float? = null,
        positionY: Float? = null,
        scaleX: Float? = null,
        scaleY: Float? = null,
        rotation: Float? = null,
        opacity: Float? = null,
        anchorX: Float? = null,
        anchorY: Float? = null,
        blendMode: String? = null,
        isFlippedH: Boolean? = null,
        isFlippedV: Boolean? = null
    ) {
        if (historySnapshotBeforeEdit == null) {
            historySnapshotBeforeEdit = _layers.value
        }
        val target = _layers.value.find { it.id == layerId } ?: return
        val time = currentTime.value
        val threshold = 0.06f

        fun updateTrack(
            keyframes: List<PropertyKeyframe>,
            newVal: Float?,
            defaultEasing: KeyframeEasing = KeyframeEasing.EASE_IN_OUT
        ): List<PropertyKeyframe> {
            if (newVal == null || keyframes.isEmpty()) return keyframes
            val existingIdx = keyframes.indexOfFirst { abs(it.time - time) < threshold }
            return if (existingIdx >= 0) {
                keyframes.mapIndexed { idx, kf ->
                    if (idx == existingIdx) kf.copy(value = newVal) else kf
                }
            } else {
                (keyframes + PropertyKeyframe(id = randomUUID(), time = time, value = newVal, easing = defaultEasing)).sortedBy { it.time }
            }
        }

        val updated = target.copy(
            positionX = positionX ?: target.positionX,
            positionY = positionY ?: target.positionY,
            scaleX = scaleX ?: target.scaleX,
            scaleY = scaleY ?: target.scaleY,
            rotation = rotation ?: target.rotation,
            opacity = opacity ?: target.opacity,
            anchorX = anchorX ?: target.anchorX,
            anchorY = anchorY ?: target.anchorY,
            blendMode = blendMode ?: target.blendMode,
            isFlippedH = isFlippedH ?: target.isFlippedH,
            isFlippedV = isFlippedV ?: target.isFlippedV,
            positionXKeyframes = updateTrack(target.positionXKeyframes, positionX),
            positionYKeyframes = updateTrack(target.positionYKeyframes, positionY),
            scaleXKeyframes = updateTrack(target.scaleXKeyframes, scaleX),
            scaleYKeyframes = updateTrack(target.scaleYKeyframes, scaleY),
            rotationKeyframes = updateTrack(target.rotationKeyframes, rotation),
            opacityKeyframes = updateTrack(target.opacityKeyframes, opacity)
        )

        _layers.value = _layers.value.map { if (it.id == layerId) updated else it }
        invalidateLayerArea(target)

        pendingSaveJob?.cancel()
        pendingSaveJob = viewModelScope.launch {
            delay(150L)
            val snapshot = historySnapshotBeforeEdit
            if (snapshot != null && snapshot != _layers.value) {
                undoStack.add(snapshot)
                if (undoStack.size > 50) {
                    undoStack.removeAt(0)
                }
                redoStack.clear()
                _canUndo.value = undoStack.isNotEmpty()
                _canRedo.value = false
                historySnapshotBeforeEdit = null
            }
            repository.saveLayer(updated)
        }
    }

    fun commitLayer(layerId: String) {
        val snapshot = historySnapshotBeforeEdit
        if (snapshot != null && snapshot != _layers.value) {
            undoStack.add(snapshot)
            if (undoStack.size > 50) {
                undoStack.removeAt(0)
            }
            redoStack.clear()
            _canUndo.value = undoStack.isNotEmpty()
            _canRedo.value = false
            historySnapshotBeforeEdit = null
        }
        val target = _layers.value.find { it.id == layerId } ?: return
        pendingSaveJob?.cancel()
        invalidateLayerArea(target)
        viewModelScope.launch {
            repository.saveLayer(target)
            val currentProj = project.value
            if (currentProj != null) {
                val maxLayerEnd = _layers.value.maxOfOrNull { it.endTime } ?: currentProj.durationSeconds
                val newDuration = maxOf(currentProj.durationSeconds, maxLayerEnd, 1.0f)
                if (abs(currentProj.durationSeconds - newDuration) > 0.05f) {
                    repository.updateProject(currentProj.copy(durationSeconds = newDuration))
                }
            }
        }
    }

    fun centerLayer(layerId: String) {
        pushHistory()
        updateLayerTransform(layerId, positionX = 0f, positionY = 0f)
        commitLayer(layerId)
    }

    fun resetTransform(layerId: String) {
        pushHistory()
        val target = _layers.value.find { it.id == layerId } ?: return
        val updated = target.copy(
            positionX = 0f,
            positionY = 0f,
            scaleX = 1f,
            scaleY = 1f,
            rotation = 0f,
            opacity = 1f,
            anchorX = 0.5f,
            anchorY = 0.5f,
            isFlippedH = false,
            isFlippedV = false
        )
        _layers.value = _layers.value.map { if (it.id == layerId) updated else it }
        invalidateLayerArea(target)
        viewModelScope.launch {
            repository.saveLayer(updated)
        }
    }

    fun setSnappingGuides(guideX: Float?, guideY: Float?) {
        _snappingGuideX.value = guideX
        _snappingGuideY.value = guideY
    }

    fun updateLayerShape(
        layerId: String,
        shapeType: ShapeType? = null,
        fillColor: Long? = null,
        strokeColor: Long? = null,
        strokeWidth: Float? = null,
        cornerRadius: Float? = null,
        baseWidth: Float? = null,
        baseHeight: Float? = null
    ) {
        val target = _layers.value.find { it.id == layerId } ?: return
        pushHistory()
        val updated = target.copy(
            shapeType = shapeType ?: target.shapeType,
            fillColor = fillColor ?: target.fillColor,
            strokeColor = strokeColor ?: target.strokeColor,
            strokeWidth = strokeWidth ?: target.strokeWidth,
            cornerRadius = cornerRadius ?: target.cornerRadius,
            baseWidth = baseWidth ?: target.baseWidth,
            baseHeight = baseHeight ?: target.baseHeight
        )
        _layers.value = _layers.value.map { if (it.id == layerId) updated else it }
        invalidateLayerArea(target)
        viewModelScope.launch {
            repository.saveLayer(updated)
        }
    }

    fun updateLayerText(
        layerId: String,
        text: String? = null,
        fontSize: Float? = null,
        textColor: Long? = null,
        isBold: Boolean? = null,
        isItalic: Boolean? = null,
        alignment: String? = null
    ) {
        val target = _layers.value.find { it.id == layerId } ?: return
        pushHistory()
        val updated = target.copy(
            text = text ?: target.text,
            fontSize = fontSize ?: target.fontSize,
            textColor = textColor ?: target.textColor,
            isBold = isBold ?: target.isBold,
            isItalic = isItalic ?: target.isItalic,
            textAlignment = alignment ?: target.textAlignment
        )
        _layers.value = _layers.value.map { if (it.id == layerId) updated else it }
        invalidateLayerArea(target)
        viewModelScope.launch {
            repository.saveLayer(updated)
        }
    }

    fun updateLayerMedia(
        layerId: String,
        uri: String?,
        fitMode: MediaFitMode? = null
    ) {
        val target = _layers.value.find { it.id == layerId } ?: return
        pushHistory()
        val updated = target.copy(
            mediaUri = uri ?: target.mediaUri,
            mediaFit = fitMode ?: target.mediaFit
        )
        _layers.value = _layers.value.map { if (it.id == layerId) updated else it }
        invalidateLayerArea(target)
        viewModelScope.launch {
            if (uri != null) {
                mediaProvider.loadBitmap(uri)
            }
            repository.saveLayer(updated)
        }
    }

    fun updateLayerTiming(
        layerId: String,
        startTime: Float,
        endTime: Float
    ) {
        if (historySnapshotBeforeEdit == null) {
            historySnapshotBeforeEdit = _layers.value
        }
        val currentProj = project.value ?: return
        val target = _layers.value.find { it.id == layerId } ?: return
        val clampedStart = startTime.coerceAtLeast(0f)
        val clampedEnd = endTime.coerceAtLeast(clampedStart + 0.05f)
        val updated = target.copy(startTime = clampedStart, endTime = clampedEnd)

        val minTime = minOf(target.startTime, clampedStart)
        val maxTime = maxOf(target.endTime, clampedEnd)
        _layers.value = _layers.value.map { if (it.id == layerId) updated else it }
        invalidateTimeRange(minTime, maxTime)

        val otherMax = _layers.value.filter { it.id != layerId }.maxOfOrNull { it.endTime } ?: 0f
        val newDuration = maxOf(currentProj.durationSeconds, otherMax, clampedEnd, 1.0f)

        pendingSaveJob?.cancel()
        pendingSaveJob = viewModelScope.launch {
            delay(150L)
            val snapshot = historySnapshotBeforeEdit
            if (snapshot != null && snapshot != _layers.value) {
                undoStack.add(snapshot)
                if (undoStack.size > 50) {
                    undoStack.removeAt(0)
                }
                redoStack.clear()
                _canUndo.value = undoStack.isNotEmpty()
                _canRedo.value = false
                historySnapshotBeforeEdit = null
            }
            repository.saveLayer(updated)
            if (newDuration > currentProj.durationSeconds || abs(currentProj.durationSeconds - newDuration) > 0.05f) {
                repository.updateProject(currentProj.copy(durationSeconds = newDuration))
            }
        }
    }

    fun setLayerTiming(layerId: String, startTime: Float, endTime: Float) {
        pushHistory()
        val currentProj = project.value ?: return
        val target = _layers.value.find { it.id == layerId } ?: return
        val clampedStart = startTime.coerceAtLeast(0f)
        val clampedEnd = endTime.coerceAtLeast(clampedStart + 0.05f)
        val updated = target.copy(startTime = clampedStart, endTime = clampedEnd)

        val minTime = minOf(target.startTime, clampedStart)
        val maxTime = maxOf(target.endTime, clampedEnd)
        _layers.value = _layers.value.map { if (it.id == layerId) updated else it }
        invalidateTimeRange(minTime, maxTime)

        viewModelScope.launch {
            repository.saveLayer(updated)
            val maxLayerEnd = _layers.value.maxOfOrNull { it.endTime } ?: currentProj.durationSeconds
            val newDuration = maxOf(currentProj.durationSeconds, maxLayerEnd, 1.0f)
            if (abs(currentProj.durationSeconds - newDuration) > 0.05f) {
                repository.updateProject(currentProj.copy(durationSeconds = newDuration))
            }
        }
    }

    fun extendLayerDuration(layerId: String, deltaSeconds: Float) {
        val target = _layers.value.find { it.id == layerId } ?: return
        val newEnd = (target.endTime + deltaSeconds).coerceAtLeast(target.startTime + 0.1f)
        setLayerTiming(layerId, target.startTime, newEnd)
    }

    fun matchLayerToProject(layerId: String) {
        val currentProj = project.value ?: return
        setLayerTiming(layerId, 0f, currentProj.durationSeconds)
    }

    fun trimLayerStartToPlayhead(layerId: String) {
        val curT = _currentTime.value
        val target = _layers.value.find { it.id == layerId } ?: return
        if (curT < target.endTime - 0.05f) {
            setLayerTiming(layerId, curT, target.endTime)
        }
    }

    fun trimLayerEndToPlayhead(layerId: String) {
        val curT = _currentTime.value
        val target = _layers.value.find { it.id == layerId } ?: return
        if (curT > target.startTime + 0.05f) {
            setLayerTiming(layerId, target.startTime, curT)
        }
    }

    fun splitLayerAtPlayhead(layerId: String) {
        val target = _layers.value.find { it.id == layerId } ?: return
        val splitTime = _currentTime.value
        if (splitTime <= target.startTime + 0.05f || splitTime >= target.endTime - 0.05f) return
        pushHistory()

        val firstPart = target.copy(endTime = splitTime)
        val secondPart = target.copy(
            id = randomUUID(),
            name = "${target.name} (Part 2)",
            startTime = splitTime,
            orderIndex = (_layers.value.maxOfOrNull { it.orderIndex } ?: 0) + 1
        )
        _layers.value = _layers.value.map { if (it.id == layerId) firstPart else it } + secondPart
        invalidateLayerArea(target)
        viewModelScope.launch {
            repository.saveLayer(firstPart)
            repository.saveLayer(secondPart)
        }
    }

    fun toggleVisibility(layerId: String) {
        val target = _layers.value.find { it.id == layerId } ?: return
        pushHistory()
        val updated = target.copy(isVisible = !target.isVisible)
        _layers.value = _layers.value.map { if (it.id == layerId) updated else it }
        invalidateLayerArea(target)
        viewModelScope.launch {
            repository.saveLayer(updated)
        }
    }

    fun toggleLock(layerId: String) {
        val target = _layers.value.find { it.id == layerId } ?: return
        pushHistory()
        val updated = target.copy(isLocked = !target.isLocked)
        _layers.value = _layers.value.map { if (it.id == layerId) updated else it }
        invalidateLayerArea(target)
        viewModelScope.launch {
            repository.saveLayer(updated)
        }
    }

    fun addShapeLayer(shapeType: ShapeType = ShapeType.ROUNDED_RECT, fillColor: Long = 0xFF00F0FF) {
        pushHistory()
        val currentProj = project.value ?: return
        val currentList = layers.value
        val nextOrder = (currentList.maxOfOrNull { it.orderIndex } ?: -1) + 1
        val name = when (shapeType) {
            ShapeType.ROUNDED_RECT -> "Rounded Box ${nextOrder + 1}"
            ShapeType.RECTANGLE -> "Rectangle ${nextOrder + 1}"
            ShapeType.CIRCLE -> "Circle ${nextOrder + 1}"
            ShapeType.STAR -> "Star ${nextOrder + 1}"
            ShapeType.TRIANGLE -> "Triangle ${nextOrder + 1}"
            ShapeType.HEART -> "Heart ${nextOrder + 1}"
            ShapeType.POLYGON -> "Hexagon ${nextOrder + 1}"
            ShapeType.CAPSULE -> "Capsule ${nextOrder + 1}"
        }

        val layer = Layer(
            id = randomUUID(),
            projectId = projectId,
            name = name,
            type = LayerType.SHAPE,
            orderIndex = nextOrder,
            startTime = 0.0f,
            endTime = currentProj.durationSeconds,
            shapeType = shapeType,
            fillColor = fillColor,
            strokeColor = 0xFFFFFFFF,
            strokeWidth = 0f,
            cornerRadius = 24f,
            baseWidth = 320f,
            baseHeight = 320f
        )
        _layers.value = _layers.value + layer
        _selectedLayerId.value = layer.id
        invalidateLayerArea(layer)
        viewModelScope.launch {
            repository.saveLayer(layer)
        }
    }

    private var lastPrefetchedBucket: Int = -1

    private val prefetchMutex = Mutex()
    private val prefetchedUris = mutableSetOf<String>()

    private fun prefetchVideoFramesForTime(time: Float) {
        val currentProj = project.value ?: return
        
        // Bucket index for current time (30fps)
        val currentBucket = (time * 30).toInt()
        
        // Only trigger if we moved to a new bucket
        if (currentBucket == lastPrefetchedBucket && _isPlaying.value) return
        lastPrefetchedBucket = currentBucket

        val currentLayers = _layers.value
        val videoLayers = currentLayers.filter {
            it.type == LayerType.VIDEO && it.isVisible && it.isActiveAt(time) && !it.mediaUri.isNullOrBlank()
        }

        if (videoLayers.isEmpty()) return

        // Fusion 2 Turbo: High-Performance Concurrent Prefetching
        viewModelScope.launch(Dispatchers.IO) {
            // If already busy prefetching, don't queue more unless playing
            if (!prefetchMutex.tryLock()) {
                if (!_isPlaying.value) return@launch
                prefetchMutex.lock() 
            }
            
            try {
                videoLayers.forEach { layer ->
                    if (!isActive) return@forEach
                    val uri = layer.mediaUri ?: return@forEach
                    val clipTime = (time - layer.startTime).coerceAtLeast(0f)
                    
                    // 1. Load exact frame (suspend)
                    val platformBitmap = mediaProvider.loadFrame(uri, clipTime, targetW = 480, targetH = 480)
                    if (platformBitmap != null) {
                        _frameVersion.value = System.currentTimeMillis()
                    }
                    yield() // Allow other IO tasks to breathe

                    // 2. If playing, prefetch a few frames ahead non-blocking
                    if (_isPlaying.value && isActive) {
                        launch {
                            mediaProvider.loadFrame(uri, clipTime + 0.5f, targetW = 480, targetH = 480)
                            yield()
                            mediaProvider.loadFrame(uri, clipTime + 1.5f, targetW = 480, targetH = 480)
                            yield()
                            mediaProvider.loadFrame(uri, clipTime + 3.0f, targetW = 480, targetH = 480)
                        }
                    }
                }
            } finally {
                prefetchMutex.unlock()
            }
        }
    }

    fun addMediaLayer(uriString: String, isVideo: Boolean = false) {
        pushHistory()
        val currentProj = project.value ?: return
        val currentList = layers.value
        val nextOrder = (currentList.maxOfOrNull { it.orderIndex } ?: -1) + 1
        val name = if (isVideo) "Video ${nextOrder + 1}" else "Image ${nextOrder + 1}"

        viewModelScope.launch {
            val originalMeta = mediaProvider.getMediaMetadata(uriString)
            val localUri = mediaProvider.copyMediaToLocalStorage(projectId, uriString, isVideo)
            val localMeta = mediaProvider.getMediaMetadata(localUri)
            val meta = if (originalMeta != null && originalMeta.durationMs > 0) originalMeta else (localMeta ?: originalMeta)
            val videoExtensions = listOf(".mp4", ".mov", ".mkv", ".avi", ".webm", ".m4v")
            val isRealVideo = isVideo || meta?.isVideo == true || videoExtensions.any { uriString.endsWith(it, ignoreCase = true) }

            var baseW = 400f
            var baseH = 400f
            if (meta != null && meta.width > 0 && meta.height > 0) {
                val aspect = meta.width.toFloat() / meta.height.toFloat()
                val targetMaxW = currentProj.width * 0.75f
                val targetMaxH = currentProj.height * 0.75f
                if (aspect > targetMaxW / targetMaxH) {
                    baseW = targetMaxW
                    baseH = targetMaxW / aspect
                } else {
                    baseH = targetMaxH
                    baseW = targetMaxH * aspect
                }
            }

            val rawDuration = meta?.durationMs ?: 0L
            val videoDurationSec = if (isRealVideo && rawDuration > 0) {
                (rawDuration / 1000.0f).coerceAtLeast(0.5f)
            } else if (isRealVideo) {
                // If metadata failed, try to estimate from file size or use project duration
                // as a better fallback than 5s.
                maxOf(currentProj.durationSeconds, 15.0f)
            } else {
                currentProj.durationSeconds
            }

            val updatedProjDuration = if (isRealVideo) {
                maxOf(videoDurationSec, currentProj.durationSeconds)
            } else {
                currentProj.durationSeconds
            }
            if (updatedProjDuration != currentProj.durationSeconds || currentList.isEmpty()) {
                val updatedProj = currentProj.copy(durationSeconds = updatedProjDuration)
                repository.updateProject(updatedProj)
            }

            val layer = Layer(
                id = randomUUID(),
                projectId = projectId,
                name = name,
                type = if (isRealVideo) LayerType.VIDEO else LayerType.IMAGE,
                orderIndex = nextOrder,
                startTime = 0.0f,
                endTime = if (isRealVideo) videoDurationSec else updatedProjDuration,
                mediaUri = localUri,
                baseWidth = baseW,
                baseHeight = baseH
            )
            _layers.value = _layers.value + layer
            _selectedLayerId.value = layer.id
            invalidateLayerArea(layer)
            repository.saveLayer(layer)
        }
    }

    fun addTextLayer(initialText: String = "Fusion Cut") {
        pushHistory()
        val currentProj = project.value ?: return
        val currentList = layers.value
        val nextOrder = (currentList.maxOfOrNull { it.orderIndex } ?: -1) + 1

        val layer = Layer(
            id = randomUUID(),
            projectId = projectId,
            name = "Text ${nextOrder + 1}",
            type = LayerType.TEXT,
            orderIndex = nextOrder,
            startTime = 0.0f,
            endTime = currentProj.durationSeconds,
            text = initialText,
            fontSize = 52.0f,
            textColor = 0xFFFFFFFF,
            baseWidth = 500f,
            baseHeight = 150f
        )
        _layers.value = _layers.value + layer
        _selectedLayerId.value = layer.id
        invalidateLayerArea(layer)
        viewModelScope.launch {
            repository.saveLayer(layer)
        }
    }

    fun addAudioLayer(uriString: String, customName: String? = null) {
        pushHistory()
        val currentProj = project.value ?: return
        val currentList = layers.value
        val nextOrder = (currentList.maxOfOrNull { it.orderIndex } ?: -1) + 1
        val name = customName ?: "Audio ${nextOrder + 1}"

        viewModelScope.launch {
            val localUri = mediaProvider.copyMediaToLocalStorage(projectId, uriString, isVideo = false)
            val meta = mediaProvider.getMediaMetadata(localUri)
            val audioDurationSec = if (meta != null && meta.durationMs > 0) {
                (meta.durationMs / 1000.0f).coerceAtLeast(0.5f)
            } else {
                maxOf(currentProj.durationSeconds, 30.0f)
            }

            val updatedProjDuration = maxOf(audioDurationSec, currentProj.durationSeconds)
            if (updatedProjDuration != currentProj.durationSeconds || currentList.isEmpty()) {
                val updatedProj = currentProj.copy(durationSeconds = updatedProjDuration)
                repository.updateProject(updatedProj)
            }

            val layer = Layer(
                id = randomUUID(),
                projectId = projectId,
                name = name,
                type = LayerType.AUDIO,
                orderIndex = nextOrder,
                startTime = 0.0f,
                endTime = audioDurationSec,
                mediaUri = localUri,
                baseWidth = 0f,
                baseHeight = 0f
            )
            _layers.value = _layers.value + layer
            _selectedLayerId.value = layer.id
            invalidateLayerArea(layer)
            repository.saveLayer(layer)
        }
    }

    fun extractAudioFromVideo(videoUriString: String) {
        addAudioLayer(videoUriString, customName = "Extracted Audio")
    }

    fun addSolidLayer(color: Long = 0xFF121622) {
        pushHistory()
        val currentProj = project.value ?: return
        val currentList = layers.value
        val nextOrder = (currentList.maxOfOrNull { it.orderIndex } ?: -1) + 1

        val layer = Layer(
            id = randomUUID(),
            projectId = projectId,
            name = "Solid Color ${nextOrder + 1}",
            type = LayerType.SOLID,
            orderIndex = nextOrder,
            startTime = 0.0f,
            endTime = currentProj.durationSeconds,
            fillColor = color
        )
        _layers.value = _layers.value + layer
        _selectedLayerId.value = layer.id
        invalidateLayerArea(layer)
        viewModelScope.launch {
            repository.saveLayer(layer)
        }
    }

    fun duplicateLayer(layerId: String) {
        pushHistory()
        val currentList = layers.value
        val target = currentList.find { it.id == layerId } ?: return
        val nextOrder = (currentList.maxOfOrNull { it.orderIndex } ?: -1) + 1
        val duplicated = target.copy(
            id = randomUUID(),
            name = "${target.name} Copy",
            orderIndex = nextOrder,
            positionX = target.positionX + 30f,
            positionY = target.positionY + 30f
        )
        _layers.value = _layers.value + duplicated
        _selectedLayerId.value = duplicated.id
        invalidateLayerArea(duplicated)
        viewModelScope.launch {
            repository.saveLayer(duplicated)
        }
    }

    fun deleteLayer(layerId: String) {
        pushHistory()
        pendingSaveJob?.cancel()
        pendingSaveJob = null
        val target = _layers.value.find { it.id == layerId }
        val remaining = _layers.value.filter { it.id != layerId }
        _layers.value = remaining
        if (_selectedLayerId.value == layerId) {
            _selectedLayerId.value = null
        }
        target?.let { invalidateLayerArea(it) }
        viewModelScope.launch {
            repository.deleteLayer(layerId, projectId)
            project.value?.let { currProj ->
                val maxRemaining = remaining.maxOfOrNull { it.endTime } ?: currProj.durationSeconds
                val newDuration = maxOf(maxRemaining, 1.0f)
                if (currProj.durationSeconds > newDuration && remaining.isNotEmpty()) {
                    repository.updateProject(currProj.copy(durationSeconds = newDuration))
                    if (_currentTime.value > newDuration) {
                        _currentTime.value = newDuration
                    }
                }
            }
        }
    }

    fun moveLayerOrder(layerId: String, moveUp: Boolean) {
        val currentList = layers.value.sortedBy { it.orderIndex }.toMutableList()
        val idx = currentList.indexOfFirst { it.id == layerId }
        if (idx == -1) return

        val targetIdx = if (moveUp) idx + 1 else idx - 1
        if (targetIdx !in currentList.indices) return

        pushHistory()
        val item1 = currentList[idx]
        val item2 = currentList[targetIdx]

        val updated1 = item1.copy(orderIndex = item2.orderIndex)
        val updated2 = item2.copy(orderIndex = item1.orderIndex)

        _layers.value = _layers.value.map {
            when (it.id) {
                updated1.id -> updated1
                updated2.id -> updated2
                else -> it
            }
        }
        invalidateTimeRange(minOf(item1.startTime, item2.startTime), maxOf(item1.endTime, item2.endTime))
        viewModelScope.launch {
            repository.saveLayers(listOf(updated1, updated2))
        }
    }

    fun updateProjectSettings(
        title: String,
        fps: Int,
        backgroundColor: Long,
        durationSeconds: Float? = null
    ) {
        val current = project.value ?: return
        val maxLayerEnd = layers.value.maxOfOrNull { it.endTime } ?: 0f
        val newDuration = durationSeconds ?: maxOf(current.durationSeconds, maxLayerEnd, 1.0f)
        val updated = current.copy(
            title = title,
            fps = fps,
            durationSeconds = newDuration,
            backgroundColor = backgroundColor
        )
        previewCache.invalidate(projectId)
        viewModelScope.launch {
            repository.updateProject(updated)
        }
    }

    fun toggleKeyframe(layerId: String, propertyName: String, easing: KeyframeEasing = KeyframeEasing.EASE_IN_OUT) {
        val target = layers.value.find { it.id == layerId } ?: return
        val time = currentTime.value
        val anim = target.getAnimatedTransformAt(time)
        val threshold = 0.05f

        pushHistory()

        val updated = when (propertyName.lowercase()) {
            "positionx" -> {
                val exists = target.positionXKeyframes.any { abs(it.time - time) < threshold }
                val newKf = if (exists) {
                    target.positionXKeyframes.filterNot { abs(it.time - time) < threshold }
                } else {
                    target.positionXKeyframes + PropertyKeyframe(id = randomUUID(), time = time, value = anim.positionX, easing = easing)
                }
                target.copy(positionXKeyframes = newKf.sortedBy { it.time })
            }
            "positiony" -> {
                val exists = target.positionYKeyframes.any { abs(it.time - time) < threshold }
                val newKf = if (exists) {
                    target.positionYKeyframes.filterNot { abs(it.time - time) < threshold }
                } else {
                    target.positionYKeyframes + PropertyKeyframe(id = randomUUID(), time = time, value = anim.positionY, easing = easing)
                }
                target.copy(positionYKeyframes = newKf.sortedBy { it.time })
            }
            "scalex" -> {
                val exists = target.scaleXKeyframes.any { abs(it.time - time) < threshold }
                val newKf = if (exists) {
                    target.scaleXKeyframes.filterNot { abs(it.time - time) < threshold }
                } else {
                    target.scaleXKeyframes + PropertyKeyframe(id = randomUUID(), time = time, value = anim.scaleX, easing = easing)
                }
                target.copy(scaleXKeyframes = newKf.sortedBy { it.time })
            }
            "scaley" -> {
                val exists = target.scaleYKeyframes.any { abs(it.time - time) < threshold }
                val newKf = if (exists) {
                    target.scaleYKeyframes.filterNot { abs(it.time - time) < threshold }
                } else {
                    target.scaleYKeyframes + PropertyKeyframe(id = randomUUID(), time = time, value = anim.scaleY, easing = easing)
                }
                target.copy(scaleYKeyframes = newKf.sortedBy { it.time })
            }
            "rotation" -> {
                val exists = target.rotationKeyframes.any { abs(it.time - time) < threshold }
                val newKf = if (exists) {
                    target.rotationKeyframes.filterNot { abs(it.time - time) < threshold }
                } else {
                    target.rotationKeyframes + PropertyKeyframe(id = randomUUID(), time = time, value = anim.rotation, easing = easing)
                }
                target.copy(rotationKeyframes = newKf.sortedBy { it.time })
            }
            "opacity" -> {
                val exists = target.opacityKeyframes.any { abs(it.time - time) < threshold }
                val newKf = if (exists) {
                    target.opacityKeyframes.filterNot { abs(it.time - time) < threshold }
                } else {
                    target.opacityKeyframes + PropertyKeyframe(id = randomUUID(), time = time, value = anim.opacity, easing = easing)
                }
                target.copy(opacityKeyframes = newKf.sortedBy { it.time })
            }
            "all" -> {
                val hasAnyAtTime = target.getAllKeyframeTimes().any { abs(it - time) < threshold }
                if (hasAnyAtTime) {
                    target.copy(
                        positionXKeyframes = target.positionXKeyframes.filterNot { abs(it.time - time) < threshold },
                        positionYKeyframes = target.positionYKeyframes.filterNot { abs(it.time - time) < threshold },
                        scaleXKeyframes = target.scaleXKeyframes.filterNot { abs(it.time - time) < threshold },
                        scaleYKeyframes = target.scaleYKeyframes.filterNot { abs(it.time - time) < threshold },
                        rotationKeyframes = target.rotationKeyframes.filterNot { abs(it.time - time) < threshold },
                        opacityKeyframes = target.opacityKeyframes.filterNot { abs(it.time - time) < threshold }
                    )
                } else {
                    target.copy(
                        positionXKeyframes = (target.positionXKeyframes + PropertyKeyframe(id = randomUUID(), time = time, value = anim.positionX, easing = easing)).sortedBy { it.time },
                        positionYKeyframes = (target.positionYKeyframes + PropertyKeyframe(id = randomUUID(), time = time, value = anim.positionY, easing = easing)).sortedBy { it.time },
                        scaleXKeyframes = (target.scaleXKeyframes + PropertyKeyframe(id = randomUUID(), time = time, value = anim.scaleX, easing = easing)).sortedBy { it.time },
                        scaleYKeyframes = (target.scaleYKeyframes + PropertyKeyframe(id = randomUUID(), time = time, value = anim.scaleY, easing = easing)).sortedBy { it.time },
                        rotationKeyframes = (target.rotationKeyframes + PropertyKeyframe(id = randomUUID(), time = time, value = anim.rotation, easing = easing)).sortedBy { it.time },
                        opacityKeyframes = (target.opacityKeyframes + PropertyKeyframe(id = randomUUID(), time = time, value = anim.opacity, easing = easing)).sortedBy { it.time }
                    )
                }
            }
            else -> target
        }

        invalidateLayerArea(target)
        viewModelScope.launch {
            repository.saveLayer(updated)
        }
    }

    fun setKeyframeEasing(layerId: String, easing: KeyframeEasing) {
        val target = layers.value.find { it.id == layerId } ?: return
        val time = currentTime.value
        val threshold = 0.06f
        pushHistory()

        fun updateEasingInTrack(list: List<PropertyKeyframe>): List<PropertyKeyframe> {
            val hasAtTime = list.any { abs(it.time - time) < threshold }
            return if (hasAtTime) {
                list.map { if (abs(it.time - time) < threshold) it.copy(easing = easing) else it }
            } else {
                list.map { it.copy(easing = easing) }
            }
        }

        val updated = target.copy(
            positionXKeyframes = updateEasingInTrack(target.positionXKeyframes),
            positionYKeyframes = updateEasingInTrack(target.positionYKeyframes),
            scaleXKeyframes = updateEasingInTrack(target.scaleXKeyframes),
            scaleYKeyframes = updateEasingInTrack(target.scaleYKeyframes),
            rotationKeyframes = updateEasingInTrack(target.rotationKeyframes),
            opacityKeyframes = updateEasingInTrack(target.opacityKeyframes)
        )
        invalidateLayerArea(target)
        viewModelScope.launch {
            repository.saveLayer(updated)
        }
    }

    fun clearAllKeyframes(layerId: String) {
        val target = layers.value.find { it.id == layerId } ?: return
        val anim = target.getAnimatedTransformAt(currentTime.value)
        pushHistory()
        val updated = target.copy(
            positionX = anim.positionX,
            positionY = anim.positionY,
            scaleX = anim.scaleX,
            scaleY = anim.scaleY,
            rotation = anim.rotation,
            opacity = anim.opacity,
            positionXKeyframes = emptyList(),
            positionYKeyframes = emptyList(),
            scaleXKeyframes = emptyList(),
            scaleYKeyframes = emptyList(),
            rotationKeyframes = emptyList(),
            opacityKeyframes = emptyList()
        )
        invalidateLayerArea(target)
        viewModelScope.launch {
            repository.saveLayer(updated)
        }
    }

    fun jumpToPreviousKeyframe() {
        val selected = layers.value.find { it.id == selectedLayerId.value } ?: return
        val allTimes = selected.getAllKeyframeTimes()
        val prev = allTimes.filter { it < currentTime.value - 0.05f }.maxOrNull()
        if (prev != null) {
            seekTo(prev)
        }
    }

    fun jumpToNextKeyframe() {
        val selected = layers.value.find { it.id == selectedLayerId.value } ?: return
        val allTimes = selected.getAllKeyframeTimes()
        val next = allTimes.filter { it > currentTime.value + 0.05f }.minOrNull()
        if (next != null) {
            seekTo(next)
        }
    }

    fun addEffectToLayer(layerId: String, type: EffectType) {
        val target = layers.value.find { it.id == layerId } ?: return
        pushHistory()
        val newEffect = LayerEffect(
            id = randomUUID(),
            type = type,
            isEnabled = true,
            intensity = when (type) {
                EffectType.RGB_SPLIT -> 0.4f
                EffectType.BLOOM_GLOW -> 0.5f
                EffectType.WAVE_WARP -> 0.35f
                EffectType.BULGE_PINCH -> 0.4f
                EffectType.DIRECTIONAL_BLUR -> 0.4f
                EffectType.RADIAL_ZOOM_BLUR -> 0.35f
                EffectType.GAUSSIAN_BLUR -> 0.5f
                EffectType.NEON_EDGE -> 0.6f
                EffectType.LIGHT_SWEEP -> 0.5f
                EffectType.FILM_GRAIN -> 0.35f
                EffectType.VIGNETTE -> 0.5f
                EffectType.GLITCH_SLICE -> 0.45f
                EffectType.PIXELATE -> 0.4f
                EffectType.MIRROR_TILE -> 0.5f
                EffectType.HUE_SHIFT -> 0.5f
                EffectType.FLASH_STROBE -> 0.5f
                EffectType.CAMERA_SHAKE -> 0.4f
            },
            parameter1 = when (type) {
                EffectType.WAVE_WARP -> 12f 
                EffectType.BULGE_PINCH -> 0.5f
                EffectType.DIRECTIONAL_BLUR -> 0f 
                EffectType.LIGHT_SWEEP -> 0.5f 
                EffectType.FLASH_STROBE -> 8f 
                EffectType.CAMERA_SHAKE -> 15f 
                else -> 0.5f
            },
            parameter2 = 0f
        )
        val updated = target.copy(effects = target.effects + newEffect)
        _layers.value = _layers.value.map { if (it.id == layerId) updated else it }
        invalidateLayerArea(target)
        viewModelScope.launch {
            repository.saveLayer(updated)
        }
    }

    fun removeEffectFromLayer(layerId: String, effectId: String) {
        val target = layers.value.find { it.id == layerId } ?: return
        pushHistory()
        val updated = target.copy(effects = target.effects.filter { it.id != effectId })
        _layers.value = _layers.value.map { if (it.id == layerId) updated else it }
        invalidateLayerArea(target)
        viewModelScope.launch {
            repository.saveLayer(updated)
        }
    }

    fun toggleEffectEnabled(layerId: String, effectId: String) {
        val target = layers.value.find { it.id == layerId } ?: return
        pushHistory()
        val updatedEffects = target.effects.map {
            if (it.id == effectId) it.copy(isEnabled = !it.isEnabled) else it
        }
        val updated = target.copy(effects = updatedEffects)
        _layers.value = _layers.value.map { if (it.id == layerId) updated else it }
        invalidateLayerArea(target)
        viewModelScope.launch {
            repository.saveLayer(updated)
        }
    }

    fun updateEffect(
        layerId: String,
        effectId: String,
        intensity: Float? = null,
        param1: Float? = null,
        param2: Float? = null,
        colorTint: Long? = null
    ) {
        if (historySnapshotBeforeEdit == null) {
            historySnapshotBeforeEdit = _layers.value
        }
        val target = layers.value.find { it.id == layerId } ?: return
        val time = _currentTime.value
        val threshold = 0.05f

        val updatedEffects = target.effects.map { fx ->
            if (fx.id != effectId) return@map fx

            var newIntensity = fx.intensity
            var newIntensityKfs = fx.intensityKeyframes
            if (intensity != null) {
                newIntensity = intensity
                if (newIntensityKfs.any { abs(it.time - time) < threshold }) {
                    newIntensityKfs = newIntensityKfs.map {
                        if (abs(it.time - time) < threshold) it.copy(value = intensity) else it
                    }
                }
            }

            var newParam1 = fx.parameter1
            var newParam1Kfs = fx.param1Keyframes
            if (param1 != null) {
                newParam1 = param1
                if (newParam1Kfs.any { abs(it.time - time) < threshold }) {
                    newParam1Kfs = newParam1Kfs.map {
                        if (abs(it.time - time) < threshold) it.copy(value = param1) else it
                    }
                }
            }

            var newParam2 = fx.parameter2
            var newParam2Kfs = fx.param2Keyframes
            if (param2 != null) {
                newParam2 = param2
                if (newParam2Kfs.any { abs(it.time - time) < threshold }) {
                    newParam2Kfs = newParam2Kfs.map {
                        if (abs(it.time - time) < threshold) it.copy(value = param2) else it
                    }
                }
            }

            fx.copy(
                intensity = newIntensity,
                intensityKeyframes = newIntensityKfs,
                parameter1 = newParam1,
                param1Keyframes = newParam1Kfs,
                parameter2 = newParam2,
                param2Keyframes = newParam2Kfs,
                colorTint = colorTint ?: fx.colorTint
            )
        }

        val updated = target.copy(effects = updatedEffects)
        _layers.value = _layers.value.map { if (it.id == layerId) updated else it }
        invalidateLayerArea(target)
        pendingSaveJob?.cancel()
        pendingSaveJob = viewModelScope.launch {
            delay(150L)
            val snapshot = historySnapshotBeforeEdit
            if (snapshot != null && snapshot != _layers.value) {
                undoStack.add(snapshot)
                if (undoStack.size > 50) {
                    undoStack.removeAt(0)
                }
                redoStack.clear()
                _canUndo.value = undoStack.isNotEmpty()
                _canRedo.value = false
                historySnapshotBeforeEdit = null
            }
            repository.saveLayer(updated)
        }
    }

    fun toggleEffectKeyframe(
        layerId: String,
        effectId: String,
        paramType: String,
        easing: KeyframeEasing = KeyframeEasing.EASE_IN_OUT
    ) {
        val target = layers.value.find { it.id == layerId } ?: return
        val time = _currentTime.value
        val threshold = 0.05f
        pushHistory()

        val updatedEffects = target.effects.map { fx ->
            if (fx.id != effectId) return@map fx

            when (paramType.lowercase()) {
                "intensity" -> {
                    val exists = fx.intensityKeyframes.any { abs(it.time - time) < threshold }
                    val currentVal = fx.getAnimatedIntensityAt(time)
                    val newKfs = if (exists) {
                        fx.intensityKeyframes.filterNot { abs(it.time - time) < threshold }
                    } else {
                        (fx.intensityKeyframes + PropertyKeyframe(id = randomUUID(), time = time, value = currentVal, easing = easing)).sortedBy { it.time }
                    }
                    fx.copy(intensityKeyframes = newKfs)
                }
                "param1" -> {
                    val exists = fx.param1Keyframes.any { abs(it.time - time) < threshold }
                    val currentVal = fx.getAnimatedParam1At(time)
                    val newKfs = if (exists) {
                        fx.param1Keyframes.filterNot { abs(it.time - time) < threshold }
                    } else {
                        (fx.param1Keyframes + PropertyKeyframe(id = randomUUID(), time = time, value = currentVal, easing = easing)).sortedBy { it.time }
                    }
                    fx.copy(param1Keyframes = newKfs)
                }
                "param2" -> {
                    val exists = fx.param2Keyframes.any { abs(it.time - time) < threshold }
                    val currentVal = fx.getAnimatedParam2At(time)
                    val newKfs = if (exists) {
                        fx.param2Keyframes.filterNot { abs(it.time - time) < threshold }
                    } else {
                        (fx.param2Keyframes + PropertyKeyframe(id = randomUUID(), time = time, value = currentVal, easing = easing)).sortedBy { it.time }
                    }
                    fx.copy(param2Keyframes = newKfs)
                }
                else -> fx
            }
        }

        val updated = target.copy(effects = updatedEffects)
        _layers.value = _layers.value.map { if (it.id == layerId) updated else it }
        invalidateLayerArea(target)
        viewModelScope.launch {
            repository.saveLayer(updated)
        }
    }

    override fun onCleared() {
        super.onCleared()
        pause()
        engine.release()
        audioPlayer.release()
        mediaProvider.clearCache()
        previewCache.clearProject(projectId)
    }
}
