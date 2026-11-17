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


## Session 2026-11-17 — P0 CI diagnosis and Kotlin compile fix

### Repository state
- Latest inspected main commit before this fix: `fa4fccdacb711ab5c7d75fccf035293c115ad807`.
- Main branch exists and the working tree is represented by the live GitHub branch.
- Latest Actions run at audit time: [44157529633](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44157529633), commit `fa4fccdacb711ab5c7d75fccf035293c115ad807`, conclusion: failure.

### CI root cause
- Android SDK setup, NDK/CMake installation and Gradle setup all completed successfully.
- The step labelled "Run JVM unit tests" failed during `:app:compileDebugKotlin`, before tests could execute.
- Compiler diagnostics point to `MainActivity.kt` around line 53: `inputDeviceIds.mapNotNull` could not resolve for the primitive `IntArray`; downstream type inference errors followed.
- APK build and artifact upload were skipped. No APK artifact exists for run 44157529633.

### Change made
- Commit: `eef1bfecab9d50e8cff0812be72be03e3153c33f` — "Fix gamepad device enumeration compilation".
- Changed gamepad enumeration to use `inputDeviceIds.asSequence().mapNotNull { id: Int -> ... }.toList()`.
- Preserved filtering for GAMEPAD/JOYSTICK input sources and existing hotplug listener behavior.
- Files changed: `app/src/main/java/com/ngr/app/MainActivity.kt`.

### Validation status
- The source fix is committed to main.
- A new CI run must complete before claiming Kotlin compilation, unit tests, native build or APK packaging are successful.
- No device-side Vulkan/gamepad test has been performed in this session.
- No APK link is available until a successful artifact is confirmed.

### Next step
1. Inspect the Actions run triggered by commit `eef1bfecab9d50e8cff0812be72be03e3153c33f`.
2. If Kotlin compilation passes, inspect JVM test results and continue through CMake/NDK APK build.
3. Only after green CI, download and verify the `NGR-arm64-debug` artifact.


## Session 2026-11-17 — Stage 2 green; NGRPACK inspection started

### Verified Stage 2 CI
- Latest fully successful Stage 2 build before archive-inspector work: [Actions run 44159630877](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44159630877), run #38, commit `9e320b49891222eaa325bef382fcefee3a772684`.
- Confirmed all steps passed: Android SDK setup, NDK/CMake install, Gradle setup, JVM unit tests, arm64-v8a debug APK build and artifact upload.
- Artifact: `NGR-arm64-debug`, ID `14363545555`, size 1,268,161 bytes, SHA-256 `b72af12f866abb9539e157c55c5b5b9c5ee07d5b2392ed63745afb5293ab2ec9`.
- Device installation, Vulkan runtime probe and physical gamepad hotplug remain untested.

### Manifest validator hardening
- Commit `75a4e3c8585a8d6e844afea8b3327a2903677c58`: strict schema-property/type checks, manifest size limit, display-name/path limits, maximum file count, per-file and total payload size limits, normalized path collision detection, no-follow regular-file checks and package-root symlink rejection.
- Commit `f87405b131310ca806560d874c26278db120b3c6`: corrected Kotlin escape literals in path normalization.
- Commit `a99660f536a73bab6c56bd6a71e79727e61110dd`: added schema parity, display-name limit and slash/backslash duplicate-path regression tests.
- Commit `9e320b49891222eaa325bef382fcefee3a772684`: JSON Schema now expresses entry point/path max length and files min/max count.
- Run #37 (commit a99660f) succeeded, confirming validator regression tests and APK build.
- Run #38 (commit 9e320b4) succeeded, confirming schema-aligned validator and APK build.

### NGRPACK archive inspection implementation
- Commit `58a684b24f103b7e551baf28229053617d0bc976`: introduced read-only `NgrPackArchiveInspector`.
- Commit `2b62e95bf82f9987611524a8ea8a26a0e2f85eaa`: corrected ZIP path escape literals after CI run #39 found unsupported Kotlin escape sequences in the earlier commit.
- Commit `1740619083481550faacc9117c429ff0c925250d`: added archive tests for valid ZIP, ZIP Slip, case-colliding names, compression ratio, Unix symlink metadata and entry-count limits.
- Commit `5a4b0fa34153a40b8317534238ac875b89c380e1`: changed compression-ratio comparison to an exact multiplication bound.
- Inspector checks central-directory structure, rejects multi-disk/ZIP64/encrypted/unsupported-method entries, rejects Unix symlinks and special file types, rejects unsafe/duplicate/case-colliding paths, enforces entry/path/manifest/per-file/total-size/compression-ratio limits, and reads only a bounded root `ngr-manifest.json`.
- The inspector is read-only: it does not extract, install, verify payload hashes against extracted files, or execute anything.
- CI run #39 [44159792179](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44159792179) failed at Kotlin compilation because it tested the initial inspector commit with invalid escape literals. The correction is in commit 2b62e95; runs #40–#42 must be inspected to confirm the corrected source and the new archive tests.
- Do not claim the archive inspector is build-verified until a run containing commit 5a4b0fa or later is green.

### Remaining risks / next steps
1. Verify latest CI for commit 5a4b0fa, especially `NgrPackArchiveInspectorTest`.
2. Fix any compile/test failures before adding extraction.
3. Add central-directory/local-header consistency and malformed-name tests if needed.
4. Implement bounded extraction to a private staging directory; create files with CREATE_NEW, reject existing symlink parents and verify actual streamed bytes against limits.
5. Run full manifest SHA-256 validation after extraction.
6. Implement atomic install/replace with rollback and crash cleanup.
7. Keep unsigned development packages non-executable by default; define signed release trust policy before payload execution.

### Architecture / safety decisions
- NGRPACK inspection is a separate read-only phase; extraction/install is a later phase.
- Do not trust ZIP metadata alone: extraction must count actual streamed bytes and re-check file paths.
- No payload execution during inspect/install.
- ZIP64 and multi-disk archives are intentionally rejected in this first bounded implementation rather than partially supported.
