# Client protocol surfaces

Facts from the unpatched `FantaTennis.exe` at SHA-256 `5477f0827acae66976403aecd2e9ebffeb4fa28da1fedae5f9541ec25e336c31` (`FantaTennis.exe.laa.bak`). The runtime `FantaTennis.exe` is a Large Address Aware patch of that binary. Rerun `scripts/client-re-extract.py` after any `PacketOperations` or client change. The script exits if that SHA-256 does not match, or if `sendto` / `recvfrom` callers disappear.

The real outgoing catalog is every in-band `push imm32` in `0x0300..0xA000` in the 32 bytes before `call 0x627AE0` (`mov ax, [esp+8]` / `mov [esi+8], ax`). The extract fails if any of those immediates is missing from `PacketOperations`. Codec-mode arguments (`0xFFFFFCxx`) and join points with no in-band push in that window are listed under `registerLoaded` and are not treated as unnamed opcodes.

This catalog is the client-constructed TCP set. It is not field layouts, RakNet message ids, or UDP datagrams.

## TCP frame

Every TCP payload uses an 8-byte header, then the body. Offsets match `Packet` (`checkSerial` at 0, `checkSum` at 2, `packetId` at 4, `dataLength` at 6).

`CPacketCoder` compares a mode word at object offset `0x1052` to `0xFF9B`, `0xFF9A`, and `0xFF99`. `0xFF9A` is also `S2CLoginWelcomePacket`. Those three values are codec modes, not extra game opcodes.

## Named TCP opcodes that were unlabeled

Instruction addresses below are the first byte of the `cmp` or `push`.

| Id | Name | Evidence |
|---|---|---|
| `0x106D` | `S2CPlayerInfoData` | Login path writes name, guild logo, records, stats, equipment, couple points. `cmp eax, 0x106D` at `0x49679b`. |
| `0x1071` | `C2SSceneChange` | `push 0x1071` at `0x46e141`. Body is `int32 sceneId`. |
| `0x17DA` | `S2CSetHostReady` | Empty server packet after `S2CSetHost`, before `S2CRoomStartGame`. This exe has no `0x17DA` immediate. The name is a server-order guess. |
| `0x237C` | `C2SInventoryOpenRequest` | `push 0x237C` at `0x4b7400`. Empty body. Server builds wear-slot answers. |

`C2S_WORLDQUEST_INIT_GAME` is a debug string. The send site at `0x4a405f` pushes `0x2209` (`C2SChallengeHp`). It is not a new opcode.

## Client-constructed leftovers now in `PacketOperations`

These ids were `push`ed into the CPacket ctor and were missing from the enum. Names with `Extra` or `Client` plus a hex suffix are cluster labels, not proven field meanings. String-backed names:

| Id | Name | Nearby client string or GUI |
|---|---|---|
| `0x0401` | `C2SRelayNetErr` | `NETERR_RELAYSERVER_PROBLEM` |
| `0x0405` | `C2SRelayNetErr2` | same net-err cluster |
| `0x1DB0` | `C2SBettingStart` | betting-start path |
| `0x1DB2` | `C2SBettingStart2` | betting-start path |
| `0x2411` | `C2SReadyWaitTime` | ready-wait GUI |
| `0x2528` | `C2SProposalListExtra` | proposal memos |
| `0x26B5` | `C2STournamentApplyDouble` | tournament double apply |
| `0x26C0` | `C2SGmSpectator` | `GuiPop_GMSpectator.xml` |
| `0x26C6` | `C2STournamentApplySingle` | tournament single apply |
| `0x26C8` | `C2SLeagueGuildTeamBattle` | league guild team battle |

The rest of the ctor leftovers (`0x0C95`, `0x1007`, `0x13A6`, `0x1451`/`0x1453`/`0x1454`, lobby `0x170D`/`0x170F`/`0x1711`, host/start `0x17D7`/`0x17DB`/`0x17E9`/`0x17F2`, chat/room `0x18A4`/`0x18A7`/`0x18AE`/`0x18B0`/`0x18B1`, inventory/shop/coupon/guild extras, town `0x2648`..`0x2650`, tournament extras, club extras, `0x2EE2`, C2C `0x32CB`/`0x3330`/`0x3392`..`0x3396`/`0x33A4`) are listed in `PacketOperations` with those cluster names.

`0x26C1` is pushed to `0x465B20`, not to the CPacket ctor. It is not a leftover opcode.

## Ctor sites with no in-band push

| Call VA | Kind | Note |
|---|---|---|
| `0x56E990` | join point | other edge pushes `0x2521` (`C2SProposalAnswerRequest`) |
| `0x5C575B` | join point | other edge pushes `0x26C2` |
| `0x5C5A59` | join point | `push 0x26C2` sits 70 bytes before the call |
| `0x5C5CE0` | join point | same `0x26C2` cluster |
| `0x6289F1` | codec mode | `0xFFFFFC11` |
| `0x628B76` | codec mode | `0xFFFFFC12` |
| `0x628D8E` | codec mode | `0xFFFFFC12` |
| `0x62AD1F` | codec mode | `0xFFFFFC16` |
| `0x62ADD5` | codec mode | `0xFFFFFC15` |
| `0x62AE6E` | codec mode | `0xFFFFFC13` |
| `0x62B31E` | codec mode | `0xFFFFFC17` |

## Inner C2C relay ids

The native builder at `0x52be79` and parser at `0x5319de` pick an inner id by relay-object type.

| Type | Id | Name |
|---:|---|---|
| 1 | `0x32C9` | `C2CPlayerAnimationPacket` |
| 5 | `0x3332` | `C2CRelayObjectType5` |
| 6 | `0x32CA` | `C2CRelayObjectType6` |
| 7 | `0x32CB` | `C2CRelayObjectType7` |

`0x3332` widths live in `OwnedPetRelay3332Layout`. Field meaning is unproven. The relay forwards a well-framed packet. It does not mutate from those fields.

## UDP

`WS2_32` imports include `sendto` and `recvfrom`. The extract script records the `FF 15` callers. On this exe they are `0x62bf82` and `0x642e4e` for `sendto`, and `0x401344` and `0x62c02e` for `recvfrom`.

The `sendto` at `0x62bf82` sits in a function that starts at `0x62bf50`. That function pushes `(dest, destlen, flags=0, len, buf, socket)` and retries on `WSAEWOULDBLOCK` (`10035`). The `recvfrom` at `0x62c02e` sits in a function that starts at `0x62c010` and uses the same error. RTTI strings for `CUDPSocket` exist. The vtable walk for that class hit string data, so the `CUDPSocket::Send` / `Receive` names are not proven.

`0x62b190` forwards a copy of the 8-byte FT header plus body to a member at `this+0xa348` when a state word is `1` or `2`. That is the evidence that one UDP path carries the TCP frame.

A second `sendto` is at `0x642e4e`. A `push 0x3a98` then `htons` is port `15000`. `RakPeer` RTTI exists. Message ids on that path are not FT opcodes and are not extracted. `S2CGameNetworkSettings` (`0x3EA`) writes host, port, session id, and four player ids. It has no UDP flag.

## Res packages

`Res/*.res` archives are zip files. The extract lists archives and table-like zip entries. The exe also embeds 69 `Res/Script/**/*.ini` and `*.txt` paths. Those tables are client data, not packet ids.
