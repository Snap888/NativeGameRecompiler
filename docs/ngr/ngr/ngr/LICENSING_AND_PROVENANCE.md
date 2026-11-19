# Licensing and component provenance

Status: preliminary source-license inventory, 2026-11-18. This is engineering documentation, not legal advice. Re-check the exact release tag/commit and every file/submodule before distributing binaries.

## Project and donor boundary

| Project | Observed license | NGR integration rule |
|---|---|---|
| NativeGameRecompiler | Originally MIT; combined distribution now GPL-3.0 | Original MIT notice is preserved in docs/ngr/LICENSE-MIT; NGR code is included in the combined GPL-3.0 work. |
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

## Combined-project license transition

The NGR source was originally published under MIT. The combined NativeGameRecompiler distribution now uses GPL-3.0 to permit direct integration of WinNative. The original MIT license text is retained at docs/ngr/LICENSE-MIT; WinNative and third-party notices remain in the repository. This is a source integration record, not legal advice.\n