package com.example.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

import kotlinx.serialization.Serializable

@Entity(
    tableName = "layers",
    foreignKeys = [
        ForeignKey(
            entity = Project::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
@Serializable
data class Layer(
    @PrimaryKey
    val id: String, // = UUID.randomUUID().toString(),
    val projectId: Long = 0,
    val name: String = "Layer",
    val type: LayerType = LayerType.SHAPE,
    val orderIndex: Int = 0, // Higher index renders on top
    val startTime: Float = 0.0f, // in seconds
    val endTime: Float = 5.0f,   // in seconds
    val isVisible: Boolean = true,
    val isLocked: Boolean = false,

    // Transform properties
    val positionX: Float = 0.0f, // Offset from project center (px)
    val positionY: Float = 0.0f, // Offset from project center (px)
    val scaleX: Float = 1.0f,
    val scaleY: Float = 1.0f,
    val rotation: Float = 0.0f,  // Degrees (-360 to 360)
    val opacity: Float = 1.0f,   // 0.0 to 1.0
    val anchorX: Float = 0.5f,   // 0.5 = center
    val anchorY: Float = 0.5f,   // 0.5 = center
    val blendMode: String = "NORMAL",
    val isFlippedH: Boolean = false,
    val isFlippedV: Boolean = false,

    // Shape properties
    val shapeType: ShapeType = ShapeType.ROUNDED_RECT,
    val fillColor: Long = 0xFF00F0FF, // Neon Cyan default
    val strokeColor: Long = 0xFFFFFFFF,
    val strokeWidth: Float = 0.0f,
    val cornerRadius: Float = 24.0f,
    val baseWidth: Float = 300.0f,
    val baseHeight: Float = 300.0f,

    // Media properties (Image / Video)
    val mediaUri: String? = null,
    val mediaFit: MediaFitMode = MediaFitMode.FIT,
    val volume: Float = 1.0f,

    // Text properties
    val text: String = "Fusion Cut",
    val fontSize: Float = 54.0f,
    val textColor: Long = 0xFFFFFFFF,
    val isBold: Boolean = true,
    val isItalic: Boolean = false,
    val textAlignment: String = "CENTER",

    // Keyframe Animation Tracks
    val positionXKeyframes: List<PropertyKeyframe> = emptyList(),
    val positionYKeyframes: List<PropertyKeyframe> = emptyList(),
    val scaleXKeyframes: List<PropertyKeyframe> = emptyList(),
    val scaleYKeyframes: List<PropertyKeyframe> = emptyList(),
    val rotationKeyframes: List<PropertyKeyframe> = emptyList(),
    val opacityKeyframes: List<PropertyKeyframe> = emptyList(),

    // Visual FX & Video Distortion/Glow Pipeline
    val effects: List<LayerEffect> = emptyList()
) {
    fun isActiveAt(timeSeconds: Float): Boolean {
        return timeSeconds >= (startTime - 0.0001f) && timeSeconds <= (endTime + 0.0001f)
    }

    fun getAnimatedTransformAt(timeSeconds: Float): LayerAnimatedTransform {
        return LayerAnimatedTransform(
            positionX = evaluateProperty(positionXKeyframes, positionX, timeSeconds),
            positionY = evaluateProperty(positionYKeyframes, positionY, timeSeconds),
            scaleX = evaluateProperty(scaleXKeyframes, scaleX, timeSeconds),
            scaleY = evaluateProperty(scaleYKeyframes, scaleY, timeSeconds),
            rotation = evaluateProperty(rotationKeyframes, rotation, timeSeconds),
            opacity = evaluateProperty(opacityKeyframes, opacity, timeSeconds).coerceIn(0f, 1f)
        )
    }

    fun hasKeyframes(): Boolean {
        return positionXKeyframes.isNotEmpty() ||
                positionYKeyframes.isNotEmpty() ||
                scaleXKeyframes.isNotEmpty() ||
                scaleYKeyframes.isNotEmpty() ||
                rotationKeyframes.isNotEmpty() ||
                opacityKeyframes.isNotEmpty() ||
                effects.any { it.intensityKeyframes.isNotEmpty() || it.param1Keyframes.isNotEmpty() || it.param2Keyframes.isNotEmpty() }
    }

    fun getAllKeyframeTimes(): List<Float> {
        val set = mutableSetOf<Float>()
        positionXKeyframes.forEach { set.add(it.time) }
        positionYKeyframes.forEach { set.add(it.time) }
        scaleXKeyframes.forEach { set.add(it.time) }
        scaleYKeyframes.forEach { set.add(it.time) }
        rotationKeyframes.forEach { set.add(it.time) }
        opacityKeyframes.forEach { set.add(it.time) }
        effects.forEach { fx ->
            fx.intensityKeyframes.forEach { set.add(it.time) }
            fx.param1Keyframes.forEach { set.add(it.time) }
            fx.param2Keyframes.forEach { set.add(it.time) }
        }
        return set.sorted()
    }

    fun hasKeyframeAt(time: Float, threshold: Float = 0.05f): Boolean {
        return getAllKeyframeTimes().any { kotlin.math.abs(it - time) < threshold }
    }

    private fun evaluateProperty(
        keyframes: List<PropertyKeyframe>,
        defaultValue: Float,
        time: Float
    ): Float {
        if (keyframes.isEmpty()) return defaultValue
        if (keyframes.size == 1) return keyframes.first().value

        val sorted = keyframes.sortedBy { it.time }
        if (time <= sorted.first().time) return sorted.first().value
        if (time >= sorted.last().time) return sorted.last().value

        var k1 = sorted.first()
        var k2 = sorted.last()
        for (i in 0 until sorted.size - 1) {
            if (time >= sorted[i].time && time <= sorted[i + 1].time) {
                k1 = sorted[i]
                k2 = sorted[i + 1]
                break
            }
        }

        if (k2.time <= k1.time) return k1.value

        val linearProgress = ((time - k1.time) / (k2.time - k1.time)).coerceIn(0f, 1f)
        val eased = when (k1.easing) {
            KeyframeEasing.LINEAR -> linearProgress
            KeyframeEasing.EASE_IN -> linearProgress * linearProgress
            KeyframeEasing.EASE_OUT -> 1f - (1f - linearProgress) * (1f - linearProgress)
            KeyframeEasing.EASE_IN_OUT -> {
                // Smooth step
                linearProgress * linearProgress * (3f - 2f * linearProgress)
            }
        }
        return k1.value + (k2.value - k1.value) * eased
    }
}
