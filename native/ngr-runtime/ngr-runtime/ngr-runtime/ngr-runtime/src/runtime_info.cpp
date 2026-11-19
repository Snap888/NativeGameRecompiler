#include "ngr/runtime_info.hpp"
namespace ngr {
std::string runtime_info() {
    return "NGR Runtime 0.1.0\nCore: C++20 / JNI\nHost ABI: Android arm64-v8a\n"
           "Stage: 1 — runtime foundation\nGame backends: not integrated yet";
}
}
