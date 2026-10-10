#include <jni.h>
#ifdef _WIN32
#include <windows.h>
#include <immintrin.h> // AVX2 & SSE Intrinsics
#include <mfapi.h>
#include <mfidl.h>
#include <mfreadwrite.h>
#include <mferror.h>
#include <propvarutil.h>
#endif
#include <iostream>
#include <vector>
#include <memory>
#include <algorithm>
#include <thread>
#include <cmath>
#include <unordered_map>
#include <string>
#include "../../commonNative/cpp/FusionEngineCore.h"
#include "../../commonNative/cpp/RenderContext.h"
#include "../../commonNative/cpp/IRenderPass.h"
#include "../../commonNative/cpp/LayerNode.h"

using namespace FusionCut;

inline std::wstring cleanWindowsPath(const std::wstring& inPath) {
    std::wstring p = inPath;
    if (p.rfind(L"file:///", 0) == 0) p = p.substr(8);
    else if (p.rfind(L"file://", 0) == 0) p = p.substr(7);
    else if (p.rfind(L"file:/", 0) == 0) p = p.substr(6);

    while (!p.empty() && (p[0] == L'/' || p[0] == L'\\')) {
        p = p.substr(1);
    }

    for (auto& ch : p) {
        if (ch == L'/') ch = L'\\';
    }
    return p;
}

// ============================================================================
// Windows Media Foundation GPU Hardware Video Frame Decoder & MP4 Encoder
// ============================================================================
#ifdef _WIN32
class MFVideoReader {
private:
    IMFSourceReader* m_pReader{nullptr};
    int m_width{0};
    int m_height{0};
    std::wstring m_pathW;
    std::vector<uint32_t> m_currentFramePixels;
    double m_lastSeekSec{-1.0};

public:
    MFVideoReader() = default;

    ~MFVideoReader() {
        close();
    }

    void close() {
        if (m_pReader) {
            m_pReader->Release();
            m_pReader = nullptr;
        }
        m_width = 0;
        m_height = 0;
        m_currentFramePixels.clear();
        m_lastSeekSec = -1.0;
    }

    bool open(const std::wstring& pathW) {
        if (pathW.empty()) return false;
        std::wstring cleanP = cleanWindowsPath(pathW);

        if (m_pReader && m_pathW == cleanP) return true;
        close();

        m_pathW = cleanP;

        DWORD dwAttrib = GetFileAttributesW(cleanP.c_str());
        if (dwAttrib == INVALID_FILE_ATTRIBUTES || (dwAttrib & FILE_ATTRIBUTE_DIRECTORY)) {
            return false;
        }

        try {
            IMFAttributes* pAttributes = nullptr;
            HRESULT hr = MFCreateAttributes(&pAttributes, 2);
            if (FAILED(hr)) return false;

            pAttributes->SetUINT32(MF_READWRITE_ENABLE_HARDWARE_TRANSFORMS, TRUE);
            pAttributes->SetUINT32(MF_SOURCE_READER_ENABLE_VIDEO_PROCESSING, TRUE);

            hr = MFCreateSourceReaderFromURL(cleanP.c_str(), pAttributes, &m_pReader);
            pAttributes->Release();

            if (FAILED(hr) || !m_pReader) return false;

            IMFMediaType* pPartialType = nullptr;
            hr = MFCreateMediaType(&pPartialType);
            if (SUCCEEDED(hr)) {
                pPartialType->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Video);
                pPartialType->SetGUID(MF_MT_SUBTYPE, MFVideoFormat_RGB32);
                m_pReader->SetCurrentMediaType(MF_SOURCE_READER_FIRST_VIDEO_STREAM, NULL, pPartialType);
                pPartialType->Release();
            }

            IMFMediaType* pCurrentType = nullptr;
            hr = m_pReader->GetCurrentMediaType(MF_SOURCE_READER_FIRST_VIDEO_STREAM, &pCurrentType);
            if (SUCCEEDED(hr)) {
                UINT32 w = 0, h = 0;
                MFGetAttributeSize(pCurrentType, MF_MT_FRAME_SIZE, &w, &h);
                m_width = static_cast<int>(w);
                m_height = static_cast<int>(h);
                pCurrentType->Release();
            }

            if (m_width <= 0 || m_height <= 0) {
                m_width = 1280;
                m_height = 720;
            }

            m_currentFramePixels.resize(m_width * m_height, 0xFF141926);
            return true;
        } catch (...) {
            return false;
        }
    }

    bool seekAndReadFrame(double timeSec, std::vector<uint32_t>& outPixels, int& outW, int& outH) {
        if (!m_pReader) return false;

        try {
            if (std::abs(timeSec - m_lastSeekSec) > 0.033 || m_lastSeekSec < 0.0) {
                m_lastSeekSec = timeSec;

                m_pReader->Flush(MF_SOURCE_READER_FIRST_VIDEO_STREAM);

                PROPVARIANT varPosition;
                PropVariantInit(&varPosition);
                varPosition.vt = VT_I8;
                varPosition.hVal.QuadPart = static_cast<LONGLONG>(timeSec * 10000000.0);

                m_pReader->SetCurrentPosition(GUID_NULL, varPosition);
                PropVariantClear(&varPosition);

                DWORD flags = 0;
                LONGLONG timestamp = 0;
                IMFSample* pSample = nullptr;

                for (int attempt = 0; attempt < 20; ++attempt) {
                    flags = 0;
                    pSample = nullptr;
                    HRESULT hr = m_pReader->ReadSample(
                        MF_SOURCE_READER_FIRST_VIDEO_STREAM,
                        0,
                        NULL,
                        &flags,
                        &timestamp,
                        &pSample
                    );

                    if (FAILED(hr) || (flags & MF_SOURCE_READERF_ENDOFSTREAM)) {
                        if (pSample) pSample->Release();
                        pSample = nullptr;
                        break;
                    }

                    if (pSample != nullptr) {
                        break; // Got decoded video frame sample
                    }
                }

                if (pSample != nullptr) {
                    IMFMediaBuffer* pBuffer = nullptr;
                    HRESULT hr = pSample->ConvertToContiguousBuffer(&pBuffer);
                    if (SUCCEEDED(hr) && pBuffer) {
                        BYTE* pData = nullptr;
                        DWORD maxLen = 0, curLen = 0;
                        hr = pBuffer->Lock(&pData, &maxLen, &curLen);
                        if (SUCCEEDED(hr) && pData && curLen >= static_cast<DWORD>(m_width * m_height * 4)) {
                            const uint32_t* src = reinterpret_cast<const uint32_t*>(pData);
                            size_t total = static_cast<size_t>(m_width) * m_height;
                            for (size_t i = 0; i < total; ++i) {
                                uint32_t bgra = src[i];
                                uint32_t b = bgra & 0xFF;
                                uint32_t g = (bgra >> 8) & 0xFF;
                                uint32_t r = (bgra >> 16) & 0xFF;
                                uint32_t a = (bgra >> 24) & 0xFF;
                                if (a == 0) a = 0xFF;
                                m_currentFramePixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
                            }
                            pBuffer->Unlock();
                        }
                        pBuffer->Release();
                    }
                    pSample->Release();
                }
            }

            outW = m_width;
            outH = m_height;
            outPixels = m_currentFramePixels;
            return true;
        } catch (...) {
            return false;
        }
    }
};

class MFVideoEncoder {
private:
    IMFSinkWriter* m_pSinkWriter{nullptr};
    DWORD m_streamIndex{0};
    int m_width{0};
    int m_height{0};
    int m_fps{30};

public:
    MFVideoEncoder() = default;

    ~MFVideoEncoder() {
        close();
    }

    void close() {
        if (m_pSinkWriter) {
            m_pSinkWriter->Finalize();
            m_pSinkWriter->Release();
            m_pSinkWriter = nullptr;
        }
    }

    bool open(const std::wstring& outputPathW, int width, int height, int fps) {
        close();
        m_width = width;
        m_height = height;
        m_fps = fps > 0 ? fps : 30;

        try {
            IMFAttributes* pAttributes = nullptr;
            HRESULT hr = MFCreateAttributes(&pAttributes, 1);
            if (FAILED(hr)) return false;

            pAttributes->SetUINT32(MF_READWRITE_ENABLE_HARDWARE_TRANSFORMS, TRUE);

            hr = MFCreateSinkWriterFromURL(outputPathW.c_str(), NULL, pAttributes, &m_pSinkWriter);
            pAttributes->Release();

            if (FAILED(hr) || !m_pSinkWriter) return false;

            IMFMediaType* pMediaTypeOut = nullptr;
            hr = MFCreateMediaType(&pMediaTypeOut);
            if (FAILED(hr)) return false;

            pMediaTypeOut->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Video);
            pMediaTypeOut->SetGUID(MF_MT_SUBTYPE, MFVideoFormat_H264);
            pMediaTypeOut->SetUINT32(MF_MT_AVG_BITRATE, width * height * 6);
            MFSetAttributeSize(pMediaTypeOut, MF_MT_FRAME_SIZE, width, height);
            MFSetAttributeRatio(pMediaTypeOut, MF_MT_FRAME_RATE, m_fps, 1);
            MFSetAttributeRatio(pMediaTypeOut, MF_MT_PIXEL_ASPECT_RATIO, 1, 1);
            pMediaTypeOut->SetUINT32(MF_MT_INTERLACE_MODE, MFVideoInterlace_Progressive);

            hr = m_pSinkWriter->AddStream(pMediaTypeOut, &m_streamIndex);
            pMediaTypeOut->Release();
            if (FAILED(hr)) return false;

            IMFMediaType* pMediaTypeIn = nullptr;
            hr = MFCreateMediaType(&pMediaTypeIn);
            if (FAILED(hr)) return false;

            pMediaTypeIn->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Video);
            pMediaTypeIn->SetGUID(MF_MT_SUBTYPE, MFVideoFormat_RGB32);
            MFSetAttributeSize(pMediaTypeIn, MF_MT_FRAME_SIZE, width, height);
            MFSetAttributeRatio(pMediaTypeIn, MF_MT_FRAME_RATE, m_fps, 1);
            MFSetAttributeRatio(pMediaTypeIn, MF_MT_PIXEL_ASPECT_RATIO, 1, 1);
            pMediaTypeIn->SetUINT32(MF_MT_INTERLACE_MODE, MFVideoInterlace_Progressive);

            hr = m_pSinkWriter->SetInputMediaType(m_streamIndex, pMediaTypeIn, NULL);
            pMediaTypeIn->Release();
            if (FAILED(hr)) return false;

            hr = m_pSinkWriter->BeginWriting();
            return SUCCEEDED(hr);
        } catch (...) {
            return false;
        }
    }

    bool writeFrame(const uint32_t* argbPixels, int frameIndex) {
        if (!m_pSinkWriter || !argbPixels) return false;

        try {
            DWORD bufferSize = m_width * m_height * 4;
            IMFMediaBuffer* pBuffer = nullptr;

            HRESULT hr = MFCreateMemoryBuffer(bufferSize, &pBuffer);
            if (FAILED(hr) || !pBuffer) return false;

            BYTE* pData = nullptr;
            hr = pBuffer->Lock(&pData, NULL, NULL);
            if (SUCCEEDED(hr) && pData) {
                const uint32_t* src = argbPixels;
                uint32_t* dst = reinterpret_cast<uint32_t*>(pData);
                size_t total = static_cast<size_t>(m_width) * m_height;
                for (size_t i = 0; i < total; ++i) {
                    uint32_t argb = src[i];
                    uint32_t a = (argb >> 24) & 0xFF;
                    uint32_t r = (argb >> 16) & 0xFF;
                    uint32_t g = (argb >> 8) & 0xFF;
                    uint32_t b = argb & 0xFF;
                    dst[i] = (a << 24) | (r << 16) | (g << 8) | b;
                }
                pBuffer->Unlock();
                pBuffer->SetCurrentLength(bufferSize);
            }

            IMFSample* pSample = nullptr;
            hr = MFCreateSample(&pSample);
            if (SUCCEEDED(hr) && pSample) {
                pSample->AddBuffer(pBuffer);
                LONGLONG frameDuration = static_cast<LONGLONG>(10000000.0 / m_fps);
                LONGLONG frameTime = static_cast<LONGLONG>(frameIndex) * frameDuration;

                pSample->SetSampleTime(frameTime);
                pSample->SetSampleDuration(frameDuration);

                hr = m_pSinkWriter->WriteSample(m_streamIndex, pSample);
                pSample->Release();
            }

            pBuffer->Release();
            return SUCCEEDED(hr);
        } catch (...) {
            return false;
        }
    }
};
#endif

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
    std::wstring videoPathW;
};

class WindowsVideoEngineInstance {
public:
    RenderContext context;
    PassPlaceholder3D pass3D;
    PassPlaceholderHeavyFX passHeavyFX;

#ifdef _WIN32
    std::unordered_map<std::wstring, std::unique_ptr<MFVideoReader>> videoReaders;
#endif

    WindowsVideoEngineInstance() {
        context.quality = QualityMode::FULL_QUALITY;
#ifdef _WIN32
        MFStartup(MF_VERSION);
#endif
    }

    ~WindowsVideoEngineInstance() {
#ifdef _WIN32
        videoReaders.clear();
        MFShutdown();
#endif
    }

    void renderSceneToBuffer(
        int width, int height,
        int projW, int projH,
        uint32_t bgPixel,
        std::vector<RenderLayerParam>& layers,
        jint* pixels
    ) {
        if (!pixels || width <= 0 || height <= 0) return;

        size_t totalPixels = static_cast<size_t>(width) * height;

        // Step 1: Fast AVX2 SIMD background fill (0.05ms)
#ifdef _WIN32
        __m256i val256 = _mm256_set1_epi32(static_cast<int>(bgPixel));
        size_t vecCount = totalPixels / 8;
        __m256i* dstPtr = reinterpret_cast<__m256i*>(pixels);

        for (size_t i = 0; i < vecCount; ++i) {
            _mm256_storeu_si256(dstPtr + i, val256);
        }

        size_t remainder = totalPixels % 8;
        if (remainder > 0) {
            std::fill(pixels + (vecCount * 8), pixels + totalPixels, static_cast<jint>(bgPixel));
        }
#else
        std::fill(pixels, pixels + totalPixels, static_cast<jint>(bgPixel));
#endif

        // Step 2: Render Layer Nodes
        float scaleCanvasX = static_cast<float>(width) / (projW > 0 ? projW : 1920);
        float scaleCanvasY = static_cast<float>(height) / (projH > 0 ? projH : 1080);

        int centerX = width / 2;
        int centerY = height / 2;

        for (auto& layer : layers) {
            if (layer.opacity <= 0.01f) continue;

            // Decode Video Frame for Video Layers using Media Foundation
            if (layer.type == 2 && !layer.videoPathW.empty()) {
#ifdef _WIN32
                auto& reader = videoReaders[layer.videoPathW];
                if (!reader) {
                    reader = std::make_unique<MFVideoReader>();
                    if (!reader->open(layer.videoPathW)) {
                        reader.reset();
                    }
                }
                if (reader) {
                    reader->seekAndReadFrame(context.currentTimeSec, layer.imagePixels, layer.imageW, layer.imageH);
                }
#endif
            }

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
            // TYPE 1 & 2: IMAGE & VIDEO MEDIA LAYERS
            else if (layer.type == 1 || layer.type == 2) {
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
                    // Fallback Media Card Container
                    uint32_t mediaBorder = (layer.type == 2) ? 0xFF00F0FF : 0xFF00E599;
                    uint32_t mediaBg = 0xFF141926;
                    uint32_t playIconColor = 0xFF00F0FF;
                    int playTriangleSize = (std::min)(layerW, layerH) / 4;

                    for (int y = startY; y < endY; ++y) {
                        for (int x = startX; x < endX; ++x) {
                            if (x < startX + 2 || x >= endX - 2 || y < startY + 2 || y >= endY - 2) {
                                pixels[y * width + x] = static_cast<jint>(mediaBorder);
                            } else {
                                bool isPlayIcon = false;
                                if (layer.type == 2 && playTriangleSize > 4) {
                                    int relX = x - layerCenterX + playTriangleSize / 2;
                                    int relY = y - layerCenterY;
                                    if (relX >= 0 && relX <= playTriangleSize) {
                                        int halfHAtX = relX / 2;
                                        if (relY >= -halfHAtX && relY <= halfHAtX) {
                                            isPlayIcon = true;
                                        }
                                    }
                                }

                                if (isPlayIcon) {
                                    pixels[y * width + x] = static_cast<jint>(playIconColor);
                                } else {
                                    pixels[y * width + x] = static_cast<jint>(mediaBg);
                                }
                            }
                        }
                    }
                }
            }
            // TYPE 3: TEXT LAYERS
            else if (layer.type == 3) {
                uint32_t textColor = layer.fillColor;
                uint32_t textBg = 0xFF181F30;
                uint32_t textAccent = 0xFF00F0FF;
                int barHeight = (std::max)(2, layerH / 6);

                for (int y = startY; y < endY; ++y) {
                    for (int x = startX; x < endX; ++x) {
                        if (y >= endY - barHeight) {
                            pixels[y * width + x] = static_cast<jint>(textAccent);
                        } else if (y >= startY + (endY - startY) / 3 && y <= startY + (endY - startY) * 2 / 3) {
                            pixels[y * width + x] = static_cast<jint>(textColor);
                        } else {
                            pixels[y * width + x] = static_cast<jint>(textBg);
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

static std::unique_ptr<WindowsVideoEngineInstance> g_WindowsEngine = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_engine_WindowsFusionEngine_nInitEngine(JNIEnv* /*env*/, jobject /*obj*/) {
    g_WindowsEngine = std::make_unique<WindowsVideoEngineInstance>();
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_example_engine_WindowsFusionEngine_nRenderFrameScene(
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
    jfloatArray transforms, // 6 floats per layer: posX, posY, scaleX, scaleY, rotDeg, opacity
    jintArray colors,       // 2 ints per layer: fillColor, strokeColor
    jfloatArray dimensions, // 4 floats per layer: baseW, baseH, strokeW, cornerR
    jintArray imageWidths,  // 1 int per layer: imageW
    jintArray imageHeights, // 1 int per layer: imageH
    jobjectArray imagePixelArrays, // Array of jintArray for each layer's image/video texture
    jobjectArray videoUris, // Array of String for each layer's video path
    jintArray pixelArray
) {
    if (!g_WindowsEngine || !pixelArray || width <= 0 || height <= 0) return;

    g_WindowsEngine->context.currentTimeSec = timeSec;
    g_WindowsEngine->context.targetWidth = width;
    g_WindowsEngine->context.targetHeight = height;

    jint* pixels = env->GetIntArrayElements(pixelArray, nullptr);
    if (!pixels) return;

    jsize pixelCount = env->GetArrayLength(pixelArray);
    if (pixelCount < width * height) {
        env->ReleaseIntArrayElements(pixelArray, pixels, JNI_ABORT);
        return;
    }

    // Default background fill
    std::fill(pixels, pixels + (width * height), static_cast<jint>(bgPixel));

    // Null and bounds checks for layer arrays
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

                    // Image / Video texture pixels from Kotlin
                    if ((p.type == 1 || p.type == 2) && iWidths != nullptr && iHeights != nullptr && imagePixelArrays != nullptr) {
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

                    // Video file path string
                    if (p.type == 2 && videoUris != nullptr) {
                        jstring vUri = (jstring)env->GetObjectArrayElement(videoUris, i);
                        if (vUri != nullptr) {
                            const char* utfStr = env->GetStringUTFChars(vUri, nullptr);
                            if (utfStr != nullptr) {
                                std::string pathStr(utfStr);
                                std::wstring wPath(pathStr.begin(), pathStr.end());

                                if (wPath.rfind(L"file:///", 0) == 0) wPath = wPath.substr(8);
                                else if (wPath.rfind(L"file://", 0) == 0) wPath = wPath.substr(7);
                                else if (wPath.rfind(L"file:/", 0) == 0) wPath = wPath.substr(6);

                                while (!wPath.empty() && (wPath[0] == L'/' || wPath[0] == L'\\')) {
                                    wPath = wPath.substr(1);
                                }
                                for (auto& ch : wPath) { if (ch == L'/') ch = L'\\'; }

                                p.videoPathW = wPath;
                                env->ReleaseStringUTFChars(vUri, utfStr);
                            }
                            env->DeleteLocalRef(vUri);
                        }
                    }

                    layers.push_back(p);
                }

                g_WindowsEngine->renderSceneToBuffer(width, height, projW, projH, static_cast<uint32_t>(bgPixel), layers, pixels);

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

JNIEXPORT jboolean JNICALL
Java_com_example_engine_WindowsFusionEngine_nEncodeVideoFrameMP4(
    JNIEnv* env,
    jobject /*obj*/,
    jstring outputPath,
    jint width,
    jint height,
    jint fps,
    jint frameIndex,
    jintArray pixelArray,
    jboolean isFinalFrame
) {
#ifdef _WIN32
    static std::unique_ptr<MFVideoEncoder> s_encoder = nullptr;

    if (frameIndex == 0 || !s_encoder) {
        if (s_encoder) s_encoder->close();
        s_encoder = std::make_unique<MFVideoEncoder>();

        const char* utfPath = env->GetStringUTFChars(outputPath, nullptr);
        std::string pathStr(utfPath ? utfPath : "");
        if (utfPath) env->ReleaseStringUTFChars(outputPath, utfStr);

        std::wstring wPath(pathStr.begin(), pathStr.end());
        wPath = cleanWindowsPath(wPath);

        if (!s_encoder->open(wPath, width, height, fps)) {
            s_encoder.reset();
            return JNI_FALSE;
        }
    }

    if (s_encoder && pixelArray) {
        jint* pixels = env->GetIntArrayElements(pixelArray, nullptr);
        if (pixels) {
            s_encoder->writeFrame(reinterpret_cast<const uint32_t*>(pixels), frameIndex);
            env->ReleaseIntArrayElements(pixelArray, pixels, JNI_ABORT);
        }
    }

    if (isFinalFrame && s_encoder) {
        s_encoder->close();
        s_encoder.reset();
    }

    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

JNIEXPORT void JNICALL
Java_com_example_engine_WindowsFusionEngine_nReleaseEngine(JNIEnv* /*env*/, jobject /*obj*/) {
    g_WindowsEngine.reset();
}

}
