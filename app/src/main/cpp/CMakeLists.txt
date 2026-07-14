cmake_minimum_required(VERSION 3.22.1)
project("mediaeditor_ndk")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)

# Create native library
add_library(native_processor SHARED
    native_processor.cpp
    image_effects.cpp
    audio_effects.cpp
)

# Link libraries
target_link_libraries(native_processor
    android
    log
    jnigraphics
    z
)

# Optimization flags
target_compile_options(native_processor PRIVATE
    -O3
    -ffast-math
    -funroll-loops
    -flto
    -fvisibility=hidden
)

target_link_options(native_processor PRIVATE
    -flto
    -Wl,--gc-sections
)
