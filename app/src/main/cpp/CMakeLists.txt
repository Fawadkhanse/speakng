cmake_minimum_required(VERSION 3.22.1)
project(SpeakNG VERSION 1.0)

# ═══════════════════════════════════════════════════════════════
#  PHASE 1 — STUB MODE (current)
#  Builds without libespeak-ng.so so the app compiles and runs.
#  The UI works fully, speak button simulates a 1-second delay.
#
#  PHASE 2 — REAL ENGINE (later)
#  When you have libespeak-ng.so:
#    1. Comment out the stub section below
#    2. Uncomment the "REAL ENGINE" section
#    3. Replace espeak_bridge_stub.cpp with espeak_bridge.cpp + audio_player.cpp
# ═══════════════════════════════════════════════════════════════

# ── STUB: compiles without any eSpeak files ──────────────────
add_library(speakng-jni SHARED
        espeak_bridge_stub.cpp
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(speakng-jni
        ${log-lib}
        ${android-lib}
)
# ─────────────────────────────────────────────────────────────


# ══ REAL ENGINE — uncomment this entire block for Phase 2 ═════
#
# add_library(espeak-ng SHARED IMPORTED)
# set_target_properties(espeak-ng PROPERTIES
#     IMPORTED_LOCATION
#     ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libespeak-ng.so
# )
#
# add_library(speakng-jni SHARED
#     espeak_bridge.cpp
#     audio_player.cpp
# )
#
# target_include_directories(speakng-jni PRIVATE
#     ${CMAKE_SOURCE_DIR}/include
# )
#
# find_library(log-lib log)
# find_library(opensl-lib OpenSLES)
# find_library(android-lib android)
#
# target_link_libraries(speakng-jni
#     espeak-ng
#     ${log-lib}
#     ${opensl-lib}
#     ${android-lib}
# )
# ═════════════════════════════════════════════════════════════

set_target_properties(speakng-jni PROPERTIES
        CXX_STANDARD 17
        CXX_STANDARD_REQUIRED ON
)
