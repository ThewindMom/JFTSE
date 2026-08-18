# Guardian shield pads (2026-08-17)

In Guardian matches, **10 seconds after `onStart` / stage start** (not the skip-click), two green stand-pads appear on the player half of the court. Walking onto either pad grants a **one-shot** `BattleState.shieldActive` that absorbs the next incoming player HP damage (skills, guardian hits, ball-loss) and then clears.

Official clients without the client hook play the fight normally and do not see pads. That is expected.

## Timing

`GameAnimationSkipTriggeredPacketHandler` sets the room Running and 8 seconds later calls `game.getHandleable().onStart(client)`. `MatchplayGuardianModeHandler.onStart` resets `stageStartTime` and then schedules the pads via `ThreadManager` / `game.scheduledFutures`. The 10s delay starts there, not at skip-click.

## Court coordinates

`MatchplayGuardianGame` spawn constants are AWT `Point(20, -75)` and `Point(-20, -75)` (x / z). Player half is **negative Z**. Defaults:

| pad   | x    | z    |
|-------|------|------|
| left  | -40  | -40  |
| right |  40  | -40  |

Containment is a circle of radius 15 (official Movement pad size), same short units as `CMSG_PlayerAnimation` `absoluteXPositionOnMap` / `absoluteYPositionOnMap` (Y = court Z). Pads are not on the spawn points.

Live-hook tutorial stills at `pad 0 85` / `pad ±40 40` were not used; those were tutorial-space experiments.

## Positions (relay → game-server)

`CMSG_PlayerAnimation` (0x32C9) is handled on the **relay**. Relay and game-server are separate processes, so last court X/Z is published over the existing Rabbit bus (same family as `MATCH_BALL_SYNC`):

- Message: `MatchCourtPositionMessage` (`MATCH_COURT_POSITION`)
- Routing key: `game.stats.match.court`
- Queue: `court-position-queue` on game-server

`PlayerAnimationHandler` still forwards the animation packet first, then publishes. `GuardianShieldPads` keeps a per-session last-position map keyed by `playerId`. If a player is already standing on a pad when they become visible, the last stored position is checked and can grant.

**Requires `jftse.rabbitmq.enabled=true` on both relay and game-server** (the default). If Rabbit is down, the mechanic does not grant from movement.

## Shield

`PlayerDamageApplier.updateHealthByDamage` (used by `PlayerCombatSystem` and `GuardianCombatSystem`) honors `BattleState.shieldActive`: a negative HP hit is absorbed and the flag is cleared. Healing (positive) does not consume the shield.

Grant is one-shot **per player per match**. Standing in the zone does not re-grant. The other pad cannot grant a second shield to the same player. Other players can still each get one.

## Client visual

1. **Hooked official client (optional):** set `jftse.guardian.shield-pads.zone-file` to the `stroke-quads.zone` path in that client's cwd. When pads appear the server atomically writes:

   ```
   pad -40 -40
   pad 40 -40
   ```

   On match end / cleanup it writes `clear`. The hook polls this file (`place` / `pad` / `ring` / `clear`).

2. **Shield VFX:** on grant the game-server broadcasts `SMSGPlayerUseSkill` with the official **Shield** skill index **9** (`FieldItem_Skills_Ini3.xml` `<ID>9</ID><Name>Shield</Name>`; `SkillUse.isShield()` is DB id 10 → index 9). This is a real skill index, not a made-up cue. Whether every official client plays the bubble from a server-originated use-skill is **untested on a live client**.

3. **Unhooked official clients** never see the green pads. Server-side absorb still applies if they walk onto the (invisible) zone.

## Multi-room zone-file limitation

A single `stroke-quads.zone` path cannot represent more than one court. If the path is set and **more than one** Guardian room currently has visible pads, the write is skipped and a warning is logged. When the count drops back to one, the next activate/end will write again.

Do not hardcode a `/workspace/...` path. Point the property at the hooked client's working directory, for example:

```
jftse.guardian.shield-pads.zone-file=C:/Games/FantaTennis/stroke-quads.zone
```

or a Linux path the hooked process actually polls.

## Config keys (`game-server` `application.properties`)

| key | default | meaning |
|-----|---------|---------|
| `jftse.guardian.shield-pads.enabled` | `true` | Master switch |
| `jftse.guardian.shield-pads.delay-seconds` | `10` | Delay after `onStart` |
| `jftse.guardian.shield-pads.left-x` / `left-z` | `-40` / `-40` | Left pad |
| `jftse.guardian.shield-pads.right-x` / `right-z` | `40` / `-40` | Right pad |
| `jftse.guardian.shield-pads.radius` | `15` | Circle radius |
| `jftse.guardian.shield-pads.zone-file` | empty | Optional hook file; empty = mechanic only |
| `jftse.guardian.shield-pads.visual-skill-index` | `9` | `SMSGPlayerUseSkill` skill index on grant |

## Cleanup

`MatchplayGuardianModeHandler.onEnd` (including the early banable path after `finished` is set) clears per-session pad state and writes `clear` when appropriate. Cancelled `scheduledFutures` on finish/disconnect prevent a late activate.

## Honest gaps

- Live official-client pad placement and shield VFX were **not** re-tested in this change.
- Without Rabbit, court positions never reach game-server.
- Unhooked clients do not see pads.
- Zone-file is single-room only.
