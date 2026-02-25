cmake_minimum_required(VERSION 3.22.1)
project(SpeakNG VERSION 1.0)

# ─────────────────────────────────────────────
#  1. Import pre-built eSpeak NG shared library
#     Place libespeak-ng.so in:
#     app/src/main/jniLibs/<ABI>/libespeak-ng.so
# ─────────────────────────────────────────────
add_library(espeak-ng SHARED IMPORTED)
set_target_properties(espeak-ng PROPERTIES
    IMPORTED_LOCATION
    ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libespeak-ng.so
)

# ─────────────────────────────────────────────
#  2. Our JNI bridge + audio player
# ─────────────────────────────────────────────
add_library(
    speakng-jni
    SHARED
    espeak_bridge.cpp
    audio_player.cpp
)

# Include headers for eSpeak NG
target_include_directories(speakng-jni PRIVATE
    ${CMAKE_SOURCE_DIR}/include
)

# ─────────────────────────────────────────────
#  3. Link against Android system libraries
# ─────────────────────────────────────────────
find_library(log-lib log)
find_library(opensl-lib OpenSLES)
find_library(android-lib android)

target_link_libraries(
    speakng-jni
    espeak-ng      # eSpeak NG engine
    ${log-lib}     # Android logcat
    ${opensl-lib}  # Audio output via OpenSL ES
    ${android-lib} # AAssetManager for data files
)

# C++17 standard
set_target_properties(speakng-jni PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
)
