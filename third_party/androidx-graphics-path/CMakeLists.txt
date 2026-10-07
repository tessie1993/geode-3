cmake_minimum_required(VERSION 3.22.1)
project(androidx.graphics.path LANGUAGES CXX)

# Geode build adapter, 2026-10-07. Upstream native sources are unchanged.
# Keep the upstream SONAME, JNI export map and compiler settings; add complete
# 16 KB LOAD and RELRO alignment when rebuilding the two ABIs shipped by Geode.
add_library(androidx.graphics.path SHARED
    src/Conic.cpp src/PathIterator.cpp src/pathway.cpp)
target_compile_features(androidx.graphics.path PRIVATE cxx_std_17)
target_compile_options(androidx.graphics.path PRIVATE
    -fno-exceptions -fno-unwind-tables -fno-asynchronous-unwind-tables
    -fno-rtti -ffast-math -ffp-contract=fast -fvisibility-inlines-hidden
    -fvisibility=hidden -fomit-frame-pointer -ffunction-sections -fdata-sections)
target_link_options(androidx.graphics.path PRIVATE
    "-Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/src/libandroidx.graphics.path.map"
    -Wl,--hash-style=both -Wl,--gc-sections -Wl,-Bsymbolic-functions
    -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384
    -Wl,-z,relro -Wl,-z,now -nostdlib++)
