#ifndef FUSION_LAYER_NODE_H
#define FUSION_LAYER_NODE_H

#include "FusionEngineCore.h"
#include <string>
#include <vector>

namespace FusionCut {

struct KeyframePoint {
    float timeSec{0.0f};
    float value{0.0f};
    // Bezier control handles
    float handleOutX{0.33f};
    float handleOutY{0.33f};
    float handleInX{0.67f};
    float handleInY{0.67f};
};

class LayerNode {
public:
    int64_t id{0};
    std::string name;
    LayerType type{LayerType::IMAGE};
    bool isVisible{true};
    bool isMuted{false};

    float startTimeSec{0.0f};
    float durationSec{5.0f};

    Transform3D transform;
    BlendMode blendMode{BlendMode::NORMAL};

    std::string sourceUri;

    bool isActiveAt(float timeSec) const {
        return isVisible && (timeSec >= startTimeSec) && (timeSec <= startTimeSec + durationSec);
    }
};

} // namespace FusionCut

#endif // FUSION_LAYER_NODE_H
