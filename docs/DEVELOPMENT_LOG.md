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
- Latest code commit: `c47aace3ac7d024c8b388b313596dbfd66548fda`; Actions run #108 [44170793636](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44170793636) passed JVM unit tests, arm64-v8a APK build and artifact upload.


### Folder/ZIP import CI result
- Actions run #108 completed the required test, native build, APK build and artifact upload steps successfully for code commit `c47aace3ac7d024c8b388b313596dbfd66548fda`.
- Artifact: `NGR-arm64-debug`, ID `14367787571`, size 1,335,563 bytes, SHA-256 `6d530d4c4436c2b39f25236e1a95b56f86436d8fbc8289ead6fdae4904d93357`; expires 2026-12-01.
- This confirms CI compilation and JVM tests only. Physical-device folder/ZIP selection and Lethal Company import remain untested.


### Repeat-import update semantics
- Follow-up audit found that random package IDs would create duplicate library entries when the same game was imported again.
- Package IDs are now deterministic from normalized game name plus selected entry-point path, so reimporting the same game updates its existing package rather than creating another entry.
- Added a JVM regression test that builds/imports the same folder twice and asserts a single installed package remains.
- Code commit: `606b50a8`; test commit: `cc65f609`. A fresh CI run is required before distributing this revision.


### Stable-ID regression CI result
- Actions run #113 [44171433744](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44171433744) passed JVM unit tests, arm64-v8a APK build and artifact upload for code commit `cc65f609e5c3e92af80b5fcdadd65cd7c337aa64`.
- Artifact: `NGR-arm64-debug`, ID `14366828932`, size 1,335,715 bytes, SHA-256 `59b2916d8257869641b19317a6bc061ed9c0caa7d7ec3dd9383a954fc1ba966a`; expires 2026-12-01.
- The repeat-import regression test passed: importing the same game folder twice keeps one installed package identity and exercises the package manager update path.
- This remains CI/JVM evidence. Real SAF folder/ZIP import and Lethal Company package installation still require testing on the user's Android device.


## Session 2026-11-17 — Lethal Company device import feedback and entry-point correction

### Device evidence supplied by user
- Folder import copied 270 files / 946,666,949 bytes, generated a valid NGRPACK, passed archive inspection, extraction, manifest validation and payload SHA-256 checks, and committed successfully.
- Inventory reported one valid installed package and zero invalid packages.
- Ordinary ZIP import of the same game completed the same pipeline successfully; it resolved to the same deterministic package ID and installed as an update rather than a duplicate.
- Removal also completed successfully.
- Important defect: both imports selected `unins000.exe` as entryPoint and classified it as Windows x86. This is an uninstaller, not the game executable. Package integrity is proven; correct game entry-point selection is not.

### Corrective change
- Added explicit exclusion rules for common installer, uninstaller, redistributable and crash-handler executables.
- Candidate ranking now prefers an executable whose normalized filename matches the selected game name, then shipping binaries, then root-level candidates; architecture and path are used as deterministic tie-breakers.
- Builder logs the detected candidate count, usable candidate count, selected path/architecture and up to 20 candidate names.
- If only installer/uninstaller/helper executables exist, import now fails with a specific diagnostic rather than silently installing an unusable package.
- Added JVM regression tests: x86 uninstaller + x64 game executable selects the game executable; uninstaller-only folder is rejected; missing-executable assertion updated.
- Changes: `87b9b34d` (selection heuristic), `bc79495f` (regression tests), `a16e483d` (diagnostic assertion).
- CI for these changes is pending; do not distribute a new APK until it passes.

### Remaining work
1. Verify the new candidate selection against a real Lethal Company folder/ZIP; if the game EXE is absent or not recognized, inspect the actual candidate list from the new log.
2. Add a user-facing executable chooser when multiple plausible game binaries remain.
3. Preserve the selected entry point on reimport and expose it in the installed package details.
4. Only after correct entry-point selection, proceed to Windows x64 backend implementation; package import itself does not execute the game.


### Entry-point fix: device retest and CI confirmation (2026-11-17)

- User retested the ordinary ZIP import of Lethal Company on Android after the entry-point selection fix.
- Builder diagnostic: `detected=5; usable=1; selected=Lethal Company.exe (windows/x86_64)`; only the game executable remained usable after installer/uninstaller/helper filtering.
- ZIP conversion recorded `platform=windows; architecture=x86_64; entryPoint=Lethal Company.exe`.
- NGRPACK inspection passed: 271 entries, approximately 946.7 MB uncompressed; manifest accepted; extraction and SHA-256 validation completed; install committed.
- Inventory: valid=1, invalid=0, errors=0. User also confirmed package removal and subsequent re-import; deterministic package ID `import.lethal-company.e8aa165a41c0` was reused.
- This is real-device evidence for ZIP import, executable detection, x86_64 classification, package install/update/remove and inventory. It is not evidence that the game launches; no Windows x64 execution backend exists yet.

### CI confirmation for entry-point fix
- Actions run #122 [44175441095](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44175441095) completed successfully for main commit `61910de7a7912aaeafa8d05a2961a97cc4001db3`.
- JVM unit tests, arm64-v8a debug APK build and artifact upload all succeeded.
- Artifact: `NGR-arm64-debug`, ID `14370371440`, size 1,337,720 bytes, SHA-256 `dbf8bd96292aab37797b6c6a85461c7bf1f9a89f02b63cc35ab4452c4773619d`; expires 2026-12-01.
- Runs #118–#122 are green; latest run #122 includes installer/uninstaller filtering, regression tests and development-log updates.

### Next engineering priorities
1. Add an explicit executable chooser for archives with multiple plausible PE/ELF candidates; show architecture and selected path before install.
2. Show installed package entry point, platform and architecture in package details, preserving/validating them on update.
3. Add archive local-header/central-directory consistency and malformed filename tests.
4. Continue backend ABI loader/conformance work only after keeping package import stable. NGR still does not launch Windows games.


## Session 2026-11-17 — Executable chooser and installed package details

### Implemented on main
- `NgrGamePackageBuilder` now returns the complete ranked list of detected usable executable candidates, including relative path, platform and architecture.
- Folder and ordinary ZIP builder entry points accept an optional explicit executable path. The selected path must match a detected usable PE/ELF candidate; arbitrary paths are rejected.
- When an import contains multiple usable executable candidates, Android UI displays a chooser with each candidate path and platform/architecture. The user selection is applied by rebuilding the internal NGRPACK manifest and SHA-256 inventory before installation. Canceling the chooser discards temporary import data.
- Single-candidate imports retain the previous automatic-selection flow.
- Installed package metadata now exposes optional `sourcePlatform` and `sourceArchitecture` from the validated manifest backend metadata.
- Package inventory now has a Details action showing game name, package ID/version, selected entry point, source platform/architecture, backend ID/version, private package path and the explicit data-only / no-backend status.
- Added JVM regression coverage for candidate enumeration, explicit entry-point selection, install validation and persisted source platform/architecture metadata.

### Commits
- `d1f46bba890b3376bcee75942834d833cc0f8e1b` — expose executable candidates and explicit selection in builder.
- `31238d6ba5ce2272c9b2667ad83beb4f2524b5c6` — expose source platform/architecture in installed metadata.
- `85e9b6e7d753456894258e32504fdbb41d885ef6` — add Android chooser and package details UI.
- `5d2b3b9f468ca81e834b4cf33ac3d8401adbea5a` — guard temporary import workspace ownership during asynchronous selection.
- `6b546a682a316cdc96a1d96c96065ebe6599be14` — add candidate/selection/metadata regression test.

### Validation status
- GitHub Actions run #129 [44178745011](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44178745011) was triggered for the test commit and is in progress at log update time.
- Do not distribute an APK or claim build success until JVM tests, arm64-v8a APK build and artifact upload complete successfully.
- Device validation of the new multi-candidate dialog and Details screen remains pending.
- The feature still does not launch games; imported packages remain data-only until a compatible execution backend is implemented.

### Next actions
1. Inspect run #129; fix compile/test issues before APK distribution.
2. Install the green build and verify Details shows `Lethal Company.exe`, Windows, x86_64.
3. Test a folder/ZIP containing at least two usable EXEs and confirm the chooser selection persists in the installed manifest/details.
4. Continue ABI v1 host-side negotiation/validation and test backend fixture only after this import UI stage is stable.


### CI result — executable chooser stage
- Actions run #129 [44178745011](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44178745011) completed successfully for code/test commit `6b546a682a316cdc96a1d96c96065ebe6599be14`.
- Confirmed all steps passed: JDK/Android SDK/NDK/CMake/Gradle setup, JVM unit tests, arm64-v8a debug APK build and artifact upload.
- Artifact: `NGR-arm64-debug`, ID `14371675507`, size 1,344,874 bytes, SHA-256 `525ef7b9e040b6fdcb344c3a6c0ec24f0112ef24e59e8698e52152a047dea173`; expires 2026-12-01.
- The candidate enumeration/explicit selection regression test passed, including persisted Windows/x86_64 source metadata after package installation.
- CI confirms compilation and JVM behavior only. The new chooser and Details UI still require device validation with a multi-executable archive.


## Session 2026-11-18 — WinNative runtime reuse and Environment Manager architecture

### User direction
- Reuse WinNative's ready Windows-on-Android environment to accelerate ordinary Windows game launch through Wine.
- Bring across the environment settings and component management, not just the Wine executable: containers/prefixes, Wine/Proton layers, Box64, FEX/FEX unix libraries, DXVK, VKD3D, Turnip/Adreno drivers, ReShade, shader packs/presets, audio/input/display and environment variables.
- Keep static recompilation as a separate execution backend. A statically recompiled ARM64 title must be able to run without Wine.

### NGR source audit
- Re-read live main README, technical spec/audit, next actions, Kotlin package manager/import builder, JNI/native CMake and capability registry.
- NGR currently has Android ARM64 host, Vulkan enumeration, gamepad discovery, NGRPACK validation/import/install and execution-backend ABI v1 header.
- No Wine, Box64, FEX, DXVK, VKD3D, Turnip provider manager, ReShade or game execution backend is implemented.
- NGR package manager explicitly installs data only and never executes or loads imported payloads. This invariant remains mandatory.

### Donor research — verified
- WinNative README describes an Android Windows x86_64 emulation app and links build, Wayland and attribution docs.
- WinNative build guide identifies JDK 17, compileSdk 35, NDK 27.3.13750724, CMake 3.22.1, Git LFS and recursive submodules.
- WinNative native CMake includes Winlator native library, Vulkan helpers, AdrenoTools, Wayland compositor, PRoot and audio shims.
- WinNative Wayland documentation requires Adreno and a compatible Wine/Proton layer containing matching winewayland and Wayland Turnip libraries; X11 remains the initial compatibility path.
- WinNative is GPL-3.0; component licenses must be tracked independently.
- Winlator Bionic lineage confirms standard x86_64 + Box64 and ARM64EC + FEXCore paths; FEX unix libraries and optional WOWBox64 are distinct artifacts.
- Public component catalogs show WCP-style distribution for Wine/Proton, DXVK, VKD3D, FEXCore, Box64 and Turnip. A ReShade catalog exists, but catalog presence does not prove ReShade injection/runtime activation in the donor APK.
- References: https://github.com/Snap888/WinNative, https://github.com/nicholasx417/WinNative-Components, https://github.com/The412Banner/winlator-contents.

### Architecture decisions
- Create NGR Environment Manager as a first-class subsystem.
- Keep Wine compatibility, static recompiled and native ARM64 execution modes independent.
- Model standard x86_64/Box64 and ARM64EC/FEX as distinct validated configurations.
- Use typed component catalogs with exact version, target ABI, source, license, checksum, compatibility constraints and rollback metadata.
- Keep Android system Vulkan untouched; Turnip is isolated per environment/game.
- X11 first; Wayland only when the selected Wine/Proton and Wayland Turnip binaries are a matching compatible set.
- ReShade/shader support is gated on source audit and a real injection compatibility test.
- Do not copy GPL code until NGR licensing and all transitive licenses are reviewed.

### Documentation
- Added `docs/WINNATIVE_INTEGRATION_PLAN.md` with donor evidence, verified/unverified boundaries, architecture, security requirements, staged implementation and acceptance criteria.
- Updated technical specification, next actions and continuation guidance to make Environment Manager and WinNative runtime reuse part of the official NGR roadmap.

### Current implementation status
- This session is architecture and documentation only. No WinNative source files or binaries have been copied into NGR.
- No Wine/Box64/FEX/DXVK/VKD3D/Turnip/ReShade runtime is implemented or build-verified.
- GitHub code search did not return donor source matches in this pass; exact donor class/file mapping remains an explicit next audit item. Do not claim the source-level port audit is complete.
- CI result for documentation commits is pending/has not been inspected. No new APK is claimed.

### Next actions
1. Complete file-by-file WinNative source audit for container/launch/catalog/WCP/settings/driver/ReShade subsystems.
2. Create upstream commit pins and SPDX/license/provenance matrix.
3. Implement versioned NGR environment profile/catalog models and JVM tests before runtime binary integration.
4. Proceed with Wine + Box64 standard x86_64 launch path, then ARM64EC/FEX and graphics components.
5. Continue the static recompiler track independently.


### Environment profile model — initial implementation
- Added `NgrEnvironmentProfile.kt`, a versioned declarative model for container mode, Wine layer, CPU translator, Box64 preset, FEXCore/WOWBox64 references, DXVK/VKD3D, Vulkan provider/Turnip, X11/Wayland prerequisites, ReShade/shader packages, audio, desktop size/DPI, environment variables and per-game overrides.
- Validation keeps standard x86_64 + Box64 separate from ARM64EC + FEXCore; requires a FEXCore component for ARM64EC; gates Wayland on compatible Wine and Turnip declarations; and prevents ReShade presets without an enabled ReShade component.
- Environment variable validation rejects runtime-managed names (LD_*, NGR_*, PATH, HOME, PREFIX), malformed keys, NULs, excessive values and excessive variable counts.
- Added `NgrEnvironmentProfileTest.kt` covering these compatibility and validation rules.
- This is a configuration model only: no component catalog downloader, installer, Wine process launcher or ReShade injector is present yet.
- Source commits: `0091a8526f097a7c28c14a96cc6e33653d6294b0`; tests: `5969959ba40a2c7e75c6f625ca023b5e76e62198`.
- Native capability registry now explicitly lists Wine runtime, Environment Manager, FEXCore/ARM64EC, Wayland and ReShade as planned (not available): `fc742d1b4cf356e3228fcf79abbeaabb0c285060`.
- CI for the new Kotlin model/tests must pass before this milestone is marked build-verified.


## Session 2026-11-18 — WinNative source audit and component catalog model

### Repository and donor pin
- NGR main head at session start: `44cddb6f80757e89542e8b5e89f8a998c355690c`.
- WinNative donor tree inspected at commit `99c3305d163780de5ca39b493aefcde436acad5c` (tree SHA).
- The donor audit is now source-based for the files listed below; it is still not a complete transitive dependency/license audit.

### Source-level findings
- Container model and lifecycle: `app/src/main/runtime/container/Container.java` and `ContainerManager.java`. Container configuration is a persisted JSON-backed set of Wine version, screen size, environment variables, DX wrapper, graphics driver, audio driver, Box64/FEXCore versions and presets, drives, CPU lists and per-game executable/arguments. Container activation changes an active-user symlink; NGR must not copy that global mutable activation design without isolating each profile.
- Proton catalog/install: `runtime/linux/LinuxProtons.kt`. Proton builds are represented with ID/name/URL/SHA-256/size and installed into Steam compatibilitytools.d; this is a useful provenance pattern, but NGR still needs an immutable typed catalog and safe staging/rollback.
- CPU translation: `runtime/compat/box64/Box64PresetManager.java` and `runtime/compat/fexcore/FEXCoreManager.java`; Container persists separate Box64 and FEXCore presets/versions. Do not model them as interchangeable flags.
- Graphics drivers: `runtime/system/GraphicsDriverCatalog.kt` queries built-in driver support and AdrenoTools installed drivers. This confirms an existing provider abstraction, not permission to alter Android's system Vulkan loader.
- Wayland: `runtime/display/wayland/WineWaylandSupport.java` requires Adreno and matching Wine `winewayland.so` plus Wayland Turnip ICD/library. It explicitly warns against copying winewayland.so from a different Wine build. NGR's gate must verify actual installed artifacts, not accept profile booleans as proof.
- ReShade: `runtime/reshade/ReshadeManager.java` scans local effect folders and reflects .fx uniforms; `ReshadeDownloader.kt` downloads effect archives. The downloader currently uses optional MD5, extracts directly into the live effect root and deletes an existing folder before extraction; this is not suitable as-is for NGR's trusted component installer.
- Component recipes: `runtime/content/component/ComponentInstaller.kt` supports recipe-driven downloads, extraction, DLL copies/overrides and registry changes. Its ZIP extraction joins entry names directly under the output directory without a canonical-path containment check, and destination resolution can produce absolute paths. NGR MUST NOT port this extraction/path-resolution code; build a new bounded, path-safe staging installer.
- Environment variables: `runtime/wine/EnvVars.java` stores ordered KEY=value pairs and has an escaped serialization form. NGR needs explicit validation and a structured map rather than copying its space-split parser.

### NGR implementation
- Added `NgrComponentCatalog.kt`: typed component IDs/types, versions, upstream commit, target ABI set, archive format, HTTPS download/source URLs, SHA-256, SPDX identifier, dependencies/conflicts, compatibility constraints, install subdirectory and rollback slot.
- Catalog validation rejects duplicate/unsafe IDs, unsupported schema, absent target ABI, non-HTTPS or credential-bearing URLs, malformed SHA-256, invalid license identifiers, unsafe relative install paths, invalid commit IDs, self-dependencies/conflicts and unknown dependency/conflict references.
- Added `NgrComponentCatalogTest.kt` covering valid metadata and the validation/security cases above.
- This is a metadata model only. No catalog parser, downloader, archive extractor, installer, activation, rollback or runtime loader is implemented.

### CI
- Previous source/test baseline commit `5969959ba40a2c7e75c6f625ca023b5e76e62198` is covered by successful Actions run #142: `44180861281`.
- Catalog model commit `492282799227bf424a619e9a3dc68112ed2b3323` triggered run #145 `44181554144`.
- Catalog test commit `38f64afef93eec3f87cc55b80a049664bd791f2b` triggered run #146 `44181583098`.
- Runs #145 and #146 were still in progress at journal update time. Do not mark the catalog model/tests CI-verified until #146 is green.
- No APK is claimed for the catalog stage until a successful artifact is confirmed.

### Next
1. Confirm run #146 JVM tests, arm64 APK build and artifact upload; fix any failure.
2. Extend catalog validation with dependency-cycle detection and catalog JSON parsing tests.
3. Define archive extraction limits and safe install staging independently of donor code.
4. Finish source mapping for launch pipeline, Wine layer resolution, driver install/activation, Box64 settings and ReShade injection.
5. Produce SPDX/provenance inventory from WinNative LICENSE, CREDITS, submodules and each binary/component upstream before copying code or binaries.


### CI correction — Kotlin path literal regression
- Run #145 (`44181554144`) and run #146 (`44181583098`) both reached `:app:compileDebugKotlin` and failed because the initial catalog model contained invalid Kotlin escape sequences in the Windows-separator/NUL path checks. No unit tests ran and no APK was produced by either run.
- Corrected the checks to use a valid escaped backslash character and `0.toChar()` for NUL detection; restored the missing path-segment validation after reviewing the edited function.
- Added a regression test for Windows-style traversal paths.
- Fix commits: `94f0ccdc11052ec90d4febe7595d09fe426089f6`, `ee33c0084849700e590d33f016052dacf7bc06a0`, and test `f9b2b5bd6b6247864355d2d3f2b0cfa458af6dc9`.
- Runs for the corrected commits were queued/in progress at the time of this note. The catalog feature remains CI-unverified until the latest run passes JVM tests and APK packaging.
