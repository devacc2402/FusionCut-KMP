#include <jni.h>
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <memory>
#include <vector>
#include <algorithm>
#include <thread>
#include <cmath>
#include "../../commonNative/cpp/FusionEngineCore.h"
#include "../../commonNative/cpp/RenderContext.h"
#include "../../commonNative/cpp/IRenderPass.h"
#include "../../commonNative/cpp/LayerNode.h"

#define LOG_TAG "FusionEngineAndroid"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace FusionCut;

struct RenderLayerParam {
    int type;      // 0=SHAPE, 1=IMAGE, 2=VIDEO, 3=TEXT, 4=SOLID
    int shapeType; // 0=RECT, 1=ROUNDED_RECT, 2=CIRCLE, 3=STAR, 4=TRIANGLE, 5=HEART, 6=HEXAGON, 7=CAPSULE
    float posX;
    float posY;
    float scaleX;
    float scaleY;
    float rotationDeg;
    float opacity;
    uint32_t fillColor;
    uint32_t strokeColor;
    float baseWidth;
    float baseHeight;
    float strokeWidth;
    float cornerRadius;
    int imageW{0};
    int imageH{0};
    std::vector<uint32_t> imagePixels;
};

class AndroidEngineInstance {
public:
    RenderContext context;
    PassPlaceholder3D pass3D;
    PassPlaceholderHeavyFX passHeavyFX;

    AndroidEngineInstance() {
        context.quality = QualityMode::FULL_QUALITY;
        LOGI("Android Engine Initialized");
    }

    void renderSceneToBuffer(
        int width, int height,
        int projW, int projH,
        uint32_t bgPixel,
        const std::vector<RenderLayerParam>& layers,
        jint* pixels
    ) {
        if (!pixels || width <= 0 || height <= 0) return;

        size_t totalPixels = static_cast<size_t>(width) * height;

        // Step 1: Background fill
        std::fill(pixels, pixels + totalPixels, static_cast<jint>(bgPixel));

        // Step 2: Render Layer Nodes
        float scaleCanvasX = static_cast<float>(width) / (projW > 0 ? projW : 1920);
        float scaleCanvasY = static_cast<float>(height) / (projH > 0 ? projH : 1080);

        int centerX = width / 2;
        int centerY = height / 2;

        for (const auto& layer : layers) {
            if (layer.opacity <= 0.01f) continue;

            int layerCenterX = centerX + static_cast<int>(layer.posX * scaleCanvasX);
            int layerCenterY = centerY + static_cast<int>(layer.posY * scaleCanvasY);

            float bW = (layer.type == 4) ? static_cast<float>(projW) : layer.baseWidth;
            float bH = (layer.type == 4) ? static_cast<float>(projH) : layer.baseHeight;

            int layerW = static_cast<int>((bW * scaleCanvasX) * layer.scaleX);
            int layerH = static_cast<int>((bH * scaleCanvasY) * layer.scaleY);

            if (layerW <= 0 || layerH <= 0) continue;

            int startX = (std::max)(0, layerCenterX - layerW / 2);
            int endX = (std::min)(width, layerCenterX + layerW / 2);
            int startY = (std::max)(0, layerCenterY - layerH / 2);
            int endY = (std::min)(height, layerCenterY + layerH / 2);

            uint32_t color = layer.fillColor;
            uint32_t stroke = layer.strokeColor;
            float strokeW = layer.strokeWidth * scaleCanvasX;

            // TYPE 0: SHAPE LAYERS (Vector Polygon Rasterizer)
            if (layer.type == 0) {
                // Circle (shapeType == 2)
                if (layer.shapeType == 2) {
                    float rx = layerW / 2.0f;
                    float ry = layerH / 2.0f;

                    for (int y = startY; y < endY; ++y) {
                        float dy = (y - layerCenterY) / ry;
                        for (int x = startX; x < endX; ++x) {
                            float dx = (x - layerCenterX) / rx;
                            float d2 = dx * dx + dy * dy;
                            if (d2 <= 1.0f) {
                                if (strokeW > 0.5f && d2 >= 0.82f) {
                                    pixels[y * width + x] = static_cast<jint>(stroke);
                                } else {
                                    pixels[y * width + x] = static_cast<jint>(color);
                                }
                            }
                        }
                    }
                }
                // Rounded Rect (shapeType == 1)
                else if (layer.shapeType == 1) {
                    float halfW = layerW / 2.0f;
                    float halfH = layerH / 2.0f;
                    float cornerR = (std::min)({layer.cornerRadius * scaleCanvasX, halfW, halfH});

                    for (int y = startY; y < endY; ++y) {
                        float dy = std::abs(y - layerCenterY);
                        for (int x = startX; x < endX; ++x) {
                            float dx = std::abs(x - layerCenterX);
                            bool inside = false;

                            if (dx <= halfW - cornerR || dy <= halfH - cornerR) {
                                inside = true;
                            } else {
                                float cx = dx - (halfW - cornerR);
                                float cy = dy - (halfH - cornerR);
                                inside = (cx * cx + cy * cy <= cornerR * cornerR);
                            }

                            if (inside) {
                                pixels[y * width + x] = static_cast<jint>(color);
                            }
                        }
                    }
                }
                // Triangle (shapeType == 4)
                else if (layer.shapeType == 4) {
                    float topX = static_cast<float>(layerCenterX);
                    float topY = static_cast<float>(layerCenterY - layerH / 2);
                    float leftX = static_cast<float>(layerCenterX - layerW / 2);
                    float rightX = static_cast<float>(layerCenterX + layerW / 2);
                    float bottomY = static_cast<float>(layerCenterY + layerH / 2);

                    for (int y = startY; y < endY; ++y) {
                        float progress = (y - topY) / (bottomY - topY);
                        if (progress < 0.0f || progress > 1.0f) continue;
                        float minX = topX + (leftX - topX) * progress;
                        float maxX = topX + (rightX - topX) * progress;

                        for (int x = startX; x < endX; ++x) {
                            if (x >= minX && x <= maxX) {
                                pixels[y * width + x] = static_cast<jint>(color);
                            }
                        }
                    }
                }
                // Star (shapeType == 3)
                else if (layer.shapeType == 3) {
                    float outerR = (std::min)(layerW, layerH) / 2.0f;
                    for (int y = startY; y < endY; ++y) {
                        float dy = static_cast<float>(y - layerCenterY);
                        for (int x = startX; x < endX; ++x) {
                            float dx = static_cast<float>(x - layerCenterX);
                            float dist = std::sqrt(dx * dx + dy * dy);
                            float angle = std::atan2(dy, dx);
                            float starR = outerR * (0.55f + 0.45f * std::cos(5.0f * angle));
                            if (dist <= starR) {
                                pixels[y * width + x] = static_cast<jint>(color);
                            }
                        }
                    }
                }
                // Heart (shapeType == 5)
                else if (layer.shapeType == 5) {
                    float rx = layerW / 2.0f;
                    float ry = layerH / 2.0f;
                    for (int y = startY; y < endY; ++y) {
                        float ny = -1.2f * (y - layerCenterY) / ry + 0.1f;
                        for (int x = startX; x < endX; ++x) {
                            float nx = 1.2f * (x - layerCenterX) / rx;
                            float term = nx * nx + ny * ny - 1.0f;
                            if (term * term * term - nx * nx * ny * ny * ny <= 0.0f) {
                                pixels[y * width + x] = static_cast<jint>(color);
                            }
                        }
                    }
                }
                // Hexagon (shapeType == 6)
                else if (layer.shapeType == 6) {
                    float rx = layerW / 2.0f;
                    float ry = layerH / 2.0f;
                    for (int y = startY; y < endY; ++y) {
                        float dy = std::abs((y - layerCenterY) / ry);
                        for (int x = startX; x < endX; ++x) {
                            float dx = std::abs((x - layerCenterX) / rx);
                            if (dx <= 1.0f && (dx * 0.5f + dy * 0.866f) <= 1.0f && dy <= 1.0f) {
                                pixels[y * width + x] = static_cast<jint>(color);
                            }
                        }
                    }
                }
                // Capsule (shapeType == 7)
                else if (layer.shapeType == 7) {
                    float halfW = layerW / 2.0f;
                    float halfH = layerH / 2.0f;
                    float capR = (std::min)(halfW, halfH);

                    for (int y = startY; y < endY; ++y) {
                        float dy = std::abs(y - layerCenterY);
                        for (int x = startX; x < endX; ++x) {
                            float dx = std::abs(x - layerCenterX);
                            bool inside = false;

                            if (dx <= halfW - capR) {
                                inside = (dy <= halfH);
                            } else {
                                float cx = dx - (halfW - capR);
                                inside = (cx * cx + dy * dy <= capR * capR);
                            }

                            if (inside) {
                                pixels[y * width + x] = static_cast<jint>(color);
                            }
                        }
                    }
                }
                // Rectangle Default (shapeType == 0)
                else {
                    for (int y = startY; y < endY; ++y) {
                        for (int x = startX; x < endX; ++x) {
                            pixels[y * width + x] = static_cast<jint>(color);
                        }
                    }
                }
            }
            // TYPE 1, 2, 3: IMAGE, VIDEO, TEXT BITMAP TEXTURE BLITTING
            else if (layer.type == 1 || layer.type == 2 || layer.type == 3) {
                if (!layer.imagePixels.empty() && layer.imageW > 0 && layer.imageH > 0) {
                    int texW = layer.imageW;
                    int texH = layer.imageH;

                    for (int y = startY; y < endY; ++y) {
                        float srcY = static_cast<float>(y - startY) / (endY - startY) * texH;
                        int texY = (std::min)(texH - 1, (std::max)(0, static_cast<int>(srcY)));

                        for (int x = startX; x < endX; ++x) {
                            float srcX = static_cast<float>(x - startX) / (endX - startX) * texW;
                            int texX = (std::min)(texW - 1, (std::max)(0, static_cast<int>(srcX)));

                            uint32_t srcPixel = layer.imagePixels[texY * texW + texX];
                            uint32_t srcA = (srcPixel >> 24) & 0xFF;

                            if (srcA > 0) {
                                if (srcA == 255 && layer.opacity >= 0.99f) {
                                    pixels[y * width + x] = static_cast<jint>(srcPixel);
                                } else {
                                    float alpha = (srcA / 255.0f) * layer.opacity;
                                    uint32_t dstPixel = static_cast<uint32_t>(pixels[y * width + x]);

                                    uint32_t srcR = (srcPixel >> 16) & 0xFF;
                                    uint32_t srcG = (srcPixel >> 8) & 0xFF;
                                    uint32_t srcB = srcPixel & 0xFF;

                                    uint32_t dstR = (dstPixel >> 16) & 0xFF;
                                    uint32_t dstG = (dstPixel >> 8) & 0xFF;
                                    uint32_t dstB = dstPixel & 0xFF;

                                    uint32_t outR = static_cast<uint32_t>(srcR * alpha + dstR * (1.0f - alpha));
                                    uint32_t outG = static_cast<uint32_t>(srcG * alpha + dstG * (1.0f - alpha));
                                    uint32_t outB = static_cast<uint32_t>(srcB * alpha + dstB * (1.0f - alpha));

                                    pixels[y * width + x] = static_cast<jint>(0xFF000000 | (outR << 16) | (outG << 8) | outB);
                                }
                            }
                        }
                    }
                } else {
                    // Fallback Container
                    for (int y = startY; y < endY; ++y) {
                        for (int x = startX; x < endX; ++x) {
                            pixels[y * width + x] = static_cast<jint>(color);
                        }
                    }
                }
            }
            // TYPE 4: SOLID COLOR PLATES
            else if (layer.type == 4) {
                for (int y = startY; y < endY; ++y) {
                    for (int x = startX; x < endX; ++x) {
                        pixels[y * width + x] = static_cast<jint>(color);
                    }
                }
            }
        }

        // Step 3: Execute Render Pass Graph Placeholders (3D & Heavy FX)
        FrameBuffer frameBuf;
        frameBuf.width = width;
        frameBuf.height = height;
        frameBuf.pixelData = reinterpret_cast<uint8_t*>(pixels);

        pass3D.execute(context, frameBuf, frameBuf);
        passHeavyFX.execute(context, frameBuf, frameBuf);
    }
};

static std::unique_ptr<AndroidEngineInstance> g_AndroidEngine = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_engine_AndroidFusionEngine_nInitEngine(JNIEnv* /*env*/, jobject /*obj*/) {
    g_AndroidEngine = std::make_unique<AndroidEngineInstance>();
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_example_engine_AndroidFusionEngine_nRenderFrameScene(
    JNIEnv* env,
    jobject /*obj*/,
    jint width,
    jint height,
    jint projW,
    jint projH,
    jfloat timeSec,
    jint bgPixel,
    jintArray layerTypes,
    jintArray shapeTypes,
    jfloatArray transforms,
    jintArray colors,
    jfloatArray dimensions,
    jintArray imageWidths,
    jintArray imageHeights,
    jobjectArray imagePixelArrays,
    jintArray pixelArray
) {
    if (!g_AndroidEngine || !pixelArray || width <= 0 || height <= 0) return;

    g_AndroidEngine->context.currentTimeSec = timeSec;
    g_AndroidEngine->context.targetWidth = width;
    g_AndroidEngine->context.targetHeight = height;

    jint* pixels = env->GetIntArrayElements(pixelArray, nullptr);
    if (!pixels) return;

    jsize pixelCount = env->GetArrayLength(pixelArray);
    if (pixelCount < width * height) {
        env->ReleaseIntArrayElements(pixelArray, pixels, JNI_ABORT);
        return;
    }

    std::fill(pixels, pixels + (width * height), static_cast<jint>(bgPixel));

    if (layerTypes && shapeTypes && transforms && colors && dimensions) {
        jsize layerCount = env->GetArrayLength(layerTypes);
        jsize shapeCount = env->GetArrayLength(shapeTypes);
        jsize transCount = env->GetArrayLength(transforms);
        jsize colorCount = env->GetArrayLength(colors);
        jsize dimCount   = env->GetArrayLength(dimensions);

        if (layerCount > 0 &&
            shapeCount >= layerCount &&
            transCount >= layerCount * 6 &&
            colorCount >= layerCount * 2 &&
            dimCount   >= layerCount * 4) {

            jint* lTypes = env->GetIntArrayElements(layerTypes, nullptr);
            jint* sTypes = env->GetIntArrayElements(shapeTypes, nullptr);
            jfloat* tForms = env->GetFloatArrayElements(transforms, nullptr);
            jint* cVals = env->GetIntArrayElements(colors, nullptr);
            jfloat* dVals = env->GetFloatArrayElements(dimensions, nullptr);
            jint* iWidths = (imageWidths != nullptr) ? env->GetIntArrayElements(imageWidths, nullptr) : nullptr;
            jint* iHeights = (imageHeights != nullptr) ? env->GetIntArrayElements(imageHeights, nullptr) : nullptr;

            if (lTypes && sTypes && tForms && cVals && dVals) {
                std::vector<RenderLayerParam> layers;
                layers.reserve(layerCount);

                for (int i = 0; i < layerCount; ++i) {
                    RenderLayerParam p;
                    p.type = lTypes[i];
                    p.shapeType = sTypes[i];
                    p.posX = tForms[i * 6 + 0];
                    p.posY = tForms[i * 6 + 1];
                    p.scaleX = tForms[i * 6 + 2];
                    p.scaleY = tForms[i * 6 + 3];
                    p.rotationDeg = tForms[i * 6 + 4];
                    p.opacity = tForms[i * 6 + 5];
                    p.fillColor = static_cast<uint32_t>(cVals[i * 2 + 0]);
                    p.strokeColor = static_cast<uint32_t>(cVals[i * 2 + 1]);
                    p.baseWidth = dVals[i * 4 + 0];
                    p.baseHeight = dVals[i * 4 + 1];
                    p.strokeWidth = dVals[i * 4 + 2];
                    p.cornerRadius = dVals[i * 4 + 3];

                    if (iWidths != nullptr && iHeights != nullptr && imagePixelArrays != nullptr) {
                        p.imageW = iWidths[i];
                        p.imageH = iHeights[i];
                        if (p.imageW > 0 && p.imageH > 0) {
                            jintArray imgArr = (jintArray)env->GetObjectArrayElement(imagePixelArrays, i);
                            if (imgArr != nullptr) {
                                jsize len = env->GetArrayLength(imgArr);
                                if (len >= p.imageW * p.imageH) {
                                    jint* iPix = env->GetIntArrayElements(imgArr, nullptr);
                                    p.imagePixels.resize(p.imageW * p.imageH);
                                    for (int k = 0; k < p.imageW * p.imageH; ++k) {
                                        p.imagePixels[k] = static_cast<uint32_t>(iPix[k]);
                                    }
                                    env->ReleaseIntArrayElements(imgArr, iPix, JNI_ABORT);
                                }
                                env->DeleteLocalRef(imgArr);
                            }
                        }
                    }

                    layers.push_back(p);
                }

                g_AndroidEngine->renderSceneToBuffer(width, height, projW, projH, static_cast<uint32_t>(bgPixel), layers, pixels);

                env->ReleaseIntArrayElements(layerTypes, lTypes, JNI_ABORT);
                env->ReleaseIntArrayElements(shapeTypes, sTypes, JNI_ABORT);
                env->ReleaseFloatArrayElements(transforms, tForms, JNI_ABORT);
                env->ReleaseIntArrayElements(colors, cVals, JNI_ABORT);
                env->ReleaseFloatArrayElements(dimensions, dVals, JNI_ABORT);
                if (iWidths != nullptr) env->ReleaseIntArrayElements(imageWidths, iWidths, JNI_ABORT);
                if (iHeights != nullptr) env->ReleaseIntArrayElements(imageHeights, iHeights, JNI_ABORT);
            }
        }
    }

    env->ReleaseIntArrayElements(pixelArray, pixels, 0);
}

JNIEXPORT void JNICALL
Java_com_example_engine_AndroidFusionEngine_nReleaseEngine(JNIEnv* /*env*/, jobject /*obj*/) {
    g_AndroidEngine.reset();
}

}
