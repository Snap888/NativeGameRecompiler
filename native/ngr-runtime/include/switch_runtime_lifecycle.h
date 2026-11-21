#pragma once

namespace ngr {

using SwitchRuntimeDestroyCallback = void (*)(void* activity);
using SwitchRuntimeUnloadCallback = void (*)(void* runtime_handle);

struct SwitchRuntimeLifecycleState {
    void* runtime_handle = nullptr;
    SwitchRuntimeDestroyCallback donor_on_destroy = nullptr;
    SwitchRuntimeUnloadCallback unload_runtime = nullptr;
};

// Clears the state before callbacks to make repeated/re-entrant destruction safe.
// The donor callback always runs before the runtime handle is unloaded.
void switch_runtime_destroy(SwitchRuntimeLifecycleState* state, void* activity) noexcept;

}  // namespace ngr
