#include <jni.h>
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <memory>
#include "../../commonNative/cpp/FusionEngineCore.h"
#include "../../commonNative/cpp/RenderContext.h"
#include "../../commonNative/cpp/IRenderPass.h"

#define LOG_TAG "FusionEngineAndroid"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace FusionCut;

class AndroidEngineInstance {
public:
    RenderContext context;
    PassPlaceholder3D pass3D;
    PassPlaceholderHeavyFX passHeavyFX;

    AndroidEngineInstance() {
        context.quality = QualityMode::FULL_QUALITY;
        LOGI("Android Hardware Engine Initialized with OpenGL ES 3.0 Context");
    }

    void renderFrameToBuffer(int width, int height, jint* pixels) {
        if (!pixels || width <= 0 || height <= 0) return;

        // OpenGL ES 3.0 / GLES Clear Pass
        glClearColor(0.04f, 0.05f, 0.08f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // Fill buffer with background obsidian color (0xFF0A0D14)
        uint32_t bgPixel = 0xFF0A0D14;
        size_t totalPixels = static_cast<size_t>(width) * height;
        for (size_t i = 0; i < totalPixels; ++i) {
            pixels[i] = static_cast<jint>(bgPixel);
        }

        // Execute 3D & Heavy FX Pass Graph Placeholders
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
Java_com_example_engine_AndroidFusionEngine_nRenderFrame(
    JNIEnv* env,
    jobject /*obj*/,
    jint width,
    jint height,
    jfloat timeSec,
    jintArray pixelArray
) {
    if (!g_AndroidEngine || !pixelArray) return;

    g_AndroidEngine->context.currentTimeSec = timeSec;
    g_AndroidEngine->context.targetWidth = width;
    g_AndroidEngine->context.targetHeight = height;

    jint* pixels = env->GetIntArrayElements(pixelArray, nullptr);
    if (pixels) {
        g_AndroidEngine->renderFrameToBuffer(width, height, pixels);
        env->ReleaseIntArrayElements(pixelArray, pixels, 0);
    }
}

JNIEXPORT void JNICALL
Java_com_example_engine_AndroidFusionEngine_nReleaseEngine(JNIEnv* /*env*/, jobject /*obj*/) {
    g_AndroidEngine.reset();
}

}
