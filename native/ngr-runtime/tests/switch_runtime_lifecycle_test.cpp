#include "switch_runtime_lifecycle.h"

#include <cassert>
#include <vector>

namespace {
std::vector<int> events;
void* expected_activity = nullptr;
void* expected_handle = nullptr;

void donor_destroy(void* activity) {
    assert(activity == expected_activity);
    events.push_back(1);
}

void unload_runtime(void* handle) {
    assert(handle == expected_handle);
    events.push_back(2);
}
}  // namespace

int main() {
    int activity_token = 0;
    int handle_token = 0;
    expected_activity = &activity_token;
    expected_handle = &handle_token;

    ngr::SwitchRuntimeLifecycleState state{
        &handle_token,
        donor_destroy,
        unload_runtime,
    };

    ngr::switch_runtime_destroy(&state, &activity_token);
    assert((events == std::vector<int>{1, 2}));
    assert(state.runtime_handle == nullptr);
    assert(state.donor_on_destroy == nullptr);
    assert(state.unload_runtime == nullptr);

    // A repeated callback must not invoke donor teardown or unload twice.
    ngr::switch_runtime_destroy(&state, &activity_token);
    assert((events == std::vector<int>{1, 2}));

    // A missing donor callback must still unload the runtime.
    events.clear();
    state.runtime_handle = &handle_token;
    state.unload_runtime = unload_runtime;
    ngr::switch_runtime_destroy(&state, &activity_token);
    assert((events == std::vector<int>{2}));

    // Null state is a no-op.
    ngr::switch_runtime_destroy(nullptr, &activity_token);
    return 0;
}
