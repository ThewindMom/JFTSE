# Battlemon hardening and native validation

Date: 2026-08-14

Base: `caa49b075253d21d05160f04d4e525ffe5b634c0`

Branch: `feature/battlemon-hardening-e2e`

## Implemented policy

- Pet movement and autonomous behavior remain native-client owned. The server
  does not invent a second AI loop.
- The complete client EXP table remains available for display, but gameplay is
  admitted only at levels 1–13. This is a chosen fail-safe boundary based on
  established `AI_Pet` profile records, not proof that level 14 caused the
  earlier ambiguous two-client failure. New pet EXP stops at 4,807; existing
  higher stored/displayed levels are not rewritten.
- Every relay session containing an owned pet installs an actor-ownership
  policy. Unknown and malformed inner relay packets are dropped rather than
  forwarded. Sessions without owned pets retain the existing relay behavior.
- Skill and spell reports require a live, authorized actor and a gameplay
  endpoint. Non-gameplay endpoints and spectators cannot submit combat. A
  nonzero spell hit must consume a recent matching skill authorization; each
  authorized target can be consumed once. Server-scheduled DoT ticks do not
  re-enter this client-report gate.
- Enhanced-match reward persistence and the durable result claim share one
  serializable transaction. A random per-session UUID identifies concurrent or
  repeated completion calls for that live session; an independent claim token
  distinguishes inserts from duplicate-key no-op updates without relying on
  connector affected-row behavior. Transaction failure rolls back the claim
  and persistence and closes participating clients so rolled-back in-memory
  mutations cannot remain usable. This provides atomic duplicate suppression,
  not durable delivery after a process restart or a failed completion.
- Lifecycle is an explicit compatibility policy: lazy whole-day decay under a
  pessimistic pet lock, hunger −1/day and energy −4/day. Expiry or zero hunger
  marks a pet dead; zero energy blocks gameplay but does not mark it dead. A
  null lifecycle cursor initializes without retroactive decay.
- PET_ITEM effects implemented: indices 1–4 (+1 stat), 5–8 (+2), 9–12 (+5),
  13 (+1 validity day), 14 (+5 `lifeMax`), 16–19 (hunger), 20–22 (energy), and
  23 (+50 hunger and energy), with species caps and a stat cap of 127. Index 15
  remains unknown and fails without consumption.
- Dedicated Battlemon deliberately remains the evidenced topology: two owners
  at 0/1 and one owned pet each at 2/3. Guardian `Allow Battlemon` remains an
  all-active-owner rule. Unsupported dedicated topologies and spectators fail
  admission rather than being extrapolated.
- Reconnect is not synthesized. Disconnecting a gameplay owner aborts and
  cleans the enhanced match, resets the room, removes relay authorization, and
  returns remaining clients to the room. A fresh match is required. A transient
  empty relay connection list does not remove actor authorization; only the
  explicit game-server cleanup message does, so reconnect cannot fail open.

## Automated verification

- Full Maven reactor regression from the final source tree: 130 game-server
  tests, 9 relay-server tests, and all other module suites passed.
- SQL integration checks against MySQL 8.0.23 prove result-claim rollback and
  concurrent duplicate behavior: the second transaction observes the first
  claim token and cannot claim the same result.
- The final focused/full rerun and package logs are stored under
  `.amp/tmp/battlemon-hardening-e2e/logs/`.

## Native observations

- Two 1280×800 recordings were captured at 60/1 FPS for the level-boundary
  policy run: 300 seconds and 18,000 frames per client. The level-14 pet and
  4,808 EXP rendered in the unmodified client, while room admission failed
  safely and the level-1 control remained healthy.
- This validates policy behavior only. It does not isolate level 14 as the
  cause of the earlier initialization failure.
- A further 1280×800, 60 FPS, six-minute native recording covers the PET_ITEM
  setup path. The unmodified client listed the fixture items, rejected feeding
  with no selected Battlemon without a database mutation, then emitted
  `CMSGPickupPet` for PotekoTest and accepted `SMSGPickupPet result=0`. Returning
  from Housing reset the Wine client before a positive item mutation, so this
  run does not claim native validation of every implemented effect. The item
  fixture was restored exactly afterward.
- The final packaged JARs were deployed together in the emulator container.
  Two isolated native clients then authenticated (`SMSGLogin result=0`) and
  entered the game server (`SMSGLoginData result=0`); both authoritative player
  rows were online on `GAME_SERVER`. A post-deployment 1280×800, 60 FPS,
  120-second recording contains 7,200 frames. This is a release smoke test, not
  additional proof of the PET_ITEM effects or match edge cases.

Raw local evidence is retained under `.amp/tmp/battlemon-hardening-e2e/`:

- `video/final4-level1-control-b.mp4` — 1280×800, 60 FPS, 18,000 frames.
- `video/final4-level14-subject-c.mp4` — 1280×800, 60 FPS, 18,000 frames.
- `video/final-all-pet-items-60fps.mp4` — 1280×800, 60 FPS, 21,600 frames,
  SHA-256 `cb45dfca2405d3cd27773eddf09beea5dbc83ed5894ed46cf379cb855af4f286`.
- `video/final-postdeploy-native-60fps.mp4` — 1280×800, 60 FPS, 7,200 frames,
  SHA-256 `bc9995779daebe41e5cb630aa3eb30f50d5f7685bacc36d78ca505e9ecec0240`.
- `db/post-all-pet-items-restore.txt` — post-run fixture and account-state
  restoration proof.
- `logs/final-source-full-maven-test.log` and
  `logs/final-postreview-package.log` — regression and release packaging from
  the exact final source tree.

## Non-claims

- Hunger/energy rates, revive expiry, level-13 admission, and stat cap are
  compatibility decisions, not recovered retail formulas.
- Result de-duplication does not survive reconstruction of a match into a new
  `GameSession`, and failed completions are not durably queued for retry.
- The native PET_ITEM run proves enumeration, selection preconditions, and pet
  pickup, but not every positive item effect; those effects currently have
  automated service/packet coverage.
- No new meaning is assigned to unknown controller direction/command fields or
  unknown relay payloads.
- Index 15, retail ranking/matchmaking formulas, alternate dedicated
  topologies, mid-match reconnection, and historical balance are not claimed.
