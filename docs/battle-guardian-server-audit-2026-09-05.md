# Battle and Guardian server audit, 2026-09-05

This is a server-side correctness audit, not a claim that Battlemon or Guardian is fully reverse engineered. It starts from the `battlemon` branch and distinguishes ordinary Battle, ordinary Guardian, and dedicated Battlemon rooms. No shared emulator or database was changed. Native combat walkthroughs are outside this delivery.

## Evidence boundaries

The tests run production Java handlers, combat calculations, event dispatch, and one bundled Guardian phase through the real Graal script executor. TCP endpoints and most persistence services are test doubles. An explicit integration test also runs the real transactional `MatchResultServiceImpl` against isolated MySQL 8.0.23. A captured outgoing packet proves the server's selected recipients and payload, not a native client's rendering or acceptance.

The HP packet is **0x184E**, `PacketOperations.S2CMatchplayDamageToPlayer`. An early work-in-progress note incorrectly called it 0x18EC. Cast is 0x18E9, hit report is 0x22F1, crystal pickup is 0x18E7, and crystal grant is 0x18E8. Spawn and despawn are 0x332C and 0x332D.

Prior native reports establish selection, level rendering, and login smoke checks. They do not establish every item effect, multiplayer collision reporting, or retail combat formulas. In particular, unknown 0x3332 payload semantics remain unresolved.

## Authority and state matrix

| State | Current server contract | Executable evidence and limits |
|---|---|---|
| Mode ownership | Ordinary Battle without pets bypasses enhanced cast and hit grants. All Guardian games use enhanced combat checks. Pet Battle and dedicated Battlemon use enhanced actors. Dedicated room type 2 modes 0 and 1 must not be conflated with ordinary Guardian room type 0 mode 2. | `BattlemonActorPolicyTest` tests representative ordinary, pet, and dedicated routes. |
| Actors and recipients | Pets are actor seats, not additional TCP clients. Cast forwarding excludes the reporting connection. HP publication includes the reporter and session peers. Guardian host reports can represent live players and guardians. Nonhost reports are constrained by ownership. | Five damage route cases use real combat and real `GameManager` broadcast, decode 0x184E, and compare reporter and peer payloads. |
| Crystal generation | Server task creates a live crystal. Numeric IDs wrap through 0..100. Spawn/despawn mutation, publication, and follow-up creation share the owning game then client monitor. | Blocked deque mutation versus the real session setter: publication stays with the original match and its follow-up cannot inherit the replacement. Finished games do not mutate or schedule. Exhaustive random-drop distribution and every stage transition are not asserted. |
| Pickup and inventory | Live crystal removal is the successful claim. Queue capacity is two, evicting the oldest on a third pickup. | Duplicate pickup, forced lost removal, capacity, invalid ID, and exact-object despawn checks. A stale despawn cannot delete a later spawn with the same numeric ID. |
| Crystal cast and swap | Cast validates the head ID and skill index before removing it. Swap requires two entries before rotating. | Three original failures: invalid ID, invalid index, and one-entry swap lost a crystal. Rejection now leaves the queue unchanged and emits no accepted-action broadcast. |
| Cast-to-hit order | 0x18E9 runs asynchronously behind a per-connection barrier. That connection cannot drain a following 0x22F1 until the cast completes. | Real blocked worker test, 100 competing update callers, handler failure, submission failure, and deferred disconnect. Another connection progresses while the first worker is blocked. This does not solve ordering across different reporters. |
| Skill indexing | Client index is ID minus one, even when IDs are sparse. Repository iteration order is not an index contract. | Scrambled IDs 3 and 1, missing ID 2, negative indexes, large IDs, and unknown IDs. No gap compaction. |
| Hit authorization | Enhanced hits consume a 15-second grant per actor and skill, once per target. Recast replaces the previous grant. AoE permits different targets. | `GameSessionTest` covers recast, target restriction, duplicate consumption, expiry boundary, and parallel consumption. This characterizes current policy, not retail multihit or native DoT intent. |
| HP mutation and publication | Accepted hit, scripted DoT, and Guardian special cast use the owning game monitor for mutation and publication. Atomic updates do not silently lose a competing delta. | Two 10,000-update tests plus a deterministic blocked-first-send test. While the first send is blocked, the second writer cannot commit. Both peers receive HP99 then HP98 and authoritative HP ends at98. |
| Status-only ball report | A Guardian-target no-effect ball report reads Guardian HP, not the player-state table. | Regression sends an authorized report for actor10 and decodes unchanged HP100 in 0x184E. Previously the player lookup rejected it and published nothing. |
| Finished-match traffic | Cast and hit handlers reject a finished game before skill lookup. Deferred cast forwarding rechecks finished state. | Battle and Guardian rejection cases. This does not change the policy for hits after a revival within a still-running match. |
| Scripted DoT | `ApplyDoTTask` is server-owned, distinct from native spell reports. It binds the original session, game, boss-stage flag, and player object. Stored HP clamps at zero and marks player death. | Overkill and replacement-session checks. The task registers delayed ticks for cancellation and stops after death. Full retail DoT semantics are not inferred from this task. |
| Script HP writes | Guardian phase updates and scoped delayed callbacks share the publication monitor on the caller side of the script handoff. | Real Graal execution of `10/4_abyssal_reckoning.js` with bounded timeout, starting through the scoped `GuardianServeTask`. Live callback publishes currentHP77 instead of staleHP100. Replacement, cancellation, and finished cases publish nothing. |
| Guardian loot and completion | Unique loot claim is reserved under the game monitor. Completion and boss transition wait for all reserved loot updates; JDBC runs outside the monitor. | Blocked final loot versus second accepted hit, direct finish, lethal scripted DoT, and a second concurrent loot update. Completion observes50 or100, never an unsettled pot. |
| Loot failure | A failed loot operation aborts the owning match rather than awarding incomplete success. Deferred continuation rejection also aborts. | No-second-hit failure, failure with a waiting completion, replacement before failure, and executor rejection. Pending count drains, reward completion is suppressed, original endpoints close, and ordinary-session cleanup removes the original session without clearing a replacement. |
| Boss and deferred Guardian work | Boss lookup cannot publish or create tasks for a replacement session. Serve, attack, and defeat-timer tasks bind their original session and reject finished games. Attack tasks retain exact original guardian objects. | Blocked boss lookup plus replacement; live, replacement, and finished routes for all three task types. Death before execution, a new guardian at the same position, and death during blocked skill lookup cannot grant or reschedule an attack. These tests do not enumerate every advanced phase transition. |
| Completion | Delayed finish captures its original session. Battle and Guardian handlers bind their own game, preserve original participant seats, and compare-and-clear only that session. Result UI is separate from persistence. | Blocked result transaction followed by replacement no longer clears session8. Persistence is not placed inside the client or game monitor. The focused transaction test uses a spectator to isolate completion callbacks, not a real database commit. |
| Delayed room return | Packet event captures match generation and exact room. Clearing the original match does not invalidate its normal return packet, but starting another match does. | Next-match ABA test: old match ends, another match starts and ends, old return event cannot send. |
| Reward selection | Manual and timeout selection share an atomic one-per-player claim across all slots. Timeout captures exact reward, room, seat, and match generation. Removal is conditional on the reward object. | Duplicate manual picks, replacement reward, replacement room with the same ID, replaced seat, and manual-pick versus timeout with blocked pocket persistence. The winning path increments once and calls save once. Retry policy after persistence failure is unchanged. |

## Current-server calculations

These are emulator formulas. `trunc` means Java conversion toward zero. Defaults come from `game-server/server.conf`; changing database configuration changes the scales. Formula owners are `BattleUtils`, `PlayerCombatSystem`, `GuardianCombatSystem`, and `ElementalEfficiencyCalculator`.

1. Human base HP is 200 + 5 × (level − 1), then equipment AddHp is added. Human combat stats combine room base, equipped contributions, and enchant contributions. Guardian couple bonus adds integer total / 20 after the sum. Pet actors use `PetView` snapshots, not copied owner stats.
2. For ordinary negative skill damage b with magnitude other than1: d = b − trunc(STR × 0.35). STR buff subtracts trunc(abs(d) × 0.20) from that result.
3. Defense r = trunc(STA × 0.30). DEF buff adds trunc(abs(d) × 0.20) to r. If r > abs(d), damage becomes −1. Otherwise r is added to d. Equality therefore yields zero damage.
4. Elemental adjustment follows defense, except the −1 sentinel. Offensive element must match the skill element. Each defensive relationship selects maximum `getMaxEfficiency`, but its contribution uses actual efficiency / 32 clamped to0..1. PLAYER modifiers are +26, −15, −20; GUARDIAN modifiers are +16, −5, −10. Offensive efficiency and the three contributions are summed. The final delta is trunc(d × (1 + efficiency / 100)).
5. HP arithmetic retains the existing short narrowing before the lower clamp. The concurrency fix changes retry behavior, not the arithmetic width. Synthetic short overflow is not evidence that production resource stats reach it.
6. Player ball damage magnitude is max(20, 10 + trunc(WIL × 0.52) + optional20% buff of the prebuff amount). Against a Guardian, the extra term is trunc(targetMaxHP × playerWIL / 10000). Serving actor4 uses existing route-specific percentage rules; these are not assumed to be interchangeable.
7. Heal computes short(maxHP × percentage / 100f), adds using the existing short narrowing, then caps at maximum. Guardian healing overrides the requested percentage with the game's configured percentage.
8. Skill5 is revive, skill38 extends the breath timer by60 seconds, and skill64 uses animation3. Skills15 and63 retain their special apply-effect handling. Raw `applySkillEffect` byte0 means true.

`CombatCalculationContractTest` executes truncation, buff ordering, strict defense threshold, ball minimum, negative and capped elemental efficiency, fractional healing, healing cap, Guardian percentage override, level HP, and concurrent damage. It is a boundary suite, not an enumeration of every database equipment combination.

## Script routes and lock order

The 13 resource `offerJS` calls are in phases7/2 and8/2 (four each), phase10/1, phase10/2, phase10/3 (one each), and phase10/4 (two). They all pass the owning connection. `PhaseManager` also scopes its delayed phase transition.

Direct script HP setters occur in phases7/2 and8/2 (two each), phase10/2 (one), phase10/3 (two), and phase10/4 (two). They run in phase update. The delayed phase10/4 full-HP packet is now constructed at execution rather than when scheduled.

The caller acquires game then `PhaseScript` lock, submits script work, and waits for it. The script worker does not acquire the caller's game monitor. Delayed scoped callbacks recheck cancellation, original session, exact game, and finished state after acquiring the game monitor. This avoids treating prequeue validation as permission to mutate later.

Guardian loot is claimed once under the publication monitor and increments a pending counter before releasing it. Finish and boss-transition continuations wait until that counter reaches zero. JDBC, asynchronous continuation dispatch, and reward persistence run outside the monitor. Failed loot marks the game finished before draining continuations, so neither an isolated failed final kill nor a waiting second hit can award an incomplete pot. Executor rejection during continuation dispatch is a fail-closed abort, not a retry policy. Session cleanup cancels fireables and scheduled futures, removes only the expected session, and preserves a replacement's state.

The handler still performs loot JDBC on the game update path, so this audit does not claim that every server update is nonblocking. The per-connection barrier specifically removes the additional global stall introduced by making quickslot persistence synchronous.

`MatchResultDatabaseIT` uses the real Spring transaction interceptor, JPA transaction manager, Hibernate, and `MatchResultServiceImpl` with an isolated `MatchResult` table and a small callback ledger. A competing claim waits for the first transaction and returns false after commit. A failed callback rolls back its claim and ledger insert; a later retry of that result ID succeeds. The test removes only its own UUID rows. This proves coordinator claim/rollback behavior, not rollback of every in-memory player snapshot or the full account/pocket schema.

## Reproduction

The affected reactor command uses JDK21:

```sh
mvn -Dmaven.gitcommitid.skip=true \
  -pl game-server,relay-server,auth-server,chat-server,ac-server -am test package
```

The worktree environment has no Java or Maven on PATH. Verification uses `maven:3.9-eclipse-temurin-21` with the checkout and Maven cache mounted. Git metadata collection is skipped because the worktree's Git indirection is not supported by that plugin in the container. No source workaround changes build metadata.

Focused selection:

```sh
mvn -Dmaven.gitcommitid.skip=true -pl game-server -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=BattlemonActorPolicyTest,GameSessionTest,CombatPacketOrderingTest,CombatCalculationContractTest,SkillServiceLookupTest,GuardianScriptPublicationTest,MatchCompletionIsolationTest,RewardClaimIsolationTest test
```

For the explicit database check, provide `JFTSE_AUDIT_JDBC_URL`, `JFTSE_AUDIT_JDBC_USER`, and `JFTSE_AUDIT_JDBC_PASSWORD` through the environment, targeting a disposable schema named `jftse_server_audit_tx`. Do not target the shared emulator database. Then run the same focused command with `-Dtest=MatchResultDatabaseIT`. It is intentionally not included by default Surefire `*Test` discovery.

The final affected reactor succeeded at 2026-09-05 07:18:13 UTC: **276 tests, zero failures, errors, or skips**. Game-server ran246 (including the explicit database integration test), chat-server1, and relay-server29. Auth-server and ac-server also packaged successfully. This run used the affected-reactor command above with `-Dsurefire.failIfNoSpecifiedTests=false '-Dtest=*Test,MatchResultDatabaseIT'` and the isolated database environment. Both audit tables had zero residual rows afterward.

The verified game-server.jar SHA-256 is `8755dc1416d0602b040f425930518798ceb81f6ff152fdb1974ba1b759c489e5`. Local iteration logs and the other jar hashes are not tracked: the logs include genuine red runs, fixture errors, and subsequent green runs. They are not a deployment artifact.

## Remaining evidence gaps

- Native reporter selection, duplicate collision emission, legitimate repeated-hit spells, cross-reporter cast ordering, and retail cooldown, buff-duration, stacking, and resistance rules need client or authoritative retail evidence.
- No generic server MP or buff-duration collection was found in the inspected battle state. That absence does not establish missing retail server ownership. Server-scripted DoT does exist and is tested separately.
- Current once-per-target hit grants remain a compatibility policy. No guessed generation or retail multihit rule was introduced for death, revival, or overlapping casts.
- The coordinator's real database claim/rollback is tested, but rollback effects on in-memory player snapshots, full account/pocket persistence, every advanced phase transition, every equipment-resource combination, and native receipt are not proven by this suite.
- Earlier native evidence and static reports remain useful but must not be read as full multiplayer completion. Optional pet ownership and later authority commits supersede older all-owners-required wording.
