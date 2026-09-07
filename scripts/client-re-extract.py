#!/usr/bin/env python3

from __future__ import annotations

import hashlib
import json
import re
import struct
import subprocess
import tempfile
import zipfile
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CLIENT_DIR = ROOT.parent / "FantaTennis-Test-Client"
EXE_UNPATCHED = CLIENT_DIR / "FantaTennis.exe.laa.bak"
EXE_RUNTIME = CLIENT_DIR / "FantaTennis.exe"
FRE = ROOT
PACKET_OPS = FRE / "server-core/src/main/java/com/jftse/server/core/protocol/PacketOperations.java"
PACKET_JAVA = FRE / "server-core/src/main/java/com/jftse/server/core/protocol/Packet.java"
RES_DIR = CLIENT_DIR / "Res"
OUT = FRE / "docs/client-re-catalog.json"

EXPECTED_UNPATCHED_SHA256 = "5477f0827acae66976403aecd2e9ebffeb4fa28da1fedae5f9541ec25e336c31"

WS2_ORDINALS = {
    1: "accept",
    2: "bind",
    3: "closesocket",
    4: "connect",
    5: "getpeername",
    6: "getsockname",
    7: "getsockopt",
    8: "htonl",
    9: "htons",
    10: "ioctlsocket",
    11: "inet_addr",
    12: "inet_ntoa",
    13: "listen",
    14: "ntohl",
    15: "ntohs",
    16: "recv",
    17: "recvfrom",
    18: "select",
    19: "send",
    20: "sendto",
    21: "setsockopt",
    22: "shutdown",
    23: "socket",
    28: "WSAEventSelect",
    40: "WSAIoctl",
    47: "WSARecvFrom",
    57: "WSASend",
    59: "WSASendTo",
    65: "WSASocketA",
    71: "WSAWaitForMultipleEvents",
}

OPCODE_MIN = 0x0300
OPCODE_MAX = 0xA000


def parse_packet_ops(src: str) -> list[dict]:
    rows = []
    for name, hexv in re.findall(r"^\s+([A-Za-z0-9_]+)\((0x[0-9A-Fa-f]+)\)", src, re.M):
        value = int(hexv, 16)
        rows.append({"name": name, "value": value, "hex": f"0x{value:04X}"})
    return rows


def packet_id_offset(src: str) -> int:
    m = re.search(r"this\.packetId = buffer\.getChar\((\d+)\)", src)
    if not m:
        raise SystemExit("Packet.packetId getChar offset missing")
    return int(m.group(1))


def parse_pe(data: bytes) -> dict:
    e_lfanew = struct.unpack_from("<I", data, 0x3C)[0]
    if data[e_lfanew : e_lfanew + 4] != b"PE\0\0":
        raise SystemExit("not a PE")
    coff = e_lfanew + 4
    nsections = struct.unpack_from("<H", data, coff + 2)[0]
    opt_size = struct.unpack_from("<H", data, coff + 16)[0]
    opt = coff + 20
    magic = struct.unpack_from("<H", data, opt)[0]
    if magic != 0x10B:
        raise SystemExit(f"expected PE32, got {magic:#x}")
    image_base = struct.unpack_from("<I", data, opt + 28)[0]
    num_rva = struct.unpack_from("<I", data, opt + 92)[0]
    dd = opt + 96
    import_rva, import_size = struct.unpack_from("<II", data, dd + 8)
    iat_rva, iat_size = struct.unpack_from("<II", data, dd + 96) if num_rva > 12 else (0, 0)
    sec_off = opt + opt_size
    sections = []
    for i in range(nsections):
        o = sec_off + i * 40
        name = data[o : o + 8].split(b"\0", 1)[0].decode("ascii", "replace")
        vsize, va, raw_size, raw_off = struct.unpack_from("<IIII", data, o + 8)
        sections.append(
            {
                "name": name,
                "va": va,
                "vsize": vsize,
                "raw_off": raw_off,
                "raw_size": raw_size,
            }
        )
    return {
        "image_base": image_base,
        "import_rva": import_rva,
        "import_size": import_size,
        "iat_rva": iat_rva,
        "iat_size": iat_size,
        "sections": sections,
    }


def rva_to_off(pe: dict, rva: int) -> int | None:
    for s in pe["sections"]:
        if s["va"] <= rva < s["va"] + max(s["vsize"], s["raw_size"]):
            return s["raw_off"] + (rva - s["va"])
    return None


def va_to_off(pe: dict, va: int) -> int | None:
    return rva_to_off(pe, va - pe["image_base"])


def read_cstr(data: bytes, off: int) -> str:
    end = data.find(b"\0", off)
    if end < 0:
        return ""
    return data[off:end].decode("latin1", "replace")


def parse_imports(data: bytes, pe: dict) -> list[dict]:
    imports = []
    off = rva_to_off(pe, pe["import_rva"])
    if off is None:
        return imports
    i = 0
    while True:
        o = off + i * 20
        ilt, _, _, name_rva, iat = struct.unpack_from("<IIIII", data, o)
        if ilt == 0 and name_rva == 0 and iat == 0:
            break
        name_off = rva_to_off(pe, name_rva)
        dll = read_cstr(data, name_off) if name_off is not None else "?"
        thunk_rva = ilt or iat
        funcs = []
        t = 0
        while True:
            toff = rva_to_off(pe, thunk_rva + t * 4)
            if toff is None:
                break
            thunk = struct.unpack_from("<I", data, toff)[0]
            if thunk == 0:
                break
            if thunk & 0x80000000:
                ordinal = thunk & 0xFFFF
                funcs.append(
                    {
                        "ordinal": ordinal,
                        "name": WS2_ORDINALS.get(ordinal, f"ord_{ordinal}")
                        if dll.lower().startswith("ws2")
                        else f"ord_{ordinal}",
                    }
                )
            else:
                hint_off = rva_to_off(pe, thunk)
                fname = read_cstr(data, hint_off + 2) if hint_off is not None else "?"
                funcs.append({"ordinal": None, "name": fname})
            t += 1
        imports.append({"dll": dll, "functions": funcs})
        i += 1
    return imports


def section_bytes(data: bytes, pe: dict, name: str) -> tuple[bytes, int]:
    for s in pe["sections"]:
        if s["name"] == name:
            blob = data[s["raw_off"] : s["raw_off"] + s["raw_size"]]
            return blob, pe["image_base"] + s["va"]
    raise SystemExit(f"missing section {name}")


def find_cpacket_ctor(text: bytes, text_va: int) -> int:
    sig = bytes.fromhex("668B44240866894608")
    sig_off = text.find(sig)
    if sig_off < 0:
        raise SystemExit("CPacket ctor signature missing")
    pro = text.rfind(b"\x56\x8b\xf1", 0, sig_off + 1)
    if pro < 0:
        raise SystemExit("CPacket ctor prologue missing")
    return text_va + pro


def ctor_callers(text: bytes, text_va: int, ctor_va: int) -> list[int]:
    hits = []
    i = 0
    n = len(text)
    while i + 5 <= n:
        if text[i] == 0xE8:
            rel = struct.unpack_from("<i", text, i + 1)[0]
            if text_va + i + 5 + rel == ctor_va:
                hits.append(text_va + i)
        i += 1
    return hits


def last_inband_push(text: bytes, text_va: int, call_va: int, window: int = 32) -> int | None:
    off = call_va - text_va
    blob = text[max(0, off - window) : off]
    val = None
    for j in range(len(blob) - 4):
        if blob[j] == 0x68:
            v = struct.unpack_from("<I", blob, j + 1)[0]
            if v <= 0xFFFF and OPCODE_MIN <= v <= OPCODE_MAX:
                val = v
    return val


def last_push_imm32(text: bytes, text_va: int, call_va: int, window: int = 32) -> int | None:
    off = call_va - text_va
    blob = text[max(0, off - window) : off]
    val = None
    for j in range(len(blob) - 4):
        if blob[j] == 0x68:
            val = struct.unpack_from("<I", blob, j + 1)[0]
    return val


def ctor_push_opcodes(text: bytes, text_va: int, call_vas: list[int]) -> tuple[list[int], list[dict]]:
    found: list[int] = []
    missed: list[dict] = []
    for va in call_vas:
        val = last_inband_push(text, text_va, va)
        if val is None:
            raw = last_push_imm32(text, text_va, va)
            kind = "unresolved"
            note = None
            if raw is not None and (raw & 0xFFFFFF00) == 0xFFFFFC00:
                kind = "codecMode"
                note = hex(raw)
            missed.append({"va": hex(va), "kind": kind, "note": note})
        else:
            found.append(val)
    return found, missed


def scan_packetid_immediates(text: bytes, packet_id: int) -> Counter:
    hits: Counter = Counter()
    n = len(text)
    disp = packet_id
    i = 0
    while i + 6 <= n:
        # 66 C7 4x 04 XX XX   mov word ptr [reg+disp], imm16
        if text[i] == 0x66 and text[i + 1] == 0xC7 and i + 6 <= n:
            modrm = text[i + 2]
            if (modrm & 0xC0) == 0x40 and text[i + 3] == disp:
                val = struct.unpack_from("<H", text, i + 4)[0]
                if OPCODE_MIN <= val <= OPCODE_MAX:
                    hits[val] += 1
        # C7 4x 04 XX XX 00 00  mov dword ptr [reg+disp], imm32
        if text[i] == 0xC7 and i + 7 <= n:
            modrm = text[i + 1]
            if (modrm & 0xC0) == 0x40 and text[i + 2] == disp:
                val = struct.unpack_from("<I", text, i + 3)[0]
                if OPCODE_MIN <= val <= OPCODE_MAX:
                    hits[val] += 1
        # 66 3D XX XX  /  3D XX XX 00 00  cmp ax/eax, imm
        if text[i] == 0x66 and text[i + 1] == 0x3D and i + 4 <= n:
            val = struct.unpack_from("<H", text, i + 2)[0]
            if OPCODE_MIN <= val <= OPCODE_MAX:
                hits[val] += 1
        if text[i] == 0x3D and i + 5 <= n:
            val = struct.unpack_from("<I", text, i + 1)[0]
            if OPCODE_MIN <= val <= OPCODE_MAX:
                hits[val] += 1
        i += 1
    return hits


def find_string_va(data: bytes, pe: dict, needle: bytes) -> int | None:
    off = data.find(needle)
    if off < 0:
        return None
    for s in pe["sections"]:
        if s["raw_off"] <= off < s["raw_off"] + s["raw_size"]:
            return pe["image_base"] + s["va"] + (off - s["raw_off"])
    return None


def find_dword_vas(data: bytes, pe: dict, value: int) -> list[int]:
    pat = struct.pack("<I", value)
    vas = []
    start = 0
    while True:
        off = data.find(pat, start)
        if off < 0:
            break
        for s in pe["sections"]:
            if s["raw_off"] <= off < s["raw_off"] + s["raw_size"]:
                vas.append(pe["image_base"] + s["va"] + (off - s["raw_off"]))
                break
        start = off + 1
    return vas


def rtti_vtable(data: bytes, pe: dict, rtti_name: bytes) -> dict:
    """Resolve MSVC type_info -> COL -> vftable for a named class."""
    string_va = find_string_va(data, pe, rtti_name)
    if string_va is None:
        return {"name": rtti_name.decode(), "found": False}
    type_info_va = string_va - 8
    col_fields = find_dword_vas(data, pe, type_info_va)
    col_vas = [va - 12 for va in col_fields]
    vftables = []
    for col_va in col_vas:
        for ptr_va in find_dword_vas(data, pe, col_va):
            vftables.append(ptr_va + 4)
    return {
        "name": rtti_name.decode(),
        "found": True,
        "stringVa": hex(string_va),
        "typeInfoVa": hex(type_info_va),
        "colCandidates": [hex(v) for v in col_vas[:8]],
        "vftableCandidates": [hex(v) for v in vftables[:8]],
    }


def iat_entry_va(data: bytes, pe: dict, dll: str, func: str) -> int | None:
    off = rva_to_off(pe, pe["import_rva"])
    if off is None:
        return None
    i = 0
    while True:
        o = off + i * 20
        ilt, _, _, name_rva, iat = struct.unpack_from("<IIIII", data, o)
        if ilt == 0 and name_rva == 0 and iat == 0:
            break
        name_off = rva_to_off(pe, name_rva)
        this_dll = read_cstr(data, name_off) if name_off is not None else ""
        thunk_rva = ilt or iat
        t = 0
        while True:
            toff = rva_to_off(pe, thunk_rva + t * 4)
            if toff is None:
                break
            thunk = struct.unpack_from("<I", data, toff)[0]
            if thunk == 0:
                break
            fname = ""
            if thunk & 0x80000000:
                ordinal = thunk & 0xFFFF
                fname = WS2_ORDINALS.get(ordinal, f"ord_{ordinal}")
            else:
                hint_off = rva_to_off(pe, thunk)
                fname = read_cstr(data, hint_off + 2) if hint_off is not None else ""
            if this_dll.lower() == dll.lower() and fname == func:
                return pe["image_base"] + iat + t * 4
            t += 1
        i += 1
    return None


def xrefs_call_iat(text: bytes, text_va: int, iat_va: int) -> list[str]:
    pat = b"\xff\x15" + struct.pack("<I", iat_va)
    hits = []
    start = 0
    while True:
        off = text.find(pat, start)
        if off < 0:
            break
        hits.append(hex(text_va + off))
        start = off + 1
    return hits


def find_push_imm32_then_iat(text: bytes, text_va: int, imm32: int, iat_va: int, window: int = 32) -> int | None:
    push = struct.pack("<BI", 0x68, imm32)
    iat = b"\xff\x15" + struct.pack("<I", iat_va)
    start = 0
    while True:
        off = text.find(push, start)
        if off < 0:
            return None
        if iat in text[off : off + window]:
            return text_va + off
        start = off + 1


WRITE_U8 = 0x4204D0
WRITE_U16 = 0x454670
WRITE_U32 = 0x4A5CB0
WRITE_U64 = 0x4662B0
WRITE_BYTES = 0x420480
WRITE_UTF16Z = 0x4546A0
CPACKET_DTOR = 0x627B10
FIXED_WRITERS = {
    WRITE_U8: 1,
    WRITE_U16: 2,
    WRITE_U32: 4,
    WRITE_U64: 8,
}

# Curated leftover C2S/C2C bodies from ctor-site stores. "var" and "mixed"
# are not a single scalar. Extract fails if a pinned scalar changes.
EXPECTED_LEFTOVER_BODIES = {
    0x0401: 1,
    0x0405: 22,
    0x0C95: "mixed",
    0x1007: 4,
    0x13A6: 4,
    0x1451: 8,
    0x1453: 8,
    0x1454: 8,
    0x170D: "var",
    0x170F: 0,
    0x1711: 1,
    0x17D7: "var",
    0x17DB: 0,
    0x17E9: 1,
    0x17F2: 0,
    0x1850: 0,
    0x18A4: 5,
    0x18A7: 6,
    0x18AE: 2,
    0x18B0: 4,
    0x18B1: "mixed",
    0x1B77: 1,
    0x1D0B: 8,
    0x1DB0: 2,
    0x1DB2: 0,
    0x1DE3: 4,
    0x1F58: 4,
    0x2033: 0,
    0x2038: 4,
    0x203E: 0,
    0x213E: 0,
    0x213F: 0,
    0x2288: "var",
    0x237B: 0,
    0x238E: 16,
    0x238F: "var",
    0x23F2: "var",
    0x23F4: 4,
    0x240E: 0,
    0x2411: 0,
    0x2416: 8,
    0x2528: "mixed",
    0x2648: 4,
    0x264A: 0,
    0x264C: 7,
    0x2650: 1,
    0x26B1: 4,
    0x26B3: 4,
    0x26B5: 16,
    0x26B7: 8,
    0x26BE: 4,
    0x26C0: 6,
    0x26C2: 6,
    0x26C6: 4,
    0x26C8: 1,
    0x26DF: 40,
    0x26F2: 4,
    0x2702: 0,
    0x270F: 0,
    0x2EE2: 6,
    0x32CB: "var",
    0x3330: 4,
    0x3392: 3,
    0x3393: 2,
    0x3394: 2,
    0x3395: 2,
    0x3396: 12,
    0x33A4: 6,
}


def _parse_imm(tok: str) -> int | None:
    tok = tok.strip().rstrip(",")
    if tok.startswith("0x"):
        return int(tok, 16)
    if re.fullmatch(r"-?\d+", tok):
        return int(tok)
    return None


def _disasm_window(text_bin: Path, text_va: int, start: int, stop: int) -> list[tuple[int, str]]:
    r = subprocess.run(
        [
            "objdump",
            "-D",
            "-b",
            "binary",
            "-m",
            "i386",
            "--adjust-vma",
            hex(text_va),
            "-M",
            "intel",
            f"--start-address={start}",
            f"--stop-address={stop}",
            str(text_bin),
        ],
        capture_output=True,
        text=True,
        check=False,
    )
    rows = []
    for line in r.stdout.splitlines():
        m = re.match(r"\s*([0-9a-f]+):\s+[0-9a-f]{2}(?:\s+[0-9a-f]{2})*\s+(.*)", line)
        if m:
            rows.append((int(m.group(1), 16), m.group(2).strip()))
    return rows


def ctor_body_width(text_bin: Path, text_va: int, call_va: int, ctor_va: int) -> int | str:
    width = 0
    variable = False
    wrote = False
    last_imm: dict[str, int] = {}
    for va, op in _disasm_window(text_bin, text_va, call_va, call_va + 0x300):
        if va == call_va:
            continue
        compact = op.replace(" ", "")
        mmov = re.match(r"mov(?:zx|sx)?\s+(e?[abcd]x),([^\[]+)$", compact)
        if mmov:
            val = _parse_imm(mmov.group(2))
            if val is not None:
                last_imm[mmov.group(1)] = val
                if mmov.group(1).startswith("e"):
                    last_imm[mmov.group(1)[1:]] = val
        if op.startswith("call"):
            m = re.search(r"0x([0-9a-f]+)", op)
            if not m:
                continue
            tgt = int(m.group(1), 16)
            if tgt in (ctor_va, CPACKET_DTOR, 0x465B20, 0x465B30):
                break
            if tgt in FIXED_WRITERS:
                width += FIXED_WRITERS[tgt]
                wrote = True
            elif tgt in (WRITE_BYTES, WRITE_UTF16Z, 0x528610):
                variable = True
                wrote = True
            continue
        madd = re.match(
            r"add\s+WORD PTR \[esp(?:\+0x[0-9a-f]+)?\],\s*(0x[0-9a-f]+|\d+|cx|dx|ax)",
            op,
        )
        if madd:
            rhs = madd.group(1)
            n = _parse_imm(rhs)
            if n is None:
                n = last_imm.get(rhs) or last_imm.get("e" + rhs)
            if n is not None and 1 <= n <= 0x200:
                width += n
                wrote = True
            elif n is None:
                variable = True
                wrote = True
    if variable and width:
        return "var"
    if variable:
        return "var"
    if not wrote:
        return 0
    return width


def leftover_bodies(text: bytes, text_va: int, ctor_va: int, sites: list[int]) -> dict[int, int | str]:
    by_id: dict[int, list[int]] = {}
    for va in sites:
        pid = last_inband_push(text, text_va, va)
        if pid is None or pid not in EXPECTED_LEFTOVER_BODIES:
            continue
        by_id.setdefault(pid, []).append(va)
    for va in (0x5C575B, 0x5C5A59, 0x5C5CE0):
        by_id.setdefault(0x26C2, []).append(va)
    with tempfile.TemporaryDirectory() as tmp:
        text_bin = Path(tmp) / "text.bin"
        text_bin.write_bytes(text)
        out: dict[int, int | str] = {}
        for pid, vas in by_id.items():
            widths: list[int | str] = []
            for va in sorted(set(vas)):
                w = ctor_body_width(text_bin, text_va, va, ctor_va)
                if w not in widths:
                    widths.append(w)
            out[pid] = "mixed" if len(widths) > 1 else widths[0]
        return out


UNLABELED_NAME = re.compile(r"Unknown|Extra(?:[0-9A-Fa-f]+)?$|Client[0-9A-Fa-f]{3,}$")
PACKET_DIR = FRE / "server-core/src/main/packets"


def unlabeled_ops(ops: list[dict]) -> list[str]:
    unlabeled = []
    for o in ops:
        name = o["name"]
        if UNLABELED_NAME.search(name):
            unlabeled.append(f"{name} {o['hex']}")
    return unlabeled


def packet_verbs(packet_dir: Path = PACKET_DIR) -> dict[int, list[str]]:
    verbs: dict[int, list[str]] = {}
    for path in sorted(packet_dir.rglob("*.packet")):
        text = path.read_text(errors="replace")
        for name, hx in re.findall(r"message\s+((?:CMSG|SMSG)_[A-Za-z0-9_]+)\s*\((0x[0-9A-Fa-f]+)\)", text):
            verbs.setdefault(int(hx, 16), []).append(f"{name} {path.relative_to(FRE)}")
    return verbs


def unlabeled_packet_collisions(ops: list[dict], verbs: dict[int, list[str]]) -> list[str]:
    hits = []
    for o in ops:
        if UNLABELED_NAME.search(o["name"]) and o["value"] in verbs:
            hits.append(f"{o['name']} {o['hex']} -> {', '.join(verbs[o['value']])}")
    return hits


def _printable_ascii(raw: bytes, off: int) -> str | None:
    if off < 0 or off >= len(raw):
        return None
    end = raw.find(b"\0", off)
    if end < 0 or end - off < 4 or end - off > 96:
        return None
    try:
        s = raw[off:end].decode("ascii")
    except UnicodeDecodeError:
        return None
    if not all(32 <= ord(c) < 127 for c in s):
        return None
    return s


def _printable_utf16(raw: bytes, off: int) -> str | None:
    if off < 0 or off + 8 > len(raw):
        return None
    chars = []
    i = off
    while i + 2 <= len(raw):
        w = struct.unpack_from("<H", raw, i)[0]
        if w == 0:
            break
        if w < 32 or w > 126:
            return None
        chars.append(chr(w))
        i += 2
        if len(chars) > 48:
            return None
    if len(chars) < 4:
        return None
    return "".join(chars)


def va_strings(raw: bytes, pe: dict, va: int) -> list[str]:
    off = va_to_off(pe, va)
    if off is None:
        return []
    found = []
    ascii_s = _printable_ascii(raw, off)
    if ascii_s:
        found.append(ascii_s)
    utf = _printable_utf16(raw, off)
    if utf and utf not in found:
        found.append(utf)
    return found


def leftover_name_evidence(
    raw: bytes,
    pe: dict,
    text: bytes,
    text_va: int,
    sites: list[int],
    unlabeled: list[dict],
) -> list[dict]:
    want = {o["value"] for o in unlabeled}
    by_id: dict[int, list[int]] = {}
    for va in sites:
        pid = last_inband_push(text, text_va, va)
        if pid in want:
            by_id.setdefault(pid, []).append(va)
    rows = []
    with tempfile.TemporaryDirectory() as tmp:
        text_bin = Path(tmp) / "text.bin"
        text_bin.write_bytes(text)
        for o in unlabeled:
            pid = o["value"]
            vas = sorted(set(by_id.get(pid, [])))
            strings: list[str] = []
            disasm: list[str] = []
            for call_va in vas:
                off = call_va - text_va
                blob = text[max(0, off - 0x280) : off + 0x80]
                for j in range(len(blob) - 4):
                    if blob[j] != 0x68:
                        continue
                    imm = struct.unpack_from("<I", blob, j + 1)[0]
                    for s in va_strings(raw, pe, imm):
                        if s not in strings:
                            strings.append(s)
                if len(disasm) < 3:
                    rows_d = _disasm_window(text_bin, text_va, call_va - 0x30, call_va + 0x50)
                    disasm.append(
                        f"{hex(call_va)}\n"
                        + "\n".join(f"  {hex(va)} {op}" for va, op in rows_d)
                    )
            rows.append(
                {
                    "name": o["name"],
                    "hex": o["hex"],
                    "id": pid,
                    "sites": [hex(v) for v in vas],
                    "nearbyStrings": strings[:24],
                    "disasm": disasm,
                }
            )
    return rows


def scan_known_opcode_bytes(text: bytes, ops: list[dict]) -> dict[int, int]:
    counts = {}
    for op in ops:
        pat = struct.pack("<H", op["value"])
        counts[op["value"]] = text.count(pat)
    return counts


def extract_rtti(data: bytes) -> list[str]:
    names = []
    for m in re.finditer(rb"\.\?AV[A-Za-z0-9_@]+", data):
        names.append(m.group().decode("ascii", "replace"))
    return sorted(set(names))


def extract_packet_strings(data: bytes) -> list[str]:
    text = data.decode("latin1", "replace")
    found = set()
    for pat in (
        r"C2S_[A-Z0-9_]+",
        r"S2C_[A-Z0-9_]+",
        r"CMSG_[A-Z0-9_]+",
        r"SMSG_[A-Z0-9_]+",
        r"Packet#[0-9]+",
        r"CUDP[A-Za-z]+",
        r"RakPeer[A-Za-z]*",
        r"TryToUDP[A-Za-z]*",
    ):
        found.update(re.findall(pat, text))
    return sorted(found)


def catalog_res() -> dict:
    zips = []
    tables = []
    for path in sorted(RES_DIR.rglob("*")):
        if not path.is_file():
            continue
        rel = str(path.relative_to(RES_DIR))
        if path.suffix.lower() == ".res" or zipfile.is_zipfile(path):
            try:
                with zipfile.ZipFile(path) as zf:
                    names = zf.namelist()
                    zips.append({"archive": rel, "entries": len(names), "sample": names[:12]})
                    for n in names:
                        low = n.lower()
                        if any(k in low for k in ("item_", "level", "ai_", "quest", "emblem", "card", "pet", "shop")):
                            tables.append(f"{rel}:{n}")
            except zipfile.BadZipFile:
                pass
    return {"archives": zips, "tableLikeEntries": sorted(tables)}


def extract_script_ini_paths(data: bytes) -> list[bytes]:
    return sorted(set(re.findall(rb"Res/Script/[A-Za-z0-9_/%]+\.(?:ini|txt)", data)))



def main() -> None:
    raw = EXE_UNPATCHED.read_bytes()
    digest = hashlib.sha256(raw).hexdigest()
    runtime_digest = hashlib.sha256(EXE_RUNTIME.read_bytes()).hexdigest()
    ops = parse_packet_ops(PACKET_OPS.read_text())
    pe = parse_pe(raw)
    imports = parse_imports(raw, pe)
    text, text_va = section_bytes(raw, pe, ".text")
    packet_id = packet_id_offset(PACKET_JAVA.read_text())
    ctor_va = find_cpacket_ctor(text, text_va)
    ctor_sites = ctor_callers(text, text_va, ctor_va)
    ctor_ops, ctor_missed = ctor_push_opcodes(text, text_va, ctor_sites)
    for row in ctor_missed:
        if row["kind"] != "unresolved":
            continue
        va = int(row["va"], 16)
        wide = last_inband_push(text, text_va, va, window=200)
        if wide is not None:
            row["kind"] = "joinPoint"
            row["note"] = f"0x{wide:04X}"

    id_hits = scan_packetid_immediates(text, packet_id)
    known = {o["value"] for o in ops}
    listed = sorted(v for v in id_hits if v in known)
    unlisted = sorted(v for v in id_hits if v not in known)

    ws2 = next((i for i in imports if i["dll"].lower().startswith("ws2")), {"functions": []})
    udpish = [
        f["name"]
        for f in ws2["functions"]
        if re.search(r"sendto|recvfrom|WSASendTo|WSARecvFrom|WSASend|WSASocket|socket|bind", f["name"], re.I)
    ]

    udp_rtti = [
        rtti_vtable(raw, pe, name)
        for name in (
            b".?AVCUDPConnector@@",
            b".?AVCUDPSocket@@",
            b".?AVRakPeer@@",
            b".?AVCClientNet@@",
            b".?AVCPacketCoder@@",
            b".?AVCTCPSocket@@",
        )
    ]

    sendto_iat = iat_entry_va(raw, pe, "WS2_32.dll", "sendto")
    recvfrom_iat = iat_entry_va(raw, pe, "WS2_32.dll", "recvfrom")
    send_iat = iat_entry_va(raw, pe, "WS2_32.dll", "send")
    recv_iat = iat_entry_va(raw, pe, "WS2_32.dll", "recv")
    sendto_callers = xrefs_call_iat(text, text_va, sendto_iat) if sendto_iat else []
    recvfrom_callers = xrefs_call_iat(text, text_va, recvfrom_iat) if recvfrom_iat else []
    htons_iat = iat_entry_va(raw, pe, "WS2_32.dll", "htons")
    htons_15000 = (
        find_push_imm32_then_iat(text, text_va, 0x3A98, htons_iat)
        if htons_iat
        else None
    )

    report = {
        "generatedFrom": {
            "unpatchedExe": str(EXE_UNPATCHED),
            "unpatchedSha256": digest,
            "unpatchedShaExpected": EXPECTED_UNPATCHED_SHA256,
            "unpatchedShaMatch": digest == EXPECTED_UNPATCHED_SHA256,
            "runtimeSha256": runtime_digest,
            "imageBase": hex(pe["image_base"]),
            "sections": [{k: (hex(v) if isinstance(v, int) else v) for k, v in s.items()} for s in pe["sections"]],
        },
        "packetOperations": {
            "total": len(ops),
            "unknownNamed": [o["name"] + " " + o["hex"] for o in ops if "unknown" in o["name"].lower()],
            "unlabeled": unlabeled_ops(ops),
        },
        "imports": {
            "dlls": [i["dll"] for i in imports],
            "ws2": [f["name"] for f in ws2["functions"]],
            "udpRelated": udpish,
        },
        "udp": {
            "rtti": udp_rtti,
            "sendtoIat": hex(sendto_iat) if sendto_iat else None,
            "recvfromIat": hex(recvfrom_iat) if recvfrom_iat else None,
            "sendtoCallers": sendto_callers,
            "recvfromCallers": recvfrom_callers,
            "sendCallersCount": len(xrefs_call_iat(text, text_va, send_iat)) if send_iat else 0,
            "recvCallersCount": len(xrefs_call_iat(text, text_va, recv_iat)) if recv_iat else 0,
            "htons15000Va": hex(htons_15000) if htons_15000 else None,
        },
        "rttiNetwork": [n for n in extract_rtti(raw) if re.search(r"Packet|Socket|UDP|TCP|Rak|ClientNet|BlowFish", n)],
        "rttiAllCount": len(extract_rtti(raw)),
        "packetStrings": extract_packet_strings(raw),
        "packetIdWrites": {
            "distinct": len(id_hits),
            "listed": [f"0x{v:04X} x{id_hits[v]} {next(o['name'] for o in ops if o['value']==v)}" for v in listed],
            "unlisted": [f"0x{v:04X} x{id_hits[v]}" for v in unlisted],
        },
        "cpacketCtor": {
            "va": hex(ctor_va),
            "callers": len(ctor_sites),
            "inBandPushes": len(ctor_ops),
            "distinct": len(set(ctor_ops)),
            "listed": [f"0x{v:04X}" for v in sorted({v for v in set(ctor_ops) if v in known})],
            "leftover": [f"0x{v:04X}" for v in sorted(set(ctor_ops) - known)],
            "registerLoaded": ctor_missed,
        },
        "res": catalog_res(),
        "scriptIniPaths": [p.decode("ascii", "replace") for p in extract_script_ini_paths(raw)],
    }
    if digest != EXPECTED_UNPATCHED_SHA256:
        raise SystemExit(f"unpatched exe sha {digest} != {EXPECTED_UNPATCHED_SHA256}")
    if not sendto_callers or not recvfrom_callers:
        raise SystemExit("WS2_32 sendto/recvfrom callers missing")
    unknown = report["packetOperations"]["unknownNamed"]
    if unknown:
        raise SystemExit("unknown-named PacketOperations remain: " + ", ".join(unknown))
    leftover = report["cpacketCtor"]["leftover"]
    if leftover:
        raise SystemExit("CPacket ctor leftovers not in PacketOperations: " + ", ".join(leftover))
    recovered = leftover_bodies(text, text_va, ctor_va, ctor_sites)
    missing_bodies = [f"0x{pid:04X}" for pid in EXPECTED_LEFTOVER_BODIES if pid not in recovered]
    if missing_bodies:
        raise SystemExit("leftover ctor sites vanished: " + ", ".join(missing_bodies))
    report["leftoverBodies"] = {f"0x{pid:04X}": recovered[pid] for pid in sorted(recovered)}
    report["packetOperations"]["unlabeled"] = unlabeled_ops(ops)
    verbs = packet_verbs()
    collisions = unlabeled_packet_collisions(ops, verbs)
    if collisions:
        raise SystemExit("unlabeled leftover has a .packet verb: " + "; ".join(collisions))
    unlabeled_rows = [o for o in ops if UNLABELED_NAME.search(o["name"])]
    report["leftoverNameEvidence"] = leftover_name_evidence(raw, pe, text, text_va, ctor_sites, unlabeled_rows)
    OUT.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(
        {
            "shaMatch": report["generatedFrom"]["unpatchedShaMatch"],
            "ops": report["packetOperations"],
            "udpRelated": report["imports"]["udpRelated"],
            "sendtoCallers": report["udp"]["sendtoCallers"],
            "recvfromCallers": report["udp"]["recvfromCallers"],
            "idListed": len(report["packetIdWrites"]["listed"]),
            "ctorLeftover": report["cpacketCtor"]["leftover"],
            "ctorMissed": report["cpacketCtor"]["registerLoaded"],
            "leftoverBodies": report.get("leftoverBodies", {}),
            "unlabeled": report["packetOperations"]["unlabeled"],
            "leftoverNameEvidence": [
                {k: row[k] for k in ("hex", "name", "sites", "nearbyStrings")}
                for row in report.get("leftoverNameEvidence", [])
            ],
            "resArchives": len(report["res"]["archives"]),
            "resTables": len(report["res"]["tableLikeEntries"]),
            "wrote": str(OUT),
        },
        indent=2,
    ))
    print("udp rtti", json.dumps(udp_rtti, indent=2))
    print("packet strings", report["packetStrings"])
    print("listed ids", report["packetIdWrites"]["listed"][:30])


if __name__ == "__main__":
    main()
