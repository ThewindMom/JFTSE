# Battlemon gap closure (2026-08-16)

This pass independently re-dumped the official updater client and closed
three gaps as **fail-closed policy plus tests**. No live two-client capture
was obtained. Docker Engine started on the work box; Wine/Proton was not
available, so the native client was not run against compose.

Executable SHA-256:
`5477f0827acae66976403aecd2e9ebffeb4fa28da1fedae5f9541ec25e336c31`

`Res/Script/Item.res` MD5 `18e31044447309565d8b981cdaef175d` matches
`files.md5`. Member `Item_PetItem.set` was decrypted with FT-ResTool
AES-128-ECB key `TIMOTEI_ZION`. Tabular extract: `docs/PET_ITEM_table.tsv`.

## PET_ITEM 15

Retail `Item_PetItem` contains exactly indices **1–14 and 16–23** (22 rows).
Index 15 is absent. The XML jumps from `Index="14"` to `Index="16"`.

`BattlemonLifecycleServiceImpl.applyPetItem` already returns false for any
index outside that set, including 15, and does not consume the stack.
Tests now name index 15 (and 0/24) as rejected unused.

## MaxUse

| Indices | MaxUse | UseType | Resource effect |
|---|---:|---|---|
| 1–12 | 10 | Count | STAT +1 / +2 / +5 |
| 13 | 300 | Count | LifeUp=1 |
| 14 | 50 | Count | MaxLifeUp=5 |
| 16–23 | 50 | Count | hunger/energy potions |

Meaning is **unproven**. There is no durable per-item use-count column on
`Pet`. Consumption is `PlayerPocket.itemCount`. Stat MaxUse=10 cannot be the
stat cap (signed-byte 127 is already native-validated). Do not implement a
MaxUse counter until a repeated-use capture distinguishes use-count vs
value-cap vs UI metadata. `PetItemRetailResourceTableTest` records the values
only.

## 0x3332

Builder VA `0x52be79` writes packet id `0x3332` for internal relay-object
type 5. Parser VA `0x5319de` compares `cx` to `0x3332`. Layout remains
25-byte inner (8-byte header + 17-byte body widths 1,1,1,1,4,2,1,2,2,2).

Field meanings, sender authority, and actor ownership are unresolved.
`OwnedPetRelay3332Layout` parses those widths for structured logging only.
`RelayPacketRequestHandler` drops 0x3332 in owned-pet sessions **before**
`PacketRegistry.decode` / `queuePacket`. It is not forwarded.

## Spectators and other dedicated layouts

`CMSG_RoomJoin` has `roomId`, `unk0`, `password` only — no spectator flag.
The client has ordinary spectator UI strings (`cSpectator0`–`cSpectator3`).
Dedicated Battlemon join still admits only positions 0 and 1. Start still
requires exactly two owners at 0/1, each with a pet at owner+2.

Unsupported layouts (1 owner, 3 humans, position-4 spectator present) fail
admission. Named tests cover those start rejections. Do not invent spectator
or 1p/3p/4-human dedicated topologies without a client-proven flow.

## ClickUp-ready notes

- PET_ITEM 15: independently dumped from retail Item_PetItem.set; index absent; server continues to reject unused.
- MaxUse: values recorded (1–12:10, 13:300, 14:50, 16–23:50); meaning unproven; no counter implemented.
- 0x3332: identity+widths reconfirmed at 0x52be79 / 0x5319de; no capture; structured parse+log then drop; not forwarded.
- Spectators/other layouts: client join has no spectator flag; dedicated Battlemon remains 2 owners + pets 2/3; rejected cases now have named tests.
