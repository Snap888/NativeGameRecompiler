cmake_minimum_required(VERSION 3.22)
project(XDroid360HostHook C)

if (NOT ANDROID)
    message(FATAL_ERROR "XDroid360 Android hook must be built with the Android NDK.")
endif()

if (NOT DEFINED XDROID360_SOURCE_ROOT)
    message(FATAL_ERROR "XDROID360_SOURCE_ROOT must point at the pinned XDroid360 submodule.")
endif()

set(XDROID360_NATIVE_DIR "${XDROID360_SOURCE_ROOT}/android/app/src/main/cpp")
if (NOT EXISTS "${XDROID360_NATIVE_DIR}/vkdriverhook.c")
    message(FATAL_ERROR "Pinned XDroid360 vkdriverhook.c is missing.")
endif()

# SDL3 itself and its Java Activity glue come from WinNative's existing SDL3 AAR.
# Do not build a second libSDL3.so here: the donor's game profiles link against the stable SDL3 ABI.
add_library(vkdriverhook SHARED "${XDROID360_NATIVE_DIR}/vkdriverhook.c")
target_compile_options(vkdriverhook PRIVATE -fvisibility=hidden -Wall -Wextra)
target_link_options(vkdriverhook PRIVATE -Wl,-z,global -Wl,-z,max-page-size=16384)
target_link_libraries(vkdriverhook PRIVATE android dl)
