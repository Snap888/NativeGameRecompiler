#include <android/log.h>
#include <android/native_activity.h>
#include "switch_runtime_lifecycle.h"
#include <dlfcn.h>
#include <jni.h>
#include <limits.h>
#include <sys/stat.h>

#include <cstdlib>
#include <cstring>
#include <initializer_list>
#include <string>

namespace {
constexpr const char* kTag = "NGRSwitchLoader";
constexpr const char* kLibraryExtra = "com.ngr.app.SWITCH_RUNTIME_LIBRARY";
constexpr const char* kGameRootExtra = "com.ngr.app.SWITCH_GAME_ROOT";
constexpr const char* kTitleIdExtra = "com.ngr.app.SWITCH_TITLE_ID";
constexpr const char* kPortsDirectory = "/switch-ports/";
constexpr const char* kRuntimeSuffix = "/runtime/libdmhost.so";
using RuntimeEntry = void (*)(ANativeActivity*, void*, size_t);
using RuntimeOnDestroy = void (*)(ANativeActivity*);
void* g_runtime_handle = nullptr;
RuntimeOnDestroy g_runtime_on_destroy = nullptr;
ngr::SwitchRuntimeLifecycleState g_runtime_lifecycle{};

void call_donor_on_destroy(void* activity) {
    RuntimeOnDestroy callback = g_runtime_on_destroy;
    g_runtime_on_destroy = nullptr;
    if (callback) callback(static_cast<ANativeActivity*>(activity));
}

void unload_runtime(void* handle) {
    if (handle) dlclose(handle);
}

void on_runtime_destroy(ANativeActivity* activity) {
    g_runtime_lifecycle.runtime_handle = g_runtime_handle;
    g_runtime_lifecycle.donor_on_destroy = g_runtime_on_destroy ? call_donor_on_destroy : nullptr;
    g_runtime_lifecycle.unload_runtime = unload_runtime;
    g_runtime_handle = nullptr;
    ngr::switch_runtime_destroy(&g_runtime_lifecycle, activity);
}

void log_error(const char* message) { __android_log_write(ANDROID_LOG_ERROR, kTag, message); }

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
    } else if (state != JNI_OK) return;
    jclass cls = env->GetObjectClass(activity->clazz);
    jmethodID finish = cls ? env->GetMethodID(cls, "finish", "()V") : nullptr;
    if (finish) env->CallVoidMethod(activity->clazz, finish);
    if (env->ExceptionCheck()) env->ExceptionClear();
    if (cls) env->DeleteLocalRef(cls);
    if (attached) activity->vm->DetachCurrentThread();
}

bool read_launch_args(ANativeActivity* activity, std::string* files_dir,
                      std::string* library_path, std::string* game_root, std::string* title_id) {
    if (!activity || !activity->vm || !activity->clazz) return false;
    JNIEnv* env = nullptr;
    if (activity->vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) return false;
    jclass activity_class = env->GetObjectClass(activity->clazz);
    if (!activity_class) return false;
    jmethodID get_intent = env->GetMethodID(activity_class, "getIntent", "()Landroid/content/Intent;");
    jmethodID get_files_dir = env->GetMethodID(activity_class, "getFilesDir", "()Ljava/io/File;");
    if (!get_intent || !get_files_dir) { env->DeleteLocalRef(activity_class); return false; }
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
    jmethodID get_extra = intent_class ? env->GetMethodID(intent_class, "getStringExtra", "(Ljava/lang/String;)Ljava/lang/String;") : nullptr;
    jmethodID get_path = file_class ? env->GetMethodID(file_class, "getAbsolutePath", "()Ljava/lang/String;") : nullptr;
    jstring k1 = env->NewStringUTF(kLibraryExtra);
    jstring k2 = env->NewStringUTF(kGameRootExtra);
    jstring k3 = env->NewStringUTF(kTitleIdExtra);
    jstring v1 = get_extra ? static_cast<jstring>(env->CallObjectMethod(intent, get_extra, k1)) : nullptr;
    jstring v2 = get_extra ? static_cast<jstring>(env->CallObjectMethod(intent, get_extra, k2)) : nullptr;
    jstring v3 = get_extra ? static_cast<jstring>(env->CallObjectMethod(intent, get_extra, k3)) : nullptr;
    jstring root = get_path ? static_cast<jstring>(env->CallObjectMethod(files, get_path)) : nullptr;
    if (!env->ExceptionCheck()) {
        *library_path = java_string(env, v1);
        *game_root = java_string(env, v2);
        *title_id = java_string(env, v3);
        *files_dir = java_string(env, root);
    } else env->ExceptionClear();
    for (jobject value : {static_cast<jobject>(k1), static_cast<jobject>(k2), static_cast<jobject>(k3),
                          static_cast<jobject>(v1), static_cast<jobject>(v2), static_cast<jobject>(v3),
                          static_cast<jobject>(root), static_cast<jobject>(intent_class), static_cast<jobject>(file_class)}) {
        if (value) env->DeleteLocalRef(value);
    }
    env->DeleteLocalRef(intent);
    env->DeleteLocalRef(files);
    env->DeleteLocalRef(activity_class);
    return !files_dir->empty() && !library_path->empty() && !game_root->empty() && !title_id->empty();
}

bool safe_title_id(const std::string& id) {
    if (id.size() != 16) return false;
    for (char c : id) if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) return false;
    return true;
}

bool validate_runtime(const std::string& files_dir, const std::string& path, const std::string& title_id) {
    if (!safe_title_id(title_id) || files_dir.empty() || path.empty()) return false;
    const std::string prefix = files_dir + kPortsDirectory + title_id + kRuntimeSuffix;
    if (path != prefix) return false;
    char root[PATH_MAX] = {}, library[PATH_MAX] = {};
    if (!realpath(files_dir.c_str(), root) || !realpath(path.c_str(), library)) return false;
    const std::string allowed = std::string(root) + kPortsDirectory + title_id + kRuntimeSuffix;
    struct stat info {};
    return allowed == library && stat(library, &info) == 0 && S_ISREG(info.st_mode) && info.st_size > 0;
}

bool validate_game_root(ANativeActivity* activity, const std::string& path, const std::string& title_id) {
    if (!activity || !activity->externalDataPath || !safe_title_id(title_id)) return false;
    char external[PATH_MAX] = {}, game[PATH_MAX] = {};
    if (!realpath(activity->externalDataPath, external) || !realpath(path.c_str(), game)) return false;
    const std::string expected = std::string(external) + "/switch-ports/" + title_id + "/data";
    if (expected != game) return false;
    struct stat d {}, manifest {}, mods {}, env {}, assets {}, overlay {}, theme {}, font_dir {}, font {};
    const std::string base(game);
    return lstat(game, &d) == 0 && S_ISDIR(d.st_mode) &&
           lstat((base + "/nx2a-data.json").c_str(), &manifest) == 0 && S_ISREG(manifest.st_mode) &&
           lstat((base + "/mods").c_str(), &mods) == 0 && S_ISDIR(mods.st_mode) &&
           lstat((base + "/env.txt").c_str(), &env) == 0 && S_ISREG(env.st_mode) &&
           lstat((base + "/assets").c_str(), &assets) == 0 && S_ISDIR(assets.st_mode) &&
           lstat((base + "/assets/overlay").c_str(), &overlay) == 0 && S_ISDIR(overlay.st_mode) &&
           lstat((base + "/assets/overlay/theme.tsv").c_str(), &theme) == 0 && S_ISREG(theme.st_mode) &&
           lstat((base + "/assets/font").c_str(), &font_dir) == 0 && S_ISDIR(font_dir.st_mode) &&
           lstat((base + "/assets/font/ui.bin").c_str(), &font) == 0 && S_ISREG(font.st_mode);
}
}  // namespace

extern "C" __attribute__((visibility("default")))
void ANativeActivity_onCreate(ANativeActivity* activity, void* saved_state, size_t saved_state_size) {
    std::string files_dir, library_path, game_root, title_id;
    if (!read_launch_args(activity, &files_dir, &library_path, &game_root, &title_id) ||
        !validate_runtime(files_dir, library_path, title_id) ||
        !validate_game_root(activity, game_root, title_id)) {
        log_error("Missing or unsafe per-title Switch runtime/data pack.");
        finish_activity(activity);
        return;
    }
    const std::string private_root = files_dir + kPortsDirectory + title_id;
    char canonical_files[PATH_MAX] = {}, canonical_private[PATH_MAX] = {};
    if (!realpath(files_dir.c_str(), canonical_files) || !realpath(private_root.c_str(), canonical_private) ||
        std::string(canonical_private) != std::string(canonical_files) + kPortsDirectory + title_id) {
        log_error("Per-title private save directory is missing or unsafe.");
        finish_activity(activity);
        return;
    }
    if (setenv("NGR_SWITCH_GAME_ROOT", game_root.c_str(), 1) != 0 ||
        setenv("NGR_SWITCH_PRIVATE_ROOT", private_root.c_str(), 1) != 0) {
        log_error("Unable to set per-title Switch data root.");
        finish_activity(activity);
        return;
    }
    if (g_runtime_handle) {
        log_error("A Switch runtime is already active in this process.");
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
    const char* error = dlerror();
    if (!entry || error) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "Runtime entry point missing: %s", error ? error : "unknown error");
        dlclose(g_runtime_handle);
        g_runtime_handle = nullptr;
        finish_activity(activity);
        return;
    }
    __android_log_print(ANDROID_LOG_INFO, kTag, "Starting Switch title %s from %s", title_id.c_str(), game_root.c_str());
    entry(activity, saved_state, saved_state_size);
    if (activity->callbacks) {
        g_runtime_on_destroy = activity->callbacks->onDestroy;
        activity->callbacks->onDestroy = on_runtime_destroy;
    } else {
        log_error("NativeActivity callback table is unavailable; runtime library will remain loaded until process exit.");
    }
}
