cmake_minimum_required(VERSION 3.22.1)

project(ojas LANGUAGES C CXX)

# C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Main native library
add_library(ojas SHARED
        native-lib.cpp
        signal_processor.cpp
        kiss_fft.c
)

# Base optimization flags (for this target only)
target_compile_options(ojas PRIVATE
        -O3
        -ffast-math
)

# Enable NEON / architecture-specific flags safely
if(ANDROID)
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        # 64-bit ARM: no -mfloat-abi / -mfpu!
        target_compile_options(ojas PRIVATE -march=armv8-a)
        target_compile_definitions(ojas PRIVATE USE_NEON)
    elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
        # 32-bit ARM: these flags are valid
        target_compile_options(ojas PRIVATE
                -mfpu=neon
                -mfloat-abi=softfp
        )
        target_compile_definitions(ojas PRIVATE USE_NEON)
    endif()
endif()

# Include directories
target_include_directories(ojas PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
)

# Link libraries
find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(ojas
        ${log-lib}
        ${android-lib}
)
