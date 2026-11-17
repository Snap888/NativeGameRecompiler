#ifndef NGR_BACKEND_ABI_V1_H
#define NGR_BACKEND_ABI_V1_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define NGR_BACKEND_ABI_V1_MAJOR 1u
#define NGR_BACKEND_ABI_V1_MINOR 0u
#define NGR_BACKEND_ABI_V1_SYMBOL "ngr_backend_get_api_v1"

#if defined(__GNUC__) || defined(__clang__)
#define NGR_BACKEND_ABI_EXPORT __attribute__((visibility("default")))
#else
#define NGR_BACKEND_ABI_EXPORT
#endif

typedef int32_t NgrStatusV1;
enum {
    NGR_STATUS_V1_OK = 0,
    NGR_STATUS_V1_NOT_SUPPORTED = 1,
    NGR_STATUS_V1_INVALID_ARGUMENT = 2,
    NGR_STATUS_V1_ABI_MISMATCH = 3,
    NGR_STATUS_V1_IO_ERROR = 4,
    NGR_STATUS_V1_OUT_OF_MEMORY = 5,
    NGR_STATUS_V1_CANCELLED = 6,
    NGR_STATUS_V1_INTERNAL_ERROR = 7
};

typedef uint32_t NgrBackendKindV1;
enum {
    NGR_BACKEND_KIND_V1_EXECUTION = 1
};

typedef uint32_t NgrLogLevelV1;
enum {
    NGR_LOG_V1_TRACE = 0,
    NGR_LOG_V1_INFO = 1,
    NGR_LOG_V1_WARNING = 2,
    NGR_LOG_V1_ERROR = 3
};

/* UTF-8 byte view. The pointed-to memory is borrowed for the duration documented by each call. */
typedef struct NgrStringViewV1 {
    const char* data;
    uint32_t size_bytes;
} NgrStringViewV1;

typedef struct NgrBackendConfigV1 {
    uint32_t struct_size;
    NgrStringViewV1 json_utf8;
} NgrBackendConfigV1;

typedef struct NgrPackageRefV1 {
    uint32_t struct_size;
    uint64_t package_handle;
    NgrStringViewV1 package_id;
    NgrStringViewV1 manifest_json_utf8;
    NgrStringViewV1 entry_point;
} NgrPackageRefV1;

typedef struct NgrProbeResultV1 {
    uint32_t struct_size;
    uint32_t supported;
    NgrStringViewV1 reason_utf8;
} NgrProbeResultV1;

typedef struct NgrHostApiV1 {
    uint32_t struct_size;
    uint32_t abi_major;
    uint32_t abi_minor;
    void* host_context;

    void (*log_utf8)(
        void* host_context,
        NgrLogLevelV1 level,
        const NgrStringViewV1* message
    );

    NgrStatusV1 (*open_package_file)(
        void* host_context,
        uint64_t package_handle,
        const NgrStringViewV1* relative_path,
        uint64_t* out_file_handle
    );

    NgrStatusV1 (*read_file)(
        void* host_context,
        uint64_t file_handle,
        void* destination,
        uint32_t destination_capacity,
        uint32_t* out_bytes_read
    );

    NgrStatusV1 (*close_file)(
        void* host_context,
        uint64_t file_handle
    );

    uint64_t (*monotonic_time_ns)(void* host_context);
} NgrHostApiV1;

typedef struct NgrBackendApiV1 {
    uint32_t struct_size;
    uint32_t abi_major;
    uint32_t abi_minor;
    NgrBackendKindV1 backend_kind;
    uint64_t capability_flags;

    NgrStringViewV1 backend_id;
    NgrStringViewV1 backend_version;

    NgrStatusV1 (*probe)(
        const NgrHostApiV1* host_api,
        const NgrPackageRefV1* package,
        NgrProbeResultV1* out_result
    );

    NgrStatusV1 (*create)(
        const NgrHostApiV1* host_api,
        const NgrBackendConfigV1* config,
        void** out_instance
    );

    NgrStatusV1 (*load)(
        void* instance,
        const NgrPackageRefV1* package
    );

    NgrStatusV1 (*start)(void* instance);

    /* Must be safe to call concurrently with start(). */
    NgrStatusV1 (*request_stop)(void* instance);

    /* Called after stop has completed and no backend callback is in flight. */
    void (*destroy)(void* instance);
} NgrBackendApiV1;

/* Export this symbol from a trusted backend shared library. */
typedef NgrStatusV1 (*NgrBackendGetApiV1Fn)(
    const NgrHostApiV1* host_api,
    uint32_t requested_abi_major,
    NgrBackendApiV1* out_api
);

#define NGR_HOST_API_V1_REQUIRED_SIZE ((uint32_t)sizeof(NgrHostApiV1))
#define NGR_BACKEND_API_V1_REQUIRED_SIZE ((uint32_t)sizeof(NgrBackendApiV1))

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* NGR_BACKEND_ABI_V1_H */
