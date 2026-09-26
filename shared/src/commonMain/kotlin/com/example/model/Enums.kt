package com.example.model

enum class LayerType {
    SHAPE,
    IMAGE,
    VIDEO,
    TEXT,
    SOLID,
    AUDIO
}

enum class ShapeType {
    RECTANGLE,
    ROUNDED_RECT,
    CIRCLE,
    STAR,
    TRIANGLE,
    HEART,
    POLYGON,
    CAPSULE
}

enum class MediaFitMode {
    FIT,
    FILL,
    STRETCH
}

enum class ProjectAspectRatio(
    val label: String,
    val widthRatio: Int,
    val heightRatio: Int,
    val defaultWidth: Int,
    val defaultHeight: Int
) {
    RATIO_16_9("16:9 Landscape", 16, 9, 1920, 1080),
    RATIO_9_16("9:16 Shorts/Reels", 9, 16, 1080, 1920),
    RATIO_1_1("1:1 Square", 1, 1, 1080, 1080),
    RATIO_4_5("4:5 Portrait", 4, 5, 1080, 1350),
    RATIO_4_3("4:3 Standard", 4, 3, 1440, 1080),
    RATIO_21_9("21:9 Cinema", 21, 9, 2560, 1080);

    val ratio: Float get() = widthRatio.toFloat() / heightRatio.toFloat()
}

enum class EffectCategory(val label: String) {
    DISTORTION("Distortion"),
    GLOW_BLUR("Glow & Blur"),
    COLOR_STYLE("Color & Style")
}

enum class EffectType(val label: String, val category: EffectCategory, val description: String) {
    // Distortion
    RGB_SPLIT("RGB Split", EffectCategory.DISTORTION, "Chromatic aberration & channel shift"),
    WAVE_WARP("Wave Warp", EffectCategory.DISTORTION, "Sine wave ripple & liquid distortion"),
    BULGE_PINCH("Bulge & Pinch", EffectCategory.DISTORTION, "Spherical lens bulge & pinch"),
    PIXELATE("Pixelate", EffectCategory.DISTORTION, "Retro 8-bit mosaic pixelation"),
    GLITCH_SLICE("Glitch Slice", EffectCategory.DISTORTION, "Cyberpunk block scanline displacement"),
    MIRROR_TILE("Mirror Tile", EffectCategory.DISTORTION, "4-way kaleidoscopic mirror symmetry"),

    // Glow & Blur
    BLOOM_GLOW("Bloom Glow", EffectCategory.GLOW_BLUR, "Soft luminous radiance & neon bloom"),
    DIRECTIONAL_BLUR("Motion Blur", EffectCategory.GLOW_BLUR, "High-speed directional motion blur"),
    RADIAL_ZOOM_BLUR("Zoom Blur", EffectCategory.GLOW_BLUR, "Explosive radial zoom blur"),
    GAUSSIAN_BLUR("Gaussian Blur", EffectCategory.GLOW_BLUR, "Silky smooth cinematic depth blur"),
    NEON_EDGE("Neon Edge", EffectCategory.GLOW_BLUR, "Glowing cyber edge & outline detection"),
    LIGHT_SWEEP("Light Sweep", EffectCategory.GLOW_BLUR, "Anamorphic flare & light beam streak"),

    // Color & Style
    FILM_GRAIN("Film Grain", EffectCategory.COLOR_STYLE, "Organic 35mm cinema noise & texture"),
    VIGNETTE("Vignette", EffectCategory.COLOR_STYLE, "Darkened cinematic vignette framing"),
    HUE_SHIFT("Hue Shift", EffectCategory.COLOR_STYLE, "Psychedelic color spectrum rotation"),
    FLASH_STROBE("Flash Strobe", EffectCategory.COLOR_STYLE, "Pulsing strobe light & flash impact"),
    CAMERA_SHAKE("Camera Shake", EffectCategory.COLOR_STYLE, "Dynamic handheld camera shake pulse")
}

data class MediaMetadataInfo(
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val rotation: Int,
    val isVideo: Boolean
)
