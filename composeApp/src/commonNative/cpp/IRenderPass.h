#ifndef FUSION_I_RENDER_PASS_H
#define FUSION_I_RENDER_PASS_H

#include "FusionEngineCore.h"
#include "RenderContext.h"

namespace FusionCut {

class IRenderPass {
public:
    virtual ~IRenderPass() = default;

    virtual const char* getName() const = 0;
    virtual bool isEnabled() const = 0;
    virtual void setEnabled(bool enabled) = 0;

    virtual EngineStatus prepare(const RenderContext& context) = 0;
    virtual EngineStatus execute(const RenderContext& context, FrameBuffer& inputBuffer, FrameBuffer& outputBuffer) = 0;
};

// ============================================================================
// PLACEHOLDER: 3D Geometry & Perspective Camera Pass (For Future Expansion)
// ============================================================================
class PassPlaceholder3D : public IRenderPass {
private:
    bool enabled{false};
public:
    const char* getName() const override { return "3D_Geometry_Perspective_Pass"; }
    bool isEnabled() const override { return enabled; }
    void setEnabled(bool e) override { enabled = e; }

    EngineStatus prepare(const RenderContext& /*context*/) override {
        // Reserved for future 3D Camera / Mesh VBO preparation
        return EngineStatus::OK;
    }

    EngineStatus execute(const RenderContext& /*context*/, FrameBuffer& /*input*/, FrameBuffer& /*output*/) override {
        if (!enabled) return EngineStatus::OK;
        // Reserved for future Perspective Camera, Mesh Rendering, Depth Buffer, PBR Shaders
        return EngineStatus::OK;
    }
};

// ============================================================================
// PLACEHOLDER: Heavy GPU Effects Pass (Gaussian Blur, Chroma Key, LUTs)
// ============================================================================
class PassPlaceholderHeavyFX : public IRenderPass {
private:
    bool enabled{false};
    float blurRadius{0.0f};
public:
    const char* getName() const override { return "Heavy_GPU_Effects_Pass"; }
    bool isEnabled() const override { return enabled; }
    void setEnabled(bool e) override { enabled = e; }

    void setBlurRadius(float radius) { blurRadius = radius; }

    EngineStatus prepare(const RenderContext& /*context*/) override {
        // Reserved for separable 2-pass compute/fragment shader blur weight allocation
        return EngineStatus::OK;
    }

    EngineStatus execute(const RenderContext& /*context*/, FrameBuffer& /*input*/, FrameBuffer& /*output*/) override {
        if (!enabled || blurRadius <= 0.0f) return EngineStatus::OK;
        // Reserved for GPU Compute / Fragment Shader Gaussian Blur & Chroma Key passes
        return EngineStatus::OK;
    }
};

} // namespace FusionCut

#endif // FUSION_I_RENDER_PASS_H
