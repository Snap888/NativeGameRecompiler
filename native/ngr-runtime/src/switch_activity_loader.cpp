#include <android/log.h>
#include <android/native_activity.h>
#include <dlfcn.h>
#include <jni.h>
#include <sys/stat.h>

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>

namespace {
constexpr const char* kTag = "NGRSwitchLoader";
constexpr const char* kLibraryExtra = "com.ngr.app.SWITCH_RUNTIME_LIBRARY";
constexpr const char* kPortsDirectory = "/switch-ports/";
constexpr const char* kRuntimeSuffix = "/runtime/libdmhost.so";

using RuntimeEntry = void (*)(ANativeActivity*, void*, size_t);

void log_error(const char* message) {
    __android_log_write(ANDROID_LOG_ERROR, kTag, message);
}

std::string java_string(JNIEnv* env, jstring value) {
    if (!value) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (!chars) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

void finish_activity(ANativeActivity* activity) {
    if (!activity || !activity->vm || !activity->clazz) return;
    JNIEnv* env = nullptr;
    bool attached = false;
    jint state = activity->vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (state == JNI_EDETACHED) {
        if (activity->vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
        attached = true;
    } else if (state != JNI_OK) {
        return;
    }

    jclass cls = env->GetObjectClass(activity->clazz);
    jmethodID finish = cls ? env->GetMethodID(cls, "finish", "()V") : nullptr;
    if (finish) env->CallVoidMethod(activity->clazz, finish);
    if (env->ExceptionCheck()) env->ExceptionClear();
    if (cls) env->DeleteLocalRef(cls);
    if (attached) activity->vm->DetachCurrentThread();
}

bool read_runtime_path(ANativeActivity* activity, std::string* files_dir, std::string* library_path) {
    if (!activity || !activity->vm || !activity->clazz || !files_dir || !library_path) return false;
    JNIEnv* env = nullptr;
    if (activity->vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) return false;

    jclass activity_class = env->GetObjectClass(activity->clazz);
    if (!activity_class) return false;
    jmethodID get_intent = env->GetMethodID(activity_class, "getIntent", "()Landroid/content/Intent;");
    jmethodID get_files_dir = env->GetMethodID(activity_class, "getFilesDir", "()Ljava/io/File;");
    if (!get_intent || !get_files_dir) {
        env->DeleteLocalRef(activity_class);
        return false;
    }

    jobject intent = env->CallObjectMethod(activity->clazz, get_intent);
    jobject files = env->CallObjectMethod(activity->clazz, get_files_dir);
    if (env->ExceptionCheck() || !intent || !files) {
        env->ExceptionClear();
        if (intent) env->DeleteLocalRef(intent);
        if (files) env->DeleteLocalRef(files);
        env->DeleteLocalRef(activity_class);
        return false;
    }

    jclass intent_class = env->GetObjectClass(intent);
    jclass file_class = env->GetObjectClass(files);
    jmethodID get_string_extra = intent_class
        ? env->GetMethodID(intent_class, "getStringExtra", "(Ljava/lang/String;)Ljava/lang/String;")
        : nullptr;
    jmethodID get_absolute_path = file_class
        ? env->GetMethodID(file_class, "getAbsolutePath", "()Ljava/lang/String;")
        : nullptr;

    jstring key = env->NewStringUTF(kLibraryExtra);
    jstring path = (get_string_extra && key)
        ? static_cast<jstring>(env->CallObjectMethod(intent, get_string_extra, key))
        : nullptr;
    jstring root = get_absolute_path
        ? static_cast<jstring>(env->CallObjectMethod(files, get_absolute_path))
        : nullptr;

    if (!env->ExceptionCheck()) {
        *library_path = java_string(env, path);
        *files_dir = java_string(env, root);
    } else {
        env->ExceptionClear();
    }

    if (key) env->DeleteLocalRef(key);
    if (path) env->DeleteLocalRef(path);
    if (root) env->DeleteLocalRef(root);
    if (intent_class) env->DeleteLocalRef(intent_class);
    if (file_class) env->DeleteLocalRef(file_class);
    env->DeleteLocalRef(intent);
    env->DeleteLocalRef(files);
    env->DeleteLocalRef(activity_class);
    return !files_dir->empty() && !library_path->empty();
}

bool validate_runtime_path(const std::string& files_dir, const std::string& requested) {
    if (files_dir.empty() || requested.empty() || requested.find('\0') != std::string::npos) return false;
    const std::string prefix = files_dir + kPortsDirectory;
    if (requested.compare(0, prefix.size(), prefix) != 0) return false;
    if (requested.size() <= std::strlen(kRuntimeSuffix) ||
        requested.compare(requested.size() - std::strlen(kRuntimeSuffix),
                          std::strlen(kRuntimeSuffix), kRuntimeSuffix) != 0) return false;

    char canonical_root[PATH_MAX] = {};
    char canonical_library[PATH_MAX] = {};
    if (!realpath(files_dir.c_str(), canonical_root) ||
        !realpath(requested.c_str(), canonical_library)) return false;

    const std::string root(canonical_root);
    const std::string library(canonical_library);
    const std::string canonical_prefix = root + kPortsDirectory;
    if (library.compare(0, canonical_prefix.size(), canonical_prefix) != 0) return false;

    struct stat info {};
    return stat(canonical_library, &info) == 0 && S_ISREG(info.st_mode);
}

void* g_runtime_handle = nullptr;
}  // namespace

extern "C" __attribute__((visibility("default")))
void ANativeActivity_onCreate(ANativeActivity* activity, void* saved_state, size_t saved_state_size) {
    std::string files_dir;
    std::string library_path;
    if (!read_runtime_path(activity, &files_dir, &library_path) ||
        !validate_runtime_path(files_dir, library_path)) {
        log_error("Missing or unsafe per-game runtime library path. Expected a verified NGR switch-port runtime.");
        finish_activity(activity);
        return;
    }

    g_runtime_handle = dlopen(library_path.c_str(), RTLD_NOW | RTLD_LOCAL);
    if (!g_runtime_handle) {
        const char* error = dlerror();
        __android_log_print(ANDROID_LOG_ERROR, kTag, "dlopen failed: %s", error ? error : "unknown error");
        finish_activity(activity);
        return;
    }

    dlerror();
    auto entry = reinterpret_cast<RuntimeEntry>(dlsym(g_runtime_handle, "ANativeActivity_onCreate"));
    const char* symbol_error = dlerror();
    if (!entry || symbol_error) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "Runtime entry point missing: %s",
                            symbol_error ? symbol_error : "ANativeActivity_onCreate");
        dlclose(g_runtime_handle);
        g_runtime_handle = nullptr;
        finish_activity(activity);
        return;
    }

    __android_log_print(ANDROID_LOG_INFO, kTag, "Forwarding NativeActivity lifecycle to verified runtime: %s",
                        library_path.c_str());
    entry(activity, saved_state, saved_state_size);
}
