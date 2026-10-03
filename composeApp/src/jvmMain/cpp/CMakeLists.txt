cmake_minimum_required(VERSION 3.18)
project(fusion_engine_win CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if (MSVC)
    string(REGEX REPLACE "/RTC(su|[1su])" "" CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS_DEBUG}")
    add_compile_options(/arch:AVX2 /W3)
endif()

find_package(JNI REQUIRED)

include_directories(${JNI_INCLUDE_DIRS})
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/../../commonNative/cpp)

add_library(fusion_engine SHARED fusion_engine_win.cpp)

if (WIN32)
    target_link_libraries(fusion_engine PRIVATE ${JNI_LIBRARIES} mfplat.lib mfreadwrite.lib mfuuid.lib propsys.lib)
else()
    target_link_libraries(fusion_engine PRIVATE ${JNI_LIBRARIES})
endif()
