# SeaWave origin and travel

Client-verified 18 August 2026 (Europe/Berlin). Live FantaTennis vs local JFTSE.
Branch `test/wave-origin-directions` on ThewindMom/JFTSE, based on `development` `568fc3ec` (matches sstokic-tgm/JFTSE development).

PDF with stills: [seawave-origin-findings-2026-08-18.pdf](seawave-origin-findings-2026-08-18.pdf)

## Answer

xyz places the drop. After that it is not a missile. Every SeaWave is a full-width foam band (crest already spans sideline to sideline) and it only travels along the court, baseline to net. No heading field. Left-to-right travel is not possible. Waves from behind the player still hit if the band passes through the character.

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
- +Y is the opponent / far baseline. -Y is the player / near baseline.
- +/-X moves the drop toward a sideline. That is the lane axis.
- +/-Z still produced a full-width wall. Z is not a useful heading.
- Travel never changed. Always baseline to net.
- Knockback stays (0, 0, 150) along that axis.
- WAVE 1 came from behind Testmon and popped 9708. WAVE 4 sat on the near baseline and popped 9042. Facing does not matter.
- HP bar stayed 200/200 because of the pad. Popups are real hits (9000+).

## For Atlantis herding

Do not spawn at the net and hope the wave comes at the players. Drop on the player half (-Y) with +/-X offsets so parallel bands sweep toward the net and leave corridors. Dummy 4 is fine. Anyone in a band takes damage, including from behind. Ground-placed herding, not a steered missile.

## Not possible

- Steering a SeaWave after it lands.
- Left-to-right travel as the motion axis.
- Per-wave knockback direction. It stays (0, 0, 150).
- Using Z as a travel axis.

## Test harness (this branch only)

`wavetest.js` fires the 9 drops in a guardian match. In the waiting room it writes `/tmp/jftse-wavetest.arm` and the volley starts after the intro. HP pad iterates ConcurrentLinkedDeque (not `.get(i)`) and sends `S2CMatchplayDealDamage` heal skill 1 with HP 30000 (client HP is a short).

`3_wavetest_autopad.js` pads HP on `MP_GAME_ANIM_SKIP_END` before GuardianAttackTask, then fires an armed volley. Solo Testmon otherwise dies in about 15s. These scripts do not replace `guardian-phase/10`.
