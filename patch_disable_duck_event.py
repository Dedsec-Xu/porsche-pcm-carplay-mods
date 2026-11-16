from __future__ import annotations

import hashlib
from pathlib import Path

from capstone import Cs, CS_ARCH_ARM, CS_MODE_ARM, CS_MODE_LITTLE_ENDIAN
from elftools.elf.elffile import ELFFile


SOURCE = Path(__file__).parent / "P3645-dio_manager"
OUTPUT = Path(__file__).parent / "P3645-dio_manager.duck-disabled"
EXPECTED_SOURCE_SHA256 = "6c2e5e17aa0ff6cc85bb903509ef168dc0b19b47000daecb441ce814cc861cf3"
PATCH_VADDR = 0x00157818
EXPECTED_BYTES = bytes.fromhex("f0 43 2d e9")  # push {r4-r9, lr}
REPLACEMENT = bytes.fromhex("1e ff 2f e1")     # bx lr


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def vaddr_to_offset(elf: ELFFile, address: int, size: int) -> int:
    for segment in elf.iter_segments():
        if segment.header.p_type != "PT_LOAD":
            continue
        start = int(segment.header.p_vaddr)
        end = start + int(segment.header.p_filesz)
        if start <= address and address + size <= end:
            return int(segment.header.p_offset) + address - start
    raise ValueError(f"virtual address 0x{address:x} is not file-backed")


original = SOURCE.read_bytes()
if sha256(original) != EXPECTED_SOURCE_SHA256:
    raise SystemExit("source hash mismatch; refusing to patch")

with SOURCE.open("rb") as stream:
    elf = ELFFile(stream)
    patch_offset = vaddr_to_offset(elf, PATCH_VADDR, len(EXPECTED_BYTES))

actual = original[patch_offset : patch_offset + len(EXPECTED_BYTES)]
if actual != EXPECTED_BYTES:
    raise SystemExit(f"patch-site mismatch: {actual.hex(' ')}")

patched = bytearray(original)
patched[patch_offset : patch_offset + len(REPLACEMENT)] = REPLACEMENT
OUTPUT.write_bytes(patched)

changed = [index for index, (a, b) in enumerate(zip(original, patched)) if a != b]
if not changed or min(changed) < patch_offset or max(changed) >= patch_offset + len(REPLACEMENT):
    raise SystemExit("changes escaped the expected patch window")

print(f"source_sha256={sha256(original)}")
print(f"output_sha256={sha256(patched)}")
print(f"patch_vaddr=0x{PATCH_VADDR:08x}")
print(f"patch_offset=0x{patch_offset:08x}")
print(f"before={EXPECTED_BYTES.hex(' ')}")
print(f"after ={REPLACEMENT.hex(' ')}")
print(f"changed_byte_count={len(changed)}")
print("disassembly:")
disassembler = Cs(CS_ARCH_ARM, CS_MODE_ARM | CS_MODE_LITTLE_ENDIAN)
for instruction in disassembler.disasm(REPLACEMENT, PATCH_VADDR):
    print(f"  0x{instruction.address:08x}: {instruction.mnemonic} {instruction.op_str}")
