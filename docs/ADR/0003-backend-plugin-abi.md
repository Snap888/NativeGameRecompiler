# ADR 0003: Versioned C ABI for execution backend plugins

**Status:** Accepted for ABI v1 design; loader and runtime integration are not implemented.

## Scope

ABI v1 defines a stable C boundary for trusted execution-backend shared libraries. It deliberately does not expose C++ classes, STL types, exceptions, RTTI, Android framework objects or Vulkan handles across the plugin boundary.

N64Recomp is a build-time static recompiler/toolchain, not an execution backend. Its generated ARM64 package will be loaded by an execution backend/runtime. ABI v1 therefore targets execution backends such as a future native ARM64 runner or dynamic translation runtime. Compiler/toolchain APIs and graphics-provider APIs require separate versioned contracts.

## Contract

- Exported symbol: `ngr_backend_get_api_v1`.
- ABI major/minor: 1.0. Major mismatch is fatal; additive minor extensions append fields and are guarded by `struct_size`.
- Every public struct starts with `struct_size`; API tables also carry ABI major/minor.
- Fixed-width integer types and explicit UTF-8 pointer/length string views only.
- No memory ownership crosses the boundary except caller-provided buffers and opaque handles.
- No C++ exception may cross the ABI. All callbacks return `NgrStatusV1`; destroy is non-throwing.
- Host file access is mediated by package/file handles and relative paths. Plugins do not receive raw package-root filesystem paths.
- Host API callbacks are thread-safe. Backend lifecycle calls are serialized except `request_stop`, which must be safe concurrently with `start`.
- String views passed into a call are borrowed for that call only. Backend ID/version views returned in the API table must remain valid for the plugin library lifetime.
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

## Validation

- Public header is compiled as C and C++ by the Android native build.
- Loader conformance tests, version negotiation tests, lifecycle tests and hostile-plugin tests are required before marking the backend ABI capability available.
