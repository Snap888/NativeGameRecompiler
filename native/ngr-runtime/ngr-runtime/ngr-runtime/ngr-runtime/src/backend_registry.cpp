#include "ngr/backend_registry.hpp"
#include "ngr/backend_abi_validation.hpp"
namespace ngr {
bool BackendRegistry::register_builtin(const NgrBackendApiV1& api, std::string* error) {
    if (!validate_backend_api_v1(api, error)) return false;
    const std::string id(api.backend_id.data, api.backend_id.size_bytes);
    std::lock_guard<std::mutex> lock(mutex_);
    if (!backend_ids_.insert(id).second) {
        if (error) *error = "backend ID is already registered";
        return false;
    }
    return true;
}
bool BackendRegistry::contains(const std::string& id) const {
    std::lock_guard<std::mutex> lock(mutex_);
    return backend_ids_.find(id) != backend_ids_.end();
}
std::size_t BackendRegistry::size() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return backend_ids_.size();
}
} // namespace ngr
