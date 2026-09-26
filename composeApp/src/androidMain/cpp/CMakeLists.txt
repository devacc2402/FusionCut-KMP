cmake_minimum_required(VERSION 3.22.1)
project("fusion_engine_android")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include_directories(${CMAKE_CURRENT_SOURCE_DIR}/../../commonNative/cpp)

add_library(
    fusion_engine
    SHARED
    fusion_engine_android.cpp
)

find_library(log-lib log)
find_library(gles3-lib GLESv3)
find_library(egl-lib EGL)
find_library(android-lib android)

target_link_libraries(
    fusion_engine
    ${log-lib}
    ${gles3-lib}
    ${egl-lib}
    ${android-lib}
)
