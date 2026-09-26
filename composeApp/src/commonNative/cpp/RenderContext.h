#ifndef FUSION_RENDER_CONTEXT_H
#define FUSION_RENDER_CONTEXT_H

#include "FusionEngineCore.h"

namespace FusionCut {

struct RenderContext {
    uint32_t targetWidth{1280};
    uint32_t targetHeight{720};
    float fps{60.0f};
    float currentTimeSec{0.0f};
    uint32_t frameIndex{0};

    QualityMode quality{QualityMode::FULL_QUALITY};
    ColorRGBA backgroundColor{0.04f, 0.05f, 0.08f, 1.0f}; // Default obsidian dark

    // Hardware handles
    void* nativeWindowHandle{nullptr};
    void* eglContextHandle{nullptr};

    float getScaleFactor() const {
        return (quality == QualityMode::FAST_SCRUB) ? 0.5f : 1.0f;
    }
};

} // namespace FusionCut

#endif // FUSION_RENDER_CONTEXT_H
