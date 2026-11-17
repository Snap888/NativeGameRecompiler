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


## Session 2026-11-17 — NGRPACK Package Manager and Android UI

### Verified CI and APK
- Latest code commit: `fc5bc6d8af192c50faeb0382e754af1a8b1136fc`.
- [GitHub Actions run #56](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44161661718) completed successfully.
- Confirmed: Android SDK setup, NDK/CMake installation, Gradle setup, JVM unit tests, arm64-v8a APK build and artifact upload all passed.
- Artifact: `NGR-arm64-debug`, ID `14364325521`, size 1,300,991 bytes.
- Artifact archive SHA-256: `7dcc9170b639ef32cfdca761ada80afb1dba2e04ea0748b57d5c7eb605cf1cff`.
- Artifact expires 2026-12-01. Device installation and runtime tests are not yet performed.

### NGRPACK archive inspector
- `app/src/main/java/com/ngr/app/NgrPackArchiveInspector.kt` added.
- Read-only inspection of ZIP central directory; rejects multi-disk and ZIP64 archives in this first implementation.
- Rejects encrypted entries and unsupported compression methods.
- Checks Unix external attributes and rejects symlinks and non-regular special file types.
- Rejects absolute paths, traversal, empty/dot path components, colon paths, duplicate/case-colliding names.
- Enforces archive size, entry count, path length, manifest size, per-file size, total uncompressed size and compression-ratio limits.
- Requires root `ngr-manifest.json` and parses bounded manifest bytes.
- CI #42 [44159985291](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44159985291) passed with APK artifact.
- CI #39 failed on unsupported escape sequences in the initial source; fixed in commit `2b62e95bf82f9987611524a8ea8a26a0e2f85eaa`. Subsequent runs #40–#42 passed.

### Manifest validator hardening
- `NgrManifestValidator.kt` now matches the JSON schema more strictly, rejects unknown/wrongly typed properties, limits manifest/path/file counts and payload sizes, rejects symlink roots/components and non-regular files.
- Path duplicate detection normalizes slash/backslash separators.
- Every regular package file other than `ngr-manifest.json` must appear in the manifest's SHA-256 file list; unlisted payload files are rejected.
- `schemas/ngr-manifest.schema.json` now defines entry point/path maximum lengths and file-list min/max counts.
- Regression tests cover schema properties, display name length, normalized duplicate paths and unlisted payload files.
- CI runs #37, #38, #48 and #49 passed.

### Package manager core
- `app/src/main/java/com/ngr/app/NgrPackPackageManager.kt` added.
- Source archives are copied into app-private package storage with a streamed size cap before inspection and extraction.
- Extracts only into an app-private staging directory; re-checks paths and streamed byte counts, uses CREATE_NEW, rejects symlink/non-directory parents and verifies entry sizes.
- Re-reads the extracted manifest and runs full manifest + SHA-256 validation before install.
- Updates preserve the old package until the new package validates; replacement uses same-filesystem atomic directory renames and a backup/rollback path.
- Startup recovery restores a backup if a crash happened before the new package rename; stale staging/incoming/trash entries are cleaned.
- Provides installed-package inventory and removal. Corrupt packages are exposed as removable invalid entries; deletion is constrained to a validated package-ID child and does not follow symlinks.
- No package payload is loaded or executed.
- Tests cover valid install/list, hash mismatch, ZIP Slip, update, symlink destination rejection, uninstall, unlisted payload rejection, invalid update preserving old version, interrupted install recovery and corrupt-package removal.
- CI #46 [44160577589](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44160577589), #47 [44160689734](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44160689734), #51 [44160990434](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44160990434), #53 [44161347280](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44161347280) and #56 passed.

### Android package UI
- `MainActivity.kt` now exposes Android Document Picker import, install/update, installed package list and removal.
- Archive copying and package operations run on a single background executor, not the UI thread.
- Invalid installed package folders are displayed with a removal action.
- CI #52 [44161238983](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44161238983) and #56 passed.

### Commits in this stage
- `75a4e3c8` — harden extracted manifest validation.
- `f87405b1` — correct validator path escape literals.
- `a99660f5` — add manifest schema regression tests.
- `9e320b49` — align manifest JSON schema limits.
- `58a684b2` — add NGRPACK ZIP inspector.
- `2b62e95b` — fix ZIP path escape literals after CI failure.
- `17406190` — add ZIP inspector security tests.
- `5a4b0fa3` — exact compression-ratio bound.
- `a3db86e4` — add staged NGRPACK package manager.
- `6f91dbae` — add package manager tests.
- `d4d4285a` — reject unlisted and non-regular payload files.
- `1e07ccc3` — test unlisted package payload rejection.
- `f763f2c7` — bound installed manifest reads.
- `b199b969` — test unlisted payload during install.
- `d6f2fdfc` — add NGRPACK import/list/remove UI.
- `dddce923` — test failed-update preservation and interrupted install recovery.
- `d3ecfb59` — allow safe removal of corrupt installed packages.
- `89924fe7` — expose removal of invalid packages in UI.
- `fc5bc6d8` — test safe removal of corrupt installed packages.

### Known limitations / not claimed
- No physical Snapdragon/Adreno installation or runtime validation has been performed.
- No backend plugin ABI, static recompiler, dynamic translator or game execution backend is implemented.
- No payload is executed; no sandbox exists.
- Package signing/trust policy is not implemented. Current package manager is a data installer only; unsigned packages must not be executed.
- ZIP64 and multi-disk ZIP are deliberately rejected, not supported.
- No automatic rollback UI or per-game configuration yet.

### Next actions
1. Install APK from run #56 on Android and verify launch, Vulkan enumeration, controller discovery and NGRPACK picker.
2. Exercise install, update, corrupt archive rejection, uninstall and restart recovery on device.
3. Define unsigned development vs signed release trust policy and signature format before any payload execution.
4. Review ZIP local-header/central-directory consistency and malformed filename edge cases.
5. Define stable versioned C ABI for backend plugins.
6. Start N64/MIPS N64Recomp proof of concept only after pinning upstream and reviewing licenses/input legality.


## Session 2026-11-17 — Backend ABI v1 contract

### Native audit
- Reviewed current native runtime headers and sources: capability registry, JNI bridge, runtime info, Vulkan probe and CMake target.
- Existing runtime had no plugin ABI, loader or backend lifecycle. Capability registry previously listed backend plugin ABI as planned.
- Decision: ABI v1 is an execution-backend C ABI. N64Recomp remains a build-time static recompiler/toolchain; generated ARM64 output is consumed by an execution backend. Graphics-provider and compiler APIs remain separate contracts.

### ABI v1 design
- Added `native/ngr-runtime/include/ngr/backend_abi_v1.h`.
- ABI major/minor: 1.0; exported symbol name: `ngr_backend_get_api_v1`.
- C-compatible fixed-width status/kind/log types, UTF-8 pointer+length views, struct-size/version fields, opaque package/file handles and function-table lifecycle.
- Host services: logging, package-root-constrained open/read/close and monotonic time.
- Backend lifecycle: probe, create, load, start, concurrent request_stop, destroy.
- No C++ STL, exceptions, Android framework objects, raw package paths or memory ownership across the ABI.
- Added C compilation check `backend_abi_c_check.c`, C++ compile-time check in capability registry and CMake source integration.
- Added ADR `docs/ADR/0003-backend-plugin-abi.md` with version negotiation, lifecycle, thread-safety and trust-boundary decisions.
- Native capability registry now reports NGRPACK validation/installation as available and plugin ABI as planned (contract defined, loader/conformance/lifecycle integration remain).
- Commits: `90886bbe` (header), `152aee00` (C check), `03bf54b7` (ADR), `ff4038c1` (CMake), `a31a6a76` (C++ compile check), `afea9a36` (capability status), `8b3c9f4f` (capability detail), `57d1f542` (entry-point declaration/size helpers), `4c189f61` (ADR lifecycle clarification).
- CI #66 [44162518320](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44162518320) passed C header compilation and native build.
- CI #68 [44162558719](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44162558719) passed the C++ header inclusion/static_assert build.
- CI #70 [44162679511](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44162679511) passed JVM tests, NDK/CMake build, APK packaging and upload.
- Latest source change `d4799d8c` zero-initializes Vulkan structs to remove existing missing-field-initializer warnings; run #71 is pending final verification at log update time.

### Explicitly not implemented
- No `dlopen` plugin loader, registry binding, ABI negotiation runtime or plugin lifecycle implementation.
- No external plugin package format, signature verification or sandbox.
- NGRPACK game data is never treated as a backend shared library.
- ABI v1 is a contract/header compile-checked by CI, not yet a functioning plugin system.

### Next ABI work
1. Complete CI for latest native warning cleanup.
2. Implement host-side ABI validation (major version, struct_size, backend kind/ID and required function pointers).
3. Add a trusted built-in/test backend fixture for conformance tests; do not load NGRPACK payloads.
4. Implement serialized lifecycle and cancellation semantics, including waiting for start to return before destroy.
5. Add tests for ABI mismatch, truncated structs, missing callbacks, invalid strings, file-handle boundaries and stop/destroy ordering.
6. Keep plugin ABI capability planned until loader and conformance tests pass.


### Final ABI/native validation update
- Commit `d4799d8c0ca0deed9395fa58bedc75fbea0d39ca`: zero-initialize Vulkan application/instance create structs and assign `sType` explicitly, removing the existing missing-field-initializer warnings.
- CI run #71 [44162883678](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44162883678) passed JVM unit tests, C/C++ native build, arm64-v8a APK build and artifact upload.
- Artifact: `NGR-arm64-debug`, ID `14364007052`, size 1,301,432 bytes, archive SHA-256 `caeb05249b81f9543cd9868601419e59ce14deec5c41c647ac6680a26af261fc`.
- The ABI v1 header is C- and C++-compile-checked; this is not evidence of a functioning loader. Plugin ABI capability remains planned.
- No physical Android device test has been completed.


## Session 2026-11-17 — Device-test feedback: diagnostics and NGRPACK import diagnosis

### User device observations
- APK starts successfully on Android ARM64.
- Vulkan enumeration reports Qualcomm Adreno 740, Vulkan API 1.3.128.
- Android input discovery reports a GameSir-X4A controller.
- NGRPACK import currently fails with: `Archive must contain root ngr-manifest.json`.
- No package is installed. The exact archive contents have not yet been supplied, so the root cause cannot be conclusively attributed to the archive or SAF picker.

### Changes implemented
- Added `NgrLogger`, a private persistent diagnostic logger:
  - UTC timestamps, severity, category, thread name and per-launch session ID.
  - Logcat mirror and private file `files/logs/ngr-runtime.log`.
  - 2 MiB active-file cap with one rotated previous file.
  - In-app recent-log viewer and Android Storage Access Framework export to a user-selected destination.
  - Export/view combines current and rotated logs.
- Added diagnostics around app lifecycle, runtime refresh, Vulkan enumeration, gamepad enumeration/hotplug, SAF selection/copy, NGRPACK inspection/install stages, package inventory and removal.
- Added actionable NGRPACK inspection diagnostics: when a manifest is nested below a wrapper directory, the error now reports the discovered path and instructs the user to repack with `ngr-manifest.json` at ZIP root; otherwise it reports top-level ZIP entries.
- Added a JVM regression test for a manifest wrapped in a top-level folder.
- No archive format relaxation was made: root manifest remains mandatory to preserve the documented NGRPACK layout and avoid ambiguous extraction roots.

### Validation status
- Source changes are committed to `main`; CI for this diagnostics stage is pending at log update time.
- The device report confirms Vulkan and controller discovery work on the user's handset.
- NGRPACK still needs a retest with the new diagnostic build. If it fails, export **Save log to storage** and provide the log; the inspector will now include the archive's top-level entries and nested manifest candidates in the failure text.
- No game backend is integrated; package payloads remain data-only and are not executed.

### Next actions
1. Confirm JVM tests and arm64 APK build in GitHub Actions.
2. Install the diagnostics APK and retry the same NGRPACK archive.
3. If root manifest is nested, repack the package with manifest at ZIP root; if no manifest is present, generate a valid manifest and SHA-256 inventory.
4. If import still fails, inspect exported `ngr-runtime-log.txt` and the full on-screen error before changing archive compatibility.
5. Continue backend ABI v1 loader only after the device-test feedback loop is stable.


## Session 2026-11-17 — Folder and ordinary ZIP game import

### User request
- User asked to remove the need to manually provide `ngr-manifest.json` and support adding games either from a folder or an archive.
- Decision: keep the manifest mandatory inside NGR's private package store, but generate it automatically for user-selected game folders and ordinary ZIP archives. Existing NGRPACK remains supported as the explicit prebuilt-package path.

### Implementation committed
- Added `NgrGamePackageBuilder.kt`:
  - Copies a selected game folder into a bounded staging tree or inspects/extracts an ordinary ZIP.
  - Unwraps a single top-level wrapper directory when present (e.g. `Lethal Company/`).
  - Rejects traversal paths, symlinks/non-regular files, duplicate ZIP output paths and excessive file/entry/total sizes.
  - Detects Windows PE executables and Linux ELF binaries; reads PE machine and ELF e_machine to identify common architectures.
  - Generates an internal formatVersion 1 manifest, package ID, source platform/architecture, entryPoint, per-file SHA-256 inventory and provenance.
  - Produces a temporary NGRPACK and hands it to the existing validated installer; it never executes imported code.
- Updated ZIP inspector with an explicit `requireManifest=false` mode for ordinary ZIP validation. The default remains true, so NGRPACK validation behavior is unchanged.
- Added Android SAF actions for selecting a folder tree and an ordinary ZIP, while retaining the NGRPACK picker.
- Folder SAF copy is bounded by archive file count, per-file size and total payload size; unsafe names and case-colliding paths are rejected.
- Added JVM tests for manifest-optional ZIP inspection, generated package from folder, wrapper-folder ZIP import, post-generation manifest/hash validation and missing-executable rejection.
- Added AndroidX DocumentFile for SAF tree traversal.

### CI findings
- Initial builder CI run #97 failed at Kotlin compilation:
  - unsupported backslash character-literal escaping;
  - collection append compiled as reassignment.
- Fixed the character-literal escaping and changed collection insertion to `files.add(file)`.
- Added valid PE test fixture bytes and stricter PE signature validation; common architecture detection is based on PE machine / ELF e_machine.
- Latest verification commit at log time: `0f3e20d810dbb6598a2da6695d3f7368ca940a24`; Actions run #102 is in progress. Do not claim this stage build-green until the run finishes successfully.

### Current limitations
- The builder selects a deterministic likely entry point automatically; a user-facing chooser for multiple executable candidates remains to be implemented.
- Only ZIP and folders are supported in this stage. 7z/TAR are not implemented.
- PE/ELF detection is classification only, not a runtime compatibility test.
- NGR still has no integrated Windows x64 or Linux x86_64 game backend. Importing Lethal Company does not mean it can run on Android.
- Imported files remain data-only; no payload is executed or loaded as a backend plugin.

### Next actions
1. Confirm JVM tests and arm64 APK build in the latest Actions run.
2. Fix any further compile/test failures before distributing an APK.
3. Device-test folder import and ordinary ZIP import with the user's Lethal Company archive.
4. Add a candidate list and explicit primary executable selection when multiple PE/ELF files are found.
5. Consider archive progress/cancellation and persistent import metadata after device validation.


### Follow-up compile corrections
- CI run #102 reached Kotlin compilation and reported one remaining unsupported backslash character literal in `MainActivity.kt`; corrected it in commit `c47aace3ac7d024c8b388b313596dbfd66548fda`.
- Added explicit 64-level nesting bounds to both local folder copying and SAF folder traversal (`33812dfa`, `70892ab8`).
- Added Linux ELF x86_64 detection regression coverage in commit `8d06364c`.
- Latest commit at this update: `c47aace3ac7d024c8b388b313596dbfd66548fda`; Actions run #108 is pending. CI is not yet green.
