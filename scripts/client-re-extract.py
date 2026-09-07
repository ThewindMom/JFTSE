#!/usr/bin/env python3
"""Extract protocol surfaces from the unpatched FantaTennis.exe.

Compares immediate packet IDs, RTTI, Winsock imports, and Res zip tables
against PacketOperations. Rerun after any PacketOperations or client change.
"""

from __future__ import annotations

import hashlib
import json
import re
import struct
import zipfile
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CLIENT_DIR = ROOT.parent / "FantaTennis-Test-Client"
EXE_UNPATCHED = CLIENT_DIR / "FantaTennis.exe.laa.bak"
EXE_RUNTIME = CLIENT_DIR / "FantaTennis.exe"
FRE = ROOT
PACKET_OPS = FRE / "server-core/src/main/java/com/jftse/server/core/protocol/PacketOperations.java"
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


def scan_packet_id_writes(text: bytes, text_va: int) -> Counter:
    """mov word/dword [reg+4], imm  matches CPacket header packetId at offset 4."""
    hits: Counter = Counter()
    n = len(text)
    i = 0
    while i + 6 <= n:
        # 66 C7 4x 04 XX XX   mov word ptr [reg+4], imm16
        if text[i] == 0x66 and text[i + 1] == 0xC7 and i + 6 <= n:
            modrm = text[i + 2]
            if (modrm & 0xC0) == 0x40 and text[i + 3] == 0x04:
                val = struct.unpack_from("<H", text, i + 4)[0]
                if OPCODE_MIN <= val <= OPCODE_MAX:
                    hits[val] += 1
        # C7 4x 04 XX XX 00 00  mov dword ptr [reg+4], imm32
        if text[i] == 0xC7 and i + 7 <= n:
            modrm = text[i + 1]
            if (modrm & 0xC0) == 0x40 and text[i + 2] == 0x04:
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
    """FF 15 XX XX XX XX  call dword ptr [iat]"""
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

    id_hits = scan_packet_id_writes(text, text_va)
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
            "sendtoCallers": xrefs_call_iat(text, text_va, sendto_iat) if sendto_iat else [],
            "recvfromCallers": xrefs_call_iat(text, text_va, recvfrom_iat) if recvfrom_iat else [],
            "sendCallersCount": len(xrefs_call_iat(text, text_va, send_iat)) if send_iat else 0,
            "recvCallersCount": len(xrefs_call_iat(text, text_va, recv_iat)) if recv_iat else 0,
        },
        "rttiNetwork": [n for n in extract_rtti(raw) if re.search(r"Packet|Socket|UDP|TCP|Rak|ClientNet|BlowFish", n)],
        "rttiAllCount": len(extract_rtti(raw)),
        "packetStrings": extract_packet_strings(raw),
        "packetIdWrites": {
            "distinct": len(id_hits),
            "listed": [f"0x{v:04X} x{id_hits[v]} {next(o['name'] for o in ops if o['value']==v)}" for v in listed],
            "unlisted": [f"0x{v:04X} x{id_hits[v]}" for v in unlisted],
        },
        "udpDatagrams": {
            "ftOverUdp": {
                "name": "FT_TCP_FRAME",
                "layout": "checkSerial:u16 checkSum:u16 packetId:u16 dataLength:u16 body[dataLength]",
                "sendVa": "0x62bf50",
                "recvVa": "0x62c010",
            },
            "rakNet": {
                "name": "RAKNET_DATAGRAM",
                "layout": "RakPeer bitstream, not an FT opcode",
                "sendtoVa": "0x642e4e",
                "defaultPortImmediate": 15000,
            },
        },
        "res": catalog_res(),
        "scriptIniPaths": [p.decode("ascii", "replace") for p in extract_script_ini_paths(raw)],
    }
    unknown = report["packetOperations"]["unknownNamed"]
    if unknown:
        raise SystemExit("unknown-named PacketOperations remain: " + ", ".join(unknown))
    OUT.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(
        {
            "shaMatch": report["generatedFrom"]["unpatchedShaMatch"],
            "ops": report["packetOperations"],
            "udpRelated": report["imports"]["udpRelated"],
            "sendtoCallers": report["udp"]["sendtoCallers"],
            "recvfromCallers": report["udp"]["recvfromCallers"],
            "idListed": len(report["packetIdWrites"]["listed"]),
            "idUnlisted": report["packetIdWrites"]["unlisted"],
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
