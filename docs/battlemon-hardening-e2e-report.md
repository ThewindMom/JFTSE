# Battlemon hardening and native validation

Date: 2026-08-14

Base: `caa49b075253d21d05160f04d4e525ffe5b634c0`

Branch: `battlemon`

## Implemented policy

- Pet movement and autonomous behavior remain native-client owned. The server
  does not invent a second AI loop.
- The complete client EXP table drives persisted/displayed progression from
  level 1 through 250. Static executable tracing separates that value from the
  native AI-profile level: all twelve recovered pet actor-construction paths
  pass AI level 1, while `AI_PetA`–`AI_PetK` independently contain difficulty
  profiles 1–13. Level 14 is therefore admitted and EXP is not capped at 4,807.
- Every relay session containing an owned pet installs an actor-ownership
  policy. Unknown and malformed inner relay packets are dropped rather than
  forwarded. Sessions without owned pets retain the existing relay behavior.
- Skill and spell reports require a live, authorized actor and a gameplay
  endpoint. Non-gameplay endpoints and spectators cannot submit combat. A
  nonzero spell hit must consume a recent matching skill authorization; each
  authorized target can be consumed once. Server-scheduled DoT ticks do not
  re-enter this client-report gate.
- Enhanced sessions accept one point report after a relay-observed serve. The
  atomic gate suppresses duplicate reports from separate authorized endpoints;
  the packet decoder independently rejects an immediate repeated packet serial.
  This is live-session duplicate suppression, not a durable replay ledger: the
  native header serial wraps after 60 packets and `CMSGPoint` contains no rally
  nonce that could distinguish a later byte-identical legitimate result.
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
- A transactional MySQL 8.0.23 check proves `Pet.level` stores and reads 250 as
  `TINYINT UNSIGNED`; the packet regression proves that Java `Integer(250)` is
  emitted as the single wire byte `FA`.
- The final focused/full rerun and package logs are stored under
  `.amp/tmp/battlemon-hardening-e2e/logs/`.

## Native observations

- Two 1280×800 recordings were captured at 60/1 FPS for the earlier
  level-boundary experiment: 300 seconds and 18,000 frames per client. The
  level-14 pet and 4,808 EXP rendered in the unmodified client. That run's
  admission rejection was the former server policy, not evidence of a native
  level-14 incompatibility; static actor-construction evidence has superseded
  that policy. The level-14 recording has SHA-256
  `c1732bf5c175e612df322606764741bc7971847b818a37e56391a999f37fb2cb`.
- A fresh client run against the final packaged emulator rendered the level-14
  fixture at 4,808 cumulative EXP and the level-250 fixture at 1,408,515
  cumulative EXP. Both pets could be selected without disconnecting. This final
  boundary run is supported by native screenshots and packet regressions. Its
  60 FPS capture ended at the login screen before the later UI automation and
  therefore is not claimed as evidence of the pet dialogs.
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

- Hunger/energy rates and revive expiry remain compatibility decisions. The
  127 stat boundary is native-observed for the current signed-byte path. The
  former level-13 admission rule has been removed because native AI difficulty
  and displayed pet progression are separate values.
- Result de-duplication does not survive reconstruction of a match into a new
  `GameSession`, and failed completions are not durably queued for retry.
- Point replay suppression is bounded by the live rally gate and packet serial
  validation. Restart-safe or wrap-safe replay protection requires a protocol
  rally identifier that the retail `CMSGPoint` packet does not carry.
- The native PET_ITEM run proves enumeration, selection preconditions, and pet
  pickup, but not every positive item effect; those effects currently have
  automated service/packet coverage.
- No new meaning is assigned to unknown controller direction/command fields or
  unknown relay payloads.
- Index 15, retail ranking/matchmaking formulas, alternate dedicated
  topologies, mid-match reconnection, and historical balance are not claimed.
