#ifndef FUSION_ENGINE_CORE_H
#define FUSION_ENGINE_CORE_H

#include <cstdint>
#include <vector>
#include <memory>
#include <string>

namespace FusionCut {

enum class EngineStatus {
    OK = 0,
    ERROR_INVALID_PARAM = 1,
    ERROR_OUT_OF_MEMORY = 2,
    ERROR_GPU_CONTEXT_FAILED = 3,
    ERROR_DECODE_FAILED = 4,
    ERROR_UNSUPPORTED = 5
};

enum class QualityMode {
    FAST_SCRUB = 0,      // 50% downsampled preview for instant 60fps playhead dragging
    FULL_QUALITY = 1     // Full 100% resolution for playback & export
};

enum class LayerType {
    VIDEO = 0,
    IMAGE = 1,
    TEXT = 2,
    SHAPE = 3,
    AUDIO = 4,
    ADJUSTMENT = 5
};

enum class BlendMode {
    NORMAL = 0,
    MULTIPLY = 1,
    SCREEN = 2,
    OVERLAY = 3,
    ADD = 4
};

struct FrameBuffer {
    uint32_t width{0};
    uint32_t height{0};
    uint32_t stride{0};
    uint8_t* pixelData{nullptr}; // RGBA8888 premultiplied
    uint64_t gpuTextureHandle{0}; // EGLImage / OpenGL / Vulkan texture handle (if hardware accelerated)
};

struct ColorRGBA {
    float r{1.0f};
    float g{1.0f};
    float b{1.0f};
    float a{1.0f};
};

struct Transform3D {
    float posX{0.0f};
    float posY{0.0f};
    float posZ{0.0f};

    float scaleX{1.0f};
    float scaleY{1.0f};
    float scaleZ{1.0f};

    float rotX{0.0f};
    float rotY{0.0f};
    float rotZ{0.0f}; // 2D rotation

    float anchorX{0.5f};
    float anchorY{0.5f};
    float anchorZ{0.5f};

    float opacity{1.0f};
};

} // namespace FusionCut

#endif // FUSION_ENGINE_CORE_H
