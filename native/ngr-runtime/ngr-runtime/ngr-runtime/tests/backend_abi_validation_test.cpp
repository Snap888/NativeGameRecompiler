#include "ngr/backend_abi_validation.hpp"

#include <cassert>
#include <cstring>
#include <string>

namespace {

NgrStatusV1 ok_probe(const NgrHostApiV1*, const NgrPackageRefV1*, NgrProbeResultV1*) { return NGR_STATUS_V1_OK; }
NgrStatusV1 ok_create(const NgrHostApiV1*, const NgrBackendConfigV1*, void** out) { if (out) *out = nullptr; return NGR_STATUS_V1_OK; }
NgrStatusV1 ok_load(void*, const NgrPackageRefV1*) { return NGR_STATUS_V1_OK; }
NgrStatusV1 ok_instance(void*) { return NGR_STATUS_V1_OK; }
void ok_destroy(void*) {}
void ok_log(void*, NgrLogLevelV1, const NgrStringViewV1*) {}
NgrStatusV1 ok_open(void*, uint64_t, const NgrStringViewV1*, uint64_t*) { return NGR_STATUS_V1_OK; }
NgrStatusV1 ok_read(void*, uint64_t, void*, uint32_t, uint32_t*) { return NGR_STATUS_V1_OK; }
NgrStatusV1 ok_close(void*, uint64_t) { return NGR_STATUS_V1_OK; }
uint64_t ok_time(void*) { return 0; }

NgrHostApiV1 host() {
    NgrHostApiV1 value{};
    value.struct_size = sizeof(value);
    value.abi_major = NGR_BACKEND_ABI_V1_MAJOR;
    value.abi_minor = NGR_BACKEND_ABI_V1_MINOR;
    value.log_utf8 = ok_log;
    value.open_package_file = ok_open;
    value.read_file = ok_read;
    value.close_file = ok_close;
    value.monotonic_time_ns = ok_time;
    return value;
}

NgrBackendApiV1 backend() {
    NgrBackendApiV1 value{};
    value.struct_size = sizeof(value);
    value.abi_major = NGR_BACKEND_ABI_V1_MAJOR;
    value.abi_minor = NGR_BACKEND_ABI_V1_MINOR;
    value.backend_kind = NGR_BACKEND_KIND_V1_EXECUTION;
    value.backend_id = {"test.native", 11};
    value.backend_version = {"1.0.0", 5};
    value.probe = ok_probe;
    value.create = ok_create;
    value.load = ok_load;
    value.start = ok_instance;
    value.request_stop = ok_instance;
    value.destroy = ok_destroy;
    return value;
}

} // namespace

int main() {
    std::string error;
    auto h = host();
    assert(ngr::validate_host_api_v1(h, &error));
    h.read_file = nullptr;
    assert(!ngr::validate_host_api_v1(h, &error));
    assert(error.find("callback") != std::string::npos);
    h = host();
    h.abi_major = 2;
    assert(!ngr::validate_host_api_v1(h, &error));
    h = host();
    h.abi_minor = NGR_BACKEND_ABI_V1_MINOR + 1;
    assert(!ngr::validate_host_api_v1(h, &error));
    h = host();
    h.struct_size = sizeof(h) - 1;
    assert(!ngr::validate_host_api_v1(h, &error));

    auto b = backend();
    assert(ngr::validate_backend_api_v1(b, &error));
    b.abi_major = 2;
    assert(!ngr::validate_backend_api_v1(b, &error));
    b = backend();
    b.abi_minor = NGR_BACKEND_ABI_V1_MINOR + 1;
    assert(!ngr::validate_backend_api_v1(b, &error));
    b = backend();
    b.backend_id = {"Bad ID", 6};
    assert(!ngr::validate_backend_api_v1(b, &error));
    b = backend();
    b.start = nullptr;
    assert(!ngr::validate_backend_api_v1(b, &error));
    b = backend();
    b.struct_size = sizeof(b) - 1;
    assert(!ngr::validate_backend_api_v1(b, &error));
    return 0;
}
