# NGR Development Log

This is the durable project memory. Update it in every development session.

## Session 2026-11-17 — Stage 2 implementation

### Repository
- https://github.com/Snap888/NativeGameRecompiler
- Primary branch: main
- Target: Android arm64-v8a, Qualcomm Adreno.
- Stage 1 foundation: Kotlin host, JNI, C++20 runtime, Vulkan probe, controller discovery, CI workflow.
- Stage 1 APK build is not yet confirmed on CI or device.

### Implemented
- Native capability registry with explicit available, in-progress and planned states.
- JNI exposure and Android UI display of capability report.
- Kotlin NgrManifestValidator for already-extracted package directories.
- Checks: format version, package ID, display name, backend metadata, ARM64 ABI, entry-point existence, safe relative paths, duplicate paths, file existence and SHA-256.
- JVM tests for valid package, path traversal and hash mismatch.
- CI now runs testDebugUnitTest before assembleDebug.
- Fixed InputManager import in the Android host.

### Not implemented / limitations
- ZIP extraction and package installation are not implemented.
- Validator is not a sandbox. Do not execute package payloads until extraction defenses, trust policy, permissions and isolation are designed.
- No static or dynamic game backend is integrated.
- No Turnip provider switching, DXVK, VKD3D, Wine, FEX or Box64 runtime is integrated.
- No successful CI result or APK artifact has been confirmed. Latest queried commit status returned no entries; CI remains unverified.

### CI investigation and correction
- Inspected failed run 44156896963 and its full job log.
- Failure occurred in android-actions/setup-android@v3 before Gradle: the action requested obsolete SDK package 'tools'; sdkmanager reported 'Failed to find package tools'.
- Updated workflow to explicitly request only 'platform-tools'. Commit: e16ac5540790a6cb69fd8242fd3a23da2b62844b.
- Run 44157299377 (commit e16ac554) confirms Android SDK setup, NDK/CMake installation and Gradle setup now succeed. Its JVM unit-test step is currently running; build/artifact result is pending.
- Additional pushes triggered runs 27–30; run 30 includes the latest symlink/test changes and was queued at last check.

### Validation required
1. Inspect run 44157299377 and verify Android SDK setup now succeeds.
2. Fix any subsequent Gradle/Kotlin/CMake/NDK errors.
3. Confirm JVM tests pass.
4. Confirm APK artifact upload.
5. Install on Snapdragon/Adreno hardware and test Vulkan enumeration and controller hotplug.

### Design decisions
- Keep Android system Vulkan loader intact.
- Never claim a backend is supported without implementation and test evidence.
- No claim of universal game recompilation.
- First static-recompiler experiment remains N64/MIPS using N64Recomp, subject to legal test input and metadata.
- Prefer small auditable commits and keep this log current.
