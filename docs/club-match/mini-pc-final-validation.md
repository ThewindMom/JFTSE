# Club Match mini-PC final validation

> **Historical partial run:** this 2026-08-12 join-gate result is superseded by the [2026-08-13 native validation report](native-validation-report.md), whose continuation completed the main Warfare Basic lifecycle while retaining explicit gaps.

**Evidence date:** 2026-08-12  
**Branch:** `feature/club-match-mode`  
**Bundle HEAD:** `54dd3cacf7f0c58ab0c6a542416fb5be56b1a44b`  
**Verdict:** partial green through native corrected room creation; blocked before
server-side join; no Ready/lifecycle green and no PDF

## 1. Verdict

The healthy mini-PC rerun resolved the previous runner's infrastructure and
fixture blockers. Two isolated official clients freshly authenticated with all
11 tutorial entries complete, selected the dedicated type-7 Club Match channel,
and reached its lobby. The non-GM host then created a fresh BasicMode room through
ordinary native UI input.

That create is a genuine corrected green:

- C2S `0x138F`: `00000000` (`roomType=0`, `mode=0`, `players=0`).
- S2C `0x138A`: `0000060000000000` (success, room type 6, mode 0).
- S2C `0x177A` and `0x138E`: room type 6, mode 0.
- S2C `0x26FF`: `05000000` (maximum play time 5).

The guest saw that new room. From a current screenshot, the custom cursor's
yellow hotspot was moved onto the first row before one native click. The client
immediately displayed `The Club Match is over.` and remained in the lobby. The
host was frozen in the live room with only `CMHostA`. Neither the dedicated
server journal nor the TCP 5901 capture contains C2S `0x138B` after that click.

This corrects the earlier historical handoff in one important respect: although
the old questioned click did miss the row, a fresh and visibly calibrated click
on a current room reproduced the same client-side gate. It still does **not**
prove a server join defect because no request reached the server. Per task scope,
no room-list schedule extension, client patch, packet injection, or direct
handler call was introduced.

Barrier 4 therefore failed before server-side join. Barriers 5–7 were not
attempted, and there is no genuine corrected `0x26F7`/`0x26F8` or lifecycle
claim.

## 2. Evidence classes

| Class | Final evidence |
|---|---|
| Observed native runtime | Official-client auth/channel/lobby/create screens; TCP 5901 bytes; decoded service journals; final DB rows |
| Static client reverse engineering | Historical dispatch evidence for Club Match room types/opcodes retained in the prior review package; not promoted to runtime proof |
| JFTSE source baseline | Reconstructed source overlay, packet definitions, focused tests, package output, handler registrations |
| Compatibility interpretation | Type-7 native `(roomType=0, mode=0)` is translated to stored/S2C `(6,0)`; unsupported adjacent behavior remains explicit |
| Implementation in this tree | Dedicated server/channel, identity mapping, guarded ready/countdown/start/relay/deadline/result code; no runner-authored production fix after the supplied continuation overlay |

## 3. Provenance, build, and topology

All three supplied bundle parts, the concatenated bundle, and all four supplied
archives matched their expected SHA-256 values. `git bundle verify` passed. The
branch bundle contains complete history for the exact HEAD above.

The official executable hash in the immutable copy and both runtime copies was:

```text
5477f0827acae66976403aecd2e9ebffeb4fa28da1fedae5f9541ec25e336c31  FantaTennis.exe
```

The host and guest had independent task-owned runtime directories, Wine prefixes,
containers, displays (`:121`, `:122`), window managers, processes, IP addresses
(`94.130.239.50`, `94.130.239.51`), accounts, players, guilds, and screenshot
trees. No runtime state was shared.

Using JDK 21:

- focused reactor suite: **50 tests, 0 failures, 0 errors, 0 skipped**;
- package: **BUILD SUCCESS**;
- exact relevant deployable `game-server.jar` SHA-256:
  `f2b424b356a848d63f0844f7ff547a258ef5f7dd52e33d775ccc11470157ac18`.

The task-owned observable stack included AC 3724, auth 5894/gRPC 9898,
ordinary game 5895, relay 5896, chat 5897, and dedicated type-7 Club game
5901/gRPC 9901. RabbitMQ had consumers for `q-game-club` and
`match-queue-club`. The Club process registered handlers `0x26F7`, `0x26F9`,
and `0x26FB` before login. The `GameServer` row for type 7 pointed to
`94.130.239.34:5901`.

## 4. Disposable fixture and fresh-auth barrier

Only the task-local disposable database was changed. Before fresh authentication:

- `cmhost` / `CMHostA` and `cmguest` / `CMGuestB` were non-GM and offline;
- each belonged to a different guild (`CMHostGuild`, `CMGuestGuild`);
- both guilds had `allowedCharacterType=000102030405060708`;
- both players had 11/11 successful tutorial progression rows;
- `anticheat.version=20260605` and service addressing consistently used the
  task-owned `94.130.239.34` alias.

The exact clients then authenticated through normal UI. Decoded auth S2C `0x1005`
records at 09:43:09Z and 09:46:29Z show `tutorialCount=11` and
`gameMaster=false` for host and guest respectively. Neither client entered or
played tutorial UI.

Both selected Club Channel #1 and connected to server type 7. The final database
snapshot at the join gate still showed two separate guild memberships and 11/11
tutorial completion.

## 5. Native room-create and join-gate sequence

| UTC | Native action / observation | Protocol result |
|---|---|---|
| 10:09:29 | Host clicked the calibrated BasicMode control | C2S `0x138F` `00000000` |
| 10:09:29 | Host entered room; guest saw fresh row `CMHostA's room` | S2C `0x138A` `0000060000000000`; `0x177A`/`0x138E` type 6/mode 0; `0x26FF` `05000000` |
| 10:10:59 | Guest cursor calibrated from the current room-list screenshot | Yellow hotspot visibly inside the first row |
| 10:11:35 | Guest clicked once through native UI | Client modal `The Club Match is over.`; no C2S `0x138B` |
| after click | Host remained frozen in room; guest remained in lobby | No roster/side mutation and no server join response |

The safe PCAPNG is limited to TCP 5901 from 10:09:20Z through 10:12:10Z.
It starts long after game login and excludes auth and AC ports. Manual ASCII and
UTF-16LE inspection found only disposable player/guild/room strings, not
credentials or tokens. The original multi-port capture remains local sensitive
evidence and is excluded from the result archive.

The client UI's Start/End/Present/Remaining labels are countdown fields supplied
by S2C `0x26F8`; S2C `0x26FF` independently supplied the observed maximum play
time. This run provides no basis for adding those values to room-list packets.

## 6. Source and verification boundary

No mini-PC runner-authored production source fix was made. The final source is
the supplied handoff plus continuation overlay and partial-green documentation,
with the intentional deletion of:

```text
game-server/src/main/java/com/jftse/emulator/server/core/packets/matchplay/S2CMatchplayEndBasicGame.java
```

The result package includes a source patch and byte-exact source overlay against
the bundle HEAD. In a second task-owned clone, `git apply --check` and
`git apply` passed; all 56 source/config/test files compared byte-for-byte and
the intentional deletion was present. Existing transferred CRLF additions make
`git diff --check` report trailing-whitespace warnings; they were preserved as
in the supplied source rather than normalized during validation.

## 7. Explicit non-claims

- No successful native C2S `0x138B`, server join response, two-player roster, or
  opposing-side rendering was reached.
- Ready was not reachable. The absence of both `0x26F7` and `0x1775` is not a
  claim about which opcode the corrected client would choose.
- No native S2C `0x26F8`, designated/unique `0x26F9`, relay setup/connections,
  `0x26FA`, point, `0x26FB`, non-tied `0x26FC`, or clean room return was reached.
- No server negative/lifecycle case was run after Ready because Ready never
  became stable.
- The client-side gate is not classified as a server defect.
- No tie bytes, draws, rewards, rankings, Castle effects, Pet support, or Club
  schedule behavior are claimed or invented.
- No PDF was generated because genuine Ready/lifecycle green did not succeed.

## 8. Evidence navigation

Safe reviewer evidence is under [`final-evidence/`](final-evidence/):

- `native/host-room-created.png`
- `native/guest-fresh-room-visible.png`
- `native/guest-room-click-calibrated.png`
- `native/guest-client-side-match-over-gate.png`
- `native/host-frozen-after-guest-gate.png`
- `protocol/corrected-non-gm-room-create.log`
- `protocol/calibrated-guest-click-server-window.log`
- `protocol/club-5901-create-and-client-gate-safe.pcapng`
- `protocol/opcode-verdict.txt`
- `db/fixture-before-fresh-auth.txt`
- `db/after-client-gate-host-frozen.txt`
- `services/club-startup-handlers-connections.log`
- `services/listeners.txt`

The result archive additionally contains exact build logs, JAR hashes and the
relevant deployable JAR, source patch/overlay/apply verification, final cleanup
evidence, and a `SHA256SUMS` manifest that excludes itself and passes completely.

## 9. Cleanup

Stopping the two isolated clients naturally returned both disposable accounts to
status 0 and both players offline while preserving 11/11 tutorial completion.
An explicit idempotent reset then set `loggedInServer=NULL` and reconfirmed the
same state. Only containers and the network labeled
`amp.task=jftse-club-match-final` were removed. The final Docker inventory had no
remaining task-labeled container or network. No unrelated service, checkout, Git
index, commit, branch, or remote was touched.
