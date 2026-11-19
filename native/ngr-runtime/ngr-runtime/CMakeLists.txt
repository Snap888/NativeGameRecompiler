cmake_minimum_required(VERSION 3.22.1)
project(ngr_runtime LANGUAGES C CXX)

# Build the upstream N64Recomp libraries in this directory scope. Keeping the
# dependency next to ngr_runtime guarantees CMake resolves real target names
# (rather than passing -lN64Recomp/-lN64RecompElf to the Android linker).
set(NGR_N64RECOMP_DIR "${CMAKE_CURRENT_LIST_DIR}/../recompilers/N64Recomp")
if(NOT TARGET N64Recomp OR NOT TARGET N64RecompElf)
    if(NOT EXISTS "${NGR_N64RECOMP_DIR}/CMakeLists.txt")
        message(FATAL_ERROR "N64Recomp source is missing: ${NGR_N64RECOMP_DIR}")
    endif()
    add_subdirectory("${NGR_N64RECOMP_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/n64recomp" EXCLUDE_FROM_ALL)
endif()

add_library(ngr_runtime SHARED
    src/jni_bridge.cpp
    src/n64recomp_bridge.cpp
    src/runtime_info.cpp
    src/vulkan_probe.cpp
    src/capability_registry.cpp
    src/backend_abi_validation.cpp
    src/backend_registry.cpp
    src/backend_abi_c_check.c
)
target_include_directories(ngr_runtime PRIVATE
    include
    "${NGR_N64RECOMP_DIR}/include"
    "${NGR_N64RECOMP_DIR}/lib/ELFIO"
)
target_compile_features(ngr_runtime PRIVATE cxx_std_20)
target_compile_definitions(ngr_runtime PRIVATE
    NGR_N64RECOMP_INCLUDE_DIR="${NGR_N64RECOMP_DIR}/include"
    NGR_N64RECOMP_LICENSE_FILE="${NGR_N64RECOMP_DIR}/LICENSE"
)
target_compile_options(ngr_runtime PRIVATE -Wall -Wextra -Wpedantic)
find_library(android-lib android)
find_library(log-lib log)
find_library(vulkan-lib vulkan)
target_link_libraries(ngr_runtime PRIVATE
    ${android-lib}
    ${log-lib}
    ${vulkan-lib}
    N64Recomp
    N64RecompElf
)
add_dependencies(ngr_runtime N64Recomp N64RecompElf)
