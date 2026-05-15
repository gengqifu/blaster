cmake_minimum_required(VERSION 3.22.1)

project(blaster_audio_identity LANGUAGES C CXX)

include(FetchContent)

set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(FFT_LIB kissfft CACHE STRING "" FORCE)
set(USE_INTERNAL_AVRESAMPLE ON CACHE BOOL "" FORCE)

FetchContent_Declare(
    chromaprint
    GIT_REPOSITORY https://github.com/acoustid/chromaprint.git
    GIT_TAG 6b13ce3a81ae931e7477c4856a86bece99157cd8
    GIT_SHALLOW TRUE
)

FetchContent_GetProperties(chromaprint)
if(NOT chromaprint_POPULATED)
    FetchContent_Populate(chromaprint)
    set(KISSFFT_ROOT "${chromaprint_SOURCE_DIR}/src/3rdparty/kissfft" CACHE PATH "" FORCE)
    set(KISSFFT_SOURCE_DIR "${chromaprint_SOURCE_DIR}/src/3rdparty/kissfft" CACHE PATH "" FORCE)
    add_subdirectory("${chromaprint_SOURCE_DIR}" "${chromaprint_BINARY_DIR}")
endif()

add_library(blaster_chromaprint_jni SHARED blaster_chromaprint_jni.cpp)

target_include_directories(blaster_chromaprint_jni PRIVATE "${chromaprint_SOURCE_DIR}/src")

target_link_libraries(
    blaster_chromaprint_jni
    chromaprint
    log
)
