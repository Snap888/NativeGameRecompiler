# Licensing and component provenance

Status: preliminary source-license inventory, 2026-11-18. This is engineering documentation, not legal advice. Re-check the exact release tag/commit and every file/submodule before distributing binaries.

## Project and donor boundary

| Project | Observed license | NGR integration rule |
|---|---|---|
| NativeGameRecompiler | MIT (root LICENSE) | Existing host/runtime source remains MIT. |
| WinNative | GPL-3.0 (Snap888/WinNative, inspected source tree commit 99c3305d163780de5ca39b493aefcde436acad5c) | Do not copy or link donor implementation into the MIT NGR host until a licensing decision is made. Any combined/derived distribution may require GPL-3.0-compatible licensing and corresponding source/notice obligations. |

Possible paths, subject to legal review:
1. Re-license the combined NGR application under GPL-3.0 if incorporating WinNative code as a derivative work.
2. Keep NGR's MIT host independent and implement clean NGR adapters from documented interfaces, while consuming separately licensed runtime binaries.
3. Isolate a GPL runtime as a separately distributed process/component only if the actual technical and legal separation is valid; do not assume process boundaries automatically solve licensing.

No WinNative source files or binary payloads have been copied into NGR in this stage.

## Preliminary upstream component inventory

The following root LICENSE files were inspected on 2026-11-18. This is not a substitute for release-pinned SPDX scanning or review of bundled dependencies.

| Component family | Upstream | Observed root license | Remaining review |
|---|---|---|---|
| Box64 | https://github.com/ptitSeb/box64 | MIT | Verify exact release commit, bundled libraries and binary package notices. |
| FEX | https://github.com/FEX-Emu/FEX | MIT | Verify FEXCore/FEX UnixLibs build artifacts and all bundled dependencies. |
| DXVK | https://github.com/doitsujin/dxvk | zlib/libpng | Verify package contents, shader/compiler dependencies and notices. |
| VKD3D-Proton | https://github.com/HansKristian-Work/vkd3d-proton | LGPL-2.1 | Preserve LGPL notices/source obligations and review bundled Wine/Proton integration. |
| Wine | https://github.com/wine-mirror/wine | LGPL-2.1-or-later | Preserve license texts, notices and corresponding source obligations. |
| vkBasalt | https://github.com/DadSchoorse/vkBasalt | zlib/libpng | Distinguish vkBasalt effect compatibility from ReShade DLL injection. |
| Mesa / Turnip | https://gitlab.freedesktop.org/mesa/mesa | Not established in this pass | Mesa is multi-license; inspect exact source files and built package contents. |
| ReShade | https://github.com/crosire/reshade | Not established in this pass | Root LICENSE path was not found by this check; do not package until exact license is established. |

WinNative's component catalogs and bundled archives may combine projects with different licenses. The root license of one upstream project must never be applied automatically to an entire Wine/Proton/driver archive.

## Catalog provenance requirements

Every catalog record should carry:
- upstream repository and immutable commit/tag;
- exact release/archive filename and HTTPS URL;
- SHA-256 of the downloaded archive;
- target ABI and supported container/runtime modes;
- SPDX license identifier or LicenseRef for a reviewed multi-license bundle;
- a license/notice manifest included with the installed component;
- source/build provenance and patch list for locally built binaries;
- attribution and source-offer obligations where applicable.

The current licenseSpdx validator accepts one SPDX identifier or LicenseRef, not a compound SPDX expression. For a multi-license bundle, use a project-specific LicenseRef backed by an included machine-readable license manifest until expression parsing and per-file attribution are implemented.

## Distribution gate

Before adding a component to the production catalog:
1. Pin the exact upstream source commit and release asset.
2. Inventory all files, submodules and dynamically linked dependencies.
3. Generate/verify SPDX SBOM data and retain each license text.
4. Record modifications and build provenance.
5. Verify redistribution rights for the actual archive, not just upstream source.
6. Add a CI check that rejects missing license/source/hash metadata.

## Static recompiler audit additions — 2026-11-21

The following root-repository findings were checked during the source audit. They are preliminary; inspect the exact pinned commit, submodules and redistributed binary contents before vendoring or shipping.

| Component | Observed root license / terms | NGR gate |
|---|---|---|
| XenonRecomp | MIT | Retain LICENSE.md and audit thirdparty/submodules. |
| ReXGlue SDK | BSD-3-Clause-style terms; LICENSE also contains explicit Xenia-derived copyright/notice language | Preserve both notices and review Xenia-derived files and all submodules; do not label the SDK MIT. |
| XboxXBERecomp | MIT reported by GitHub repository metadata | Verify root license at pinned commit; retain notices for Zydis, toml++, Dear ImGui, SDL3 and fetched dependencies. |
| N64Recomp | MIT | Audit recursive submodules and their licenses. |
| N64ModernRuntime | GPL-3.0 | Do not link/vendor into the MIT NGR application without an explicit distribution/relicensing decision and corresponding-source compliance plan. |
| SCDA-XBERecomp-android | Project states MIT; dependencies retain their own licenses | Review Android fork diff and every dependency; upstream project license does not cover all dependencies. |
| UnleashedRecomp-Android | Must verify pinned repository LICENSE and all bundled thirdparty components | Do not copy Android-specific code before per-file and dependency review. |
| RT64 | Separate project license; inspect pinned revision | Required renderer candidate for N64; review Android support and dependency licenses separately. |

ReXGlue's license is not MIT. Its LICENSE includes a BSD-3-Clause-style grant and explicit Xenia-derived copyright/notice language. Preserve the full upstream license text and any applicable Xenia notices.

The N64Recomp compiler (MIT) and N64ModernRuntime (GPL-3.0) are separate projects with different licenses. The runtime's license must not be inferred from the compiler's license.

No third-party source was copied into NGR during the audit.
