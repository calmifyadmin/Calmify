cmake_minimum_required(VERSION 3.18)
project(calmify_audio LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Fetch Oboe (Google's C++ audio library for Android)
include(FetchContent)
FetchContent_Declare(
    oboe
    GIT_REPOSITORY https://github.com/google/oboe.git
    GIT_TAG        1.9.0
    GIT_SHALLOW    TRUE
)
FetchContent_MakeAvailable(oboe)

# Main native audio library
add_library(calmify_audio SHARED
    jni_bridge.cpp
    AudioBridge.cpp
    OboePlaybackEngine.cpp
    OboeRecordingEngine.cpp
    SoftwareAecProcessor.cpp
)

target_include_directories(calmify_audio PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${oboe_SOURCE_DIR}/include
)

target_link_libraries(calmify_audio
    oboe
    android
    log
)

# Optimize for release builds
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    target_compile_options(calmify_audio PRIVATE -O2 -fvisibility=hidden)
endif()
