cmake_minimum_required(VERSION 3.18.1)
# CXX, not C: libneedle.a is C++ (libc++ __ndk1 symbols), so the NDK has to link the
# C++ runtime in. A C-only project fails at link with thousands of undefined std:: refs.
project(needle_jni LANGUAGES CXX)

add_library(needle STATIC IMPORTED)
set_target_properties(needle PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/lib/${ANDROID_ABI}/libneedle.a)

add_library(needle_jni SHARED needle_jni.cpp)
target_include_directories(needle_jni PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(needle_jni PRIVATE needle log)

# The model weights are embedded in libneedle.a (needle_embed.S.o, ~13.7MB), so the
# linker must not drop them as unreferenced.
target_link_options(needle_jni PRIVATE "-Wl,-z,max-page-size=16384")
