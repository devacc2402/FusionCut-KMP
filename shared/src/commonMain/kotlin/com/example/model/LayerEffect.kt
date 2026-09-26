package com.example.model

import kotlinx.serialization.Serializable

@Serializable
data class LayerEffect(
    val id: String = "",
    val type: EffectType = EffectType.RGB_SPLIT,
    val isEnabled: Boolean = true,
    val intensity: Float = 0.5f,      // General intensity (0..1 or scaled)
    val parameter1: Float = 0.5f,     // e.g. radius / distance / wavelength
    val parameter2: Float = 0.0f,     // e.g. angle / phase / frequency
    val colorTint: Long = 0xFF00F0FF, // Optional tint
    val intensityKeyframes: List<PropertyKeyframe> = emptyList(),
    val param1Keyframes: List<PropertyKeyframe> = emptyList(),
    val param2Keyframes: List<PropertyKeyframe> = emptyList()
) {
    fun getAnimatedIntensityAt(time: Float): Float {
        if (intensityKeyframes.isEmpty()) return intensity
        return evaluateKeyframeList(intensityKeyframes, intensity, time).coerceIn(0f, 1f)
    }

    fun getAnimatedParam1At(time: Float): Float {
        if (param1Keyframes.isEmpty()) return parameter1
        return evaluateKeyframeList(param1Keyframes, parameter1, time)
    }

    fun getAnimatedParam2At(time: Float): Float {
        if (param2Keyframes.isEmpty()) return parameter2
        return evaluateKeyframeList(param2Keyframes, parameter2, time)
    }

    private fun evaluateKeyframeList(
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
        val progress = ((time - k1.time) / (k2.time - k1.time)).coerceIn(0f, 1f)
        val eased = when (k1.easing) {
            KeyframeEasing.LINEAR -> progress
            KeyframeEasing.EASE_IN -> progress * progress
            KeyframeEasing.EASE_OUT -> 1f - (1f - progress) * (1f - progress)
            KeyframeEasing.EASE_IN_OUT -> progress * progress * (3f - 2f * progress)
        }
        return k1.value + (k2.value - k1.value) * eased
    }
}
