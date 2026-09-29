# Configure the Halloween Arena encounter

This guide records the server and database work that remains outside
`guardian-phase/6/1_halloween_arena.js`. Arena is client map 6 and database map
7. The phase script runs only when Arena uses scenario 3, `BOSS_BATTLE_V2`.

Back up the live database before changing these rows. Apply the database and
game-server changes during the same maintenance window. The new roster cannot
run correctly on an old game-server build, and the script cannot run against
the old scenario link.

## Use the Devaberg Witch family

Devaberg is database map 10. Its scenario-2 boss roster uses guardian IDs 31
through 36 as one family. `GuardianInfo.xml` identifies them as Windra,
Earthra, Moora, Aquara, Fyra, and Thundera. They share `ResID="5"` and use six
different textures.

Use guardian 31, Windra, and guardian 36, Thundera, for Halloween Arena. This
gives the encounter two visibly different members of the same family. The
phase script replaces their health, stats, and attack skill, so their original
Devaberg balance does not carry into Arena.

Hell Blood is boss guardian ID 1.

## Activate scenario 3 and install the roster

Run this transaction against the live game database:

```sql
START TRANSACTION;

DELETE FROM Guardian_2_Maps
WHERE map_id = 7
  AND scenario_id IN (2, 3);

DELETE FROM Map_2_Scenarios
WHERE map_id = 7
  AND scenario_id IN (2, 3);

INSERT INTO Map_2_Scenarios (scenario_id, map_id)
VALUES (3, 7);

INSERT INTO Guardian_2_Maps
    (created, modified, side, boss_guardian_id, guardian_id,
     map_id, scenario_id, status_id)
VALUES
    (NOW(6), NOW(6), 'LEFT',   NULL, 31, 7, 3, 1),
    (NOW(6), NOW(6), 'RIGHT',  NULL, 36, 7, 3, 1),
    (NOW(6), NOW(6), 'MIDDLE', 1,    NULL, 7, 3, 1);

COMMIT;
```

Do not keep both scenario 2 and scenario 3 as Arena boss scenarios. The server
uses an unordered `findFirst()` query and can select either row.

Confirm the live rows after the transaction:

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
| LEFT | `NULL` | 31, Windra |
| RIGHT | `NULL` | 36, Thundera |
| MIDDLE | 1, Hell Blood | `NULL` |

To roll back, restore Arena's scenario-2 rows from
`scripts/sql/guardian2maps.sql`, replace the `(3, 7)` map link with `(2, 7)`,
and deploy the previous game-server build.

## Block hard mode on Arena

Add client map 6 to the deny list in `HardModeCommand`:

```java
Arrays.asList(1, 2, 4, 6, 7, 8, 10)
```

This change requires a game-server build and restart. Until that build is
deployed, operators must not enable hard mode in an Arena room. Hard mode
rejects scenario 3 during boss selection and can leave Arena without a boss
scenario.

## Disable equipped Heal and Shield

This rule cannot be configured safely with SQL. Enforce it in
`PlayerUseSkillHandler` before cooldown validation and before
`handleQuickSlotItemUse()` consumes an inventory item.

Apply the rejection only when all of these conditions are true:

- The attacker is a player.
- `isQuickSlot` is `true`.
- The match is Guardian mode on client map 6.
- The selected skill is an equipped Heal or Shield skill.

Crystal skills use `isQuickSlot = false`, so the same Heal and Shield skills
remain usable when a crystal granted them. Return before broadcasting the use
packet and before decrementing the equipped item count.

The standard crystal outcomes use these identifiers:

| Outcome | Packet or XML index | Database skill ID |
| --- | ---: | ---: |
| Heal, `SmallHeal` | 0 | 1 |
| Shield | 9 | 10 |

If the event must block every equipped healing variant, also include database
skill IDs 2, 16 through 19, 31, and 39. Those IDs are `BigHeal`, the four range
heals, `FullHeal`, and `Fast SmallHeal`. Keep the check restricted to
`isQuickSlot = true`.

Do not use the phase script's `onHeal` hook for this rule. That hook receives
the heal after skill use and does not preserve whether the source was an
equipped slot or a crystal.

## Give crystals the event-specific pool

Do not edit `SkillDropRate` rows to create the Halloween pool. Those rows are
shared by other maps and player levels.

Add an Arena branch in `PlayerPickingUpCrystalHandler`. The handler already has
the active room, the map number, and the dead-player state. Use these rules:

1. If the match is not Guardian mode on client map 6, keep the existing drop
   table logic.
2. If a teammate is dead, preserve the existing 35% Revive roll. Revive uses
   packet skill index 4.
3. If Revive does not win, return Heal index 0 on 70% of the remaining rolls
   and Shield index 9 on 30%.
4. If nobody is dead, return Heal index 0 at 70% and Shield index 9 at 30%.

With a dead teammate, the effective distribution is 35% Revive, 45.5% Heal,
and 19.5% Shield. This distribution keeps the owner's decision that Revive
crystals are allowed. The 70 to 30 split applies to non-Revive results.

Add a deterministic test around the selection function. Supply boundary rolls
for Revive, Heal, and Shield rather than relying on repeated random sampling.

## Finish the remaining server work

The following requirements also need game-server changes. They cannot be
installed through SQL:

- Add client map 6 to the hard-mode deny list.
- Reject equipped Heal and Shield while allowing the crystal versions.
- Apply the Arena-only crystal outcome pool.
- Finish the match immediately after Hell Blood dies. `END_PHASE` currently
  stops `PhaseManager` but does not queue `FinishGameTask`.
- Award one Halloween Coin to each eligible player after a successful clear.

The phase script already implements the Chicken intro, the two-Witch immunity
rule, independent 20-second Witch revivals, the Small and Big Inferno cadence,
per-player Chicken, Confused, Heal, and Movement Speed outcomes, a Big Inferno
warning, the encounter rules message, and 5% healing for living players after
a detected won rally. The Movement Speed packet path is tested, but its native
client effect still needs verification in a real match.

## Verify one real match

Restart the game server after deploying the code and applying the database
transaction. Start a non-hard-mode Guardian match on Arena and clear the first
stage within three minutes.

Confirm both log messages:

```text
Phase registered from script: 1_halloween_arena for map: 6
Advanced boss guardian mode loaded for map: Arena, scenarioId: 3
```

Confirm the following behavior in the client:

1. Hell Blood appears in position 10. Windra and Thundera appear in positions
   11 and 12.
2. Hell Blood takes no damage while either Witch is alive.
3. Each Witch revives 20 seconds after its own death.
4. Equipped Heal and Shield do not consume an item or activate.
5. Crystal Heal, Shield, and Revive still activate.
6. Killing Hell Blood ends the match without another hit or a timer wait.
7. Every eligible player receives exactly one Halloween Coin.
