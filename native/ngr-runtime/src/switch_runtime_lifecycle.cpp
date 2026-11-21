#include "switch_runtime_lifecycle.h"

namespace ngr {

void switch_runtime_destroy(SwitchRuntimeLifecycleState* state, void* activity) noexcept {
    if (state == nullptr) return;

    const auto donor_on_destroy = state->donor_on_destroy;
    const auto runtime_handle = state->runtime_handle;
    const auto unload_runtime = state->unload_runtime;

    state->donor_on_destroy = nullptr;
    state->runtime_handle = nullptr;
    state->unload_runtime = nullptr;

    if (donor_on_destroy != nullptr) donor_on_destroy(activity);
    if (runtime_handle != nullptr && unload_runtime != nullptr) unload_runtime(runtime_handle);
}

}  // namespace ngr
