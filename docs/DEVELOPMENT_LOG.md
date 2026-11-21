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


- Added `tools/switch/build_runtime.py`: pins nx2android revision `f32b3d563f0615d0e825747285d4e38d780e4ebb`, invokes its actual `dm/android/build.py` for a matching title profile, checks the AArch64 NativeActivity entry point and places `libdmhost.so` in NGR's per-title runtime folder. It never calls the donor APK writer.
- Added `SwitchRuntimePackageVerifier.kt`: checks the pinned donor revision, title ID, ABI, entry point, non-APK marker and runtime SHA-256 before the launcher opens the library. The profile-header hash is required in the receipt; full package/profile validation remains a separate installer responsibility.
- Added a dedicated GitHub Actions smoke workflow that fetches the pinned donor checkout and builds a profile-only runtime for compile/link validation. Its artifact is not a playable game and contains no commercial game files.
- CI for the new runtime build and Android NativeActivity loader is pending; do not call this stage build-verified until both workflows pass.


- Follow-up hardening: runtime launch now requires the JSON build receipt and checks the actual library SHA-256; malformed title IDs and hash mismatches have regression tests.
- Added the dedicated smoke workflow at `.github/workflows/switch-runtime-smoke.yml`; it checks out nx2android at the pinned revision and runs the real donor host builder for a profile-only smoke output, without invoking APK packaging.
- Latest implementation commit at this journal update: `3b44bee839cc93db3c02ed43445c01071a6adbfe`. CI result has not yet been confirmed.

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


## Session continuation — catalog parser and secure ZIP component staging (2026-11-18)

### CI findings and fixes
- Run #155 (`44184022132`) passed after restoring the missing `java.net.URI` import. JVM tests, ARM64 debug APK and artifact upload all succeeded.
- Run #166 (`44184560868`) passed at `ca08211817af5f0a8ed7fc5216bd395c7140faad`. JVM tests, ARM64 debug APK and artifact upload all succeeded. Artifact ID `14374685465`, SHA-256 `3191e73181fec3bc57c3db651cc37b1d353bbbb61dc2daebf278725dce154408`.
- Intermediate failures #157, #159, #160 and #161 were caused by a test assertion comparing `List<Int>` with `List<Byte>`; the implementation's extraction counts and file output were otherwise successful. Corrected the test to compare `List<Byte>` in commit `ca082118`.
- Run #166 predates the later snapshot/execute-bit hardening. Do not claim its APK contains the later changes.

### Catalog parser and dependency graph
- Added `NgrComponentCatalogParser.kt`: bounded JSON input, strict root/component/constraint keys, enum validation, typed arrays/booleans, optional field handling and post-parse catalog validation.
- Schema version must be an integer; unknown fields and invalid enum values are rejected.
- Added parser tests for valid input, unknown keys, invalid enum/boolean types, oversized JSON and non-integer schema versions.
- Catalog validation now uses Kahn's topological algorithm to reject dependency cycles; added a cycle regression test.

### Component ZIP staging installer
- Added `NgrComponentArchiveInstaller.kt`, initially ZIP-only.
- Before inspection/extraction, copies the input into a bounded app-private snapshot; SHA-256, central-directory inspection and extraction all operate on that same snapshot to avoid validation/extraction TOCTOU against a mutable input file.
- Reuses `NgrPackArchiveInspector` with component-specific entry/file/total-size/compression-ratio limits; no NGRPACK manifest is required for component archives.
- Extracts only into a newly created staging directory, enforces canonical containment, rejects duplicate targets and size mismatches, uses create-new output files, syncs writes, removes failed staging trees and deletes the private snapshot.
- Catalog ZIP Unix mode metadata now exposes the executable bit; the extractor preserves owner-executable permission for validated regular files. Symlinks and special files remain rejected by the shared inspector.
- Installer validates component ID and relative install subdirectory before using them.
- Added tests for valid extraction, hash mismatch, Zip Slip, unsupported formats, unsafe install paths and executable-bit preservation.
- This does not yet download components, parse TAR variants, validate component-specific expected layouts, activate a version, or implement rollback/uninstall. Extracted staging output is not loaded or executed.

### Current CI gate
- Successful checkpoint #166: `ca08211817af5f0a8ed7fc5216bd395c7140faad`.
- Later hardening commits: `b821af83`, `75139b96`, `b803e8e8`, `d1b6436e`, `e3cc8ca9`, `2d4dd105`.
- Latest test fix and subsequent snapshot/executable-mode changes have new Actions runs queued/in progress. Confirm the latest main commit's JVM tests, ARM64 APK build and artifact upload before issuing a new APK.
- The donor-source audit and component security design remain bounded; no WinNative runtime binary or donor code has been copied into NGR.

### Next actions
1. Verify latest CI after private snapshotting and executable-mode preservation; fix all test/build failures.
2. Add a trusted component catalog fixture and dependency-resolution plan output (download/install order, conflicts and ABI gates).
3. Implement HTTPS download with redirect policy, timeouts, bounded streaming and SHA-256 before extraction.
4. Add safe TAR.GZ/TAR.XZ/TAR.ZSTD handlers only with audited decompression libraries; otherwise reject those formats explicitly.
5. Validate component-specific expected layout and required files before atomic promotion.
6. Implement per-version install store, activation pointer, rollback and uninstall without touching Android system Vulkan.
7. Continue exact WinNative launch-pipeline and license/provenance audit before porting runtime binaries or source.


### CI verification — ZIP staging implementation
- Run #172 (`44184927065`) completed successfully for commit `2d4dd105f6c8e5ab4f16f4bc42da61d3ff32a620`.
- Confirmed JVM unit-test task, ARM64 debug APK build and artifact upload all succeeded. The test log includes the catalog parser and component archive installer suites, including executable-bit preservation.
- Artifact: `NGR-arm64-debug`, ID `14373866839`, SHA-256 `487fef223d1c011e6008d1a8fd3ab8192cab5090a1f0f0ed5635eb77b0ea5f50`, expires 2026-12-01.
- Replaced deprecated test-only `createTempDir` calls with `Files.createTempDirectory` in commit `7955754a4d2c303cb4495436154e7d12a2fb5b97`; its own CI run is pending. This is a test-fixture hygiene change only; production code is unchanged from the successful #172 build.


### HTTPS component downloader — 2026-11-18
- Added `NgrComponentDownloader.kt` and declared Android INTERNET permission.
- Downloader accepts only HTTPS URLs without credentials/fragments, disables automatic redirects, follows a bounded number of redirects only when each target remains HTTPS, rejects encoded HTTP responses and non-200 status codes, applies connect/read timeouts, enforces declared and streamed byte limits, checks Content-Length consistency and verifies SHA-256 while writing.
- Downloaded bytes are written to a temporary file in the supplied app-private directory; failed downloads remove the temporary file. A successful result is a verified file only: no extraction, installation, activation or execution occurs here.
- Added URL policy and preflight rejection tests. Live network behavior, redirect servers and TLS failure cases still need dedicated integration tests.
- Code commits: downloader `caca5fd0`, manifest permission `56880434`, output-file correction `2c3c0ffc`, URL tests `427b32f0`, strict HTTP 200 handling `ef2ef3c6`.
- CI for downloader/manifest commits is pending; do not claim the new downloader is verified until the latest workflow passes.


### Component version store, install pipeline and dependency resolver — 2026-11-18
- Added `NgrComponentStore.kt`: private staging/download directories, versioned component store, atomic directory promotion, atomic active/previous state file, rollback toggle, guarded removal of inactive versions, uninstall and payload-path validation. Symlinked store directories and non-regular staged payloads are rejected.
- Added `NgrComponentInstallPipeline.kt`: combines verified archive extraction and atomic activation; also exposes a download-and-install path using the HTTPS downloader. It removes downloaded archives and failed staging directories; it does not load or execute payloads.
- Added `NgrComponentResolver.kt`: resolves profile-selected components and recursive dependencies into dependency-first install order; checks profile validation, target ABI compatibility, container-mode constraints, Wayland/Vulkan capabilities, required/forbidden components and conflicts with selected/installed IDs.
- Added tests for version activation/rollback, removal guards, uninstall, stage-root containment, local archive install, hash mismatch, dependency ordering, already-installed dependencies, architecture mismatch, conflicts, Wayland and Vulkan requirements.
- CI: run #190 (`44185844227`) passed store tests; run #202 (`44186403039`) passed payload-layout validation and install-pipeline tests; run #205 (`44186662469`) passed resolver tests, JVM tests, ARM64 APK build and artifact upload. Artifact #205: `14375191502`, SHA-256 `02e56c547f9594c986995c48b5ae9693086b3f2775591ae04f456e89be8a66e5`.
- Run #199 failed because store tests had not yet been updated to include the newly required installSubdirectory; fixtures were corrected and #200 passed. Run #201 failed because the pipeline test still treated the returned active path as the version root; the API now returns the validated payload directory and the test was corrected in #202.
- ZIP extraction now normalizes backslash path separators to forward slashes to match inspector path normalization; regression test added. Runs for this last path normalization change are pending.

### WinNative launch-pipeline audit
- Pinned donor remains `99c3305d163780de5ca39b493aefcde436acad5c`.
- Mapped `XServerDisplayActivity` override resolution and launch setup, `GuestProgramLauncherComponent` runtime assembly, standard x86_64 Box64 command vs ARM64EC direct Wine/HODLL selection, and the separate PRoot-based Linux/Proton launcher.
- Confirmed container JSON persists Wine, DX wrapper, graphics/audio, Box64/FEX versions and presets, CPU lists, drives, executable and environment settings. Container activation still uses a global `xuser` symlink; NGR must not copy this architecture.
- Confirmed the current donor's effect path is vkBasalt using ReShade-compatible `.fx` effects and `ENABLE_VKBASALT`/`VKBASALT_CONFIG_FILE`, not a proven ReShade Vulkan DLL injector. NGR documentation now distinguishes these capabilities.
- LinuxProtons TAR install code offers useful staging, hash, path containment, symlink target and expected-Wine-ELF validation patterns. It is not copied into NGR; its dependency/license and full edge-case behavior still need audit.


## 2026-11-18 — Component catalog parsing and staged ZIP installer

### CI diagnosis and correction
- Actions run #230 (`44189222721`, commit `37c3c2b1`) failed during Kotlin compilation in `MainActivity.kt`: the local `field()` helper referenced `form` before its declaration. Fixed by constructing the form before declaring the helper in commit `9b3e91eb2104a131e41d51a0ee79de426b1f85a9`.
- Previous baseline run #229 (`44188936701`, commit `f251560d`) completed successfully.
- Run #231 (`44189626075`) checks the MainActivity fix. Runs #232 (`44189719581`), #233 (`44189758325`) and #234 (`44189803672`) check the catalog parser/tests and subsequent strict-type correction. Their final status must be checked before calling this stage green.

### Implemented
- Added `NgrComponentCatalogJson.kt`, a strict JSON parser for catalog schema v1. It rejects unknown root/component/constraint fields, duplicate array entries, invalid enum values and invalid catalog metadata; optional fields have explicit defaults.
- Tightened JSON parsing for schema version, constraints object and boolean values rather than relying on permissive JSONObject coercion.
- Added `NgrComponentCatalogJsonTest.kt` for valid parse, compatibility constraints, unknown keys, unknown enums and duplicate values.
- Added dependency-cycle detection to `NgrComponentCatalog.validate()` using topological resolution; a cycle regression test is present.
- Added `NgrComponentArchiveInstaller.kt`: copies the archive to an app-private snapshot, verifies SHA-256 against that exact snapshot, calls the existing ZIP central-directory inspector with component-specific limits, extracts into a fresh staging directory, rejects path escape/collisions, bounds actual streamed sizes, preserves Unix executable bits, removes failed staging output and never activates or executes payloads.
- Added `NgrComponentArchiveInstallerTest.kt` covering verified extraction, hash mismatch, ZIP Slip rejection, unsupported formats, unsafe install path, executable bit preservation and backslash path normalization.
- ZIP is the only archive format currently extracted. TAR/RAW formats remain explicitly unsupported; no network downloader, component activation, atomic version promotion or rollback is implemented yet.

### Security review notes
- Installer validation and extraction operate on an app-private snapshot to avoid a hash/inspection/extraction TOCTOU against a mutable source file.
- The shared NGRPACK inspector rejects unsafe paths, duplicate/case-colliding names, symlink/special file entries, encrypted/unsupported compression methods, ZIP64 and excessive declared size/ratio.
- Installer separately validates actual decompressed byte counts and cleans the staging directory on extraction failure.
- Remaining hardening before production: add a dedicated directory/file collision matrix, ensure all archive path normalization semantics are consistent, test malicious local-header/central-directory disagreements, and implement promotion only under a private component root with rollback.

### Current CI status
- No APK is claimed for this stage until the latest run passes unit tests, arm64 APK build and artifact upload.


### CI checkpoint
- Run #231 (`44189626075`, commit `9b3e91eb`) completed successfully after the MainActivity profile-dialog fix.
- Runs #232–#236 cover catalog JSON parser, parser tests, strict JSON type validation and journal updates; they were still running at this checkpoint. Run #231 does not include the later parser/installer changes and is not evidence that the latest source is green.


## 2026-11-18 — Catalog parser consolidation and dependency-graph hardening

- Repository audit found two catalog JSON decoders: the original bounded `NgrComponentCatalogParser` and a second `NgrComponentCatalogJson` introduced during later work. The original parser already had the stronger byte limit, contextual enum errors, strict scalar types and tests.
- Removed the duplicate `NgrComponentCatalogJson.kt` and its redundant tests in commits `2f7be573` and `1dc58268`. The single supported parser is now `NgrComponentCatalogParser`.
- Run #239 (`44190109701`) and #240 (`44190235891`) failed only in the redundant duplicate-parser test `NgrComponentCatalogJsonTest.rejectsCoercedBooleanAndWrongConstraintsType`; the parser's established strict-type tests are in `NgrComponentCatalogParserTest`. This duplicate test/file has now been removed.
- Catalog dependency-cycle detection now includes both explicit `dependencies` and `constraints.requiredComponents`; added regression coverage for a cycle formed through required-component constraints. Also reject a component that both requires and forbids the same component, or requires a component it declares as a conflict, and validate the minimum NGR version field length.
- Commits: `16ee463e` (constraint graph validation), `7d6ec05d` (required-component cycle test), `2f7be573` and `1dc58268` (duplicate parser removal).
- Runs #241–#243 and subsequent latest-main CI must be checked; do not use the earlier green APK artifacts as proof of this exact latest source.


### Follow-up CI failures and fixes
- Runs #239 (`44190109701`) and #240 (`44190235891`) failed because the temporary duplicate-parser test asserted an error message from the duplicate decoder; that decoder and its test suite have been removed in favor of `NgrComponentCatalogParser`.
- Run #241 (`44190262305`) failed compiling the new required-component-cycle test because the catalog constructor was called positionally with schemaVersion first. Corrected the test to use named arguments in commit `b7563d5948e598a4af2705adab2a2028afe3ce53`.
- Verify the parser cleanup and cycle test fixes in the newest CI before treating catalog validation as green.


### WinNative CPU/graphics configuration mapping
- Inspected pinned donor sources for `Box64PresetManager`, `FEXCorePresetManager`, `FEXCoreManager`, `WineInfo`, `GuestProgramLauncherComponent`, `DXVKConfigUtils`, `GraphicsDriverCatalog`, `AdrenotoolsManager` and `DriverPackages`.
- Confirmed Box64 presets are environment maps with distinct dynarec safety/performance settings and custom presets in SharedPreferences.
- Confirmed FEXCore has a separate preset family, SMC-check alias normalization and per-executable AppConfig overrides.
- Confirmed standard x86_64 guest command is Box64-wrapped; ARM64EC uses native ARM64 Wine directly and chooses HODLL based on FEXCore vs WOWBox64. This architecture-specific routing must be represented explicitly in NGR.
- Confirmed driver providers are separate: Android AdrenoTools packages and Linux-runtime ICD packages. Container selection is passed into the launch environment; system Vulkan must remain untouched.
- Confirmed DXVK async/cache and VKD3D feature-level options are environment configuration, not evidence of runtime availability.
- Detailed source mapping appended to `docs/WINNATIVE_INTEGRATION_PLAN.md`.


## 2026-11-18 — Reproducible component version pins

- Extended `NgrEnvironmentProfile` with `componentVersions: Map<String,String>`, validating component IDs and safe version strings.
- Extended the profile JSON codec to persist pinned versions. Older schema-v1 profiles without the optional map decode as an empty map, preserving backward compatibility.
- Added profile editor UI for entering `component-id=version` pins and displays the number of pinned components in profile details.
- Extended `NgrComponentResolver` to compare a requested pin against the catalog version before download, or against verified installed-version metadata when the component is already installed. A pin cannot be silently satisfied by an unversioned installed ID.
- Added codec tests for round-trip and invalid pins; resolver tests cover catalog mismatch, installed mismatch and missing installed-version metadata.
- Commits: `e4a87dd1` model, `22bb69dd` codec, `9119e2d3` tests, `2257cd53` resolver, `31e28ed3` resolver tests, `25f31ed8` editor UI.
- CI runs #252–#257 are pending/in progress at the time of this entry. No new APK is claimed for version-pin support until the latest code run passes all tests/build/artifact steps.


### Downloader integration testability and resolver correctness
- Added an injectable HTTPS connection factory to `NgrComponentDownloader` so redirects, status/headers, hash failures and streaming limits can be tested without external network dependencies.
- Replaced the downloader's URL-only tests with deterministic fake-HTTPS connection tests for successful SHA-256-verified download, HTTPS redirect, downgrade rejection, hash mismatch cleanup and streaming size-limit cleanup.
- Audit of `NgrComponentResolver` found that installed components were marked satisfied before their dependency graph was traversed. Corrected resolution so installed components must exist in catalog metadata, their dependencies/required components are still resolved, and pins are checked against supplied installed-version metadata.
- Added resolver tests for installing a missing dependency of an already-installed component and rejecting installed IDs absent from catalog metadata.
- Code/test commits: `0975c40b`, `baf17723`, `cdcb8583`, `647e272d`, `c5a969ff`.
- CI runs #260–#264 cover these changes and remain pending/in progress at the time of this entry.


### Latest verified component pipeline build — Actions #264
- Run #264: https://github.com/Snap888/NativeGameRecompiler/actions/runs/44192157349
- Source/test commit: `c5a969ff3e7b6e27a5b52e35d51ae03ae23d5825`.
- Confirmed all CI steps passed: JVM unit tests, ARM64-v8a debug APK build and artifact upload.
- The passing source includes component-version pin persistence/UI, resolver enforcement, installed-component dependency traversal, HTTPS connection injection and deterministic download tests.
- Artifact: `NGR-arm64-debug`, ID `14378300480`, archive size 1,424,818 bytes, expires 2026-12-01.
- Downloaded and inspected artifact ZIP: contains `app-debug.apk` (2,358,277 bytes). Extracted APK SHA-256: `7eac1872e0773cd83b6a55ca7637a3003b5bc1726af817eeeb569fbc2d7b8b44`. APK contains `lib/arm64-v8a/libngr_runtime.so`.
- Subsequent commits through `9b466ce9` only update documentation; no source code changed after the verified build. This APK therefore matches the current source tree at the time of this note.
- Device installation/runtime execution has not been tested.

### Licensing/provenance checkpoint
- Added `docs/LICENSING_AND_PROVENANCE.md` after confirming NGR root license is MIT and WinNative is GPL-3.0.
- Preliminary upstream root licenses inspected: Box64 MIT, FEX MIT, DXVK zlib/libpng, VKD3D-Proton LGPL-2.1, Wine LGPL-2.1-or-later and vkBasalt zlib/libpng. Mesa/Turnip and ReShade licensing remain unverified in this pass.
- No WinNative code or binaries have been copied. The MIT/GPL boundary requires an explicit licensing decision before donor source reuse.


## Session 2026-11-18 — WinNative-inspired dashboard refresh

### User feedback
- User requested a more polished interface inspired by WinNative after confirming game ZIP import, executable detection, NGRPACK install/inventory and removal on device.

### UI changes
- Replaced the plain white, vertically stacked screen with a dark graphite dashboard and restrained electric-blue accents.
- Added NGR wordmark/header, introductory game-library hero panel, prominent folder import action and secondary ZIP/NGRPACK actions.
- Grouped installed games, runtime profiles, hardware/Vulkan/controller information and diagnostics into visually separated sections/cards.
- Added rounded surfaces, consistent spacing, typography hierarchy, readable muted metadata and high-contrast primary actions.
- Switched the Android Material base theme to dark so system dialogs/editors align with the dashboard.
- Kept all existing import, package manager, profile, Vulkan, controller and diagnostic callbacks intact; no runtime/backend behavior was changed.

### Device feedback recorded
- User-provided log confirms Lethal Company ZIP import selected Lethal Company.exe as Windows x86_64, generated and validated the manifest/hash inventory, installed successfully, survived app restart inventory scan, and was removed successfully.
- This confirms the package-management flow on the user's device; it does not demonstrate game execution, as no compatible execution backend is integrated yet.

### Validation
- UI source and Android manifest committed to main.
- Android CI was triggered for the UI commits; do not share a new APK until the latest run passes JVM tests, ARM64 APK packaging and artifact upload.
- No visual inspection on a physical device has yet been performed. User should verify spacing and contrast on the target phone after installing the CI artifact.

 
## Session 2026-11-18 — Large game folder import limits

### Device log analysis
- User-provided session 99aee2c1 shows normal app startup, Vulkan probe and input enumeration; the controller list did not include the previously attached GameSir device in this session.
- Folder import reached copyDocumentDirectory and failed at the explicit size guard in MainActivity.kt:892.
- The old limits were 512 MiB per file and 2 GiB total uncompressed payload. The log does not include the selected folder's exact size, so it is not possible to determine whether the per-file or total-payload threshold fired.
- This is an import policy limit, not an executable detection, package validator corruption, Vulkan or runtime launch failure.

### Changes
- Raised the bounded per-file payload limit to 2 GiB and total uncompressed game payload limit to 8 GiB.
- Raised maximum NGRPACK archive input/output size to 3 GiB. This deliberately stays below the current ZIP32 implementation limit; ZIP64 remains unsupported and must not be implied.
- Kept the 4,096-entry limit, path/depth checks, regular-file/symlink rejection, SHA-256 manifest verification and bounded streaming extraction.
- Folder-import errors now identify whether the per-file or total payload limit was exceeded and include the offending relative path.
- Reused one limits object per directory traversal rather than allocating it for every 64 KiB read.
- Added a unit test asserting archive/manifest size-limit consistency.

### Validation
- Source commits: 47d9afc9 archive limits, 8264e01c manifest limits, 6efde3cb folder diagnostics, 04c61cb5 tests, 76019797 streaming-loop allocation cleanup.
- Android CI must pass JVM tests, ARM64 APK build and artifact upload before this larger-import change is considered validated.
- The larger limits increase possible temporary storage use. They do not bypass Android free-space constraints; import still needs room for its private staging copies and final package installation.

 
### Large-import CI verification — Actions #282
- Run #282: https://github.com/Snap888/NativeGameRecompiler/actions/runs/44241032020
- Source/test commit: 76019797eefc79d67fbd3489b38aac03dec1cc14.
- Confirmed JVM unit tests, ARM64-v8a debug APK build and artifact upload all passed.
- Artifact ID: 14399851332 (NGR-arm64-debug). Downloaded and inspected ZIP; it contains app-debug.apk (2,374,661 bytes) and lib/arm64-v8a/libngr_runtime.so.
- Extracted APK SHA-256: 9859793a9fa1068fa6d03cf325b932eba27353667975c9d3aafe0ff22cedf5e0.
- Commits after this source/test commit only update development/next-action documentation. The APK therefore contains all large-import source changes and the new limits consistency test.
- Physical-device retest of the same folder is still required; CI does not prove available storage or SAF-provider behavior on the user's phone.


## Session 2026-11-18 — Wine prefix lifecycle foundation

### Implemented
- Added `NgrWinePrefixManager` under app-private `wine-prefixes/` and `wine-prefix-backups/`.
- Prefix creation produces a `PREPARED` empty prefix only. It is never marked runnable merely because the directory exists.
- `READY` requires regular `system.reg`, `user.reg`, `userdef.reg` plus real `drive_c/` and `dosdevices/` directories.
- Clone and backup are allowed only for validated READY prefixes. Restore verifies a deterministic SHA-256 tree digest before promotion.
- Clone/backup/restore use private staging directories and atomic directory promotion.
- Reset and removal are constrained to managed prefix roots. Symlinks, special files, unsafe IDs, oversized trees and out-of-root paths are rejected.
- Added JVM tests for prepared-vs-ready state, clone/backup/restore, duplicate IDs, reset/removal and symlink rejection.
- Added a NGR-styled prefix section in the Android UI with prepare, clone, private backup, reset and delete actions.

### Explicit limitations
- No Wine binary is integrated and no `wineboot` initialization is performed.
- Backups are app-private directory snapshots, not portable ZIP exports.
- Prefixes are not connected to environment profiles or game launch yet.
- No process is launched from prefix contents; this manager only stores and validates files.
- READY means structural marker validation, not proof that the registry is semantically healthy or compatible with a particular Wine build.

### Validation
- Source and tests committed to `main`; CI result is pending inspection.
- No APK is claimed for this stage until JVM tests, ARM64 build and artifact upload are confirmed.
- Device-side lifecycle testing remains outstanding.


## Session 2026-11-18 — Environment / prefix / component binding

### Implemented
- Migrated environment profile schema from v1 to v2 with optional `winePrefixId`.
- Added backward-compatible decoding: v1 profiles load as v2 with no prefix assigned; existing component and display settings are retained.
- Added strict prefix ID validation and codec round-trip/migration tests.
- Profile editor now allows assigning a managed Wine prefix ID through a picker; profile cards show the binding and report missing, unsafe or PREPARED prefixes as draft prerequisites.
- Prefix deletion is blocked while one or more profiles reference it, preventing silent dangling references.
- Added `NgrEnvironmentReadiness`, which combines profile launch validation, actual prefix state, installed component active-version state, version pins, catalog compatibility and Vulkan/Wayland capability checks. It also reports selected components and transitive dependencies that still need installation.
- Added readiness tests for missing prefix/components and PREPARED prefix rejection.
- Readiness is an inspection report only. It does not activate components, load libraries, initialize Wine or launch games.

### Current constraints
- Component catalog source is not yet connected to the main UI, so readiness evaluator is available to the runtime layer but not yet exposed as a complete user-facing validation action.
- Environment profiles still store component IDs and settings; runtime activation and Wine process launch are not implemented.
- Prefixes remain PREPARED until a compatible Wine runtime and controlled wineboot integration are added.
- Profile prefix binding is currently entered as an ID in the editor; a picker is a follow-up UI improvement.

### Validation
- CI is triggered by the main-branch commits and must be inspected before distributing an APK.
- Physical device validation is outstanding.


## Session 2026-11-18 — P0 Wine Prefix Manager CI repair

### Repository and CI audit
- Audited live `main` at `aed35c2d0804f5bb0fd43774a978ea0a8a22de34` and read README, development log, next actions, technical specification, technical audit and continuation prompt.
- Latest ten Android CI runs (#303–#312) were failures. For current HEAD run #312 (44247661958), Android SDK, NDK/CMake and Gradle setup succeeded; failure occurred during Kotlin compilation before unit tests ran, APK build and artifact upload were skipped.
- Full job log identified one Kotlin compile error in `NgrWinePrefixManager.kt:280`: `files += file` resolves as reassignment/type mismatch because `Path` is iterable-like in Kotlin.

### Fix
- Commit `18b02fcb8454b2f43b44106b968ca58c9c7d0e49` — `Fix Wine prefix backup digest compilation`.
- Replaced the ambiguous collection plus-assignment with explicit `files.add(file)` in the backup tree-digest traversal. No other behavior changed.
- Existing prefix lifecycle tests cover prepared-vs-ready markers, clone/backup/restore, duplicate IDs, traversal, reset/remove and symlink rejection. Existing readiness tests cover missing and prepared prefixes.

### Validation
- CI run #313: [44249549118](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44249549118), source-fix commit above. At journal update time it was still in progress; no passing tests, APK or artifact claimed.
- No physical-device test performed.

### Next
- Wait for run #313; inspect full logs if it fails. If green, verify ARM64 APK and artifact.
- Then continue first incomplete P2 task: host-side backend ABI negotiation/validation, keeping untrusted NGRPACK payloads out of the loader.


## 2026-11-18 — CI recovery and ABI v1 validation foundation

### CI recovery (P0)
- Rechecked live `main` and GitHub Actions after the Wine Prefix Manager compile fix.
- Run #315 (`44249623666`) at commit `489384e384ef386dcde70b9e14622251d9201943` passed every job step: Android SDK, NDK/CMake, Gradle setup, JVM unit tests, ARM64 debug APK build and artifact upload.
- Downloaded artifact `NGR-arm64-debug` (ID `14402883613`), verified ZIP contains `app-debug.apk` and verified APK contains `lib/arm64-v8a/libngr_runtime.so`.
- APK SHA-256: `10327bc566746291a1f403b025b62b36774eca321ebca55675e463c23c5af8be`.

### ABI v1 audit and implementation
- Audited `backend_abi_v1.h`, ADR 0003, capability registry and Android native CMake configuration.
- Existing work was only the C-compatible ABI declaration and a C-header compile check; there was no host-side table validator or conformance test.
- Added `backend_abi_validation.hpp/.cpp`: validates host API size/major/callbacks and backend API size, ABI major/minor, execution kind, bounded backend ID/version strings and all required lifecycle callbacks.
- Added native conformance tests for valid tables, missing host/backend callbacks, invalid backend ID, truncated API struct and ABI major mismatch.
- Added native conformance test execution to Android CI before JVM tests; compiled validator into `libngr_runtime.so`.
- Capability is now `partial`, not available: dynamic loading, trusted-library enforcement and lifecycle orchestration are explicitly not implemented.
- Updated ADR 0003 validation requirements.

### Commits
- `45a4f8fe` — add ABI validation header.
- `d82bc95c` — implement host/backend table validation.
- `aa61149e` — add native conformance tests.
- `e3b6c3a3` — compile validator into runtime.
- `c09e1a84` — execute native ABI tests in CI.

### Validation
- The successful APK is from commit `489384e3`; it predates the ABI validator changes.
- CI for the ABI validator commits has not yet completed; no new APK is claimed for these changes.
- No physical-device tests performed.

### Next
- Run CI and fix any native C++ or Kotlin failures.
- After green CI, implement a trusted in-process backend registry/lifecycle only for app-bundled or verified components; never dlopen NGRPACK game payloads.
- Keep untrusted plugins disabled until signature/provenance policy exists; this ABI is not a sandbox.


## Session 2026-11-18 — Backend ABI v1 host validation

### Baseline and CI
- Verified current main baseline commit `489384e384ef386dcde70b9e14622251d9201943` and successful Android CI run #315 (`44249623666`): JVM tests, ARM64 APK build and artifact upload all passed. Artifact `NGR-arm64-debug`, ID `14402883613`.
- Downloaded the run #315 artifact ZIP and verified it is a valid ZIP containing `app-debug.apk` (2,391,045 bytes). This artifact predates the ABI validation changes below.

### ABI validation implementation
- Added `backend_abi_validation.hpp/.cpp` with host API and backend API table checks: struct sizes, ABI major/minor compatibility, execution backend kind, bounded backend ID/version string views and required lifecycle callbacks.
- Added native conformance tests for valid tables, missing host callback, ABI major mismatch, invalid backend ID, missing lifecycle callback and undersized struct.
- Added validator source to `ngr_runtime` CMake target so the Android native library compiles the same validation code tested on CI.
- Added a native ABI conformance CI step ahead of JVM tests and APK packaging.
- ABI remains trusted-code boundary only: this validator does not load plugins, sandbox them, verify signatures or execute NGRPACK payloads.

### CI diagnosis / correction
- Initial conformance CI runs #321–#324 failed before Gradle because YAML shell continuations contained doubled backslashes, causing the linker to interpret a literal backslash as an input file.
- Corrected shell line continuations to one backslash in commit `28ad3d4eac093b58849f93872fcc102248b1396a`.
- CMake integration commit: `c2ef6d04f069c361fd9fa7dbae23536d40fb0de5`.
- Earlier ABI implementation files were added in commits leading to `e4498083b0f7074ae80e24c20162c0dcc9d22f6d`.
- CI run #327 (`44270340309`) was in progress at journal update time; native tests, JVM tests, ARM64 APK and artifact upload are not yet confirmed.


- Follow-up hardening: host API now rejects a host minor version newer than the runtime; conformance tests now cover host major/minor/struct-size failures and backend minor-version mismatch. Commits: `fa94ec1f3ae9ac2aa775392a017e86613cbc4d80`, `7f9276034ff2dccbe97c18c027cfb231b44bb09b`.

### Next
- Verify run #327 end-to-end. If native conformance fails, inspect compiler/test output before proceeding.
- Only after green CI, mark ABI validation complete; then add a trusted built-in test backend fixture and lifecycle state machine. Do not implement dynamic loading of arbitrary package content.


## Session 2026-11-18 — ABI v1 CI verified; built-in registry foundation

### CI verification
- Latest main commit before this stage: `2b95090ae5c1e84f773916fc7068842236785d58`.
- Android CI run #334 (`44270549436`) completed successfully. Every step passed: Android SDK, NDK/CMake, Gradle, native ABI conformance tests, JVM unit tests, ARM64 debug APK build and artifact upload.
- Artifact `NGR-arm64-debug`, ID `14413025119`, archive SHA-256 `d82e9e4469032f631689a491abfddf9ea0584337fd40204da12907fe70fc591f`.
- Extracted APK size: 2,394,605 bytes; SHA-256 `8abd8b7c6dc6ed1bdef25e2b4fb4d16316547f3d44fed6703a1e020f0aec501f`.
- Physical-device validation remains outstanding.

### Audit and design boundary
- Re-read README, technical specification/audit, continuation prompt, development log and next-actions list.
- Audited current ABI header, validator, CMake target, JNI bridge and capability registry.
- No backend registry or lifecycle manager existed yet.
- Added a deliberately narrow in-process registry for trusted, app-bundled API tables only. It validates ABI tables, rejects duplicate backend IDs, and provides thread-safe membership/count queries.
- Registry does not load libraries, execute callbacks, accept NGRPACK paths, or imply a package is trusted. Dynamic loading remains disabled.

### Changes in progress
- Added `backend_registry.hpp/.cpp` and native tests for registration, duplicate IDs, invalid IDs and registry queries.
- Linked registry source into the Android runtime target and added a native CI conformance command.
- Capability report now distinguishes this registry foundation from the still-unimplemented lifecycle and verified dynamic loading.

### Next
1. Verify native registry tests, JVM tests, ARM64 build and artifact upload in CI.
2. Add lifecycle orchestration only after the registry stage is green. Define stop-completion ownership explicitly; do not destroy backend state while callbacks or worker threads may still be active.
3. Keep arbitrary NGRPACK payloads permanently outside the dynamic-library loader boundary until a separate signature/provenance policy is implemented.


## Session 2026-11-18 — Runtime management audit and shortcut foundation

### Baseline audit
- Re-verified main at 45cc39680f361de7a19bfff4f51e82a2878f1f6e and read README, development log, next actions, technical specification/audit, continuation prompt, WinNative integration plan and licensing/provenance inventory.
- Inspected NGR Activity, environment profile/codec/store, Wine prefix manager, component catalog/store/readiness and JVM tests.
- Inspected WinNative container/shortcut, Box64/FEX preset, ReShade, controller, Adrenotools/graphics-driver and Wine metadata source families.
- NGR already has profile, component download/install/rollback and prefix-management foundations. It does not yet have game shortcut UI or Android pinned shortcuts, Wine launcher/session, real driver activation, effect injection, frame-generation backend or controller mapping editor.
- WinNative donor is GPL-3.0; NGR remains MIT. This stage implements an independent NGR metadata layer and copies no GPL donor source.

### CI baseline
- Actions run #342 (44277416635) at 45cc39680f361de7a19bfff4f51e82a2878f1f6e passed native ABI conformance, native registry tests, JVM tests, ARM64 debug APK and artifact upload.
- Artifact NGR-arm64-debug, ID 14414943540, digest sha256:48937e9a1dd29c74e358469aa1880247fd4fcb5bd1ff0bb3f8a8d607df9f387c.

### Implementation started
- Added NgrGameShortcut and NgrGameShortcutStore: guest executable/working-directory paths, launch mode, package/profile binding, arguments, per-game environment overrides, managed icon reference, enabled flag and launch timestamps.
- Store uses bounded JSON schema v1, app-private atomic replacement, ID/path/environment validation, symlink rejection and corrupt/unknown-schema reporting.
- Added JVM tests for save/load/update/delete, unsafe path/protected variable rejection, corrupt schema handling and execution-mode serialization.
- Commits: be111ede2b481507d8b9f03c9e157b4df2c4b731 (shortcut metadata store); ab518ca0e26a685802ee24225634015dc6f3c3a5 (tests).
- Expanded WINNATIVE_INTEGRATION_PLAN.md with the full pre-recompiler completion gate for shortcuts, launch configuration, runtime, drivers, effects, frame generation, controllers, audio and display.
- Updated NEXT_ACTIONS.md with P0 shortcut work and the full runtime-management backlog.

### Validation status
- CI run #344 (44280634973) was still running at the time of the source audit; shortcut JVM tests, APK and artifact are not yet confirmed.
- Shortcut metadata persistence is implemented only. No shortcut UI, Android home-screen pinning or process launch is claimed.
- No physical-device test has been performed.

### Next
1. Verify run #344 and fix any compile/test failures.
2. Integrate shortcut CRUD into the game library UI and add safe icon import.
3. Implement launch settings/profile resolution and the session API.
4. Continue with controller profiles, runtime presets, driver/effects management and real Wine/Box64/FEX launcher integration.
5. Keep static recompilation blocked until the completion gate in WINNATIVE_INTEGRATION_PLAN.md is satisfied.


### Shortcut UI follow-up — 2026-11-18
- Added a Game Shortcuts section to the NGR dashboard. Users can create a shortcut from an installed package and existing environment profile, then edit title, package-relative executable, working directory, arguments, profile ID, execution mode and per-game environment overrides.
- Shortcut records are removed when their game package is successfully removed. Saving validates that the selected environment profile exists.
- The UI clearly labels shortcuts as metadata-only because no process launcher is connected. Android home-screen pinning, icon/cover import and actual process launch remain pending.
- CI run #344 (44280634973) failed one JVM assertion: the test created two shortcut fixtures with different default timestamps and compared them as equal. Corrected the fixture by retaining and comparing the same object in commit d3dc6110e610c93b7e522ef89067cc6f74910fb7.
- Current verification run #351 (44280992531) includes the timestamp fixture correction; it was still running at the time of this update. Runs #349/#350 were triggered by earlier intermediate commits and are not authoritative for the current HEAD.


## Session continuation 2026-11-18 — Launch configuration resolver

### CI and artifact verification
- Verified main commit `c33a2b9334bb2665ab3ef8da3303fcf734871da9` and Actions run #353 (`44281090326`): native ABI conformance, backend registry tests, JVM tests, ARM64 debug APK and artifact upload all succeeded.
- Downloaded artifact ID `14417860254` (`NGR-arm64-debug`), extracted `app-debug.apk` (2,437,013 bytes). Artifact ZIP SHA-256 `1d00a39473d4e50de4ac5778f03b9fdeb74164e1ae5348e07604c58de2d76acf`; APK SHA-256 `0de237786a36559cc7ce1f65c521743432bcbbe9d75e6a4d80f63d8c4f6cd8f2`.

### Implementation
- Added centralized `NgrEnvironmentVariablePolicy` and applied it to both environment profiles and per-game shortcut overrides. It blocks host/runtime-owned loader paths, Vulkan ICD/layer selection, Adrenotools driver injection, Wine prefix/loader and FEX rootfs controls, in addition to PATH/HOME/PREFIX, LD_* and NGR_*.
- Added `NgrLaunchConfigurationResolver`: validates shortcut/profile identity; requires Wine/Box64 or FEX components for Wine mode; leaves static-recompiled and native ARM64 modes independent of Wine; merges base profile, package-specific override and shortcut override in deterministic precedence; resolves Wine/DXVK/VKD3D/Turnip/display choices; rejects unsupported Wayland combinations.
- Added JVM tests for merge precedence, profile mismatch, Wine readiness, Wine-independent execution modes, Wayland provider selection and environment injection prevention.
- Added a per-shortcut UI action to preview the resolved configuration and effective environment. It explicitly reports that no process will be started.
- Commits: `dbd802f24d87f9c7467692669750540e81c11d27` (profile validation policy), `c96cc2d59b6daec1fd86d09f2382387d73364395` (shortcut policy), `28610d0f1d84eebd8b4d6513826c0ae3580704f0` (policy object), `8f1e8648cbdcc7580c764e51e9f67928ba3e03a8` (resolver), `cf7a165e2f7eae8f50f32147b80d59954b191408` (per-game Wayland override validation), `3333e6f8628bb553da8565160b323ff5db8adb40` (tests), `9f38e4788c47c26b6656b17c37eb9a9135fc3cd6` (UI preview), `9ad75f09503007557276ce0fa719bf3398faa422` (test assertion fix).

### Status and limits
- CI run #353 passed the previous shortcut UI stage. The resolver and preview commits are newer and still require a fresh CI result.
- The resolver is configuration-only. It does not verify component payloads against the installed component store/catalog, activate Vulkan drivers, initialize Wine prefixes or start a process.
- No physical-device launch test has been performed.


## Session continuation 2026-11-18 — Component catalog and manager UI

### Implementation
- Added `NgrComponentCatalogStore`: strict parser validation, 4 MiB catalog bound, app-private atomic replacement, catalog ID/filename consistency, symlink rejection, bounded catalog count, corrupt-catalog reporting and safe delete.
- Added `NgrComponentInstallPlanner`: dependency-first closure, installed dependency skipping, unknown dependency rejection and conflict/forbidden-component checks.
- Added JVM tests for catalog store lifecycle/validation/corrupt files and dependency planning/order/conflicts.
- Added the Components & Drivers UI: import catalog JSON through SAF, list catalog components with type/version/license/ABI, open upstream source, install/update ZIP components and dependencies through the existing HTTPS + SHA-256 + bounded extraction + atomic promotion pipeline. Partial installation is reported explicitly. Non-ZIP formats are disabled.
- The UI warns that catalog metadata is untrusted and that stored payloads remain inert; no driver is activated or loaded into Vulkan, and no Wine/Box64/FEX process is launched.
- Commits: `24b809ad735cfb3f0b26bbe3c817ea52a904793c` (catalog store), `564ad20b677a170abe45fcdef9edd9714ce29773` (catalog tests), `8199a14bf64fe6bef75276c73474095757b9c86e` (dependency planner), `3dc78efabf3aa18b7f8a1677b9024a9e533e72fa` (planner tests), `045a40e73dbc92bb91a4c4f7a182465d986fdd4a` (catalog/driver manager UI).

### Validation status
- Launch resolver JVM tests passed in Actions run #360 (`44284421245`), including native ABI and registry checks. Later launch preview and catalog manager changes require a fresh run.
- Latest UI/catalog build run #370 (`44284848217`) was still running at the time of this entry.
- Catalog metadata has no signature trust chain yet; HTTPS and archive SHA-256 protect transport/integrity relative to the imported catalog, not the trustworthiness of the catalog author. Use only trusted catalogs until signature/pinning is implemented.
- Only ZIP component archives are installable. TAR/RAW formats remain disabled.
- No physical-device component download/install or runtime activation test has been performed.


### Component version lifecycle follow-up — 2026-11-18
- Extended `NgrComponentInstallPlanner` with installed-version metadata. It skips only the exact catalog version; mismatched versions are planned as updates.
- Added `NgrComponentStore.storedVersionDirectory` and `activateStoredVersion`. Existing stored trees are validated before switching the atomic active-version pointer, avoiding unnecessary downloads when a requested version is already present.
- Component manager now reuses stored versions and exposes rollback/uninstall. Uninstall is blocked while a base profile, game override, or installed dependent component references the component.
- Added tests for mismatched-version update planning and reactivation of a previously stored version.
- Commits: `3ceb604d73b0e4ba6c7b5aa067220ed0c420190d` (version-aware planning), `6bd6e93a4ef8cd1f43ca8a04a0eb0c31ef1c3676` (UI version metadata), `27fec1c16742a63b45da14a34470e90b1f3a6c7d` (stored version activation), `26883c4c700c6cc19081a41bf46598a02214e13a` (reuse stored version in UI), `9a166c0ac389d5c8b30b7b1df81c4947228e781d` (update planning test), `e0e40973c952365e5d45c41d205e0f8ae600b87a` (stored version activation test), `211e8526df5ed53966e00d74c2eed28a42fd39fa` (rollback/uninstall UI).
- Actions run #370 (`44284848217`) for the initial catalog manager UI passed native ABI, backend registry, JVM tests, ARM64 APK and artifact upload. Later version lifecycle commits require run #380 (`44285264320`) verification.
- Component payloads remain inert; no Wine/Box64/FEX process, Vulkan ICD activation, ReShade injection or frame-generation provider is active yet.


## Session continuation 2026-11-18 — Controller mapping profiles

### Implementation
- Added `NgrControllerProfile` with Android device descriptor/name, optional game package assignment, action-to-key/axis/hat bindings, duplicate physical binding checks, dead zones, sensitivity, axis inversion, stick curves, trigger threshold, vibration and touch-layout tuning.
- Added bounded atomic `NgrControllerProfileStore` with strict JSON schema, symlink/path checks and corrupt-profile reporting.
- Added JVM tests for round-trip persistence, invalid/duplicate bindings, invalid tuning ranges and unknown schema handling.
- Added CONTROLLERS UI: detects connected Android gamepads/joysticks, creates a profile for a selected device, edits binding map and tuning, shows connected/disconnected state and deletes profiles.
- Binding text format is `ACTION=KEY:code`, `ACTION=AXIS:code:+1`, or `ACTION=HAT:code:-1`. This is manual configuration, not an input-capture wizard.
- The UI explicitly warns that saved mappings do not affect a running game until the session input adapter is connected.
- Commits: `185cfa719d2443cbfd6ea49dda418a314248dede` (controller model), `3fa2ced8c518c160e8f2cf276e3869b23ac5d7a6` (store), `c4225eb7fd58faff2e2bf13812e1d82ddd443304` (tests), `50a887c3b6d158bfdfbadd787ebf0866338cea14` (UI), `bd6a01b3d6e750560f4468ffcd6f08c172e923e6` (explicit view insertion).

### Validation status
- Controller model/store/tests/UI commits are newer than the last verified CI and require a fresh run.
- Run #380 (`44285264320`) is still verifying the prior component version lifecycle stage.
- No physical controller remapping or game-session input test has been performed.


### CI correction — component version result typing
- Actions runs #380–#383 failed at Kotlin compilation because the component install loop tried to access `success`/`error` on an `if` expression whose branches returned two different result types (`NgrComponentStore.Result` and `NgrComponentInstallPipeline.Result`).
- Fixed by normalizing both branch results to a `Pair<Boolean, String?>` before handling the outcome in commit `a7aaf6ff8718ef136acc62503093c2810ddaafd7`.
- This was a compile-time type mismatch; native ABI and backend registry tests had passed. The failed runs did not produce an APK.
- Added controller hotplug refresh for connected/disconnected profile status in commit `4ba9f812ea9bea4cb991064be68d40aea55f0812`.
- Latest CI after these corrections is pending; do not distribute an APK from the failed runs.


### Controller compile correction — 2026-11-18
- The first controller UI CI attempt then found that `InputManager.inputDeviceIds` is an `IntArray`, which does not expose the used `mapNotNull` extension directly.
- Fixed enumeration by converting IDs to a sequence before `mapNotNull`, sorting and materializing the device list in commit `4f1a1db84ba1dc50565653eb8375e027fccccce1`.
- Latest verification is run #395 (`44285983485`); controller model/store/UI and the component result typing fix remain unverified until this run completes.


### CI failure diagnosis and planner compatibility fix — 2026-11-18
- Actions run #397 (`44286020694`) at `43bd06c936770aab71967c29eac96a4d22d2af01` completed with failure: 126 JVM tests ran, one failed; native ABI conformance and backend registry tests passed. APK build/upload were skipped.
- Failing test: `NgrComponentInstallPlannerTest.skipsAlreadyInstalledDependencies` at line 49. Root cause: version-aware planning treated an installed component with no version metadata as an update, breaking callers using the legacy installed-ID-only contract.
- Fixed planner compatibility: an installed ID is treated as installed when version metadata is absent; when an explicit installed version exists, it is skipped only if it exactly matches the catalog version. The mismatched-version update test remains authoritative.
- Fix commit: `255a4684614b69319b5328787b9ad4db725c01aa`. New CI verification is required; no APK is available from #397.

## Verified CI and direct SAF folder import — 2026-11-18

### CI and APK
- Actions run #400 (44287692239) for commit dcc1719592b1cd71e66bdb5614d764c8623a10a0 passed: native ABI conformance, backend registry, JVM unit tests, ARM64 debug APK build and artifact upload.
- Artifact: NGR-arm64-debug, artifact ID 14419548847; downloaded and inspected ZIP. It contains app-debug.apk (2,486,165 bytes). APK SHA-256: 1f897b4f19b708936a1f90903a41e53c3191d4392997d6a400720a3515b4f55b.
- CI run #398 for planner compatibility fix and #399 for development log update also passed all steps.
- Physical-device install/runtime test remains outstanding.

### P0 direct SAF folder import
- Replaced the old folder path (SAF -> cache copy -> generated NGRPACK -> package-manager incoming copy -> extraction) with a direct path: SAF -> private managed folder staging -> streamed file/path/size validation -> in-place executable scan + SHA-256 manifest -> validated atomic promotion.
- Multiple executable selection now rewrites only the generated manifest; game payload files are not copied or re-archived again.
- Added NgrPackPackageManager.createFolderImportStaging() and installPreparedFolder() with private-parent/UUID/path checks, manifest/hash validation, atomic replacement and backup rollback.
- Added NgrGamePackageBuilder.prepareFolderInPlace(); existing NGRPACK and ordinary ZIP import flows remain supported.
- Added JVM tests for in-place preparation, direct install, hash validation, atomic staging move and rejection of an outside staging path.
- MainActivity direct folder import commits: dadab2ee6a9460ede7ae56838ddbdd0704e70984, 0489f3ebc6db48575d4c7b68c1c23efcfe28fd5f, df0b8601ed53ecaf953ddc9c30cf80543b54cb80, 25ebfd80991891f3eedfc7ddb5a177817241c88b, 0c1cf8cee15a9b1e74c179065339c90d9efe0e5b.
- Expected storage behavior: only one full payload copy in managed package storage; no cache copy, generated multi-GB archive, second incoming archive copy or second extraction. Actual device storage behavior still needs validation with a large SAF folder.
- New direct-import commits require CI verification.


### Direct folder import CI verification — 2026-11-18
- Actions run #409 (44292851873), commit f415398974c9aa509701c68bf428fbd6fbd6f170, passed native ABI conformance, backend registry, full JVM unit tests, ARM64 APK build and artifact upload.
- New direct folder import tests passed in the JVM suite.
- Artifact ID 14422691411 (NGR-arm64-debug) downloaded and inspected. APK contains lib/arm64-v8a/libngr_runtime.so and is 2,486,165 bytes.
- Verified APK SHA-256: 61723fa6be929d32aec02785610f7691c39ac4264615157002a15c8a56936a45.
- Earlier intermediate direct-import commits #401–#407 exposed a missing return statement in the new in-place builder; fixed in commit 47e2aa4fe32af0e47e7b1759a276dcdad9e19290. The final run #409 passed after the follow-up MainActivity staging-reference correction.
- This verifies CI and package contents, not a physical Android installation or a multi-GB SAF import on device.


## Translator preset management — 2026-11-18
- Added a versioned FEX preset preference to environment profiles; schema advanced from v2 to v3. Existing v1/v2 profile JSON migrates with FEX preset defaulting to BALANCED.
- Added Box64 and FEX Compatibility/Balanced/Performance choices to the environment profile editor and details. UI labels explicitly identify them as saved intent, not active runtime flags.
- Extended resolved launch configuration and preview to expose both translator presets; resolver still does not execute a process or translate these choices into Box64/FEX runtime options.
- Added codec migration/round-trip tests and launch resolver assertions. Translator profile and preview changes passed CI run #420; abandoned-staging cleanup and its retention test passed in run #429.
- Commits: 522768655b7cc8c7fafd00a7f4110c51db479200, b5eaa86ee0dfb624bc1fd1615f97b39bf52ad3e9, 06f8b0a8fe561f8ad325e65e37786d331500f988, 14a959a9436b4a50d9071a6e0a322242c1170864, 6cc85681e6250162fbb7ce4b509e4ec615d793d4, 749480364aaa8d1960e10ffd51d225c4d8f12458, 41409ac1f1812a265680efb2850c184298eff417, 1946f9da99ee1403e149cd431967325c62da219b.

### Latest verified artifact
- Actions run #411 (44293256826), commit 0d1f3c1332f455ffedcc58ef2c4c4546606d439d, passed native ABI conformance, backend registry, JVM unit tests, ARM64 debug APK build and upload.
- Artifact ID 14422247556 downloaded; ZIP contains app-debug.apk (2,486,165 bytes), APK SHA-256 00393af5291eb636157b7df792f0b99ebe90214b41ed5b93d0fd23bf5a70d802.
- This artifact predates the translator-preset schema/UI changes. The latest preset changes must receive their own successful CI artifact before being called verified.


## Translator preset management — 2026-11-18
- Added a versioned FEX preset preference to environment profiles; schema advanced from v2 to v3. Existing v1/v2 profile JSON migrates with FEX preset defaulting to BALANCED.
- Added Box64 and FEX Compatibility/Balanced/Performance choices to the environment profile editor and details. UI labels explicitly identify them as saved intent, not active runtime flags.
- Extended resolved launch configuration and preview to expose both translator presets; resolver still does not execute a process or translate these choices into Box64/FEX runtime options.
- Added codec migration/round-trip tests and launch resolver assertions. CI verification is pending for the latest commits.
- Commits: 522768655b7cc8c7fafd00a7f4110c51db479200, b5eaa86ee0dfb624bc1fd1615f97b39bf52ad3e9, 06f8b0a8fe561f8ad325e65e37786d331500f988, 14a959a9436b4a50d9071a6e0a322242c1170864, 6cc85681e6250162fbb7ce4b509e4ec615d793d4, 749480364aaa8d1960e10ffd51d225c4d8f12458, 41409ac1f1812a265680efb2850c184298eff417, 1946f9da99ee1403e149cd431967325c62da219b.

### Latest verified artifact
- Actions run #411 (44293256826), commit 0d1f3c1332f455ffedcc58ef2c4c4546606d439d, passed native ABI conformance, backend registry, JVM unit tests, ARM64 debug APK build and upload.
- Artifact ID 14422247556 downloaded; ZIP contains app-debug.apk (2,486,165 bytes), APK SHA-256 00393af5291eb636157b7df792f0b99ebe90214b41ed5b93d0fd23bf5a70d802.
- This artifact predates the translator-preset schema/UI changes. The latest preset changes must receive their own successful CI artifact before being called verified.


## Abandoned folder staging cleanup and current verified build — 2026-11-18
- Added `NgrPackPackageManager.cleanupAbandonedFolderStaging()`; it removes only UUID-named real staging directories older than 30 days under the private `.folder-staging` root. Symlink roots/children and recent directories are skipped.
- MainActivity schedules cleanup on its package worker at startup; added a JVM test confirming expired staging is removed while recent staging is retained.
- Commits: cleanup method `179217a62bc9b505ed8fea56f7b19a2c6be11f3b`, startup hook `aa8ac8117684f50eb7d8201e2659057313eeb85a`, test `03f369e5fed7bf657bca68c76674cdfe466030cd`.
- Actions run #429 (44316501792), commit `03f369e5fed7bf657bca68c76674cdfe466030cd`, passed Native ABI conformance, backend registry, JVM unit tests, ARM64 debug APK build and artifact upload.
- Artifact ID `14433695246` downloaded and inspected. ZIP contains `app-debug.apk` (2,486,165 bytes), including `lib/arm64-v8a/libngr_runtime.so`. APK SHA-256: `5e3dd973b753f935939f9f0bf64ad70a9f072b556bda9c53b67429a801f79d44`.
- Physical Android installation and multi-GB SAF import remain unverified; no device test is claimed.


## Session 2026-11-21 — Static recompiler source audit

### Repository baseline
- Inspected current main README and technical documents in Snap888/NativeGameRecompiler.
- Confirmed NGR has an Android ARM64 shell, package/component management foundation and backend ABI header, but no compiler plugin loader, static recompiler or title runtime yet.
- Current NGR toolchain is JDK 17, NDK 27.2.12479018, CMake 3.22.1 and Gradle 8.9.

### Upstream audit
- Reviewed README/build configuration for XenonRecomp, ReXGlue SDK, UnleashedRecomp-Android, XboxXBERecomp, N64Recomp and N64ModernRuntime.
- Reviewed Android Original Xbox port SCDA-XBERecomp-android and Android N64 examples Zelda64Recomp-Android, CBFD-Recompiled-Android-Evolved and automobili-lamborghini-recomp.
- Key findings and the proposed NGR module/file map are recorded in docs/RECOMPILER_INTEGRATION_AUDIT.md.

### Decisions
- N64Recomp remains the first planned compiler proof-of-concept; N64ModernRuntime is GPL-3.0 and requires an explicit licensing/distribution decision before integration.
- XenonRecomp and ReXGlue are alternative/overlapping Xbox 360 code-generation paths. ReXGlue's Linux ARM64 support is not proof of Android NDK support; its current CMake requires Clang 20+, C++23 and CMake 3.25+.
- UnleashedRecomp-Android is a title-specific Android reference, not a universal Xbox 360 runtime.
- XboxXBERecomp's upstream host is Windows/D3D12/XAudio2-oriented; SCDA-XBERecomp-android demonstrates the Android Vulkan/SDL3 adaptation path.
- Keep compiler ABI separate from execution backend ABI v1. N64Recomp is a build-time compiler, not a runtime plugin.
- No upstream source, game assets, ROMs, generated PPC code or copyrighted game payloads were copied into NGR in this audit.

### Validation status
- Audit documentation committed to main.
- No native code changed and no APK build was triggered by this documentation-only pass.
- No physical-device validation or game compatibility claim is made.

### Next actions
- Pin upstream commits/submodules and finish SPDX/transitive dependency review.
- Build N64Recomp host CLI in CI with synthetic/redistributable test input.
- Decide the GPL runtime boundary before integrating N64ModernRuntime.
- Compare XenonRecomp and ReXGlue with a non-copyrighted PPC fixture before selecting an Xbox 360 codegen backend.



## Session 2026-11-21 — Scope reset: Xbox 360 + Nintendo Switch

### User decision
- Active console scope is now restricted to Xbox 360 and Nintendo Switch only.
- The selected foundations are the writable Snap888/XDroid360 fork and shirai91/nx2android.
- N64, Original Xbox, PlayStation, Xbox One, Switch 2 and generic PC/EXE recompilation are excluded from this workstream.

### Source audit
- XDroid360 fork commit inspected: 6eefb7f738d4e63cb11cb6bb63911f55094e8ada.
- Inspected XDroid360 Android app Gradle configuration, RecipeRunner, on-device toolchain build script, submodule declaration and NOTICE.
- Confirmed Xbox 360 reference path: user-owned ISO/ZIP → title recipe → XenonRecomp PPC-to-C++ → Android Clang/LLD → title-specific runtime + Xenos Vulkan or Xenia Vulkan graphics path.
- Confirmed the donor does not provide one universal finished Xbox 360 runtime: DAORecomp, SoDRecomp and ME1Recomp are title-specific.
- nx2android commit inspected: 828ded46adb11722591c4353a2ba88175640d250.
- Inspected nx2android architecture/research, porter CLI, NSO/NRO rewriter, Android runtime build, installation guide, nxnative README and MIT license.
- Critical Switch finding: nx2android currently performs preparation on Windows/macOS/Linux and produces an Android APK + game data pack; it is not an Android on-device importer/compiler. Android execution is native ARM64 through Horizon host services and NVN-to-Vulkan.
- Detailed findings: docs/XBOX360_SWITCH_INTEGRATION_PLAN.md.

### Code added
- Added app/src/main/java/com/ngr/app/recompiler/ConsoleRecompilerTargets.kt with exactly two typed targets.
- Xbox 360 metadata records PowerPC Xenon, arm64-v8a, on-device preparation reference, ISO/ZIP input and title-specific runtime reference.
- Switch metadata records ARM64 NSO/NRO rewriting, arm64-v8a, desktop-assisted preparation reference, NSP/XCI/NSZ/XCZ input and user-provided key requirement.
- Both targets are explicitly RESEARCHED_NOT_INTEGRATED. This catalog is not a compiler implementation or compatibility claim.
- Added ConsoleRecompilerTargetsTest.kt covering target exclusivity, input formats, preparation modes and key requirement.

### Project documents updated
- README.md, docs/TECHNICAL_SPEC.md and docs/NEXT_ACTIONS.md now prioritize Xbox 360 + Switch only.
- Earlier N64 roadmap is superseded. Generic Wine/Box64/FEX work is legacy/deferred while this console workstream is active.
- License/provenance review is being extended for both donor stacks.

### Validation status
- Source and documentation changes committed to main.
- Isolated Kotlin compiler check passed for the new target catalog (`kotlinc` + runtime assertions: exactly two targets, extension detection, Switch key requirement).
- Full Gradle JVM test suite and Android arm64-v8a CI have not yet been run/verified for the new target catalog.
- No donor runtime/compiler source has been copied into NGR yet.
- No APK artifact or playable-game result is claimed.
- No physical-device validation is claimed.

### Next actions
1. Run JVM tests and arm64-v8a APK CI.
2. Define compiler job ABI and per-title profile/fingerprint models.
3. Start Xbox 360 vertical slice: pin dependencies, build Android XenonRecomp/Clang/LLD and validate a synthetic PPC fixture.
4. Start Switch vertical slice: pin dependencies, build desktop preparation tools, validate NSO/NRO rewrite fixture, then build Android Horizon/NVN Vulkan runtime.
5. Decide whether Switch preparation remains desktop-assisted initially or is ported to Android after host/toolchain feasibility tests.


## Session 2026-11-21 — Compiler job manager foundation

### Implemented
- Added ConsoleBuildJob and ConsoleBuildJobStore as a separate contract from execution Backend ABI v1.
- Job metadata is restricted to Xbox 360 and Nintendo Switch and stores a SAF content URI, persisted grant flags, source/profile/toolchain fingerprints, workspace identifier, stage, progress, lifecycle state and timestamps.
- Target-specific stages distinguish Xbox PPC recompilation/ARM64 compile from Switch NSO rewrite/shader preparation.
- Added atomic JSON persistence, strict schema and URI validation, bounded job count, immutable source identity, write-once fingerprints and monotonic stage/progress updates.
- Added queued/running/cancel-requested/cancelled/failed/succeeded states, queued cancellation and recovery of interrupted workers after process death.
- Added per-job bounded logs with control-character sanitization and redaction of common prod.keys/title-key/password/token assignments. Game data, firmware and keys are not persisted in job metadata.
- Added JVM tests for both target records, invalid source URIs, target-stage mismatch, transition rules, recovery, cancellation, log redaction and corrupt metadata.

### Validation
- Source and tests have been authored and committed; full Gradle/JVM and Android APK CI are pending.
- No compiler binary, runtime, UI wiring or playable title is claimed at this stage.
- No APK artifact is available from this change yet.

### Next
1. Run Android CI and correct any Kotlin/test failures.
2. Connect SAF permission acquisition and the job store to the import screen.
3. Add a real job worker with cancellation checkpoints and workspace quotas.
4. Build the pinned XenonRecomp toolchain and validate a synthetic PPC fixture before adding a title runtime.


### Compiler job manager CI result — 2026-11-21
- Actions run #467 (44914879736) completed successfully at commit a83cb286057b40cda4f5ccd59a1d71246c0ce0cb.
- Passed: native ABI conformance, backend registry, all JVM unit tests, ARM64 debug APK build and artifact upload.
- Artifact: NGR-arm64-debug, ID 14711137187, expires 2026-12-05.
- Downloaded ZIP SHA-256: ca754e23c03f9d433a90155494b0630d1856a61417ba421c6c9d5ff51b37b60e.
- Extracted app-debug.apk size: 2,518,991 bytes; SHA-256: bd0a984bdd7c0a27c45f9785a949810fdbcae3372a8c74fadc882b381725ed4b.
- APK inspection confirms lib/arm64-v8a/libngr_runtime.so is present.
- This verifies CI build/package contents only. Device installation, SAF persistence and actual console compilation/runtime remain unverified.


### Console build queue UI and CI — 2026-11-21
- Added a CONSOLE RECOMPILERS section with separate Xbox 360 and Nintendo Switch source pickers.
- Uses ACTION_OPEN_DOCUMENT and takePersistableUriPermission; persists only the content URI and granted read/write flags, display name and job metadata. No game content or keys are copied into the job record.
- Added visible queue rows with target, source name, state, stage, progress and a cancel action for queued/running jobs. UI explicitly states that compilation has not started because workers are not yet connected.
- Added source extension checks against the two-target catalog: Xbox 360 ISO/ZIP; Switch NSP/XCI/NSZ/XCZ.
- CI run #469 (44915277180) failed at Kotlin compilation because MainActivity was missing imports for ConsoleBuildJobStore and ConsoleTargetId. This was diagnosed from the job log and corrected in commit 36d464ba65be57e4896612535d905f771cf08f07.
- Corrected commit passed Actions run #470 (44915313963): native ABI conformance, backend registry, all JVM tests, ARM64 APK build and artifact upload.
- Artifact NGR-arm64-debug ID 14711342035; ZIP SHA-256 c9758a075144fd4f3406dbbeb81f907f37800b5c92bbb96ba0cccfbf95aae29b; APK SHA-256 a01790c140a4a8ab7755bb86306617d58bc5052c47a904503ffa7b98aa2078e2.
- APK contains lib/arm64-v8a/libngr_runtime.so. Device install and SAF permission persistence across process death remain unverified.
- This is a queue UI, not an operational recompiler: XenonRecomp/Clang/LLD worker, Switch preparation adapter, title runtime and gameplay are not yet integrated.


### Xbox 360 XenonRecomp Android toolchain — 2026-11-21
- Added toolchains/xbox360/build_xenonrecomp_android.sh, pinning XenonRecomp ddd128bcca99fe8bfbb99bea583c972351fa6ace and the XDroid360 patch bfbaa874323181191228ca20a7775f1725ee287a with SHA-256 verification.
- Script builds XenonRecomp for Android API 29, arm64-v8a and 16 KB page-size compatibility using NDK 27.3.13750724; verifies the output ELF is AArch64.
- Added an isolated GitHub Actions workflow and artifact containing the compiler, corresponding patched source archive, patch script, GPL-2.0 text, MIT notices, provenance and hashes.
- This stage intentionally excludes Clang/LLD, Xenos shader compilation, the GPL-3.0 native renderer and all title runtimes. A XenonRecomp executable only emits PPC-derived C++ and cannot launch a game by itself.
- CI result pending; no compiler binary or playable Xbox 360 support is claimed yet.


### XenonRecomp Android ARM64 toolchain CI result — 2026-11-21
- Actions workflow Xbox 360 XenonRecomp ARM64 run 44915893900 completed successfully.
- Built from XenonRecomp commit ddd128bcca99fe8bfbb99bea583c972351fa6ace with XDroid360 patch commit bfbaa874323181191228ca20a7775f1725ee287a; patch SHA-256 checked.
- Artifact NGR-Xbox360-XenonRecomp-arm64 ID 14712235777; archive SHA-256 09b6e6964fa96c3c5d86b8769e27ddd166912a864f400f9495495115cb2be352.
- Binary SHA-256 1ae719f34b888fc15d113e700ec4e695ef826ecf0b2399cfb733bfe474390fe2; source archive SHA-256 31af71f8eed4858551099c879d94778c5b1e10ba209651fda3baac751cc0d024.
- ELF inspection confirms AArch64, Android API 29 and 16 KB page compatibility. DT_NEEDED is limited to libm.so, libdl.so and libc.so.
- This is a real PPC-to-C++ compiler executable, not a game runtime. Synthetic PPC fixture, Android Clang/LLD and title-specific runtime integration remain pending.


### Xbox 360 Android Clang/LLD kit — 2026-11-21
- Added toolchains/xbox360/build_clang_kit_android.sh using LLVM 22.1.8 source SHA-256 pin and NDK 27.3.13750724.
- Workflow builds host tablegens, cross-compiles Android Clang and LLD for API 29/arm64-v8a with 16 KB page compatibility, and assembles the matching NDK sysroot/resource-header kit.
- Artifact contains libclang.so, liblld.so, kit.zip, LLVM/NDK license notices, provenance and SHA-256 manifest.
- Build is intentionally separate from XenonRecomp. No game build worker, title runtime or playable title is claimed.
- CI result pending.


### Synthetic Xbox 360 PPC translation fixture — 2026-11-21
- Added a small synthetic, unencrypted XEX2 fixture generator containing a PE32 image with .text/.pdata and a two-instruction PPC function (addi r3,r0,1; blr).
- Added a host XenonRecomp CI workflow using the same pinned source and XDroid360 patch; it checks generated ppc_config.h, ppc_context.h, shared header, function map, and _xstart C++ output.
- The fixture contains no commercial game code, firmware or user keys. CI result pending.


### Synthetic XEX2/PPC fixture CI — 2026-11-21
- Actions run 44916892595 passed translation of a generated unencrypted XEX2 fixture into PPC C++ and verified expected generated files/symbols.
- Actions run 44917032342 passed the same translation plus host Clang compilation of ppc_recomp.0.cpp and ppc_func_mapping.cpp.
- Fixture is a generated PE32/XEX2 image with .text and empty .pdata; PPC function contains addi r3,r0,1 and blr. It contains no commercial game data, firmware or keys.
- The recompiler logs that eight common save/restore helper addresses are unspecified in this minimal fixture. This is expected for the synthetic profile; real title profiles must supply the required helper addresses and runtime symbols.
- Two CI iterations were needed: GCC host build rejected the upstream anonymous aggregate type, so host fixture builds now use Clang; the generated entry point is emitted as PPC_FUNC_IMPL(__imp___xstart), and the assertion was corrected accordingly.
- This verifies parser -> PPC analysis -> C++ generation -> host compilation for one tiny function only. It does not validate a commercial XEX, complete game profile, Android cross-link, or gameplay.


## Session 2026-11-21 — SAF source staging worker

### Implemented
- Connected the console import picker to unique WorkManager jobs.
- Added a foreground data-sync worker which verifies the persisted SAF read grant, copies the selected source into a private per-job workspace, computes SHA-256 in the same streaming pass and persists the source fingerprint.
- Enforced target extension, non-empty file, reported-vs-copied size consistency, 64 GiB maximum source size and a 128 MiB private-storage reserve.
- Xbox ZIP intake scans the central directory without extracting, rejects traversal-like paths and requires at least one .iso entry. Raw Xbox ISO is staged without guessing undocumented header rules.
- Switch NSP/XCI/NSZ/XCZ intake is bounded and staged, but package/keys validation is intentionally deferred to the pinned nx2android/LibHac toolchain.
- Added source-staged lifecycle state, cancellation integration, progress notifications and worker restart re-entry.
- UI now shows SHA-256 and accurately distinguishes source staging from the not-yet-integrated compiler/runtime.

### Safety and limitations
- Source content is copied only to app-private storage; no game files, keys or firmware are uploaded or committed.
- The worker does not extract ISO/NSP/XCI containers, run XenonRecomp, rewrite NSO, compile ARM64, translate shaders or create a playable package.
- WorkManager/foreground service integration has been authored but awaits CI and physical-device verification.

### Next
1. Run full JVM tests and ARM64 APK CI.
2. Verify multi-gigabyte copy, notification, cancellation and process-death behavior on Snapdragon Android.
3. Add an isolated Xbox ISO/ZIP extractor and synthetic fixture tests.
4. Pin and build XenonRecomp plus the Android patch before implementing the PPC compiler worker.


## CI verification — source staging worker

- Run #483 (44917831975) passed at commit d8b1a08a85902b407bee92f513b1d6cc719acb9b: native ABI conformance, backend registry, all JVM tests, ARM64 debug APK and artifact upload.
- Artifact NGR-arm64-debug ID 14712576422 (expires 2026-12-05); ZIP SHA-256 e5a0b9075bd0000bd12e57e6b7c3f94500438476ee29e3e673ef3c7c457726bf.
- Extracted app-debug.apk size 4,178,861 bytes; SHA-256 d581caae4745d5252e17190842d847842e5bb53ff7105af0f6ca2a4535a1f762.
- APK contains lib/arm64-v8a/libngr_runtime.so; archive contains WorkManager runtime metadata.
- Follow-up fix in commit 2eb0757b84f8288a53d3cbe73c0531b139ba9f85 treats negative SAF size metadata as unknown instead of rejecting the source. Run #484 passed native tests, all JVM tests, ARM64 APK build and artifact upload. Artifact ID 14712008705; ZIP SHA-256 068bfaa3e551f9c7623f331d41e537bd9f59f4e2d8349675210b246c56e35f79. Extracted APK size 4,178,861 bytes; SHA-256 93feb3b6efd730dbbd59a29810480380fb0e18595fd5eb3f912d428fa5d37087.
- Device behavior remains unverified; this build does not contain a console compiler or playable game runtime.

 
## Session 2026-11-21 — Nintendo Switch runtime embedding

### Donor audit
- Rechecked nx2android at commit `f32b3d563f0615d0e825747285d4e38d780e4ebb`.
- Confirmed `nxnative/dm/android/build.py` compiles the title-profiled Android host and emits `libdmhost.so`; `nxnative/dm/android/apk/mkapk.py` is only the minimal NativeActivity APK packager/signing step. Game files are a separate data pack.
- Confirmed the runtime library exports `ANativeActivity_onCreate`, uses a title-specific generated profile header and expects the game's data under its app-private files area.
- The donor CLI preparation path remains Python + .NET 10 + NDK r27c + user-provided keys. It is not yet an Android-native on-device pipeline.

### NGR implementation
- Added `native/ngr-runtime/src/switch_activity_loader.cpp`: a NativeActivity dispatcher that reads a library path from the NGR activity intent, restricts it to a canonical regular file under NGR's private `files/switch-ports/` directory, loads it with `dlopen`, and forwards the lifecycle to the title runtime's `ANativeActivity_onCreate`.
- Added the `ngr_switch_loader` CMake target.
- Registered an internal `android.app.NativeActivity` in the NGR manifest, backed by `ngr_switch_loader`; added the network, Wi-Fi and vibration permissions required by donor-host features.
- Added `SwitchRuntimeLauncher.kt` to validate 16-hex title IDs, verify the expected per-title runtime file and launch the NativeActivity in the existing NGR package.
- Added JVM tests for title-ID validation and runtime file presence/path checks.
- Added `docs/SWITCH_RUNTIME_INTEGRATION.md` with the per-title package layout, pipeline and security/licensing gates.
- Updated README status to distinguish this dispatch scaffold from a working Switch runtime.

### Current limitations
- This is the first in-package dispatch slice; no nx2android runtime C sources have yet been vendored into the NGR tree and no game-specific `libdmhost.so` has been built or launched.
- NativeActivity lifecycle forwarding is not yet CI-verified or device-tested. A synthetic native runtime fixture is required before attempting a commercial title.
- The extraction/NSO rewrite/shader pipeline is still desktop/CI-assisted; no claim of on-device Switch recompilation.
- No per-title NGR Switch package installer/manifest verifier is complete yet.

### Next
1. Run Android CI and fix compile/link/manifest issues in the loader.
2. Add a synthetic donor-style NativeActivity library and instrumentation test for create/pause/resume/destroy/surface callbacks.
3. Implement the Switch Port Package manifest, hash inventory, profile fingerprint and atomic install/rollback.
4. Vendor/pin nxnative core and dependency revisions with a complete license/SBOM inventory.
5. Wire donor extraction, NSO rewrite, shader translator and per-title host build into a resumable preparation adapter.
6. Build and device-test one profile-matched Switch title on Adreno; only then expose a playable status.


## Session 2026-11-21 — embedded nx2android Switch runtime

### Architecture correction
- Rejected the donor's per-game APK as the NGR distribution model. NGR remains the only installed app; each Switch title gets a private runtime shared object and data pack.
- Audited nx2android at commit f32b3d563f0615d0e825747285d4e38d780e4ebb. Confirmed build.py produces a title-profile-specific libdmhost.so NativeActivity, while porter/datapack.py produces a separate data directory (mods, RomFS, optional DLC/system data, shaders.dmsv, env.txt, settings.txt, nx2a-data.json).
- Added an NGR NativeActivity loader library. It validates title ID, runtime path and data root, sets NGR_SWITCH_GAME_ROOT, dlopens the title-specific libdmhost.so and forwards ANativeActivity_onCreate.
- Added a fail-closed patcher for nx2android front_android.c so its g_root can point at NGR's per-title data directory. This is essential: donor's default path is the app-wide externalDataPath and would cause different games to overwrite one another.
- Added SwitchRuntimeLauncher, an installed-port store/importer, a Switch library UI section and launch buttons. Prepared port import expects runtime/libdmhost.so, runtime-manifest.json and data/nx2a-data.json plus mods/ and env.txt. It checks AArch64 ELF64 and runtime SHA-256 before installing into internal/external app-specific storage.
- Added a manual GitHub Actions workflow to build the pinned nx2android runtime for a selected Android title profile with NDK r27c.
- Added docs/SWITCH_IN_PROCESS_RUNTIME.md and updated licensing provenance.

### Not yet verified
- No build has yet run after these changes. NativeActivity loader C++ and Kotlin importer require Android CI/JVM validation.
- The manual runtime workflow has not yet been run for a known supported title ID; toolchain/profile availability and ELF dependencies remain unverified.
- The raw NSP/XCI intake worker still only stages source bytes. The complete LibHac extraction, NSO rewrite, shader pipeline and data-pack generation are not yet integrated into NGR.
- No physical-device Switch runtime launch has been performed. Do not label the current app a playable Switch recompiler.
- The donor APK's per-title gamepad theme/font assets still need an NGR per-title asset-loading solution.

### Next actions
1. Run Android/JVM/native CI immediately and fix all compile/test errors in the new loader, manifest and importer.
2. Run the manual runtime workflow for a title with a verified nx2android Android profile; inspect ELF NEEDED entries, 16 KiB alignment and artifact hash.
3. Build a prepared port bundle format (runtime + data pack + runtime manifest) from nx2android's existing porter output; do not include keys, firmware or game dumps in CI.
4. Integrate the actual nx2android porter preparation workflow and choose a supported on-device/desktop companion execution model for Python/.NET/LibHac.
5. Device-test import, launch, lifecycle, Vulkan, audio, controller input, saves, suspend/resume and isolation between two title IDs.

## Session 2026-11-21 — nx2android porter connected without per-game APK

### Implemented
- Added `patches/nx2android/0001-use-ngr-title-data-root.patch`: the donor Android host reads `NGR_SWITCH_GAME_ROOT` before falling back to its app-wide external files directory. This keeps each game's mods, RomFS, shaders, settings and logs isolated under its NGR title directory.
- Added `patches/nx2android/0002-ngrn-build-without-per-game-apk.patch`: the donor porter skips only `self.apk()`; it still extracts the source, rewrites NSO modules, translates shaders, builds the title-profiled `libdmhost.so` and creates the nx2android data pack.
- Added `tools/switch/port_game.py`: pinned-checkout orchestration for the real donor `python -m porter port` command. It requires the user's own `prod.keys` and configured NDK/.NET tool paths, applies both patches, disables APK generation, and packages `runtime/libdmhost.so`, the donor data pack and a SHA-256 build receipt into one NGR-importable folder.
- Hardened data-pack copying: rejects symlinks and non-regular files instead of following links out of the donor output tree.
- Updated the Switch runtime smoke workflow to syntax-check both Python adapters and verify both donor patches apply to the exact pinned revision.
- Updated `docs/SWITCH_RUNTIME_INTEGRATION.md` and `docs/NEXT_ACTIONS.md`.

### Architecture now
- NGR remains the only installed Android application.
- Per title, NGR stores the NativeActivity library privately and stores the larger nx2android data pack in app-specific external storage.
- NGR's NativeActivity loader selects the correct title library and passes its per-title data root. The NGR adapter does not create per-game APKs.
- The current Android UI imports an already prepared NGR Switch Port folder and shows a launch entry. The desktop adapter is not yet wired into Android-side Python/.NET execution.

### Not yet verified / not claimed
- GitHub Actions has not yet reported the new run result for the latest commits. Do not claim patch application, Python syntax, CMake, JVM tests or APK build are green until the run is inspected.
- No real NSP/XCI has been ported through the new adapter in this session; no `prod.keys`, firmware or game content was used.
- No title-specific runtime has yet been imported and launched on a physical Adreno device.
- The donor runtime is profile-specific, not a universal Switch emulator. A title must exist in nx2android's Android profile catalog and match the exact game/update build.
- The separate `build_runtime.py` smoke artifact is only a host-library build, not a playable port.

### Next actions
1. Verify both donor patches apply in CI and fix any hunk drift.
2. Verify the NGR NativeActivity loader compiles and all JVM tests pass in the same Android build.
3. Run `port_game.py` against a legal test dump/profile with the user's keys and inspect the package inventory, runtime ELF dependencies, profile hash and data manifest.
4. Import that package through NGR, test lifecycle/surface recreation/audio/input/saves and verify two title IDs do not share data.
5. Launch a supported title on Adreno before calling the Switch pipeline playable.

### Follow-up hardening — per-title state isolation
- Added `0003-use-ngr-per-title-save-and-temp-root.patch` and applied it from `port_game.py`: donor save and temporary directories now resolve beneath `files/switch-ports/<TITLE_ID>/`, not one shared app-wide directory.
- Updated the NativeActivity dispatcher to validate the private title directory and set both `NGR_SWITCH_GAME_ROOT` and `NGR_SWITCH_PRIVATE_ROOT` before loading the donor runtime.
- Updated the Switch smoke workflow to check all three pinned donor patches.
- Still awaiting Actions verification; the new patches and NativeActivity forwarding remain unverified until CI and device tests pass.

### Data-pack path correction
- The donor writes absolute `DM_AOC_DIR`, `DM_SYSDATA` and optional font paths against `/sdcard/Android/data/com.ngr.app/files`. NGR stores the pack below `switch-ports/<TITLE_ID>/data`, so `port_game.py` now rewrites that base prefix in the generated `env.txt` to the title-specific data root before packaging.
- Corrected unified-diff hunk counts in all three donor patches and added CI checks for patch application plus adapter CLI help/syntax. The actual workflow result is still pending.


## Session 2026-11-21 — Switch integration CI diagnosis

### Verified repository state
- Main was at `6c4f9169869f28f1a6f4d40dbf73ecb983f1f3a2` before this session's CI fixes.
- Switch integration source is present: desktop `port_game.py` adapter, three pinned nx2android patches, `libdmhost.so` runtime builder, Kotlin import/store/launcher/receipt verifier, NativeActivity dispatcher, and Switch library UI.
- The adapter deliberately skips per-game APK creation; NGR imports a runtime/data folder and keeps one NGR APK.
- The Switch porting pipeline remains desktop-assisted (Python/.NET/NDK and user-owned keys); Android source staging does not run the donor toolchain.
- No legal title has been ported/launched in NGR. No device test has been performed.

### CI failures found and corrected
- Android CI run #570 [44935583030](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44935583030) failed during Kotlin compilation, before JVM tests: literal backslash-n sequences were accidentally inserted into the `REQUEST_SWITCH_PORT_FOLDER` callback in `MainActivity.kt` (compiler errors around line 1578).
- Fixed the callback into normal Kotlin source lines. Commit: `e541e87c596303b9a3f7b497eeb5fdb5ba9be7ca` — `Fix Switch folder picker callback syntax`.
- Switch smoke run #25 [44935562412](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44935562412) failed in Android SDK setup because `setup-android` attempted to install the removed SDK package `tools`.
- Added explicit `packages: platform-tools` to the Switch smoke workflow. Commit: `cebee457da31a730b78a33adffc0bc815cd76afb` — `Fix Switch smoke Android SDK setup`.
- New Android CI run #572 [44938186904](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44938186904) and Switch smoke run #26 [44938186883](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44938186883) started after these fixes. Their results must be checked before marking either path green.

### Current validation boundary
- Python adapter syntax and native ABI/registry checks passed in run #570, but Android Kotlin compilation failed and no APK was produced.
- The Switch smoke run failed before donor checkout/build; it did not test patch applicability or compile `libdmhost.so`.
- Keep README status aligned with this boundary: Switch integration code exists, but it is not yet build-verified or playable in NGR.

### Next actions
1. Verify runs #571/#572 and #26; fix any Kotlin/CMake/NDK or donor-patch/build failures.
2. Add a synthetic NativeActivity test library and lifecycle test; validate dlopen/NativeActivity forwarding.
3. Complete package inventory/hash validation and make the two-root (private runtime + external data) install atomic across crash/restart.
4. Confirm every asset and dependency needed by the donor runtime is present when the donor APK step is skipped.
5. Port a user-owned game matching a pinned nx2android Android profile, import the generated folder, and test launch, surface, audio, input, pause/resume and saves on Adreno.


## Session 2026-11-21 — Switch runtime build and asset integration

### Successful CI checkpoints
- Android CI run #585 [44938575345](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44938575345), commit `e1f7b6ee87dcced65a865e0994c3f576b88eb0eb`: native ABI checks, backend registry tests, Switch tooling syntax, JVM tests, CMake/NDK build of both `libngr_runtime.so` and `libngr_switch_loader.so`, ARM64 debug APK and artifact upload all passed. Artifact `NGR-arm64-debug` ID `14720757607`, ZIP SHA-256 `f8bd92a8398464fb01b45ebc2575e45c79b19191bf67f3d79bbc371a9ac7af9e`.
- Switch runtime smoke run #38 [44938805585](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44938805585), commit `63cd4a2226865093395ec655776a76b441f61610`: all four NGR patches were applied sequentially to the pinned nx2android checkout, then the patched title-profiled ARM64 NativeActivity host was built successfully. Artifact `NGR-Switch-Runtime-Smoke` ID `14720643069`, SHA-256 `d691f01e6b13bd04d7cdaa6c864b535c7296cf4515de8c6d4a64a753faa03b58`.
- These are compile/packaging checks only. No commercial title dump or `prod.keys` was used; no actual game was ported or launched.

### Integration issue found and fixed
- Audit found that skipping the donor APK also skipped its per-title controller theme and overlay font, which normally arrive through `AAssetManager`. Without this, NGR would run the game with a missing theme/font even if the host loaded.
- Added donor patch `0004-load-per-title-assets-from-ngr-data.patch`: the donor asset loader still prefers APK assets but can fall back to `NGR_SWITCH_GAME_ROOT/assets/`, rejecting unsafe relative paths.
- `port_game.py` now resolves the donor profile's actual theme and copies its theme files plus the shared licensed overlay font into the title's data pack. Kotlin importer, launcher and native loader require those files.
- Updated the Switch library UI copy to clearly state that nx2android preparation is desktop-assisted today; NGR does not yet run NSP/XCI recompilation on-device.

### Import hardening in progress
- Added a bounded SAF copy budget: 64 GiB aggregate, 32 GiB per file, 250,000 files, 500,000 total entries and 128 directory levels. Unsupported entries, duplicate/unsafe paths and over-limit copies fail and the staging directory is cleaned up.
- Added unit tests for exact limits and each rejection boundary.
- Android CI for these latest import-budget changes is still running; do not mark this follow-up verified until its result is checked.
- The current two-directory install has rollback on ordinary errors but is not crash-atomic across internal runtime and external data roots. Add a durable transaction journal/recovery pass before claiming crash-safe installs.

### Next
1. Verify latest Android CI after the bounded-copy changes.
2. Add a synthetic `libdmhost.so` fixture and test the loader's NativeActivity lifecycle forwarding.
3. Exercise the full `porter port` wrapper with a user-owned title/profile and keys; verify data pack, assets, receipt and no-APK result.
4. Import the package into NGR and test launch, Vulkan surface, input, audio, pause/resume, saves and two-title isolation on Adreno.
5. Decide whether the Python/.NET/LibHac preparation toolchain will run on-device or via a supported companion workflow; until then, keep the desktop-assisted boundary explicit.


## Session 2026-11-21 — package integrity, lifecycle and Vulkan boundary

### Additional verified checkpoints
- Android CI run #613 [44939742227](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44939742227), commit `87a999f8bfa43c3cc58af112fde244dfb4599fbb`: complete Android/JVM/native build passed after adding the NativeActivity teardown wrapper. APK artifact `NGR-arm64-debug` ID `14721162062`, ZIP SHA-256 `d683ec41a1298f538d5b9534426d1494f9c3dd1e2adc22699aba3d9b9057dde4`.
- Switch smoke run #45 [44939742310](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44939742310), same commit: patch application, title-profiled host build and real donor theme/font staging passed. Artifact `NGR-Switch-Runtime-Smoke` ID `14721390437`, SHA-256 `37d6c6b650a86afab4bc852ae5f4bdc60f374c7c5731153380f46194290cd7ce`.
- Switch smoke run #48 [44940034506](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44940034506), commit `041efde53a73905d1ebb797ce04fb10615e61a0c`, passed again after the package receipt was extended with the Vulkan driver mode. Artifact ID `14721480591`, SHA-256 `dfcdabfc2e444bd6a8da4871f62c25515ed85f07809f4a28df232bcf4431f591`.
- Android CI #606 [44939525228](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44939525228) passed the profile-header hash-mismatch regression test.

### Further hardening
- The package now carries `runtime/game_profile.h`; the importer and pre-launch verifier check its SHA-256 against the port receipt as well as the native library hash.
- Added a durable per-title install journal with `prepared`, `runtime_backed_up`, `backed_up`, `runtime_installed` and `installed` phases. Startup recovery restores previous roots or commits a complete validated pair. Recovery and library enumeration are dispatched off the UI thread.
- The NativeActivity loader wraps the donor's `onDestroy`: it calls the donor callback first and unloads `libdmhost.so` only after donor teardown. It refuses a second active runtime in the same process.
- The adapter explicitly passes `--vulkan-driver system` and the receipt records that mode. This avoids a false-success package when nx2android's optional adrenotools/Turnip APK libraries are omitted. NGR currently does not load a custom Turnip driver for the embedded Switch runtime; this is a known graphics limitation, not an implied feature.
- Theme/font staging is a dedicated helper and is exercised by the smoke workflow against the pinned donor profile. It rejects asset-path collisions rather than overwriting files from the game data pack.
- SAF copying now validates destination creation before opening source streams and closes streams reliably on copy failures.

### Current validation boundary
- Android APK compilation, JVM tests, donor patch application, profile-specific ARM64 host compilation and theme/font staging are CI-verified.
- Android CI run #621 [44940042307](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44940042307), commit `7f36140b42636ba806719a9bc230e23cc8b3b025`, passed after the system-driver receipt, profile-header verifier/tests, copy-stream hardening, journal recovery and NativeActivity teardown changes. Artifact `NGR-arm64-debug` ID `14721064183`, SHA-256 `321a8cebae4afb447ade1e479dded7effb6740e8e31ce5a1a89d4d99486ba4af`. A later run triggered by documentation-only commits is not needed to establish this code checkpoint.
- No full NSP/XCI port has been run with a real user-owned dump/keys. No Switch title has been imported or launched on a physical Android device.
- The desktop adapter uses the donor's real extraction/rewrite/shader/host/data pipeline but is not an on-device Python/.NET/LibHac toolchain.


## Session 2026-11-21 — final CI checkpoint and donor CLI contract

- Android CI run #630 [44940351366](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44940351366), commit `e60d9ca5bba5d0fce6c7d54b9533c2cfdd708b79`, passed after the explicit system-Vulkan package receipt and smoke CLI contract change. Artifact `NGR-arm64-debug` ID `14721940302`, archive SHA-256 `ed239a60f1efc0b71d513bd782d1538a8345436926b4277624ad2c95428296e`; extracted APK SHA-256 `1a94eb4784bc97380f3ca68e2e992efdec1fae05cb016e4015d6ef7403ab8eff`.
- Switch smoke #49 [44940351393](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44940351393), commit `e60d9ca5bba5d0fce6c7d54b9533c2cfdd708b79`, passed the donor `python -m porter port --help` contract check for `--keys`, `--out`, `--version`, `--language`, `--experimental`, `--package`, `--jobs`, and `--vulkan-driver`, then staged the theme/font and built the patched profile host. Artifact `NGR-Switch-Runtime-Smoke` ID `14721481165`, SHA-256 `2431a3de9e4ab68424d72ee9dd12d65df5f98a47ad77c0901758cbb2a4162c00`.
- The font staging helper was aligned with donor `ui_font_assets()`: it now copies all nested `*.txt` notices (including the `LICENSES/` subtree) without flattening paths. Smoke #50/#51 are running to verify the nested-license assertion against the pinned donor.
- Current main at this log update is `84f465a3062b8937fe442d4984c791081d14bee1`. The APK from #630 contains the current Android app code; subsequent changes are to the desktop preparation helper/smoke assertion and documentation, not the APK's Kotlin/native sources.
- Still no real NSP/XCI port, imported game or physical-device launch. The smoke artifact is a profile-only native host and does not contain copyrighted game data.


### Nested font-license staging smoke
- Switch smoke #51 [44940546014](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44940546014), commit `84f465a3062b8937fe442d4984c791081d14bee1`, passed after asserting both root `OFL.txt` and nested `LICENSES/**/*.txt` are preserved. Artifact ID `14721795180`, SHA-256 `f1f3d8e3f6becbbf3ed5b8cbb7be90e5496c78cd9810f33aac036617854d68a5`.
- This validates asset/license staging only; it does not run a real `porter port` with a game dump.


## Session 2026-11-21 — bind the data pack to its compiled game profile

- Donor `nx2a-data.json` contains `profile_h`; the adapter now refuses to package a data pack unless that hash matches the generated `game_profile.h`.
- The runtime receipt now includes `data_manifest_sha256`. The SAF importer verifies the exact manifest bytes and `profile_h`; the installed-library scan and crash-recovery validator repeat those checks; the launch path verifies title ID, `profile_h` and manifest SHA-256 against the internal runtime receipt.
- Added Kotlin regression tests for a wrong title ID, mismatched profile hash and mismatched data-manifest hash. Added Python unit tests for valid/missing/mismatched/symlinked generated profile headers.
- Switch smoke #58 [44941082552](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44941082552), commit `3b0a4bea4d1f72e5e25ddc628c8958ed03202d5c`, passed: Python adapter unit tests, donor CLI contract, per-title theme/font/license staging and patched ARM64 host build. Artifact ID `14720649995`, SHA-256 `4b9c1459331f3f1409629b81ed6dd86be5f64bbf33e393dd6cdf9c99491ca505`.
- Android CI #655/#656 initially failed because the test receipt fixtures omitted the newly required `data_manifest` path. The fixtures were corrected. Android CI #660 [44941312094], commit `fdc708deb0f04c5c2fb25029c37542efb6513e8b`, then passed the JVM unit-test suite, native ABI/registry checks, CMake/NDK build and ARM64 APK upload. Artifact `NGR-arm64-debug` ID `14721149150`, archive SHA-256 `c66d68b37a9d728339d44272a6b64c847c8d8fb23446330f5e864de15e7ac2f0`; extracted APK SHA-256 `aa523895ac22826ded832a0ccf582fd6a9b35e492e38d9ff6a43d0bbc1668e37`.
- This still does not replace a full real-title port test. The CI data/profile fixtures are synthetic.


### Final recovery-validation APK
- Android CI #665 [44941664371](https://github.com/Snap888/NativeGameRecompiler/actions/runs/44941664371), commit `eed67d55f9fadee910d1f4631f583781a6faea49`, passed after making interrupted-install recovery re-verify the complete runtime receipt and runtime/profile hashes before committing a recovered pair. JVM unit tests, native checks, CMake/NDK and ARM64 APK all passed.
- Artifact `NGR-arm64-debug` ID `14721568584`, archive SHA-256 `68c6b5a399babda61e00cad41a40e234bdb6f7b9af74e80c629fd33a0210172a`; extracted APK SHA-256 `0b4e61e0cac670ad485b75e41b639bb116528851af098facf66218fa6d33e926`.
- This is the newest verified Android app build. It still has no physical-device or real-game launch evidence.
