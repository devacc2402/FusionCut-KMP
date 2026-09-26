package com.example.model

import kotlinx.serialization.Serializable

enum class KeyframeEasing {
    LINEAR,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT
}

@Serializable
data class PropertyKeyframe(
    val id: String = "",
    val time: Float, // Absolute time in project seconds
    val value: Float,
    val easing: KeyframeEasing = KeyframeEasing.EASE_IN_OUT
)

data class LayerAnimatedTransform(
    val positionX: Float,
    val positionY: Float,
    val scaleX: Float,
    val scaleY: Float,
    val rotation: Float,
    val opacity: Float
)
