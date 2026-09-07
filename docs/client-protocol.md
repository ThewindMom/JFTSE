# Client protocol surfaces

Facts from the unpatched `FantaTennis.exe` at SHA-256 `5477f0827acae66976403aecd2e9ebffeb4fa28da1fedae5f9541ec25e336c31` (`FantaTennis.exe.laa.bak`). The runtime `FantaTennis.exe` is a Large Address Aware patch of that binary. Rerun `scripts/client-re-extract.py` after any `PacketOperations` or client change.

## TCP frame

Every TCP payload uses an 8-byte header, then the body.

| Offset | Width | Field |
|---:|---:|---|
| 0 | 2 | `checkSerial` |
| 2 | 2 | `checkSum` |
| 4 | 2 | packet id |
| 6 | 2 | body length |

`CPacketCoder` selects a codec with a mode word at object offset `0x1052`. The compared values are `0xFF9B`, `0xFF9A`, and `0xFF99`. `0xFF9A` is also `S2CLoginWelcomePacket`. Those three values are codec modes, not extra game opcodes.

## Named TCP opcodes that were unlabeled

| Id | Name | Evidence |
|---|---|---|
| `0x106D` | `S2CPlayerInfoData` | Login path writes name, guild logo, records, stats, equipment, couple points. Client compares `eax` to `0x106D` at `0x49679c`. |
| `0x1071` | `C2SSceneChange` | Client pushes `0x1071` at `0x46e142`. Body is `int32 sceneId`. |
| `0x17DA` | `S2CSetHostReady` | Empty companion after `S2CSetHost`. No `0x17DA` immediate in this exe. Server sends it to the slot-0 host before `S2CRoomStartGame`. |
| `0x237C` | `C2SInventoryOpenRequest` | Client pushes `0x237C` at `0x4b7401`. Empty body. Server builds wear-slot answers. |

`C2S_WORLDQUEST_INIT_GAME` is a debug string at `0x4a4052`. The send site pushes `0x2209` (`C2SChallengeHp`). It is not a new opcode.

## Inner C2C relay ids

The native builder at `0x52be79` and parser at `0x5319de` pick an inner id by relay-object type.

| Type | Id | Name |
|---:|---|---|
| 1 | `0x32C9` | `C2CPlayerAnimationPacket` |
| 5 | `0x3332` | `C2CRelayObjectType5` |
| 6 | `0x32CA` | `C2CRelayObjectType6` |

`0x3332` body is 17 bytes: four bytes, one float, one byte-width hole at offset 10, and four 16-bit fields. Field meaning is unproven. The relay forwards a well-framed packet. It does not mutate from those fields.

## UDP

`WS2_32` imports include `sendto` and `recvfrom`.

`CUDPSocket::Send` starts at `0x62bf50`. It calls `sendto` at `0x62bf82` with `(socket, buf, len, flags=0, dest, destlen)`. `CUDPSocket::Receive` starts at `0x62c010` and calls `recvfrom` at `0x62c02e`. Both treat `WSAEWOULDBLOCK` (`10035`) as a retry.

`CClientNet` keeps a `CUDPConnector` at `this+0xa348`. When net state is `1` or `2`, `0x62b190` forwards a copy of the TCP frame: length is the header length word plus 8, bytes start at header offset 0. The UDP datagram payload is the same 8-byte FT header plus body.

`RakPeer` is a second stack. One `sendto` site is `0x642e4e`. A nearby `htons(0x3a98)` is port `15000`. RakNet message ids are not FT opcodes. `S2CGameNetworkSettings` (`0x3EA`) writes host, port, session id, and four player ids. It has no UDP flag.

## Res packages

`Res/*.res` archives are zip files. The extract script lists 630 archives and 395 table-like entries (item, level, AI, quest, emblem, card, pet, shop). Those tables are client data, not packet ids.
