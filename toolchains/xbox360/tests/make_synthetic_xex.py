#!/usr/bin/env python3
"""Create a tiny, redistributable, unencrypted XEX2 fixture with two PPC instructions."""
import pathlib
import struct
import sys

root = pathlib.Path(sys.argv[1]).resolve()
root.mkdir(parents=True, exist_ok=True)
image_base = 0x82000000
text_rva = 0x1000
entry = image_base + text_rva
image_size = 0x3000
image = bytearray(image_size)

# PE32 header. XenonRecomp maps sections by RVA, so the code bytes live at image[0x1000].
image[0:2] = b"MZ"
struct.pack_into("<I", image, 0x3C, 0x80)
nt = 0x80
image[nt:nt + 4] = b"PE\0\0"
file_header = struct.pack("<HHIIIHH", 0x01F2, 2, 0, 0, 0, 224, 0x0002)
image[nt + 4:nt + 24] = file_header
optional = bytearray(224)
struct.pack_into("<H", optional, 0, 0x10B)
image[nt + 24:nt + 24 + 224] = optional
section_offset = nt + 4 + 20 + 224
text_header = struct.pack("<8sIIIIIIHHI", b".text\0\0\0", 8, text_rva, 8, 0x200, 0, 0, 0, 0, 0x20)
pdata_header = struct.pack("<8sIIIIIIHHI", b".pdata\0", 0, 0x2000, 0, 0x208, 0, 0, 0, 0, 0x40)
image[section_offset:section_offset + 40] = text_header
image[section_offset + 40:section_offset + 80] = pdata_header

# addi r3,r0,1 ; blr — minimal PPC code, no game data or proprietary content.
image[text_rva:text_rva + 8] = struct.pack(">II", 0x38600001, 0x4E800020)

security_offset = 0x40
security_size = 0x184
format_offset = security_offset + security_size
header_size = format_offset + 8
header = struct.pack(">6I", 0x58455832, 0, header_size, 0, security_offset, 3)
optional_headers = b"".join([
    struct.pack(">II", 0x000003FF, format_offset),       # file format info (offset)
    struct.pack(">II", 0x00010201, image_base),           # image base
    struct.pack(">II", 0x00010100, entry),                # entry point
])
security = struct.pack(
    ">II256sIII20sI20s16s16sI20sIII",
    security_size, image_size, bytes(256), 0, 0, image_base,
    bytes(20), 0, bytes(20), bytes(16), bytes(16), 0,
    bytes(20), 0, 0, 0
)
assert len(security) == security_size, (len(security), security_size)
file_format = struct.pack(">IHH", 8, 0, 0)  # no encryption, no compression
prefix = header + optional_headers
prefix += bytes(security_offset - len(prefix))
prefix += security + file_format
assert len(prefix) == header_size, (len(prefix), header_size)
(root / "synthetic.xex").write_bytes(prefix + image)
(root / "config.toml").write_text(
    '[main]\nfile_path = "synthetic.xex"\nout_directory_path = "out"\n\n'
    '[[main.functions]]\naddress = 0x82001000\nsize = 8\n',
    encoding="utf-8"
)
(root / "out").mkdir(exist_ok=True)
print(f"fixture={root / 'synthetic.xex'} size={len(prefix) + len(image)} entry=0x{entry:X}")
