# ADR 0003: Versioned C ABI for execution backend plugins

**Status:** ABI v1 design and host-side API-table validation implemented; dynamic loader, trusted backend fixture and runtime lifecycle integration are not implemented.

## Scope

ABI v1 defines a stable C boundary for trusted execution-backend shared libraries. It deliberately does not expose C++ classes, STL types, exceptions, RTTI, Android framework objects or Vulkan handles across the plugin boundary.

N64Recomp is a build-time static recompiler/toolchain, not an execution backend. Its generated ARM64 package will be loaded by an execution backend/runtime. ABI v1 therefore targets execution backends such as a future native ARM64 runner or dynamic translation runtime. Compiler/toolchain APIs and graphics-provider APIs require separate versioned contracts.

## Contract

- Exported symbol: `ngr_backend_get_api_v1`.
- ABI major/minor: 1.0. Major mismatch is fatal; additive minor extensions append fields and are guarded by `struct_size`.
- Every extensible versioned struct starts with `struct_size`; the fixed-layout `NgrStringViewV1` value type is the deliberate exception. API tables also carry ABI major/minor.
- Fixed-width integer types and explicit UTF-8 pointer/length string views only.
- No memory ownership crosses the boundary except caller-provided buffers and opaque handles.
- No C++ exception may cross the ABI. All callbacks return `NgrStatusV1`; destroy is non-throwing.
- Host file access is mediated by package/file handles and relative paths. Plugins do not receive raw package-root filesystem paths.
- Host API callbacks are thread-safe. Backend lifecycle calls are serialized except `request_stop`, which must be safe concurrently with `start`.
- String views passed into a call are borrowed for that call only. Backend ID/version views returned in the API table must remain valid for the plugin library lifetime.
- `start` is synchronous for the active backend session; `request_stop` may be called concurrently to ask it to finish. The host waits for `start` to return before `destroy`.
- `read_file` advances a per-handle cursor; each opened file handle is independent and must be closed.
- Plugin instances are destroyed only after stop completes and no callback remains in flight.

## Lifecycle

1. Host loads only a trusted backend library from an approved location.
2. Host resolves `ngr_backend_get_api_v1` and negotiates ABI major/struct sizes.
3. Host validates backend kind, ID, version and required function pointers.
4. Host calls `probe` with a package handle and manifest view.
5. Host calls `create`, then `load`, then `start`.
6. On exit/cancellation, host calls `request_stop`, waits for completion, then `destroy`.

## Security boundary

The ABI is not a sandbox. A shared library loaded into the app process has the app process's privileges. NGRPACK game payloads must never be dlopened as backend plugins. Plugin installation and loading require a separate trust policy (signed release plugins or app-bundled trusted plugins); unsigned development packages remain data-only and non-executable. A future untrusted-plugin mode requires process isolation, not merely this ABI.

## v1 host services

- bounded UTF-8 logging;
- open/read/close package file operations rooted in an opaque validated package handle;
- monotonic time in nanoseconds.

Input, audio, display, threading, graphics-provider and asynchronous event services are intentionally not part of v1. Add them only through a minor-version extension or separate ABI after concrete backend requirements are audited.

## Host-side validation implementation

- `backend_abi_validation.hpp/.cpp` validates host and backend API tables before future lifecycle integration.
- Checks include struct size, ABI major/minor, execution backend kind, bounded backend ID/version string views and required callbacks.
- Native conformance tests cover valid tables and representative malformed tables. The Android CMake target compiles this validator.
- This validation is not a sandbox, signature verifier or dynamic loader. Only trusted, provenance-verified shared libraries may ever be loaded in-process.

## Validation

- Public header is compiled as C and C++ by the Android native build. Struct-size helpers and the exported entry-point declaration are part of the header.
- Host-side validation checks struct sizes, ABI major/minor compatibility, execution backend kind, bounded backend ID/version views and all required lifecycle callbacks.
- Native host conformance tests exercise valid tables, missing callbacks, invalid IDs, truncated structs and major-version mismatch.
- The validator does not load shared libraries. Trusted-library loading, lifecycle orchestration, package-file handle implementation and hostile-plugin tests are still required before marking the backend ABI capability available.
