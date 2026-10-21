# Call_Record: native build of the whisper.cpp speech recognition engine.
#
# The whisper.cpp sources are downloaded and checksum verified by the Gradle build
# (see app/build.gradle.kts) and extracted into the folder passed as WHISPER_SOURCE_DIR.

cmake_minimum_required(VERSION 3.22.1)
project(callrecord_whisper C CXX)

if(NOT DEFINED WHISPER_SOURCE_DIR)
    message(FATAL_ERROR "WHISPER_SOURCE_DIR was not provided. Build the project with Gradle instead.")
endif()

set(WHISPER_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_IS_DEV   OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS      OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP            OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE         OFF CACHE BOOL "" FORCE)
set(GGML_CPU               ON  CACHE BOOL "" FORCE)

add_subdirectory(${WHISPER_SOURCE_DIR} whisper-build)

add_library(callrecord_whisper SHARED whisper_jni.cpp)

target_compile_options(callrecord_whisper PRIVATE -O3 -fvisibility=hidden)

target_link_libraries(callrecord_whisper
    whisper
    android
    log
)
