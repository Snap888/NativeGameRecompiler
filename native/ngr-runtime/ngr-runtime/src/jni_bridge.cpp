#include "ngr/runtime_info.hpp"
#include "ngr/capability_registry.hpp"
#include <jni.h>
extern "C" JNIEXPORT jstring JNICALL
Java_com_ngr_app_MainActivity_nativeRuntimeInfo(JNIEnv* env, jobject) {
    const auto value = ngr::runtime_info();
    return env->NewStringUTF(value.c_str());
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_ngr_app_MainActivity_nativeVulkanProbe(JNIEnv* env, jobject) {
    const auto value = ngr::vulkan_probe();
    return env->NewStringUTF(value.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ngr_app_MainActivity_nativeCapabilityReport(JNIEnv* env, jobject) {
    const auto value = ngr::capability_report();
    return env->NewStringUTF(value.c_str());
}
