# Client protocol surfaces

This branch reverse-engineers every packet the unpatched client constructs or parses. A packet is done only when it has an id, a direction, a proved body, and a name that is not `Unknown` / `Extra` / `Client` plus hex.

Facts from `FantaTennis.exe` at SHA-256 `5477f0827acae66976403aecd2e9ebffeb4fa28da1fedae5f9541ec25e336c31` (`FantaTennis.exe.laa.bak`). The runtime `FantaTennis.exe` is a Large Address Aware patch of that binary. Rerun `scripts/client-re-extract.py` after any `PacketOperations` or client change. The script exits if that SHA-256 does not match, or if a CPacket ctor leftover is missing from `PacketOperations`. It records leftover body widths and the unlabeled Extra/Client names. A leftover is not done until that name is gone.

Outgoing C2S/C2C ids are every in-band `push imm32` in `0x0300..0xA000` in the 32 bytes before `call 0x627AE0` (`mov ax, [esp+8]` / `mov [esi+8], ax`). Codec-mode arguments (`0xFFFFFCxx`) and join points with no in-band push in that window are listed under `registerLoaded` and are not unnamed opcodes. Body widths come from WriteU8/U16/U32/U64 / WriteBytes / WriteUTF16z and inlined `dataLength` adds after the ctor.

## TCP frame

Every TCP payload uses an 8-byte header, then the body. Offsets match `Packet` (`checkSerial` at 0, `checkSum` at 2, `packetId` at 4, `dataLength` at 6).

`CPacketCoder` compares a mode word at object offset `0x1052` to `0xFF9B`, `0xFF9A`, and `0xFF99`. `0xFF9A` is also `S2CLoginWelcomePacket`. Those three values are codec modes, not extra game opcodes.

Capture live frames with `scripts/client-live-proxy.py`. `--self-test` checks high-bit XOR keys and redacts `0x0FA1`. `--probe-welcome` opens TCP to the remote and must see `0xFF9A` first. On 2026-09-07 `game.jftse.com:5897` sent a 16-byte welcome with `decKey=0` and `encKey=0`.

`jftse.dll` opens its own TCP before the exe reads `ServerInfo.ini`. A WineD3D run of this test client connected to `game.jftse.com:3725` (`94.130.239.34`) and logged `[AC] Connected to the server successfully`, then the peer closed. Pointing `ServerInfo.ini` at the proxy does not intercept that socket. Login TCP to port 5897 was not observed in that session. `C2SLoginRequest` (`0x0FA1`) bodies are redacted in the JSONL.

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
| `0x26B1` | `C2STournamentApply` | same id as `CMSG_TournamentApply`; leftover name was a cluster label |
| `0x26B3` | `C2STournamentCancel` | same id as `CMSG_TournamentCancel` |
| `0x26B5` | `C2STournamentExtra26B5` | qualifying-screen layout only; verb unknown; no handler |
| `0x26B7` | `C2STournamentExtra26B7` | qualifying-screen layout only; verb unknown; no handler |
| `0x26BE` | `C2STournamentInfo` | same id as `CMSG_TournamentInfo` |
| `0x26C0` | `C2STournamentBracket` | PCAP from the final-bracket button; nearby `GuiPop_GMSpectator.xml` is not the opcode |
| `0x26C2` | `C2STournamentBracketMatch` | same id as `CMSG_TournamentBracketMatch` |
| `0x26C6` | `C2STournamentExtra26C6` | `tournamentId` only, `myState == 2`; not apply and not spectator |
| `0x26C8` | `C2SLeagueGuildTeamBattle` | league guild team battle; not tournament-family |
| `0x26F2` | `C2SGuildLeagueList` | same id as `CMSG_GuildLeagueList`; leftover name was a Club cluster label |

The rest of the ctor leftovers (`0x0C95`, `0x1007`, `0x13A6`, `0x1451`/`0x1453`/`0x1454`, lobby `0x170D`/`0x170F`/`0x1711`, host/start `0x17D7`/`0x17DB`/`0x17E9`/`0x17F2`, chat/room `0x18A4`/`0x18A7`/`0x18AE`/`0x18B0`/`0x18B1`, inventory/shop/coupon/guild extras, town `0x2648`..`0x2650`, club extras `0x2702`/`0x270F`, `0x2EE2`, C2C `0x32CB`/`0x3330`/`0x3392`..`0x3396`/`0x33A4`) are listed in `PacketOperations` with those cluster names.

`0x1DB0` is the room start-betting-game path (`MSG_CAN_NOT_START_BETTING_GAME`). It is not SPECIAL 20 Betting Coin. `0x1DE3` is C2S card-slot expand (SPECIAL 48). `0x1453` is `CMSG_ServerTimeSync`. `0x1D0B` is split-stack (`MSG_SPLITING_ITEM`).

`0x26C1` is pushed to `0x465B20`, not to the CPacket ctor. It is not a leftover opcode.

## Unlabeled leftover names

`docs/client-re-catalog.json` records 46 unlabeled leftovers in `packetOperations.unlabeled`. `leftoverNameEvidence` stores ctor sites, nearby strings, and disasm for those same ids. The leftover table above already names the string-backed Extra rows. It does not repeat the unlabeled list.

No unlabeled leftover id joins a `.packet` `CMSG_` or `SMSG_` verb. Ctor-site review held all 46.

`C2STournamentExtra26B5` (`0x26B5`), `C2STournamentExtra26B7` (`0x26B7`), and `C2STournamentExtra26C6` (`0x26C6`) stay Extra. Nearby `MSG_TOURNEY_ASK_APPLY_DOUBLE`, `MSG_TOURNEY_ASK_CANCEL_DOUBLE`, `MSG_TOURNEY_ASK_APPLY_SINGLE`, and `MSG_TOURNEY_ASK_CANCEL_SINGLE` strings are dialogs.

A name that still ends in `Extra`, or in `Client` plus hex, is the honest name until a `CMSG_` verb exists for that id. If a leftover later gains a `.packet` verb and still keeps an Extra or Client name, `unlabeled_packet_collisions` in `scripts/client-re-extract.py` exits.

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

## Three transports, one game packet

XxharCs's split is real. The official client is not TCP-only. JFTSE implemented one of the three paths.

**1. TCP (`CTCPSocket` / `CClientNet`).** Login, lobby, shop, rooms. `ServerInfo.ini` only lists this (`game.jftse.com:5897` on this test copy). JFTSE already speaks it.

**2. UDP that carries the same CPacket.** `CClientNet` slot 8 at `0x62B190` (stored at `0x7212D8`) copies the 8-byte header plus body to `this+0xA348` when the state word at `this+0x14` is `1` or `2`. Eight call sites (`0x628713` … `0x629ECF`) then call the `sendto` wrapper at `0x62BF50` with a 16-byte `sockaddr`. That is datagram send of an already-built CPacket, not a second opcode table. `CPacketCoder` at `0x62CE80` still switches on modes `0xFF9B` / `0xFF9A` / `0xFF99`. The ctor sites that push `0xFFFFFC11`–`0x17` sit on this encode path. `recvfrom` wrappers are `0x62C010` (one caller `0x6291FE`) and `0x401344`.

**3. RakNet.** Second `sendto` at `0x642E4E`. Nearby `push 0x3A98` / `htons` is port `15000`. `RakPeer` RTTI exists. Those bytes are not FT opcodes.

`S2CGameNetworkSettings` (`0x3EA`) writes host, port, session id, and four player ids. It has no UDP flag. After that packet, official FT can open path 2 or 3. JFTSE instead opens a second TCP connection and wraps in-match C2C inside `C2SRelayPacketToAllClients` (`0x414`). `C2SRelayNetErr` (`0x0401`) is the client telling the game server that relay path failed. Which stack consumes the `0x3EA` port is unproven without a pcap.

`WS2_32` `sendto` callers on this exe are `0x62BF82` and `0x642E4E`. `recvfrom` callers are `0x401344` and `0x62C02E`. The extract exits if those vanish. RTTI strings for `CUDPSocket` exist. The vtable walk for that class hit string data, so `CUDPSocket::Send` is not a proven name. Real port 15000 is `push 0x3A98` then `htons` at `0x642DAE` / `0x642DBA`. A lone `push 0x3A98` at `0x46B8AD` is not htons.

Zero UDP datagrams have been captured. The emulator still has no `DatagramChannel`. Guardian and Basic have been played against JFTSE's TCP relay with this same client build, so path 2 is official-server fidelity, not a gate for "this exe can enter a JFTSE match." Path 3 stays blocked until a match-start pcap.

## Res packages

`Res/*.res` archives are zip files. The extract lists archives and table-like zip entries. The exe also embeds 69 `Res/Script/**/*.ini` and `*.txt` paths. Those tables are client data, not packet ids.
