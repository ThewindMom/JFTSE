#!/usr/bin/env python3
"""TCP proxy that decodes Fantasy Tennis frames from the live test client.

Welcome (0xFF9A) is plaintext and carries decKey/encKey. Later frames XOR
those keys. Writes JSONL the extract can join to PacketOperations names.
"""
from __future__ import annotations

import argparse
import json
import re
import socket
import struct
import threading
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACKET_OPS = ROOT / "server-core/src/main/java/com/jftse/server/core/protocol/PacketOperations.java"
WELCOME_ID = 0xFF9A
HEADER = 8


def parse_ops(src: str) -> dict[int, str]:
    return {int(hx, 16): name for name, hx in re.findall(r"^\s+([A-Za-z0-9_]+)\((0x[0-9A-Fa-f]+)\)", src, re.M)}


REDACT_IDS = {0x0FA1}


def u32(value: int) -> int:
    return value & 0xFFFFFFFF


def xor_key(data: bytes, key: int) -> bytes:
    key = u32(key)
    if key == 0:
        return data
    out = bytearray(data)
    for i in range(0, len(out) - 3, 4):
        word = u32(struct.unpack_from("<I", out, i)[0] ^ key)
        struct.pack_into("<I", out, i, word)
    for i in range(len(out) & ~3, len(out)):
        out[i] ^= (key >> ((i & 3) * 8)) & 0xFF
    return bytes(out)


def read_u16(buf: bytes, off: int) -> int:
    return struct.unpack_from("<H", buf, off)[0]


def read_i32(buf: bytes, off: int) -> int:
    return struct.unpack_from("<i", buf, off)[0]


class StreamDecoder:
    def __init__(self, direction: str, names: dict[int, str], sink, keys: dict[str, int]):
        self.direction = direction
        self.names = names
        self.sink = sink
        self.keys = keys
        self.buf = bytearray()
        self.saw_welcome = False

    @property
    def key(self) -> int:
        return self.keys["dec" if self.direction == "c2s" else "enc"]

    def feed(self, chunk: bytes) -> None:
        self.buf.extend(chunk)
        while True:
            frame = self._pop_frame()
            if frame is None:
                return
            self.sink(frame)

    def _pop_frame(self) -> dict | None:
        if len(self.buf) < HEADER:
            return None
        header = bytes(self.buf[:HEADER])
        if not self.saw_welcome and self.direction == "s2c":
            pid = read_u16(header, 4)
            length = read_u16(header, 6)
            if pid == WELCOME_ID and HEADER + length <= len(self.buf):
                raw = bytes(self.buf[: HEADER + length])
                del self.buf[: HEADER + length]
                dec_key = u32(read_i32(raw, 8)) if length >= 4 else 0
                enc_key = u32(read_i32(raw, 12)) if length >= 8 else 0
                self.saw_welcome = True
                self.keys["dec"] = dec_key
                self.keys["enc"] = enc_key
                return {
                    "dir": self.direction,
                    "id": pid,
                    "hex": f"0x{pid:04X}",
                    "name": self.names.get(pid, "UNLISTED"),
                    "len": length,
                    "welcomeDecKey": dec_key,
                    "welcomeEncKey": enc_key,
                    "plain": raw.hex(),
                }
        plain_header = xor_key(header, self.key)
        length = read_u16(plain_header, 6)
        total = HEADER + length
        if length > 16384 or total > 65535:
            return None
        if len(self.buf) < total:
            return None
        raw = bytes(self.buf[:total])
        del self.buf[:total]
        plain = xor_key(raw, self.key)
        pid = read_u16(plain, 4)
        body = plain[HEADER:].hex()
        if pid in REDACT_IDS:
            body = "REDACTED"
        return {
            "dir": self.direction,
            "id": pid,
            "hex": f"0x{pid:04X}",
            "name": self.names.get(pid, "UNLISTED"),
            "len": length,
            "body": body,
        }


def pipe(src, dst, decoder: StreamDecoder) -> None:
    try:
        while True:
            chunk = src.recv(65536)
            if not chunk:
                break
            decoder.feed(chunk)
            dst.sendall(chunk)
    except OSError:
        pass
    finally:
        try:
            dst.shutdown(socket.SHUT_WR)
        except OSError:
            pass


def serve(listen: str, listen_port: int, remote_host: str, remote_port: int, out_path: Path) -> None:
    names = parse_ops(PACKET_OPS.read_text())
    out_path.parent.mkdir(parents=True, exist_ok=True)
    lock = threading.Lock()
    frames: list[dict] = []

    def sink(frame: dict) -> None:
        frame["ts"] = time.time()
        with lock:
            frames.append(frame)
            with out_path.open("a") as fh:
                fh.write(json.dumps(frame) + "\n")
            print(json.dumps({k: frame[k] for k in ("dir", "hex", "name", "len")}), flush=True)

    lsock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    lsock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    lsock.bind((listen, listen_port))
    lsock.listen(8)
    print(json.dumps({"listening": f"{listen}:{listen_port}", "remote": f"{remote_host}:{remote_port}", "out": str(out_path)}), flush=True)
    while True:
        client, addr = lsock.accept()
        print(json.dumps({"accepted": list(addr)}), flush=True)
        remote = socket.create_connection((remote_host, remote_port), timeout=15)
        keys = {"dec": 0, "enc": 0}
        s2c = StreamDecoder("s2c", names, sink, keys)
        c2s = StreamDecoder("c2s", names, sink, keys)
        threading.Thread(target=pipe, args=(client, remote, c2s), daemon=True).start()
        threading.Thread(target=pipe, args=(remote, client, s2c), daemon=True).start()


def make_frame(pid: int, body: bytes, key: int) -> bytes:
    raw = bytearray(HEADER + len(body))
    struct.pack_into("<HHHH", raw, 0, 1, 2, pid, len(body))
    raw[HEADER:] = body
    return xor_key(bytes(raw), key)


def self_test() -> None:
    got: list[dict] = []
    keys = {"dec": 0, "enc": 0}
    names = {0xFF9A: "S2CLoginWelcomePacket", 0x0FA1: "C2SLoginRequest", 0x0FA2: "S2CLoginAnswerPacket"}
    s2c = StreamDecoder("s2c", names, got.append, keys)
    c2s = StreamDecoder("c2s", names, got.append, keys)
    dec, enc = 0x80000001, 0xF00DF00D
    s2c.feed(make_frame(0xFF9A, struct.pack("<IIII", dec, enc, 0, 0), 0))
    assert keys["dec"] == dec and keys["enc"] == enc, keys
    c2s.feed(make_frame(0x0FA1, b"secret", dec))
    s2c.feed(make_frame(0x0FA2, b"\x00", enc))
    assert [f["hex"] for f in got] == ["0xFF9A", "0x0FA1", "0x0FA2"], got
    assert got[1]["body"] == "REDACTED"
    print("decoder_ok")


def probe_welcome(host: str, port: int, out_path: Path) -> None:
    names = parse_ops(PACKET_OPS.read_text()) if PACKET_OPS.exists() else {}
    got: list[dict] = []
    keys = {"dec": 0, "enc": 0}
    decoder = StreamDecoder("s2c", names, got.append, keys)
    sock = socket.create_connection((host, port), timeout=10)
    try:
        deadline = time.time() + 8
        while time.time() < deadline and not got:
            sock.settimeout(max(0.1, deadline - time.time()))
            try:
                chunk = sock.recv(4096)
            except TimeoutError:
                break
            if not chunk:
                break
            decoder.feed(chunk)
    finally:
        sock.close()
    if not got:
        raise SystemExit("probe_welcome: no frame")
    first = got[0]
    if first.get("hex") != "0xFF9A":
        raise SystemExit(f"probe_welcome: first frame {first.get('hex')}")
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with out_path.open("a") as fh:
        fh.write(json.dumps({**first, "ts": time.time(), "probe": True}) + "\n")
    print(json.dumps({k: first[k] for k in ("dir", "hex", "name", "len") if k in first}), flush=True)
    print("probe_welcome_ok")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--listen", default="127.0.0.1")
    ap.add_argument("--listen-port", type=int, default=15897)
    ap.add_argument("--remote", default="game.jftse.com")
    ap.add_argument("--remote-port", type=int, default=5897)
    ap.add_argument("--out", default=str(ROOT / "docs/client-live-packets.jsonl"))
    ap.add_argument("--self-test", action="store_true")
    ap.add_argument("--probe-welcome", action="store_true")
    args = ap.parse_args()
    if args.self_test:
        self_test()
        return
    if args.probe_welcome:
        probe_welcome(args.remote, args.remote_port, Path(args.out))
        return
    serve(args.listen, args.listen_port, args.remote, args.remote_port, Path(args.out))


if __name__ == "__main__":
    main()
