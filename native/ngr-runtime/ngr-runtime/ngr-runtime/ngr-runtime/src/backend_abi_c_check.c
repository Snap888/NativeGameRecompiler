#include "ngr/backend_abi_v1.h"

_Static_assert(NGR_BACKEND_ABI_V1_MAJOR == 1u, "NGR backend ABI major version mismatch");
_Static_assert(sizeof(NgrStatusV1) == sizeof(int32_t), "NgrStatusV1 must be 32-bit");
_Static_assert(offsetof(NgrBackendApiV1, probe) > offsetof(NgrBackendApiV1, backend_version),
               "Backend API function table layout is invalid");

/* Compiled as C to keep the public plugin header C-compatible. */
int ngr_backend_abi_c_header_check(void) {
    return (int)NGR_BACKEND_ABI_V1_MAJOR;
}
