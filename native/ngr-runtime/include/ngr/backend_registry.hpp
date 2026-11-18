#pragma once
#include "ngr/backend_abi_v1.h"
#include <cstddef>
#include <mutex>
#include <string>
#include <unordered_set>
namespace ngr {
// In-process registry for trusted, app-bundled API tables only.
// Intentionally has no dynamic loader and never accepts package paths.
class BackendRegistry final {
public:
    bool register_builtin(const NgrBackendApiV1& api, std::string* error = nullptr);
    bool contains(const std::string& backend_id) const;
    std::size_t size() const;
private:
    mutable std::mutex mutex_;
    std::unordered_set<std::string> backend_ids_;
};
} // namespace ngr
