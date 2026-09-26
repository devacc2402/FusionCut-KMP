cmake_minimum_required(VERSION 3.18)
project(fusion_engine_win CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if (MSVC)
    add_compile_options(/O2 /arch:AVX2 /W4)
endif()

find_package(JNI REQUIRED)

include_directories(${JNI_INCLUDE_DIRS})
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/../../commonNative/cpp)

add_library(fusion_engine SHARED fusion_engine_win.cpp)

target_link_libraries(fusion_engine PRIVATE ${JNI_LIBRARIES})
