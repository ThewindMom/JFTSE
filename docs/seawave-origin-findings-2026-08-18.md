# SeaWave origin and travel

Client-verified 18 August 2026 (Europe/Berlin). Live FantaTennis vs local JFTSE.
Branch `test/wave-origin-directions` on ThewindMom/JFTSE, based on `development` `568fc3ec` (matches sstokic-tgm/JFTSE development).

PDF with stills: [seawave-origin-findings-2026-08-18.pdf](seawave-origin-findings-2026-08-18.pdf)

## Answer

xyz places the drop. After that it is not a missile. This morning run was **vanilla** (no LTR cave): every SeaWave is a full-width foam band and it only travels along the court, baseline to net (client **Z**). No heading field. Left-to-right travel needs the later skill-27 cave (`dir.x = ±1.0`). Waves from behind the player still hit if the band passes through the character.

Axis correction (19 Aug): packet `(xTarget, zTarget, yTarget)` is client **X / height / Z**. The “Y” in the table below is **packet `yTarget`**, which is client Z (baseline↔net), not D3D Y. Packet `zTarget` is height. See `docs/atlantis-v2-green-wave-2026-08-19.md`.

## Packet

`S2CMatchplayUseSkill(attacker, target, skillId, seed, x, z, y)`

- skillId 27 = SeaWave (Skill table id 28)
- attacker 4 = dummy slot (no guardian mesh). Dummy 5 also works.
- knockback baked `(0, 0, 150)` along court length
- Court xyz forwarded from the client, no extra server scale
- No direction argument. xyz only moves the ground drop.

## How it was tested

Command `-wavetest` in a Guardian match on Nest of Rubycrab. Nine SeaWaves, 5 seconds apart, labeled in chat. Account `test` / Testmon. HP padded at intro end so a level-1 character survived. Complete run reached WAVE 9/9, Testmon alive at 01:12.

## The nine drops

| Wave | Attacker | xyz (x, z, y) | Intent |
|------|----------|---------------|--------|
| 1/9 | 4 | (0, 0, 0) | net / court origin |
| 2/9 | 4 | (-150, 0, 0) | -X sideline |
| 3/9 | 4 | (150, 0, 0) | +X sideline |
| 4/9 | 4 | (0, 0, -150) | -Y baseline (player half) |
| 5/9 | 4 | (0, 0, 150) | +Y baseline (opponent half) |
| 6/9 | 4 | (0, -150, 0) | -Z axis |
| 7/9 | 4 | (0, 150, 0) | +Z axis |
| 8/9 | 5 | (0, 0, 0) | dummy slot 5, origin 0 |
| 9/9 | 4 | (-150, 0, 150) | corner -X / +Y |

## What the client did

- (0,0,0) is net-center, not under the player.
- Packet +Y (`yTarget`) is the opponent / far baseline = client **Z**. Packet −Y is the player / near baseline.
- Packet +/-X moves the drop toward a sideline = client **X**. That is the LTR/RTL lane axis once the cave is installed.
- Packet +/-Z (`zTarget`) is height. It still produced a full-width wall. It is not a travel heading.
- Vanilla travel never changed. Always baseline to net (client Z). The later LTR cave supersedes “left-to-right is not possible.”
- Knockback stays (0, 0, 150) along that axis.
- WAVE 1 came from behind Testmon and popped 9708. WAVE 4 sat on the near baseline and popped 9042. Facing does not matter.
- HP bar stayed 200/200 because of the pad. Popups are real hits (9000+).

## Final Atlantis constraint

The player-half / rear-origin placement was experimental and is not part of the live encounter. Atlantis randomizes every SeaWave's packet `xTarget` and selects `yTarget` only from the positive enemy-half depths `50`, `75`, and `100`. No live script can select a negative/rear depth. Dummy 4 remains the actorless caster.

## Not possible

- Steering a SeaWave after it lands.
- Left-to-right travel as the motion axis.
- Per-wave knockback direction. It stays (0, 0, 150).
- Using Z as a travel axis.

## Test harness (this branch only)

`wavetest.js` fires the 9 drops in a guardian match. In the waiting room it writes `/tmp/jftse-wavetest.arm` and the volley starts after the intro. HP pad iterates ConcurrentLinkedDeque (not `.get(i)`) and sends `S2CMatchplayDealDamage` heal skill 1 with HP 30000 (client HP is a short).

`3_wavetest_autopad.js` pads HP on `MP_GAME_ANIM_SKIP_END` before GuardianAttackTask, then fires an armed volley. Solo Testmon otherwise dies in about 15s. These scripts do not replace `guardian-phase/10`.
