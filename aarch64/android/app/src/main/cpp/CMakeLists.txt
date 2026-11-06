cmake_minimum_required(VERSION 3.22.1)
project(nanjni C)
set(CMAKE_C_STANDARD 99)
set(CMAKE_C_EXTENSIONS ON)
# app/src/main/cpp -> the repository root is four levels up
get_filename_component(R "${CMAKE_CURRENT_SOURCE_DIR}/../../../.." ABSOLUTE)
add_library(nanjni SHARED
    ${R}/jni/nan_jni.c ${R}/core/nan16.c ${R}/core/nan_dev.c ${R}/core/nan_load.c ${R}/core/nan_render.c ${R}/core/nan_state.c ${R}/core/nan_net_driver.c)
target_include_directories(nanjni PRIVATE ${R}/core)
# Android edge universe: 1,048,576 NaN16 words x 8 bytes = 8 MiB.
target_compile_definitions(nanjni PRIVATE NAN_WORDS=0x100000)
# -ffp-contract=off: the FP rules in core/OPS.md must not be fused (arm64 would otherwise emit FMA)
target_compile_options(nanjni PRIVATE -O2 -ffp-contract=off -fno-fast-math -Wno-misleading-indentation)
target_link_libraries(nanjni m)
target_link_options(nanjni PRIVATE -Wl,-z,max-page-size=16384)
