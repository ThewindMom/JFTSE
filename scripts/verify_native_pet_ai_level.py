#!/usr/bin/env python3
"""Verify the native Fantasy Tennis pet-AI level-selection contract.

This does not execute or reimplement TennisAI. It checks the exact validated
client binary for the loader, lookup, and call-site instructions that connect a
pet's level to the matching AI_Pet* ``Level%d`` record. With ``--packet-log`` it
also verifies the level byte JFTSE sent for a named pet.
"""

from __future__ import annotations

import argparse
import hashlib
import re
from pathlib import Path


EXPECTED_SHA256 = "5477f0827acae66976403aecd2e9ebffeb4fa28da1fedae5f9541ec25e336c31"


def require(data: bytes, needle: bytes, description: str) -> None:
    if needle not in data:
        raise SystemExit(f"FAIL: {description} was not found")
    print(f"PASS: {description}")


def verify_binary(path: Path) -> None:
    data = path.read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    if digest != EXPECTED_SHA256:
        raise SystemExit(
            f"FAIL: FantaTennis.exe SHA-256 is {digest}; expected {EXPECTED_SHA256}"
        )
    print(f"PASS: validated FantaTennis.exe SHA-256 {digest}")

    for letter in "ABCDEFGHIJK":
        require(
            data,
            f"Res/Script/ETC/AI_Pet{letter}.ini".encode(),
            f"AI_Pet{letter}.ini path",
        )
    require(data, b"Level%d\0", "Level%d section-name format")
    require(
        data,
        b"AI Level(%d) requested not found for nPetModel(%d)\n\0",
        "missing-level diagnostic",
    )

    # TennisAIMgr initialization: twelve AI files are loaded into adjacent
    # per-model vectors (increment model and vector, compare against 12).
    require(
        data,
        bytes.fromhex("83 c6 01 83 c7 10 83 fe 0c 7c e4"),
        "twelve-model AI_Pet loader loop",
    )

    # TennisAIMgr lookup at VA 0x004F9BD0:
    #   level = [ebp+8], output = [ebp+0xc], nPetModel = [ebp+0x10]
    #   vector = this + ((nPetModel + 1) << 4) + 4
    #   each 88-byte record begins with its exact integer level.
    require(
        data,
        bytes.fromhex(
            "8b 45 10 83 c0 01 83 f8 0b 0f 87 b2 01 00 00 "
            "c1 e0 04 8b 5c 08 08 3b 5c 08 0c 8d 74 08 04"
        ),
        "nPetModel vector selection without level clamping",
    )
    require(
        data,
        bytes.fromhex(
            "b9 16 00 00 00 8b f3 8d 7c 24 10 f3 a5 "
            "8b 4c 24 10 3b 4d 08"
        ),
        "88-byte record copy followed by exact requested-level comparison",
    )

    # Both pet-AI constructors push their level argument and destination object
    # before calling the same TennisAIMgr lookup. These signatures distinguish
    # Basic and Double pet AI call sites.
    require(
        data,
        bytes.fromhex("8b 44 24 0c 56 50 e8 49 60 00 00 8b c8 e8 42 59 00 00"),
        "TennisAIPetBasic level-to-TennisAIMgr call",
    )
    require(
        data,
        bytes.fromhex("8b 4c 24 34 50 56 51 e8 f6 55 00 00 8b c8 e8 ef 4e 00 00"),
        "TennisAIPetDouble level-to-TennisAIMgr call",
    )


def verify_packet_log(path: Path, pet_name: str, expected_level: int) -> None:
    text = path.read_text(errors="replace")
    matches = re.findall(r'Packet \{ "id": "0x151B".*?"data": ([0-9A-F ]+) \}', text)
    encoded_name = pet_name.encode("utf-16le") + b"\0\0"
    observed: list[int] = []
    for match in matches:
        payload = bytes.fromhex(match)
        name_offset = payload.find(encoded_name)
        if name_offset < 0:
            continue
        level_offset = name_offset + len(encoded_name)
        if level_offset < len(payload):
            observed.append(payload[level_offset])
    if expected_level not in observed:
        raise SystemExit(
            f"FAIL: 0x151B did not carry {pet_name!r} level {expected_level}; "
            f"observed {observed or 'no matching pet'}"
        )
    print(
        f"PASS: JFTSE 0x151B carried {pet_name!r} level byte "
        f"0x{expected_level:02X} ({expected_level})"
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("exe", type=Path)
    parser.add_argument("--packet-log", type=Path)
    parser.add_argument("--pet-name")
    parser.add_argument("--expected-level", type=int)
    args = parser.parse_args()

    verify_binary(args.exe)
    packet_args = (args.packet_log, args.pet_name, args.expected_level)
    if any(value is not None for value in packet_args):
        if any(value is None for value in packet_args):
            parser.error(
                "--packet-log, --pet-name, and --expected-level must be supplied together"
            )
        if not 0 <= args.expected_level <= 255:
            parser.error("--expected-level must fit the one-byte wire field")
        verify_packet_log(args.packet_log, args.pet_name, args.expected_level)


if __name__ == "__main__":
    main()
