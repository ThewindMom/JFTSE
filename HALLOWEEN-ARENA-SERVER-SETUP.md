# Halloween Arena implementation status and server setup

This report records what the `feat/halloween-2026-arena` branch implements for
the Halloween Arena boss. Arena is client map 6 and database map 7. The phase
script runs only when Arena uses scenario 3, `BOSS_BATTLE_V2`. The encounter
was tested on a test server with a cloned database. The final seal-isolation
fix was tested separately as described below. There is no live deployment.

## Current status

| Area | Status | Details |
| --- | --- | --- |
| Boss phase script | Done | The script contains the encounter mechanics listed below. |
| Scenario and roster data | Done in the test database | Apply the scenario-3 transaction in this report. |
| Hard mode and random mode | Done | Client map 6 is on the deny list of `HardModeCommand` and `RandomModeCommand`, like maps 7, 8 and 10. |
| Equipped Heal and Shield | Done | Hidden from the HUD for the whole Arena match and rejected by the server. A chat notice at match start explains it. Crystal Heal, Shield and Revive still work. |
| Crystal pool | Done | Boss stage only: Revive 35% with a dead teammate, otherwise Heal 70% and Shield 30%. The guardian stage keeps the standard pool. |
| Halloween Coin | Done | One Pumpkin (material 264) per active player after a won boss stage. The item choice needs owner confirmation. |
| Match completion | Same as other boss scripts | The phase ends when every guardian is dead, like `7/2_hbPhase2.js`. The server finishes the match on the next reported hit, as it does for the Monslava boss scripts. |
| Server tests | Passed | Protocol bots played 1 to 4 player matches and a rules match on the branch server. Broken variants of the script and the server failed. See the verification section. |
| Native-client validation | Partly done | See the verification section for what the official client showed. |
| Guardian health | Done in the test database | Health comes from Arena-only guardian rows, like every other boss, so the client health bars match the server. |

## Phase script

`game-server/src/main/resources/scripts/guardian-phase/6/1_halloween_arena.js`
provides:

- Hell Blood in boss position 10.
- Two Witch-family guardians in positions 11 and 12, with normalized stats and
  attack skills.
- Health from the database rows, as in the Monslava and Atlantis scripts. The
  script does not change max health.
- Hell Blood immunity while either Witch lives.
- Independent Witch revival 20 seconds after death, only while Hell Blood lives.
- Small Inferno every 5 seconds. Each wave has 1, 2 or 3 rounds, 900 ms apart,
  as the number of living Witches falls from 2 to 0. Every round hits each
  living player once.
- Big Inferno every 10, 7.5 or 5 seconds on one random living player, as the
  number of living Witches falls. There is no warning line.
- Three intro lines followed by one Chicken cast on each living player.
- Per-player Trick-or-Treat outcomes every 30 seconds: Chicken, Confused, 5%
  healing and Movement Speed.
- A 5% max-health heal for each living player after a won rally.
- The phase ends when every guardian is dead, the same rule as the other boss
  scripts.

Every Inferno, trick and heal reads the living players at the moment it fires.
This is the same pattern as the Atlantis `castGuardianSkill` in
`10/1_echoes_of_the_deep.js`, which filters players with health above 0 before
it picks a silence or polymorph target. A player who dies during a Small
Inferno wave is skipped from the next round on. A revived player is included
again from the next round on.

## Arena server rules

The rules that the script cannot enforce are in
`game-server/src/main/java/com/jftse/emulator/server/core/matchplay/guardian/HalloweenArenaRules.java`.
They apply only when the match is Guardian mode on client map 6.

### Hard mode and random mode

`HardModeCommand` and `RandomModeCommand` use the deny list
`Arrays.asList(1, 2, 4, 6, 7, 8, 10)`. The room answers "Hard mode is not
allowed on this map" or "Random mode is not allowed on this map". Hard mode
would reject scenario 3 during boss selection. Random mode would replace the
Witches with random guardians.

### Equipped Heal and Shield

Equipped Heal and Shield are sealed for the whole Arena match, guardian stage
and boss stage. The crystal pool stays boss-stage only.

The stock client builds the in-match quick-slot HUD from the last quick-slot
list (`S2CInventoryWearQuickAnswer`, 0x1BD9) it received before the match
loads. A list sent during the match is ignored (native run g7). So the seal
works in two layers:

1. **HUD.** `MatchplayGuardianModeHandler.onPrepare` sends each Arena player a
   match-only list in which every Heal or Shield item is 0, for example
   `[32,201,185,210,0]` becomes `[0,0,0,210,0]`. Revive and other items stay.
   The server log shows `(<player>) Halloween Arena match quick slots: [...]`.
   The Heal and Shield slots are empty for the whole match, so pressing their
   keys does nothing: no animation, no counter change. `onStart` sends the chat
   line "Halloween Arena: equipped Heal/Shield are sealed this match." The
   database `QuickSlotEquipment` is not changed. `onEnd` (before the room
   returns) and `GameManager.handleRoomPlayerChanges` (when a player leaves an
   Arena match) send the real list back.
2. **Server check.** A modified client can still send the use.
   `PlayerUseSkillHandler` rejects a quick-slot use (`isQuickSlot = true`) of
   an equipped Heal or Shield on Arena. The check runs before the cooldown
   check and before `handleQuickSlotItemUse()`. The effect:

- The item is not consumed. The caster receives the unchanged item count.
- Teammates do not receive the cast.
- The caster receives the chat line "Equipped Heal/Shield are sealed in the
  Arena."
- The server log shows `Halloween Arena sealed equipped skill <id> (<name>)
  from quick slot <n>`.
- A client that still shows the slot plays the skill locally before the server
  answers, then reports its hits. `SpellHitsTargetHandler` therefore ignores
  the caster's hits for 4 seconds:
  - A heal hit from the sealed caster and skill is ignored, and the server
    sends the real health back. Hits with the God attacker (position 4) use
    the reporting player's seat as their source.
  - A shielded hit (`damageType = 1`) or a DEF buff on a covered player deals
    full damage. Shield covers the caster. Area Shield covers positions 0 to 3.

Crystal skills use `isQuickSlot = false`, so they are never rejected. A crystal
Heal lifts only its caster's seal for that skill. Crystal Shield protection is
tracked separately for `Skill.playTime` seconds, so rejected Shield or Area
Shield uses cannot cancel an active crystal effect. Crystal Area Shield covers
positions 0 to 3.

Sealed skills, by database skill ID:

| Group | Skill IDs |
| --- | --- |
| Heal | 1 SmallHeal, 2 BigHeal, 16 to 19 range heals, 23, 24, 30, 31, 33 food heals, 39 Fast SmallHeal |
| Shield | 10 Shield, 20 Area Shield |

Items hidden from the match HUD, by `Item_Quick` index: 1, 3, 4, 5, 7, 13, 19
to 23, 25 to 29 and 50 (`HalloweenArenaRules.isSealedQuickItem`).

### Crystal pool

`PlayerPickingUpCrystalHandler` uses `HalloweenArenaRules.crystalSkillIndex` in
the Arena boss stage. The Arena guardian stage and other maps keep the
`SkillDropRate` table.

1. If a teammate is dead, the existing 35% Revive roll applies (packet skill
   index 4).
2. Every other roll gives Heal (index 0) at 70% and Shield (index 9) at 30%.

With a dead teammate, the result is 35% Revive, 45.5% Heal and 19.5% Shield.

### Halloween Coin

`MatchplayGuardianModeHandler` gives one Halloween Coin to each active player
when the players win the boss stage on Arena. The coin is Pumpkin, material
item 264 (`HALLOWEEN_COIN_ITEM_INDEX`). The player's inventory updates at once.
The existing rule that a boss clear under 60 seconds gets no reward still
applies. The game log line contains `Halloween Coin;`.

## Verification

The final seal-isolation fix passed all 19 game-server tests, including 12
`HalloweenArenaRulesTest` cases, and the JDK 21 affected-reactor package build.
Three overlap regression tests failed before the fix. Tests cover crystal and
rejected equipped Heal casts in both orders, God-attacker Heal reports,
crystal Shield preservation, Area Shield coverage, and shield expiry.
Native-client and protocol-bot matches were not rerun after this fix.

Before that fix, the lab ran the game server built from this branch against a
copy of the database with the scenario-3 transaction applied. The runtime
results below refer to that earlier build.

Protocol bots played 1, 2, 3 and 4 player matches. All four runs passed the
checks for roster, intro, Chicken, stats, immunity, Witch revival, Small and
Big Inferno cadence, no Big Inferno warning, Trick-or-Treat outcomes, rally
healing, dead-player exclusion, Revive crystal and match finish. The 1-player
run cannot test dead-player exclusion. In the 2 to 4 player runs, one player
stays dead for about 14 seconds. This covers three Small Inferno waves, one Big
Inferno and one Trick-or-Treat.

A 2-player rules match checked the server rules. It passed these checks:

- `-hard` and `-random` are refused in the Arena room.
- Both players get the match-start notice.
- Each player receives the masked quick-slot list once while the match loads
  (`[0,0,0,210,0]` for the player with Heal/Shield equipped) and no list later
  in the match. The real list `[32,201,185,210,0]` comes back after the match,
  and 2 ms after a player leaves a running match.
- Guardian stage: equipped Healing Deluxe sent anyway does not heal (532 →
  532); equipped Shield does not block (41 damage against 43 unshielded).
  Crystals come from the standard pool (7, 5, 3).
- Boss stage: equipped Healing Deluxe does not heal and is not shown to the
  teammate. The caster gets the notice.
- No item is consumed (469, 108, 189 before and after). The server logs 5 seal
  lines: Healing Deluxe and Shield in the guardian stage, Healing Deluxe,
  Shield and Area Shield in the boss stage.
- Crystal Heal heals.
- Equipped Shield and Area Shield do not reduce damage. A crystal Shield used
  right after the equipped Shield blocks the hit (1 damage).
- Every boss-stage crystal is Heal or Shield. Revive appears only while the
  teammate is dead.
- Each player receives one Pumpkin after the win.

Each check was also run against broken versions. Every broken version failed
at least one check:

| Variant | Failed checks |
| --- | --- |
| Pushed PR state (server and script at commit 5b40ed5), rules match | `-hard` and `-random` accepted (Hard mode ON, Random mode ON); equipped heal healed 446 → 600 and was shown to the teammate; equipped Shield and Area Shield blocked (1 damage); items consumed (473 → 472, 195 → 194, 109 → 108); 10 of 14 crystals outside the pool; no Pumpkin; no seal log lines |
| Seal in the boss stage only, slots left visible (previous jar `1b9d9d21a125`), rules match | No match-start notice; stage-1 equipped heal healed (526 → 600); stage-1 Shield blocked (1 damage against 46); no masked list at match load and no restore; items consumed (469 → 468, 189 → 188); 3 seal log lines instead of 5 |
| Crystal pool for the whole Arena match (previous jar `db7bea971bcf`), rules match | Stage-1 crystals only Heal (0, 0, 0) |
| Pushed PR script (5b40ed5), matrix | Big Inferno warning still sent |
| Script without the living-player filter (mutant H) | Dead player hit by 5 boss casts (2 and 4 players); Trick-or-Treat on the dead player (4 players) |
| No immunity, 10-second revival, no rally delay (previous script revision) | Immunity, Witch revival, messages, rally heal |
| Fixed cadence and no dead-player filter for targets (previous revision) | Small and Big Inferno cadence. The target filter change was not detected then; mutant H now covers it |
| Changed intro, Chicken, Trick-or-Treat timing and heal amount (previous revision) | Intro, Chicken, rally heal, Trick-or-Treat, match end |
| No stat changes, no immunity, Infernos after boss death (previous revision) | Stats and immunity |
| Dead players healed and targeted (previous revision) | Rally heal and dead-player exclusion |
| No end when all guardians die (previous revision) | Match finish |
| Unit mutants of `HalloweenArenaRules` (Heal 50%, no Revive, Area Shield caster-only, crystal keeps the seal, seal never expires, Shield not sealed) | Each fails at least one of the 8 unit tests |

The native client results are in the evidence report
(`.amp/in/artifacts/halloween-arena-native/REPORT.md`).

The server sends Movement Speed as packet index 45 for database skill 46. The
official client shows its wing status icon for about 20 seconds. The lab did
not measure whether the character moves faster.

## Use the Devaberg Witch family

Devaberg is database map 10. Its scenario-2 boss roster uses guardian IDs 31
through 36 as one family. `GuardianInfo.xml` identifies them as Windra,
Earthra, Moora, Aquara, Fyra, and Thundera. They share `ResID="5"` and use six
different textures.

Arena uses Windra (guardian 31) and Thundera (guardian 36). This gives the
encounter two visibly different members of the same family. Hell Blood is boss
guardian 1.

The server computes guardian health at spawn from `hpBase + hpPer × players`
and sends it to the client. Other boss maps multiply it by 1.5 with four
players; Arena skips that multiplier (`MatchplayGuardianGame`), so health stays
linear as in the scope brief. The phase
script does not change it. To give Arena its own health without changing
Devaberg, the transaction below adds three Arena-only rows. They copy the
originals and change only `hpBase` and `hpPer`:

| New row | Copy of | `guardIndex` (client model) | `hpBase` | `hpPer` |
| --- | --- | ---: | ---: | ---: |
| `BossGuardian` 103 | Hell Blood (1) | 1 | 7000 | 3000 |
| `Guardian` 101 | Windra (31) | 31 | 2230 | 800 |
| `Guardian` 102 | Thundera (36) | 36 | 2230 | 800 |

The client picks the model by `guardIndex`, so the copies look the same as the
originals. They use new IDs because the auth server re-imports rows 1–7
(`BossGuardian`) and 1–72 (`Guardian`) from the XML files at every start and
would overwrite changes to the original rows. The boss ID must differ from both
Witch IDs: `MatchplayGuardianGame.getRandomGuardian` skips the middle guardian
when its ID equals the left or right guardian ID, even though they are in
different tables. With boss ID 101 the boss stage spawned without Hell Blood.

Resulting health:

| Players | Hell Blood | Each Witch |
| ---: | ---: | ---: |
| 1 | 10000 | 3030 |
| 2 | 13000 | 3830 |
| 3 | 16000 | 4630 |
| 4 | 19000 | 5430 |

These are the scope-brief values. Without the Arena exception, four players
would get the standard 1.5 multiplier (28500 and 8145).

## Activate scenario 3 and install the roster

Run this transaction against the test game database:

```sql
START TRANSACTION;

DELETE FROM Guardian_2_Maps
WHERE map_id = 7
  AND scenario_id IN (2, 3);

DELETE FROM Map_2_Scenarios
WHERE map_id = 7
  AND scenario_id IN (2, 3);

DELETE FROM BossGuardian WHERE id = 103;
DELETE FROM Guardian WHERE id IN (101, 102);

INSERT INTO BossGuardian
    (id, addDex, addSta, addStr, addWill, baseDex, baseSta, baseStr, baseWill,
     btItemID, earth, elementGrade, fire, guardIndex, hpBase, hpPer, level, name,
     rewardExp, rewardGold, rewardRankingPoint, water, wind)
SELECT 103, addDex, addSta, addStr, addWill, baseDex, baseSta, baseStr, baseWill,
       btItemID, earth, elementGrade, fire, guardIndex, 7000, 3000, level, name,
       rewardExp, rewardGold, rewardRankingPoint, water, wind
FROM BossGuardian
WHERE id = 1;

INSERT INTO Guardian
    (id, addDex, addSta, addStr, addWill, baseDex, baseSta, baseStr, baseWill,
     btItemID, earth, elementGrade, fire, guardIndex, hpBase, hpPer, level, name,
     rewardExp, rewardGold, rewardRankingPoint, water, wind)
SELECT CASE id WHEN 31 THEN 101 ELSE 102 END,
       addDex, addSta, addStr, addWill, baseDex, baseSta, baseStr, baseWill,
       btItemID, earth, elementGrade, fire, guardIndex, 2230, 800, level, name,
       rewardExp, rewardGold, rewardRankingPoint, water, wind
FROM Guardian
WHERE id IN (31, 36);

INSERT INTO Map_2_Scenarios (scenario_id, map_id)
VALUES (3, 7);

INSERT INTO Guardian_2_Maps
    (created, modified, side, boss_guardian_id, guardian_id,
     map_id, scenario_id, status_id)
VALUES
    (NOW(6), NOW(6), 'LEFT',   NULL, 101, 7, 3, 1),
    (NOW(6), NOW(6), 'RIGHT',  NULL, 102, 7, 3, 1),
    (NOW(6), NOW(6), 'MIDDLE', 103,  NULL, 7, 3, 1);

COMMIT;
```

Do not keep both scenario 2 and scenario 3 as Arena boss scenarios. The server
uses an unordered `findFirst()` query and can select either row.

Confirm the rows after the transaction:

```sql
SELECT scenario_id, map_id
FROM Map_2_Scenarios
WHERE map_id = 7
ORDER BY scenario_id;

SELECT side, boss_guardian_id, guardian_id, scenario_id, status_id
FROM Guardian_2_Maps
WHERE map_id = 7
  AND scenario_id IN (2, 3)
ORDER BY side;
```

The first query must return scenarios 1 and 3. The second query must return
exactly these active scenario-3 rows:

| Side | Boss guardian | Guardian |
| --- | ---: | ---: |
| LEFT | `NULL` | 101, Windra |
| RIGHT | `NULL` | 102, Thundera |
| MIDDLE | 103, Hell Blood | `NULL` |

To roll back, restore Arena's scenario-2 rows from
`scripts/sql/guardian2maps.sql`, replace the `(3, 7)` map link with `(2, 7)`,
delete `BossGuardian` 103 and `Guardian` 101 and 102, and deploy the previous
game-server build.

## Verify one match

Restart the game server after applying the database transaction. Start a
non-hard-mode Guardian match on Arena and clear the first stage within three
minutes.

Confirm both log messages:

```text
Phase registered from script: 1_halloween_arena for map: 6
Advanced boss guardian mode loaded for map: Arena, scenarioId: 3
```

Confirm the following behavior in the client:

1. `-hard` and `-random` in the Arena room answer "... is not allowed on this
   map".
2. Hell Blood appears in position 10. Windra and Thundera appear in positions
   11 and 12. With two players the health bars show 13000, 3830 and 3830.
3. Hell Blood takes no damage while either Witch is alive.
4. Each Witch revives 20 seconds after its own death.
5. The chat shows "Halloween Arena: equipped Heal/Shield are sealed this
   match." at match start. The Heal and Shield quick slots are empty for the
   whole match and their keys do nothing; Revive stays in its slot. The
   database count stays the same, and the room shows the real slots again
   after the match.
6. Crystal Heal, Shield and Revive still activate.
7. Killing Hell Blood, after both Witches are dead, shows the Game Result
   screen after the next hit, as in other boss matches.
8. Every player receives exactly one Pumpkin (Halloween Coin).
