# Corrected room-identity run and runtime blocker

> **Historical handoff:** the stale-click diagnosis below was correct for that
> interrupted run, but it is superseded by
> [`mini-pc-final-validation.md`](mini-pc-final-validation.md). On the healthy
> mini-PC, a fresh, visibly calibrated click on the current room reproduced
> `The Club Match is over.` before any C2S `0x138B` was emitted.

**Evidence date:** 2026-08-12  
**Scope:** original genuine-client executor after applying the final continuation overlay  
**Status:** partial green for room identity only; no Ready/lifecycle green claim and no PDF

## Verified build and deployment inputs

| Artifact | Path in genuine-client executor | SHA-256 |
|---|---|---|
| Final continuation overlay | `.amp/handoff/club-match-continuation-final.tar.gz` | `7700f8967983e9fb268b24df1e46d7e630e0f6e81454cda1f297496ebb9d9cf3` |
| Exact focused reactor log, 50/50 passed | `/tmp/club-match-overlay-focused.log` | `0180d5c6efe4faf9a3641b8264b7f7a647a0121fa38bc9b95b7cd5cb7bc6ce61` |
| Exact package log, build passed | `/tmp/club-match-overlay-package.log` | `379cc8d0252efebfe77b10c5f3de9912abe6e385ff253e449c256a85483119b1` |
| Deployed `game-server.jar` | `game-server/target/game-server.jar` | `4a69f4349598fab7b3690237842f4da306338a92ad5dd1a42567b260d0ce522c` |

The corrected type-7 service registered C2S handlers `0x26F7`, `0x26F9`, and `0x26FB` and listened on TCP 5901 and gRPC 9901.

## Genuine normal-UI room-identity proof

A fresh BasicMode Club Match room was created through ordinary UI input with an unmodified genuine client:

- C2S `0x138F` payload: `00000000` (`roomType=0`, `mode=0`).
- S2C `0x138A` payload: `0000060000000000` (`roomType=6`, `mode=0`).
- S2C `0x177A` and `0x138E` likewise exposed room type 6/mode 0.

| Artifact | Path in genuine-client executor | SHA-256 |
|---|---|---|
| Pre-restart packet snapshot | `/tmp/club-match-before-service-restart-snap.pcap` | `05498cfa2f3ef2c74c624dee477a45aaea353a240cb2fa6c7fee7290d119e698` |
| Pre-restart service journal | `/tmp/club-game-pre-unplanned-restart-journal.txt` | `05db41299ea9a50c1b7649aadd8c63da935fd5002e3f2ea5598fb6f946c10717` |

Decisive screenshots remain in that executor:

- `/tmp/club-corrected-a-room-created.png`
- `/tmp/club-corrected-b-room-listed.png`
- `/tmp/club-corrected-b-room-row-pointer.png`

These external `/tmp` artifacts could not be copied into this repository package after the executor entered persistent severe process/I/O overload. Their paths and checksums are recorded here without claiming local inclusion.

## Corrected interpretation of the interrupted join attempt

- The blank Start/End/Present/Remaining values are countdown fields populated by S2C `0x26F8`.
- S2C `0x26FF` independently sets maximum play time.
- The `The Club Match is over.` modal maps to room-join result `-22`, but no C2S `0x138B` occurred during the questioned interaction.
- Screenshot reanalysis located modal OK near `(420,523)` and the first room-row center near `(300,226)`. The earlier double-click was below the row, so the modal was stale and did not prove a server join rejection or missing schedule field.
- Do **not** implement a room-list schedule extension based on that disproven interim hypothesis.

## Blocker and exact confidence boundary

Before the corrected ordinary-UI row click, the type-7 service unexpectedly restarted at **2026-08-12 01:28:38 UTC**. The restart invalidated the room and connections; both clients then displayed server-disconnected. The executor subsequently entered persistent process/I/O overload while relaunching its isolated Wine clients, and even trivial commands stopped completing reliably. Continuing there risked evidence corruption.

Therefore no valid two-client join, genuine `0x26F7`, `0x26F8`, `0x26F9`, relay, expiry, or `0x26FC` proof exists. Source/history remained uncommitted and unpushed, and the intentional deletion of `S2CMatchplayEndBasicGame.java` remained intact.

## Resume point

On a healthy executor with two isolated unmodified clients:

1. Deploy the checksummed final overlay/package and verify the type-7 handlers/listeners.
2. Re-login both clients after deployment and start fresh packet capture and service journaling.
3. Create a fresh BasicMode Club room through normal UI.
4. Click the actual first room row near its visually confirmed center; first prove C2S `0x138B` and a successful join response.
5. Ready both clients through normal UI and prove C2S `0x26F7`, not `0x1775`, followed by `0x26F8`.
6. Continue with `0x26F9`, relay, expiry, and non-tied result checks only when safely reachable.
7. Preserve and checksum all evidence. Generate the final PDF only after the corrected Ready/lifecycle green proof succeeds.
