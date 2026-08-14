#!/usr/bin/env python3
"""
Patch ELF shared libraries to be 16 KB page-size compatible.

Scans every .so under the given directory and bumps any program-header
alignment (p_align) that is smaller than 16 KB (0x4000) up to 0x4000.
This fixes the "RELRO segment not aligned" Play-Console rejection without
needing to recompile prebuilt libraries.

Usage:
    python3 elf-align-16kb.py <jniLibsDirectory>
"""

import os
import struct
import sys

PAGE_SIZE = 0x4000  # 16 KB


def patch_elf(path: str) -> bool:
    with open(path, "r+b") as f:
        data = bytearray(f.read())

        if data[:4] != b"\x7fELF":
            return False  # not an ELF file

        ei_class = data[4]  # 1 = 32-bit, 2 = 64-bit
        ei_data = data[5]   # 1 = little-endian, 2 = big-endian

        if ei_data == 1:
            endian = "<"
        elif ei_data == 2:
            endian = ">"
        else:
            return False

        if ei_class == 1:  # 32-bit
            e_phoff = struct.unpack_from(endian + "I", data, 0x1C)[0]
            e_phentsize = struct.unpack_from(endian + "H", data, 0x2A)[0]
            e_phnum = struct.unpack_from(endian + "H", data, 0x2C)[0]
            ph_align_offset = 0x1C  # p_align is at offset 28 in 32-bit Phdr
            ph_struct = endian + "8I"
        elif ei_class == 2:  # 64-bit
            e_phoff = struct.unpack_from(endian + "Q", data, 0x20)[0]
            e_phentsize = struct.unpack_from(endian + "H", data, 0x36)[0]
            e_phnum = struct.unpack_from(endian + "H", data, 0x38)[0]
            ph_align_offset = 0x30  # p_align is at offset 48 in 64-bit Phdr
            ph_struct = endian + "2I6Q"
        else:
            return False

        patched = False
        for i in range(e_phnum):
            offset = e_phoff + i * e_phentsize
            if ei_class == 1:
                p_type, p_offset, p_vaddr, p_paddr, p_filesz, p_memsz, p_flags, p_align = struct.unpack_from(
                    ph_struct, data, offset
                )
                if p_align < PAGE_SIZE:
                    struct.pack_into(endian + "I", data, offset + ph_align_offset, PAGE_SIZE)
                    patched = True
            else:
                p_type, p_flags, p_offset, p_vaddr, p_paddr, p_filesz, p_memsz, p_align = struct.unpack_from(
                    ph_struct, data, offset
                )
                if p_align < PAGE_SIZE:
                    struct.pack_into(endian + "Q", data, offset + ph_align_offset, PAGE_SIZE)
                    patched = True

        if patched:
            f.seek(0)
            f.write(data)
            f.truncate()
        return patched


def main():
    if len(sys.argv) < 2:
        print(f"Usage: {sys.argv[0]} <jniLibsDirectory>", file=sys.stderr)
        sys.exit(1)

    root = sys.argv[1]
    patched_count = 0
    for dirpath, _dirnames, filenames in os.walk(root):
        for name in filenames:
            if not name.endswith(".so"):
                continue
            path = os.path.join(dirpath, name)
            try:
                if patch_elf(path):
                    print(f"Patched 16 KB alignment: {path}")
                    patched_count += 1
            except Exception as e:
                print(f"Failed to patch {path}: {e}", file=sys.stderr)

    print(f"Done. Patched {patched_count} library(s).")


if __name__ == "__main__":
    main()
