# Club Match draft implementation report

**Draft date:** 2026-08-11  
**Scope:** dirty `feature/club-match-mode` working tree plus extracted runtime evidence  
**Publication status:** Markdown draft updated with the 2026-08-12 mini-PC verdict; no PDF because genuine-client Ready/lifecycle green was not reached

## 1. Executive summary and evidence vocabulary

The branch adds a dedicated Club Match server/channel and a guarded Warfare lifecycle. The implementation is materially beyond packet stubs: it partitions room identity, enforces guild/team composition, coordinates ready/countdown/start, launches through relay with rollback, applies a server-side deadline, filters rally events by session ownership, and records at most one non-tied result. It intentionally does not implement Warfare Pet, draw resolution, rewards, rankings, or Castle ownership effects.

Claims below use these labels:

- **Observed:** bytes/UI behavior captured from an unmodified genuine client.
- **Statically inferred:** recovered from Ghidra analysis, not exercised end-to-end.
- **Unit-tested:** exercised by retained automated tests; this does not prove client interoperability.
- **Deployed:** the prior orb journal shows the code/service was loaded and listening.
- **Not yet verified:** implementation or inference without a successful corrected genuine-client run.

No evidence category is silently promoted. In particular, static `0x26F7` dispatch plus passing mapping tests is **not** a genuine green capture.

## 2. Protocol and room identity

### 2.1 Identity contract

| Phase | Server type | Client request room type | Stored/S2C room type | Wire game mode | Status |
|---|---:|---:|---:|---:|---|
| Warfare native creation | 7 | 0 | 6 | 0 | request and corrected create/list/info output observed with genuine client; unit-tested |
| Warfare Pet native creation | 7 | 0 | 7 | 1 | statically inferred; intentionally rejected as unsupported |
| Ordinary/Basic room in red baseline | ordinary server | 0 | 0 | 0 | observed genuine client |

The red run proves that native quick-create does **not** submit room type 6: C2S `0x138F` carried `00 00 00 00`. The old response exposed room type 0, so the genuine client remained on its ordinary Ready path and sent `0x1775`. After applying the final overlay and rebuilding, a fresh genuine normal-UI create again sent `0x138F` payload `00000000`; the corrected server returned `0x138A` payload `0000060000000000`, and `0x177A` plus `0x138E` likewise exposed room type 6/mode 0. Ghidra logs show that room types 6 and 7 select the Club Match callback/dispatch path and that Warfare mode values are 0 and 1 respectively. The corrected code therefore translates native `(0,0)` to stored/S2C `(6,0)` on server type 7 rather than treating 6/7 as game modes.

Primary public evidence is the safe TCP-5901 derivative under `final-evidence/protocol/`. The original room-create capture, decoded trace, and Ghidra logs remain in the checksummed historical handoff outside the repository because the original captures include authentication traffic.

### 2.2 Packet table

| Direction | Opcode | Role in current design | Evidence status |
|---|---:|---|---|
| C2S | `0x138F` | Native quick room creation; observed request has roomType/mode `0/0` | **Observed red and corrected genuine-client create** |
| S2C | `0x138A` | Create response; old implementation returned room type 0, corrected implementation returned type 6/mode 0 | **Observed red and corrected genuine-client create** |
| C2S | `0x1775` | Ordinary Ready selected when old room identity remained 0 | **Observed red** |
| C2S | `0x26F7` | Club Match ready/countdown request | **Static dispatch + implemented + unit-tested; no genuine corrected capture** |
| S2C | `0x26F8` | Ready result/countdown timestamps and designated auto-start flag, or cancellation | **Implemented + packet-tested; not genuine-green verified** |
| C2S | `0x26F9` | Designated client reports countdown expiry/start | **Implemented + coordinator-tested; not genuine-green verified** |
| S2C | `0x26FA` | Game time in seconds after relay launch | **Implemented + packet-tested; not genuine-green verified** |
| C2S | `0x26FB` | Client timer-expired report | **Implemented; server deadline is authoritative; not genuine-green verified** |
| S2C | `0x26FC` | One-byte winning side result | **Implemented + packet-tested; result semantics/end-to-end client handling not genuine-green verified** |

## 3. State and lifecycle design

1. **Create/join:** type-7 Warfare maps to room type 6/mode 0. Join and slot rules require real, opposing guild teams and keep players on their guild side. Composition-changing actions cancel an active countdown.
2. **Ready:** `0x26F7` updates ordinary room-ready visibility. A countdown starts only when the room is not running, all visible participants are ready, the implemented wire mode is selected, and team composition is valid.
3. **Countdown:** state snapshots participants as `(playerId, position, guildId)`, records generation/timestamps, and designates one player. `0x26F8` is broadcast with FILETIME timestamps; cancellation invalidates stale reports.
4. **Start claim:** only the designated player may send `0x26F9`, only after countdown expiry, only once, and only if room status and the participant snapshot still match. The room moves atomically to `StartingGame` before launch.
5. **Relay launch:** `RoomGameLauncher` creates a session from the claimed participants in slot order, sends network settings, and waits for relay success. A bounded relay deadline (default 30 seconds) prevents indefinite `StartingGame` state.
6. **Rollback:** disconnects, changed readiness/composition, relay failure/cancellation, unsupported mode, missing relay, setup exceptions, timeout, or interruption reset room status/readiness/relay flags, clear active sessions, remove the session, republish player/room state, and send cancellation/ack packets.
7. **Game deadline:** after successful start, `0x26FA` sends configured seconds. The server stores `gameEndsAt` and schedules expiry; `tryExpire(sessionId, now)` is session-bound and idempotent. Client `0x26FB` cannot authorize early completion.
8. **Scoring/result:** point and end transitions are synchronized. Natural game end and timer expiry converge on `tryRecordResult()`, so only one `0x26FC` is emitted. Cleanup cancels fireables, detaches clients, clears rally state, and removes the session.
9. **Tie:** expiry with equal sets logs and emits no result because the historical draw/tie-break byte is not proven.

### Continuation hardening visible in the current diff

- **Server-deadline expiry:** authoritative `gameEndsAt`, session-id check, scheduled expiry, and idempotent timer state.
- **Launch rollback:** bounded relay wait and restoration of room/client/session state on all observed launch failures.
- **Synchronized score/expiry transitions:** synchronized point/end handlers plus room-locked result claim prevent score/timeout double settlement.
- **Terminal-safe continuations:** queued point/set/serve packets capture the exact lifecycle generation and session, reacquire the room monitor at execution, and send only while that same lifecycle remains non-terminal and before its authoritative deadline.
- **Session-safe teardown:** disconnect, item-settings, timer-expiry, and delayed cleanup paths use one captured session ID and compare-and-clear semantics, preventing stale cleanup from erasing a replacement session.
- **Rally-event filtering:** the consumer rejects null identity fields, unknown/local-missing sessions, and events whose player/position is not a member of that session.
- **Type-partitioned session IDs:** `GameServerType * 10,000 + random[0,9999]`; ordinary and type-7 servers occupy disjoint 10,000-ID ranges, reducing cross-server RabbitMQ collision risk.

These continuation changes are present in source and have corresponding test sources, but the retained runtime deployment predates a fresh, complete green run. Treat deployment of every continuation hardening item as **not yet verified** unless a newer journal is added.

## 4. Deployment topology and boundaries

| Service/channel | Type | TCP | gRPC | Queue/boundary |
|---|---:|---:|---:|---|
| Ordinary game | 1 | 5895 | deployment-specific | ordinary sessions/rooms |
| Relay | 4 | 5896 | n/a | relay service |
| Chat / Club Castle-House | 0 / DB type 1 | 5900 | deployment-specific | Castle is a separate domain |
| Club Match | 7 | 5901 | 9901 | `q-game-club`; rally queue `match-queue-club` |

The prior journal demonstrates type 7 listening at 5901/9901 and registering handlers `0x26F7`, `0x26F9`, and `0x26FB`. Docker and SQL configuration add the dedicated listener. Session IDs are partitioned by server type as described above. Do not route Club Match through the Castle/chat service or infer that room type and server type are interchangeable.

The post-overlay package was also deployed in the genuine-client executor. Before an unplanned service restart, it registered the same handlers, listened on TCP 5901/gRPC 9901, and served the corrected room-identity run. Exact artifact paths and checksums are preserved in `partial-green-handoff.md`.

## 5. Safety and explicit non-goals

- Warfare Pet (room type 7/mode 1) is recognized but rejected in this implementation slice.
- No invented draw byte or tie-break rule; tied deadline expiry sends no `0x26FC`.
- No ordinary Basic rewards, player statistics, rankings, guild league/battle records, or club points are changed.
- No Castle acquisition, ownership (`Guild.castleOwner`), lease, weekly reset, schedule, ranking, or reward effect is connected to `0x26FC`.
- No helper injection, `REMOTE:*`, `HIJACK:*`, or direct in-process callbacks are acceptable as green evidence. Use an unmodified client and ordinary mouse/keyboard input.
- Published evidence omits fixture credentials and authentication traffic. The isolated disposable accounts were not production accounts.

## 6. Test and deployment evidence

### Retained automated evidence

- Corrected room-identity focused run: **21/21 passed**; its checksummed log remains in the historical handoff.
- Retained historical Surefire reports total **38 tests, 0 failures, 0 errors, 0 skipped** across packet (6), rules (15), state (6), coordinator (5), launcher (3), and relay-handler (3) suites.
- The reports cover serialization, room/mode mapping and rejection, team constraints, countdown designation/idempotence/composition invalidation, claimed participant selection, and relay-handler authorization.
- Exact final continuation focused run: **50 tests, 0 failures, 0 errors, 0 skipped**, with all seven selected reactor modules successful.
- Exact final full-reactor run: **57 game-server tests, 0 failures, 0 errors, 0 skipped**, with all 11 reactor modules successful.
- The final narrow concurrency review returned **CLEAN** after exact-session teardown and guarded queued-continuation fixes. Remaining non-blocking gaps are deterministic latch tests for session replacement during teardown, post-terminal queue draining, and a bounded four-way point/timeout/abort/rollback race.

The original orb could not safely complete a normal full reactor build because generated-source recompilation caused severe storage/I/O contention. Its focused run and deployed fat-jar evidence remain useful historical deployment evidence. The continuation orb has now supplied clean focused and full-reactor runs for the exact transferred dirty source tree; those build results still do not substitute for genuine-client interoperability evidence.

## 7. Runtime evidence and limitations

The genuine red evidence establishes the baseline failure and native create shape. Static Ghidra evidence establishes the likely activation contract. Unit tests establish internal mapping/state/packet behavior. Runtime service logs establish that the earlier type-7 artifact started and registered handlers.

The older file `protocol/captures/club-match-roomtype-green.pcap` is **not green protocol proof**: it contains only four startup/network packets. A later checksummed snapshot and the final mini-PC run genuinely prove corrected S2C room type 6/mode 0 for create/list/info, but they still do not prove the Ready or match lifecycle. No valid second-client join occurred, and there is no genuine `0x26F7`, `0x26F8`, `0x26F9`, relay, expiry, or `0x26FC` evidence. Client parsing of timestamps, auto-start behavior, game-time units, and result byte semantics therefore remain interoperability risks.

The earlier `The Club Match is over.` interaction remained invalid because its click missed the row. The final mini-PC run removed that ambiguity: after a fresh room appeared, the guest cursor's active yellow hotspot was visibly calibrated into the current first row before a native click. The official client reproduced `The Club Match is over.` without emitting C2S `0x138B`; the host room remained alive. This is a client-side barrier, not a server join rejection or proof of a server defect. The blank Start/End/Present/Remaining fields are countdown fields populated by `0x26F8`; `0x26FF` independently sets max play time. No room-list schedule extension was inferred or implemented.

Other limitations:

- no genuine-client Warfare Pet validation (feature intentionally unsupported);
- no proven tied-result behavior;
- no evidence for rewards or Castle effects, by design;
- no end-to-end failure-injection evidence for relay timeout/rollback;
- the checksummed corrected jar was built from the final overlay and deployed, but its post-create Ready/lifecycle behavior was not reached before the restart.
- no deterministic integration race tests for replacement-session teardown or queued continuation suppression; these are guarded in code, state-tested where practical, and passed final blocker review.
- the corrected service unexpectedly restarted at 2026-08-12 01:28:38 UTC before the corrected room-row click, invalidating the room and both client connections; subsequent severe process/I/O overload prevented safe client restoration and evidence packaging in that executor.

## 8. Exact next genuine-client green-validation checklist

1. Record the final commit/dirty-tree patch and run a clean focused reactor test/package; retain console log and fresh Surefire XML. Confirm tests for server deadline, launch rollback, synchronized settlement, rally filtering, and session-ID partitioning are included.
2. Start auth, world, chat, relay, ordinary game, and dedicated type-7 game. Confirm type 7 listens on TCP 5901/gRPC 9901 and logs registration of `0x26F7`, `0x26F9`, `0x26FB`.
3. Recreate two isolated non-GM guild fixtures without publishing their credentials. Use unmodified clients and ordinary mouse/keyboard only.
4. Log in with the disposable fixtures, select the Club Match channel, and retain a channel-selection screenshot.
5. Start a fresh PCAP before room creation. Create BasicMode through the native UI and prove C2S `0x138F` payload `00 00 00 00`.
6. Prove S2C `0x138A` room-type byte **6**, and prove room-information and room-list packets also expose room type **6** with mode **0**.
7. Join the second disposable account from the normal lobby list. Verify visible cards, opposing guild sides, and no reserved GM slot behavior.
8. Ready both clients through the native button. Prove the genuine client sends **`0x26F7`, not `0x1775`**, then decode S2C `0x26F8` countdown fields on both clients.
9. At expiry, prove only the designated client sends `0x26F9`; check duplicates/non-designated reports do not start another session.
10. Prove relay network settings, both relay connections, host/start sequence, ordinary game start, and S2C `0x26FA` with the configured duration.
11. Play at least one non-tied run. Capture each client's `0x26FB` behavior and exactly one S2C `0x26FC`; verify the winning side byte against the visible score and verify both clients return cleanly to room.
12. Run a tied deadline expiry and verify no result is sent. Preserve the warning log; do not invent a draw result.
13. Exercise one launch failure (disconnect or withheld relay connection) and verify timeout rollback restores room status/readiness and leaves no session.
14. Save the PCAP, decoded trace, service journals, fresh tests, and only decisive screenshots; checksum every file and update this report before generating the PDF.

## 9. Fixture use and restore

The complete redacted fixture/config snapshot remains in the checksummed historical handoff rather than the public evidence directory. The final mini-PC run used two isolated disposable non-GM accounts in separate guilds and restored their online/login state after validation. Do not mutate guild points, records, gold, or `castleOwner` when recreating the fixture.

## 10. Evidence integrity and review completion

The original runtime archive SHA-256 is `ffb6fcc76e6933c5d84ae145b40a803d9f19f31166d89a91187707efd4676efb`; its full 288-image payload remains external. The repository publishes only the final mini-PC report and curated final evidence. The original captures, authentication journals, fixture details, reverse-engineering logs, and complete build records remain in checksummed external handoffs.

Before final publication: add the fresh green evidence, update claim labels, record exact final source identity, regenerate checksums for any new evidence, resolve every limitation honestly, and only then render the PDF.
