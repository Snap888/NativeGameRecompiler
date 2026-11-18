#include "ngr/backend_abi_validation.hpp"

#include <cstddef>
#include <cstdint>

namespace ngr {
namespace {

bool fail(std::string* error, const char* message) {
    if (error != nullptr) *error = message;
    return false;
}

bool valid_view(const NgrStringViewV1& value, uint32_t maximum, bool allow_empty) {
    if (value.size_bytes > maximum) return false;
    if (value.size_bytes == 0) return allow_empty && value.data == nullptr;
    if (value.data == nullptr) return false;
    for (uint32_t i = 0; i < value.size_bytes; ++i) {
        const unsigned char c = static_cast<unsigned char>(value.data[i]);
        if (c == 0 || c < 0x20 || c == 0x7f) return false;
    }
    return true;
}

bool valid_backend_id(const NgrStringViewV1& value) {
    if (!valid_view(value, 96, false)) return false;
    for (uint32_t i = 0; i < value.size_bytes; ++i) {
        const char c = value.data[i];
        const bool allowed = (c >= 'a' && c <= 'z') ||
                             (c >= '0' && c <= '9') ||
                             c == '.' || c == '_' || c == '-';
        if (!allowed) return false;
    }
    return true;
}

} // namespace

bool validate_host_api_v1(const NgrHostApiV1& api, std::string* error) {
    if (api.struct_size < NGR_HOST_API_V1_REQUIRED_SIZE)
        return fail(error, "host API struct is smaller than ABI v1.0");
    if (api.abi_major != NGR_BACKEND_ABI_V1_MAJOR)
        return fail(error, "host API major version mismatch");
    if (api.log_utf8 == nullptr || api.open_package_file == nullptr ||
        api.read_file == nullptr || api.close_file == nullptr ||
        api.monotonic_time_ns == nullptr)
        return fail(error, "host API is missing a required callback");
    if (error != nullptr) error->clear();
    return true;
}

bool validate_backend_api_v1(const NgrBackendApiV1& api, std::string* error) {
    if (api.struct_size < NGR_BACKEND_API_V1_REQUIRED_SIZE)
        return fail(error, "backend API struct is smaller than ABI v1.0");
    if (api.abi_major != NGR_BACKEND_ABI_V1_MAJOR)
        return fail(error, "backend ABI major version mismatch");
    if (api.abi_minor > NGR_BACKEND_ABI_V1_MINOR)
        return fail(error, "backend requires a newer host ABI minor version");
    if (api.backend_kind != NGR_BACKEND_KIND_V1_EXECUTION)
        return fail(error, "unsupported backend kind");
    if (!valid_backend_id(api.backend_id))
        return fail(error, "backend ID is invalid");
    if (!valid_view(api.backend_version, 64, false))
        return fail(error, "backend version is invalid");
    if (api.probe == nullptr || api.create == nullptr || api.load == nullptr ||
        api.start == nullptr || api.request_stop == nullptr || api.destroy == nullptr)
        return fail(error, "backend API is missing a required lifecycle callback");
    if (error != nullptr) error->clear();
    return true;
}

} // namespace ngr
