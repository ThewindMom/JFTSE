# Server validation continuation

Baseline: `b78741dc6a460630e5b8738727bd23e814fb44d3`. This pass is server-only. It does not treat the previous 276 passing tests as proof of unexecuted scenarios.

## Definition of done

Every inventory row must identify its runtime owner, supported or rejected configuration, executed check and result, or a concrete blocker. VERIFIED means an executed check observed the stated behavior. NOT VERIFIED means no such check ran. INCONCLUSIVE means the observation or environment could not establish the result. Resource membership is not behavioral proof.

The final gate is the affected JDK21 reactor plus explicit isolated database tests. Persistence checks must execute real mode completion, service implementations, repositories, transactions and database writes. A callback ledger or mocked persistence is not sufficient for that gate. No game/client monitor may span persistence. Packet checks establish server output, not native acceptance.

## Checklist and sequence

- [x] Read the poteto-mode Principles in full.
- [x] Confirm clean source baseline and preserve previous evidence.
- [x] Frame the code/resource inventory and link every coverage row below.
- [x] Build and verify the real database completion fixture before changing reward behavior.
- [x] Unit 1a: reproduce Basic completion/session replacement with the existing bounded regression, then align its ownership checks without changing formulas.
- [x] Unit 1b: execute Basic, Battle and Guardian completion with real player/account/pocket/stat/pet data; verify success and duplicate completion.
- [x] Unit 1c: inject failures at player, pet, statistics, inventory and completion persistence; inspect durable rows and detached in-memory state separately.
- [x] Unit 2: execute all eight bundled phases through Graal, including startup, their actual trigger branches, transitions and interrupted callbacks.
- [x] Unit 3: enumerate resource-backed legal input bounds and execute calculation/special-skill boundaries and overlapping actions.
- [x] Unit 4: run bounded concurrency, task cleanup and isolated game-relay actor-policy integration checks.
- [x] Audit the coverage/decision records, run final reactor, stop task-owned resources and deliver exact results.

No fan-out or candidate agents will be used. The user assigned this checkout to one implementation owner; the parent independently challenges coverage and reviews artifacts. Architectural exploration is deferred until a concrete unresolved design decision requires it. There is no permission to commit, push, merge or deploy from this worker.

## Initial quantified inventory

There are three completion owners and five startable mode families: ordinary Basic, ordinary Battle, ordinary Guardian, dedicated Battlemon Basic, and dedicated Battlemon Battle. The start boundary rejects dedicated Battlemon Guardian. Dedicated Battlemon requires two owner endpoints in seats0/1, each with a valid pet. Ordinary selected pets are admitted only for Guardian with its allowance enabled; synthetic ordinary Battle-with-pet policy tests do not prove a startable configuration.

Tracked SQL contains15 map rows,3 scenario rows and25 map/scenario associations. Fresh transactional import establishes273 active guardian/map rows (IDs through282), not282 rows. The original297 skill/guardian rows included100 references to already-disabled map associations283..291. Those100 rows are now retained as commented unavailable data;197 valid rows survive unchanged. Runtime scenario and guardian-skill services query repositories, not the JSON resource inventory.

| Phase key | Resource | New-pass status | Check required |
|---|---|---|---|
| 7/1 | `guardian-phase/7/1_hbPhase1.js` | PARTIAL: executed | `BundledGuardianPhaseTest`: startup, death states, transition; random branches not exhaustive |
| 7/2 | `guardian-phase/7/2_hbPhase2.js` | PARTIAL: executed | Same fixture: startup, combat hooks, death/replacement/finished and chain completion |
| 8/1 | `guardian-phase/8/1_hbPhase1.js` | PARTIAL: executed | Same fixture: startup, death states and transition |
| 8/2 | `guardian-phase/8/2_hbPhase2.js` | PARTIAL: executed | Same fixture: startup, combat hooks and chain completion |
| 10/1 | `guardian-phase/10/1_echoes_of_the_deep.js` | PARTIAL: executed | Same fixture: startup, cast authorization and transition |
| 10/2 | `guardian-phase/10/2_maelstrom_unleashed.js` | PARTIAL: executed | Same fixture: timed combat and transition |
| 10/3 | `guardian-phase/10/3_leviathans_will.js` | PARTIAL: executed | Same fixture:85s HP assignment,90s silence and transition |
| 10/4 | `guardian-phase/10/4_abyssal_reckoning.js` | PARTIAL: executed | Same fixture: actual three-tick DoT and late callbacks; `GuardianScriptPublicationTest` stale-generation controls |

Phase folder keys are not assumed to equal database map primary keys. The generated inventory must preserve that distinction.

## Rigor and first experiment

This is a high-rigor run because duplicate payouts, partial commits, stale-session writes and leaked scheduled tasks affect durable state or other participants. Each unit ends in a reproducible result before the next starts. Failed fixtures are recorded separately from genuine regressions.

The first persistence unit uses real Spring transaction interception, Hibernate repositories and MySQL in a task-owned schema. It must reach `onEnd` with real `FTPlayer` snapshots, persist EXP/gold/statistics/pet changes, and read committed rows through an independent connection. Account fields that the code does not mutate must remain unchanged rather than inventing an account reward rule.

Local raw evidence lives in `.amp/tmp/all-modes-20260905/`. The canonical decision trail is `docs/server-validation-decisions-2026-09-05.tsv`. The previous `.amp/tmp/crystal-queue-20260905/` evidence is preserved. Full database fixture cost and any missing schema/resource dependencies will be reported after bootstrap, before a large matrix run.

## Executed units

Unit 1a extended `MatchCompletionIsolationTest` to Basic. The red run executed 4 tests with 1 failure and no errors. Basic cleared replacement session8 to null. The corrected run executed the same 4 tests with no failures or errors. Evidence files are `basic-replacement-red.log` and `basic-replacement-green-2.log` in the new evidence directory. The first green attempt was a compilation failure, not a behavioral result.

Basic now uses the original seat snapshot, reward-picker generation and session-conditional publication/clear already used by Battle. Its scoring-owned finished state remains unchanged. This regression uses spectator completion and mocked persistence interception. It does not establish durable reward correctness or active-player pet publication.

### Real completion persistence and publication

`ModeCompletionDatabaseIT` uses actual mode handlers, Hibernate entities, Spring transaction interception, repositories and MySQL. Game completion predicates and already-calculated rewards are supplied by the fixture. This proves reward persistence, not resource formulas, startup or native acceptance. The schema name is guarded as `jftse_server_audit_modes`. No shared database is used.

The initial game-log failure experiment ran12 cases with6 failures. Ordinary modes retained partial durable writes; pet modes rolled back their writes but had already published success and cleared sessions. All three completion owners now claim once, persist in one transaction and publish only after the transaction returns successfully. Commit-stage injection runs in Spring `beforeCommit` after the completion callback returns. It is not a lost-commit-acknowledgment experiment.

Working snapshots avoid mutating live player entities inside the transaction. Participant rows are locked in ascending player-ID order. Committed reward-only reloads use a local snapshot revision and bounded retries instead of stale whole-player snapshots or arithmetic reward deltas. No FTPlayer monitor spans a database read.

`modes-db-two-completions-1.log` passed54 database cases and4 isolation controls. The54 cases cover three owners with and without pets, each with success, log failure, commit failure, duplicate concurrent completion, postcommit network failure, concurrent scalar refresh, full refresh, later distinct completion and later completion followed by authoritative full refresh. Assertions read independent committed rows and live snapshots. Two legitimate completions result in gold200/EXP50 and pet EXP40 from gold100/EXP10 and pet EXP0. Account AP123 remains unchanged.

`modes-db-inventory-red-2.log` added actual EXP-ring inventory service paths. It ran72 database cases and4 isolation controls with6 failures and no errors. Decrement and rollback passed. Final-row deletion committed in every mode but left the deleted ID in the live special-slot cache. Postcommit invalidation now removes consumed pocket IDs from current slots rather than replacing the whole equipment snapshot. Slot-switch and delayed-refresh checks passed in the overlap run below.

`modes-db-refresh-exhaust-red.log` ran78 database cases and4 isolation controls with6 failures and no errors. Inventory controls passed. Four consecutive conflicting reloads threw as designed, but publication failure did not close affected clients. The overlap run verifies explicit postcommit failure cleanup separately from rollback. Persisted rewards and the durable result claim remain committed.

`modes-db-ring-switch-red-2.log` ran84 database cases and4 isolation controls with6 failures and no errors. The switched valid ring survived in the database/cache, but the delayed wear packet still cleared its slot. Completion now rebuilds that packet from current live slots. Count/removal packets retain their original pocket identities.

`modes-db-overlap-green-1.log` passed96 database cases and4 isolation controls with no failures, errors or skips. It adds actual inventory-handler switching, a handler equipment read blocked before deletion that must not resurrect the ID in cache or its last emitted wear packet, bounded refresh-exhaustion cleanup, and overlapping distinct completions blocked at actual pessimistic row acquisition. Independent JDBC checks assert zero durable claims on rollback and one after commit, including publication failure. Repeated old completion does not re-award.

The completion publication lock order is client → FTPlayer. Inventory persistence/reload runs outside its packet-publication FTPlayer monitor. Reload holds FTPlayer only for revision capture/application. The wear packet constructor reads an immutable slot record and writes integers; it does not call `getPlayer()`. The inspected `Connection.sendTCP` path wraps/queues packets on its channel without entering a client monitor or loading a player. The database fixture asserts player/equipment service interception never occurs while holding the observed live snapshot monitor.

Reproduce this unit with JDK21 and the explicitly isolated schema credentials in the environment:

```sh
mvn -Dmaven.gitcommitid.skip=true -pl game-server -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=ModeCompletionDatabaseIT,MatchCompletionIsolationTest test
```

The fixture is a finite completion unit. Its subsequent137-case version includes111 completion cases and26 real reward-picker cases. Picker issuance and resource-backed level thresholds are covered below. Crash recovery after commit acknowledgment loss and general shop concurrency remain outside the proven contract.

### Bundled Guardian phase execution

`BundledGuardianPhaseTest` discovers all eight shipped JavaScript files and executes their real Graal interfaces, `PhaseScript`, `PhaseManager` and `GuardianServeTask`. The fixture supplies two players and guardians at production positions10/11, with deterministic time/randomness and a mocked skill repository. GameManager's actual broadcast path serializes packets into two captured sockets. This is server output proof, not native acceptance or resource-backed startup proof.

`eight-phase-late-timers-1.log` passes132 cases with no failures/errors/skips. These comprise55 phase scenarios,5 delayed-publication controls and72 pre-existing actor-policy controls. The phase scenarios exercise startup/combat hooks, live/all-player-death/partial-death/session-replacement/finished states, each phase's transition trigger, three complete phase chains and four late-timer states. Real transition callbacks advance7/1→7/2,8/1→8/2 and10/1→10/2→10/3→10/4. Secondary GuardianAttackTask submissions are mocked; queued callbacks are explicitly invoked with five-second executor-handoff bounds rather than sleeping through wall-clock delays.

The late Abyssal case executes the script-created DoT tasks and all three scheduled ticks for both players. Each ends at40HP from100, both sockets receive identical broadcasts, and the tick queue empties. The Leviathan timer case characterizes its existing absolute45%-HP assignment after85seconds and silence skill57 after90seconds. It does not reinterpret the assignment as additive healing. Configured Guardian healing overrides the requested percentage; Leviathan's player healing reduces20% to7HP of100. No retail rule is inferred from these server formulas.

The script-cast regression exposed a server-contract mismatch. Script-emitted skill packets bypassed the cast-report handler and never created the hit authorization that Guardian's hit handler requires. Twenty active cast-send sites in the four Atlantis scripts now use `PhaseManager.sendSkill`, which authorizes the existing15-second, once-per-target policy immediately before publication in the caller-owned game scope. Packet bytes retain attacker/target/client index. The test decodes actor11,target0,index5 and synthesizes skill-ID6 reports through the real handler. Wrong actor/target/skill, duplicate, expiry and finished publication are rejected. A control executes normal GuardianAttackTask assignment, decodes its index, invokes PlayerUseSkillHandler, and reaches the same hit handler. These synthetic reports do not prove which packets the native client emits.

`eight-phase-stale-phase-red.log` ran125 cases with one genuine failure and no errors. An already-dequeued Abyssal callback published HP77 after its PhaseScript had been replaced. `EventHandler.offerJS` now captures phase-manager/script identity and rechecks it under the existing game scope. `eight-phase-stale-phase-green.log` passes the same125. This uses no game-monitor acquisition from a script worker while its caller waits. The finite controls retain live/cancelled/finished/replaced-session behavior.

Reproduce the phase unit:

```sh
mvn -Dmaven.gitcommitid.skip=true -pl game-server -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=BundledGuardianPhaseTest,GuardianScriptPublicationTest,BattlemonActorPolicyTest test
```

The phase inventory table records the earlier execution milestone. The final65-case phase fixture adds controlled RNG branches, phase-local DoT interruption and disconnected/replaced guests. The scheduler and resource units below close those separate finite checks. `continuation-reactor-1.log` is an earlier successful game/relay reactor check without the opt-in database IT environment.

### Seed, admission and resource HP envelope

`GuardianSeedDatabaseIT` imports current tracked seed SQL transactionally with real foreign keys and stop-on-error. `fresh-seed-red.log` failed first at skill255 referencing missing map-association291. The fix disables only100 skill rows dependent on already-disabled associations283..291. The test asserts exact197 surviving relationships, probabilities and IDs plus all25 scenario links. No shared database was modified.

All native map IDs0..14 plus−1/15 execute the actual Hibernate availability repository. Snow Moon/native4 has no available Guardian actors and is rejected before room status/session/relay initialization. Map3/native2 remains available despite one empty boss link. `RoomStartGamePacketHandlerTest` covers no pet, one pet, two pets, invalid configuration and selection changed during availability lookup. `guardian-admission-red-2.log` has29 cases/4 failures; `guardian-admission-green-3.log` passes29. Earlier compilation failures are not regression evidence.

`GuardianResourceDatabaseIT` uses isolated DB rows:79 Guardian/boss definitions,40 enchant levels, skill definitions, equipment maxima and actual player/pet constructors. A conservative envelope combines all79 actors with1..4players, normal/hard and ordinary/advanced scaling. Highest Guardian HP is24900. Twelve equipment slots each assigned the observed maximum100HP, levels1/60/127 and Guardian couple bonus produce conservative human max2131. Real pet creation IDs1..9 and EXP→250 retain HP180..280. These bounds cover tested HP/heal arithmetic, not every damage formula or valid equipment combination.

Resource skills10/20 carry positive damage1. The real hit handler previously emitted HP101 at max100. `positive-one-hp-red.log` has103 cases/6 failures. Three combat mutation overloads now cap authoritative HP before serialization. `positive-one-hp-green.log` passes120 cases:91 actor-policy and29 admission. Added controls cover below-cap+1, dead targets rejected without implicit revive/publication, and blocked-first-publication+1 followed by−1 at cap: no later mutation passes the first send, both recipients end at99 authoritative HP. The existing negative-damage controls remain. No guessed retail meaning of these skills is introduced.

### Real scheduler unit

`MatchSchedulerTest` executes the actual virtual-thread executor and scheduled executor. The red run has3 cases,1 failure and1 deliberately injected script error:256 cancelled one-hour tasks remained queued, and a throwing inline event lost the other match's drained event. The scheduler now removes cancelled entries immediately; EventHandler logs each failed runtime callback and continues unrelated events without retrying the failed action. `scheduler-green.log` passes the scheduler and actual phase tests. A256-event async batch completes exactly once,256 cancelled events never execute, queues empty and owned executors terminate within five seconds. This is bounded executor evidence, not production-load certification.

## Final finite coverage ledger

These rows supersede the earlier PARTIAL phase milestone. Each VERIFIED label applies to the listed experiment, not an unrestricted cross-product of every input and timing.

| Inventory / invariant | Executed owner and evidence | Result / boundary |
|---|---|---|
| Five supported mode families; Guardian optional pets; dedicated Guardian rejection | `RoomStartGamePacketHandlerTest`, final reactor; `ModeCompletionDatabaseIT` | VERIFIED admission and three completion owners with/without pets. Ordinary Battle-with-pet fixture characterizes persistence, not an admitted configuration. |
| All25 map/scenario links, native map IDs0..14 and invalid−1/15 | `GuardianSeedDatabaseIT`; admission fixture | VERIFIED exact FK associations and repository availability. Snow Moon has no available actors and rejects before running/spending. No invented missing guardians. |
| Player counts1..4, normal/hard, ordinary/advanced HP scaling | `GuardianResourceDatabaseIT` | VERIFIED constructor/calculation envelope over79 real definitions. This is not65×79×all-phases startup execution. |
| Phases7/1 and8/1 | `BundledGuardianPhaseTest`, `cast-recipient-green.log` | VERIFIED startup, death/partial death, lifecycle interruption, phase transitions and full chains. |
| Phases7/2 and8/2 | Same65-case fixture | VERIFIED HP80/45 subparts,122-second enrage, combat hooks and chain completion. |
| Phases10/1..4 | Same65-case fixture | VERIFIED all four startup/transition paths, timed casts, healing/status/shield paths, late timers and three actual DoT ticks. Leviathan revive true/false controls execute. |
| Distinct random branches | Same65-case fixture with RNG0.1/0.9 | VERIFIED silence/polymorph branch, first/last target selection and differing attack interval. Not every random floating-point value. |
| Phase-local DoT interruption | `BattlemonActorPolicyTest.scriptedDotCommitsClampedDeathButCannotFollowReplacementSession`; phase fixture | VERIFIED captured PhaseManager/PhaseScript, ended phase, stage/session replacement, clamped death and live tick control. |
| Special/status/heal/negative damage and sandglass branches | `BattlemonActorPolicyTest.specialDamageAndStatusBranchesPreserveHpAndAnimationContract`, `sandglassExtendsOnlyAuthorizedExistingMatchTimer`; resource fixture | VERIFIED reachable branch inputs, timer present/absent/unauthorized, all resource negative-damage skills in both actor directions with buffs on/off. |
| Level thresholds | `GuardianResourceDatabaseIT` | VERIFIED all59 resource thresholds below/at/above through actual LevelService, multilevel progression and level60 cap. |
| Real completion SQL and snapshots | `ModeCompletionDatabaseIT` | VERIFIED111 completion cases. Flushed player/stat/pet failures, log and beforeCommit faults, concurrent duplicate and distinct completions, refresh/replacement/ring races and postcommit failure cleanup. |
| Real random picker issuance | Same database fixture | VERIFIED26 cases. Manual/timeout × new pocket/existing stack, flushed item/belongings rollback, retry, concurrent manual/timeout claim, existing durable claim, replacement registry and postcommit network failure. |
| HP commit and publication order | Actor fixture blocked-first-send/two-writer tests | VERIFIED no later HP commit overtakes the blocked send; both recipients end at authoritative HP. |
| Stale guest packets and lock order | `MatchPublicationTest`, actor fixture, phase fixture | VERIFIED GameManager combat broadcasts, phase/script output and PlayerUseSkillHandler forwarding. Disconnected/replaced guests reject; live recipients receive; cast excludes sender. Unrelated lobby/chat output is not migrated. |
| Scheduler handoff/cancellation/load | `MatchSchedulerTest`, `GuardianScriptPublicationTest` | VERIFIED actual executor handoff, bounded256-event batch, cancellation queue removal and independent callback survival after error. |
| Game→relay actor-policy contract | `RelayPolicyProducerIT` + `RelayPolicyConsumerIT`, `game-producer-wire-2.log` / `relay-consumer-wire-2.log` | VERIFIED two separate JVMs over isolated Rabbit, ordinary/optional-pet/dedicated actor maps and all three removals. Six acknowledged RPCs. No native transport claim. |

### Reward-picker transaction policy

The initial real picker run executed18 cases with14 failures and no errors. Success controls for both pocket shapes passed. The manual and timeout paths now share one claim/grant operation and the real `MatchResultService.executeOnce` transaction. Each original slot has a stable UUID. Pending claim and durable commit are distinct; rollback releases only the matching pending slot/position, while success or an existing durable claim never releases for reaward. Selected product/quantity/position remain bound to the original operation. Delayed old completion cannot remove a replacement registry.

`complete-db-final-1.log` passed146 checks, comprising137 database cases,4 completion-isolation checks and5 claim-isolation checks. The137 database cases contain111 completion and26 picker cases. No success selection/inventory packet precedes commit. Network failure after commit retains the grant and claim. UUIDs are not persisted as restart-restorable reward-generation identities. Process restart and lost commit acknowledgment are not proven exactly-once delivery scenarios.

### Publication lock proof

`recipient-lock-red-2.log` reproduced a two-game overlapping-roster deadlock. Taking recipient FTClient monitors beneath the sender monitor was not safe. The fix uses a private leaf membership lock with immutable session/room/generation snapshots. Session replacement, room replacement and expected-session clear update that same leaf. Conditional enqueue does not acquire game/client/FTPlayer monitors or call JDBC. Netty's inspected enqueue path only checks channel state, wraps packets and calls writeAndFlush.

`MatchPublicationTest` executes a blocked enqueue and shows membership replacement waits; subsequent old-generation sends reject. The two-game actor regression includes stale overlapping membership and completes within its bound. The original HP no-overtake regression remains unchanged. `cast-recipient-red.log` ran5 cases with2 failures and0 errors because direct skill forwarding bypassed this owner. `cast-recipient-green.log` passes205 checks, comprising128 actor-policy,65 bundled-phase,5 script-publication,4 membership-publication and3 scheduler checks. Skill forwarding now uses the same conditional-send API with captured original session and room. The direct combat iteration audit found no other peer cast-forwarding loop; score/serve/ball/HP routes use GameManager. Inventory and lobby/chat routes are outside this combat-recipient scope.

### Reproduction and limitations

Use JDK21 and Maven3.9. The default reactor excludes opt-in `*IT` classes; do not count those as executed by `package`.

```sh
mvn -Dmaven.gitcommitid.skip=true -pl game-server,relay-server -am package
mvn -Dmaven.gitcommitid.skip=true -pl game-server -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=ModeCompletionDatabaseIT,MatchCompletionIsolationTest,RewardClaimIsolationTest test
mvn -Dmaven.gitcommitid.skip=true -pl game-server -am \
  -Dsurefire.failIfNoSpecifiedTests=false -Dtest=GuardianSeedDatabaseIT test
mvn -Dmaven.gitcommitid.skip=true -pl game-server -am \
  -Dsurefire.failIfNoSpecifiedTests=false -Dtest=GuardianResourceDatabaseIT test
```

Supply `JFTSE_AUDIT_JDBC_URL`, `JFTSE_AUDIT_JDBC_USER` and `JFTSE_AUDIT_JDBC_PASSWORD` only for an isolated MySQL instance. Fixtures require their exact guarded schemas `jftse_server_audit_modes`, `jftse_server_audit_seed` and `jftse_server_audit_resources`, respectively. The seed fixture requires empty scenario/link tables with real FK constraints and imported referenced base entities. It imports tracked statements transactionally and rolls them back. The resource fixture requires imported Guardian, BossGuardian, Skill, ItemEnchantLevel, LevelExp and ItemPart data. The completion fixture builds its own Hibernate schema.

For the broker experiments, install reactor dependencies with `-DskipTests install`, then run each pair of IT classes concurrently in their separate modules without `-am`. Supply `JFTSE_AUDIT_RABBIT_HOST=jftse-audit-rabbit`, user and password for a task-owned broker. Each process must report its own green result.

```sh
# Pair 1: game actor-policy RPCs to relay, six acknowledgments.
mvn -Dmaven.gitcommitid.skip=true -pl relay-server -Dtest=RelayPolicyConsumerIT test
mvn -Dmaven.gitcommitid.skip=true -pl game-server -Dtest=RelayPolicyProducerIT test
# Pair 2: relay ball messages to game, seven acknowledged observations.
mvn -Dmaven.gitcommitid.skip=true -pl game-server -Dtest=MatchBallWireConsumerIT test
mvn -Dmaven.gitcommitid.skip=true -pl relay-server -Dtest=MatchBallWireProducerIT test
```

### Reverse ball wire and concurrent cleanup

`MatchBallWireProducerIT` runs the actual relay producer and virtual-thread executor. `MatchBallWireConsumerIT` receives through real Rabbit/Jackson into the actual game consumer in a separate JVM. Seven messages exercise Basic serve/return ace, Battle serve/non-ace rally, player101/position0 versus player102/position1, duplicate stroke and late messages after each session is cleared and removed. Each serve opens one accepted point; duplicate stroke increments the player's statistic twice but does not increment the same-position rally transition twice. That is the existing policy, not transport deduplication. Match scoring and native packet emission are not synthesized as broker evidence.

`ball-wire-game-server-red-3.log` has1 failure and0 errors; late traffic recreated cleared statistics. The matching relay producer passed. Earlier fixture errors are excluded. An absent-session ingress check fixed sequential arrivals, but `ball-cleanup-red.log` then ran3 tests with1 failure and0 errors. Cleanup completed while an accepted stroke was blocked before stats insertion; the resumed stroke recreated an orphan statistic.

The existing concurrent rally map's per-session `compute` now owns both rally/stat mutation and their cleanup. Original session identity and completion claim are rechecked inside that operation. The rally updater receives the captured session instead of resolving a replacement. No JDBC or game/client monitor runs inside compute; event callbacks run afterward. No bundled `MP_BALL_HIT` subscriber exists that reinserts state or touches replacement matches. The deterministic regression retains a live unrelated session and asserts its stats survive. This does not invent a generation identifier absent from the wire or promise distinction between a stale message and a newly reused numeric session ID.

Both `ball-wire-game-server-final.log` and `ball-wire-relay-server-final.log` pass1 check with0 failures/errors/skips. The full reactor below also executes all4 cleanup/serve consumer controls.

The final abort review added `realAbortCleanupRejectsStrokeBetweenClearAndRegistryRemoval`, which executes actual GameManager cleanup and registry removal with a hook immediately after real stats clearing. With `completionHandled=false`, a stroke in that gap recreated orphan stats. `abort-clear-window-red.log` ran4 cases with1 failure and0 errors. Cleanup now conditionally removes the original session from the registry before clearing its stats; failed identity removal never clears replacement stats. The same regression passes in the final reactor and preserves an unrelated session's statistics. No completion flag or tombstone is introduced.

Remaining evidence boundaries are native packet emission/retail semantics, full production-load certification, restart restoration and ambiguous commit acknowledgments. They are not marked VERIFIED. Database resource-envelope enumeration is not proof of every simultaneous equipment/configuration/phase cross-product. No shared database, emulator, native client, deployment or branch merge was used.

### Final gates and artifact identities

`mvn -Dmaven.gitcommitid.skip=true package` completed the entire reactor, including auth/game/chat/relay/ac, at2026-09-05T12:38:08Z. `final-all-services-reactor-3.log` records BUILD SUCCESS and418 executed tests:388 game,1 chat and29 relay. Other modules compiled/packaged with no tests present. There were0 failures/errors/skips.

Separate real database gates are `final-completion-db.log` (146 checks, including137 actual database cases), `final-seed-db.log` (1 aggregate FK/import check) and `final-resources-db.log` (1 aggregate resource check). All passed without failures/errors/skips. These aggregate and overlapping targeted counts must not be summed into a claim of distinct scenarios.

SHA-256 of the packaged server artifacts:

| Artifact | SHA-256 |
|---|---|
| `auth-server/target/auth-server.jar` | `a8a43b9c0bff1179e9fac3454f179f441000d0a10c079e6ef6b7a681489f6d67` |
| `game-server/target/game-server.jar` | `a154f8c94a860889c53bb6c88d8f2359cf210bb93b831c12ee2d921ee89abb6e` |
| `chat-server/target/chat-server.jar` | `f2270c2fb0fa399aab3e3a4e14a33d2430db70ca44248233af6681f3b481255a` |
| `relay-server/target/relay-server.jar` | `230c055db364872faa4014b9a0b033ee6d38e113733919b27b2f30a0dd9f9b0e` |
| `ac-server/target/ac-server.jar` | `452c48e90af567b3f9c391c625c773af5dff385c1cc07b3aa97ade5590815c58` |

Both actor-policy processes passed again in `policy-wire-game-server-final.log` and `policy-wire-relay-server-final.log`, each1 check with0 failures/errors/skips. Final `sha256sum -c` passed for all five jars. `git diff --check` is clean.

The task-owned Rabbit and MySQL containers were stopped and inspected as `running=false`, `status=exited`. Their retained data is not published or deleted; no shared service was stopped. Local raw logs, cleanup output, exact48-file delivery manifest and hash file remain under `.amp/tmp/all-modes-20260905/` and must not be staged. The two docs in this directory and task-owned source/tests/SQL are stable for parent review. The worker did not commit or push.
