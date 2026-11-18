#include "ngr/backend_registry.hpp"
#include <cassert>
#include <string>
namespace {
NgrStatusV1 probe(const NgrHostApiV1*, const NgrPackageRefV1*, NgrProbeResultV1*) { return NGR_STATUS_V1_OK; }
NgrStatusV1 create(const NgrHostApiV1*, const NgrBackendConfigV1*, void** out) { if (out) *out = nullptr; return NGR_STATUS_V1_OK; }
NgrStatusV1 load(void*, const NgrPackageRefV1*) { return NGR_STATUS_V1_OK; }
NgrStatusV1 instance(void*) { return NGR_STATUS_V1_OK; }
void destroy(void*) {}
NgrBackendApiV1 make_backend(const char* id) {
    NgrBackendApiV1 api{};
    api.struct_size=sizeof(api); api.abi_major=NGR_BACKEND_ABI_V1_MAJOR;
    api.abi_minor=NGR_BACKEND_ABI_V1_MINOR; api.backend_kind=NGR_BACKEND_KIND_V1_EXECUTION;
    api.backend_id={id,static_cast<uint32_t>(std::char_traits<char>::length(id))};
    api.backend_version={"0.1.0",5}; api.probe=probe; api.create=create;
    api.load=load; api.start=instance; api.request_stop=instance; api.destroy=destroy;
    return api;
}
}
int main() {
    ngr::BackendRegistry registry; std::string error;
    const auto first=make_backend("fixture.builtin");
    assert(registry.size()==0);
    assert(registry.register_builtin(first,&error));
    assert(registry.size()==1 && registry.contains("fixture.builtin"));
    assert(!registry.contains("unknown.backend"));
    error.clear(); assert(!registry.register_builtin(first,&error));
    assert(error.find("already registered")!=std::string::npos);
    auto invalid=make_backend("bad id"); error.clear();
    assert(!registry.register_builtin(invalid,&error));
    assert(!registry.contains("bad id") && registry.size()==1);
    auto second=make_backend("fixture.second");
    assert(registry.register_builtin(second,&error) && registry.size()==2);
    return 0;
}
