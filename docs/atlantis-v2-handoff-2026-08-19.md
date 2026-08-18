# JFTSE Atlantis V2 handoff — green tiles, wave redirection, lessons (2026-08-19)

**Author:** ThewindMom / JFTSE
**Written:** just after midnight, 19 August 2026, Europe/Berlin (PT)
**Audience:** a new agent session whose only job is to **implement** a new Atlantis boss on `ThewindMom/JFTSE`.
**Tone:** facts only. Draft / untested items are marked. Do not invent branches, hashes, or fight numbers.

**Do not commit or push from this note.**

This document is the implementation brief. GitHub is source of truth. ClickUp may be stale.

---

## 0. What you are building

Thewind (19 Aug 2026 00:14 PT):

> implement the new atlantis boss script with the green tiles as safe zones perhaps and the wave redirection

Thewind (18 Aug 2026 23:38 PT) confirmed the three pieces of prior work:

> green tiles, atlantis boss script (new)? and wave redirection

There is **no V2 fight branch**. The 18 Aug concept is a **chat-only, untested draft**. Current map 10 on `development` is the old four-phase fight. Implement V2 on a **new branch** from a taken-over `development`. Do **not** silently overwrite the old files.

**Preferred script path:** `game-server/src/main/resources/scripts/guardian-phase/10-v2/`
so `guardian-phase/10/` stays intact until Thewind merges.

**Why a loader change is required:** `ScriptManagerFactory` sets `groupPath` to the folder after the type (`guardian-phase/10` → `"10"`). Loader filter in `MatchplayGuardianGame.loadAdvancedBossGuardianMode` is `scriptFile.getGroupPath().equals(map.getMap().toString())` → `"10"`. A `10-v2` folder will **not** load until you wire map 10 / `BOSS_BATTLE_V2` to that group (flag or explicit confirm). See §12.

---

## 1. Repo / workflow (Thewind’s required path)

| Item | Fact |
|---|---|
| Fork | `ThewindMom/JFTSE` |
| Upstream | `sstokic-tgm/JFTSE` |
| Default branch for work | `development` |
| GitHub | source of truth for code, issues, PRs |
| ClickUp | may be stale — do not treat as current |
| Box `gh` | **no login** |
| ThewindMom credentials | on the home PC (`gh` as ThewindMom) |
| Clone policy | **Do not clone onto a machine unless Thewind asks.** Cloud agent or existing worktrees. |
| GM test account | `test` / `test`, character **Testmon** |
| Dirty checkout | `/workspace/jftse-work/JFTSE` is on `battlemon`. **Do NOT commit the dirty battlemon checkout.** |

### Take-over rule (do not merge)

When implementing:

1. Take over upstream `development` onto the fork `development` — **no merge, take it over**.
2. Branch from that taken-over `development`.
3. Fix, validate live, commit, push **the feature branch**.
4. Do not force-push. Do not commit battlemon dirt.

Verified this session (19 Aug ~00:15 PT, `git ls-remote` + fetch):

| Ref | SHA | Date (PT) | Subject |
|---|---|---|---|
| `ThewindMom/JFTSE` `development` | `568fc3ec93294f2844940d371b50b530d45d49ea` | 17 Aug 12:12 | `fix def on targetGuardian` |
| `sstokic-tgm/JFTSE` `development` (local `upstream/development`) | `568fc3ec` same tip | 17 Aug 12:12 | same |
| Fork `battlemon` (do not commit) | remote `9756d343…`; local worktree was `7a4d7a44` | — | RE evidence only |

---

## 2. What exists (branches, PRs, files, hashes)

GitHub is the table you implement from. Local worktrees on this box may be **ahead or dirty**; do not treat them as the branch tip.

### 2.1 Open PRs — these are **not** the fight

| PR | Title | Branch / tip | What it is |
|---|---|---|---|
| **#1** | Fix messenger Invite so the existing Yes/No modal works | `fix/invite-people-development-20260815` `c92e093a` | Invite packets. Unrelated to Atlantis. |
| **#3** | docs: SeaWave LTR first working cave (2026-08-18) | `docs/seawave-ltr-first-cave` `b38430e0` | **Docs only.** No game-code. |
| **#4** | docs: SeaWave four-direction recipes (2026-08-18) | `docs/seawave-four-directions` `22c21dca` | **Docs only.** No game-code. |

Do not confuse these with the fight. None of them implement Atlantis V2.

### 2.2 Green tiles (safe-zone **visual + one-shot shield**)

| Item | Fact |
|---|---|
| Branch | `feature/guardian-shield-pads-20260817` |
| GitHub tip | `5d984d5123694296cd2a5c234294dd4f92fced92` |
| Date | 18 Aug 2026 **07:34 PT** — `Listen for MP_MATCH_START/END to schedule and clear Guardian shield pads` |
| Ahead of `development` `568fc3ec` | **10 commits**. **No PR.** |
| Local worktree | `/workspace/jftse-work/JFTSE-shield-pads` at `79980f93` (unpushed extra: `Hook shield pads to MP_MATCH_START after onStart`). **GitHub tip is `5d984d51`.** |

10 GitHub commits (`568fc3ec..5d984d51`):

```
4f2fce62 Add Guardian shield pads that appear 10s after match start
adcccd58 Add Guardian shield pad docs, damage applier, and court-position message
c3c7ca9c Add court-position Rabbit consumer, relay publish, and pad config
40803815 Add GuardianShieldPads state machine and grant service
0eab13fd Add GuardianShieldPads unit tests
2a3ebd8c Add spring-boot-starter-test to relay-server for pad mapping tests
82a8367c Honor BattleState shield in PlayerCombatSystem.updateHealthByDamage
f84ca6b2 Honor BattleState shield for Guardian-to-player HP damage
332a84fe Fire MP_MATCH_START after onStart and clear pads on session removal
5d984d51 Listen for MP_MATCH_START/END to schedule and clear Guardian shield pads
```

Key files on that branch:

- `game-server/.../guardian/GuardianShieldPadService.java`
- `game-server/.../guardian/GuardianShieldPads.java`
- `game-server/.../combat/PlayerDamageApplier.java`
- `relay-server/.../handler/PlayerAnimationHandler.java` (publish after forward)
- `server-core/.../rabbit/messages/MatchCourtPositionMessage.java`
- `docs/guardian-shield-pads-20260817.md`

Local tutorial-pad branch `feature/twinkle-town-green-tutorial-pad-20260817` tip `f162d75e` — **never pushed**. Visual research only. Do not treat it as a fight branch.

### 2.3 Wave redirection + wavetest

| Item | Fact |
|---|---|
| Branch | `test/wave-origin-directions` |
| GitHub tip | `3b11ffc30bdd2cdb2151723847b128698a9a1dfc` |
| Date | 18 Aug 2026 12:41 PT — `Ignore SeaWave hits on guardians so herding only damages players.` |
| Ahead of `development` | **4 commits**. **No PR.** |
| Local worktree | `/workspace/jftse-work/JFTSE-wavetest` at `e450c447` + **dirty** `wavetest.js` / `3_wavetest_autopad.js`. GitHub tip is `3b11ffc3`. |

4 GitHub commits:

```
2722908e Add -wavetest command to probe SeaWave spawn origin and travel direction.
b1e88862 Record client-verified SeaWave origin and travel findings.
38b4a1e0 Add SeaWave origin findings PDF with client stills.
3b11ffc3 Ignore SeaWave hits on guardians so herding only damages players.
```

Files on that branch:

- `game-server/src/main/resources/scripts/command/wavetest.js`
- `game-server/src/main/resources/scripts/event/3_wavetest_autopad.js`
- `game-server/.../SpellHitsTargetHandler.java` (guardian ignore)
- `docs/seawave-origin-findings-2026-08-18.md` (+ PDF)

**Cave is on the CLIENT EXE, not in git.** See §6.

### 2.4 Other local worktrees (do not mix in)

| Worktree | Branch | Note |
|---|---|---|
| `/workspace/jftse-work/JFTSE` | `battlemon` | Dirty. Do not commit. |
| `/workspace/jftse-work/JFTSE-revive` | `fix/guardian-revive-ghost-ball` `1d721fd3` | Revive investigation. Not V2. |
| `/workspace/jftse-work/JFTSE-green-pad` | tutorial-pad, never pushed | Visual RE only. |

---

## 3. Current Atlantis — do not silently overwrite

Live on `development` map **10**, `BOSS_BATTLE_V2` (scenario id 3), folder:

`game-server/src/main/resources/scripts/guardian-phase/10/`

| File | Phase name |
|---|---|
| `1_echoes_of_the_deep.js` | Echoes of the Deep |
| `2_maelstrom_unleashed.js` | Maelstrom Unleashed |
| `3_leviathans_will.js` | Leviathan's Will |
| `4_abyssal_reckoning.js` | Abyssal Reckoning |

Present in every worktree checked (JFTSE, shield-pads, wavetest, green-pad, revive) and on upstream `development`.

### How it is registered (verified from scripts + `MatchplayGuardianGame`)

| Layer | Value |
|---|---|
| Client map | `10` |
| Wiki / design-brief `S_Maps` | id `11`, name Atlantis, `playTime=8`, `triggerBossTime=4`, `isBossStage=1` |
| Stage 1 | `GUARDIAN` |
| Boss scenario | `BOSS_BATTLE_V2` |
| Script type | `GUARDIAN-PHASE` |
| Load filter | `groupPath == map.getMap().toString()` → `"10"` |
| Export | `var phase = { … }` implementing `BossBattlePhaseable` |
| Phase order | filename sort |
| Bindings | `gameManager`, `serviceManager`, `threadManager`, `eventHandler`, `scriptContextHelper`, `geb`, `log`, `game` |

Every current phase does `g.getSkills().clear()` and `getGuardianAttackLoopTime() = -1` — no default guardian AI. All casts are scripted `S2CMatchplayUseSkill`.

### What the four files actually do (read from the JS this session)

**Phase 1 — Echoes of the Deep**

- Boss **immune** (`onDealDamage` / ball-loss return current HP) until both adds die.
- Adds: every **10s** Silence (id 57, 40%) or Polymorph (id 7). Stagger +3750 ms.
- Last add alive → aggro interval **5s** + delayed Homing (id 6).
- Ends when both adds dead.

**Phase 2 — Maelstrom Unleashed**

- Boss damageable. BigMeteo id **3** / 20s + DoT; SeaWave id **28** / 14s, target pos **4**, xyz **0,0,0**; Chaos id **25** / 16s all players.
- First dead add: **one** Rebirth at **30% HP**.
- Ends at **120s** or boss **< 50% HP**.

**Phase 3 — Leviathan's Will**

- MeteoBall **37**/20s, Inferno **35**/15s, MegaFireBall **26**/18s (all), Blizzard **13**/25s.
- Silence once at 90s. 35% chance one 30% add revive.
- At 85s: boss HP set to **45%**.
- **Player heal × 0.35** via `onHeal`.
- Ends at **120s** or boss **< 25% HP**.

**Phase 4 — Abyssal Reckoning**

- Each add revived **once at 100% HP** (3s delay).
- Earth **32**/25s, Water_Pillar **61**/16s, Storm **62**/50s, Laser **65**/60s, Homing **6**/30s all.
- At 30s: boss **full heal**.
- Ends when all three dead.

**Keep these four files until Thewind merges.** Implement V2 under `guardian-phase/10-v2/`.

---

## 4. V2 concept — Thewind, 18 Aug 2026 01:53 PT — **UNTESTED DRAFT**

Source: transcript `b6352f6f-e679-43e5-aca3-e41ddf5fe74a`, first real user design message (also echoed in agent memory `2026-08.md`).

Thewind’s own framing:

> also ich schreib mal so ein basic konzept zusammen das noch verfeinert werden muss da ja noch nichts davon getestet wurde

Follow-up 02:16 PT:

> wie gesagt ist nur ein erster entwurf, denke da muss noch einiges angepasst werden auch wegen der schwierigkeit, könnte sein dass es viel zu leicht ist mit den 5 oder auch 10 waves, muss man halt testen

**5 and 10 are starting counts, tunable.** Difficulty is untested. An image was attached at 02:16 PT (“and also this”); image bytes are **not** in the JSONL dump. Spec = the written draft below.

A later **agent review** in the same transcript suggested cutting to three acts and changing some timings (e.g. inferno 8–10s). **Thewind did not adopt that as the spec.** Implement Thewind’s draft. Do not replace it with the review.

### 4.1 Thewind’s beats (quote / close paraphrase)

Map 10. Atlantis boss + 2 adds. Match starts ordinary — “normale spells von den guardians”.

1. **+30s:** all **guardian** spells deactivated.
2. **+35s:** player **shields + teamshields + defensive buff + heals + teamheals** deactivated (5s gap so an in-flight blizz/meteor can finish).
3. **“Phase 1” volley:** **5** waves in a row. Prefer the script that works **without a guardian** (dummy attacker 4). If spawn position can be changed, randomize start positions. *(Spawn/heading: see §6 — xyz is drop only; LTR needs the client cave.)*
4. **5 seconds later:** **10** waves in a row.
5. Re-enable shields and co. Guardians return to normal spell rotation.
6. When **both adds are dead** (boss **immune until both adds down**): boss casts **only normal blizzards**; player shields+co disabled again. All other boss spells off.
7. **5 waves** again, with blizzard as extra navigation difficulty.
8. After those 5: boss **only waves**.
9. Tell players the adds revive in **2 minutes**. Players have 2 minutes to spam **crabs** (SpiderMine, DB id **12** / packet **11**) on the opponent court in the best zones so the adds die immediately after revive.
10. **2 minutes later:** adds revived at **full HP**. Player heal is re-enabled but **only 20% effective** (“nicht 100% sondern nur 20% effektiv”). Adds also **only waves**, same as the boss. When both adds are down again, the wave phase stops. *“man muss in der praxis testen wie schwierig das tatsächlich wird.”*
11. Player heal back to **100%** after both adds down again. **Shields stay disabled.**
12. **Big blizzards:** stay in **safe zones left and right of the court**; boss only big blizz. Do not play the ball (too risky). Every **5 seconds** an **inferno on all players from the script (not guardian)** to keep people moving and stop corner camping.
13. After **30 seconds** this ends. Boss **charges** ~**50s**. Thewind: charge animation “ich weiß nicht ob das möglich ist, wäre aber cool”. Boss does **not** play the ball; still takes damage. **Charge animation = UNKNOWN / untested.**
14. After the 50s charge: **megawave** — “evtl auch einfach 20 direkt hintereinander quasi fast unmöglich zu dodgen”. One player tanks; others stand behind to stop knockback. Tank probably dies unless high STA or maybe defensive buff. **No shield.** There is **no megawave skill** in the table (18 Aug design brief). Closest: 20× SeaWave (packet 27) or Storm id 62.
15. Short recovery: everything re-enabled including shield. Boss **stunlocked 5s**.
16. Adds revived again (good players can crab during the 50s charge — **not told**).
17. Boss **enrages** after the adds die: only **waves + blizzard**, no player shields/heals, **faster** than before.
18. Kill the boss. Last phase.

### 4.2 Numbers at a glance (all DRAFT)

| Beat | Number | Status |
|---|---|---|
| Strip guardian spells | +30s | draft |
| Strip player shields/heals/def | +35s | draft |
| First volley | **5** waves | draft, Thewind: may be too easy |
| Gap | **5s** | draft |
| Second volley | **10** waves | draft, same caveat |
| Boss immune | until both adds dead | also true of current phase 1 |
| Blizzard + 5 waves | after both adds dead | draft |
| Crab window | **2 min** | draft |
| Add revive HP | **100%** | draft |
| Player heal during post-revive waves | **20%** | draft (current live phase 3 uses **35%** — different fight) |
| Big-blizz / safe-zone dwell | **30s** | draft |
| Inferno interval | **5s**, all players, script-cast | draft |
| Charge | **~50s**, animation UNKNOWN | draft |
| Megawave | **20** SeaWaves **or** a missing skill | draft; no megawave skill found |
| Stun | **5s** | draft |
| Enrage | waves + blizzard, faster, no shields/heals | draft |

**Later Thewind (19 Aug):** green tiles as the **safe zones** for wave herding (and for the “left and right of the court” big-blizz idea). Combine with redirected SeaWave. See §7.

Skill IDs you will actually fire (from live scripts / FieldItem / 18 Aug brief — do not invent new ones):

| Name | DB `findSkillById` | Packet index (`id - 1`) |
|---|---|---|
| SeaWave | 28 | **27** |
| Blizzard | 13 | 12 |
| Storm (“big blizzard” in current phase 4 / brief) | 62 | 61 |
| Inferno | 35 | 34 |
| SpiderMine / crab | 12 | 11 |
| Shield (VFX on pad grant) | index **9** | — |

Fire SeaWave as `new S2CMatchplayUseSkill(4, 4, 27, seed, x, z, y)` for dummy-caster / no guardian mesh. Current phase 2 uses `(bossPos, 4, 27, seed, 0, 0, 0)` — boss may still play a cast.

---

## 5. Green tiles — what the branch does vs what V2 needs

### 5.1 What `feature/guardian-shield-pads-20260817` (`5d984d51`) already does

- **10 seconds after Guardian `onStart` / stage start**, **not** the skip-click.
- Two pads: **(−40, −40)** and **(40, −40)**, radius **15**.
- Coordinates are court **X / Z**. Script Y = client Z. **Player half is negative Z.**
- One-shot `BattleState.shieldActive`: absorbs the **next incoming player HP damage**, then clears. Healing does not consume it.
- **One grant per player per match.** Standing in the zone does not re-grant. The other pad cannot grant a second shield.
- Positions via Rabbit `MATCH_COURT_POSITION` (`MatchCourtPositionMessage`, routing `game.stats.match.court`, queue `court-position-queue`). Relay `PlayerAnimationHandler` publishes after forwarding `CMSG_PlayerAnimation` (0x32C9). **Requires `jftse.rabbitmq.enabled=true`** on relay and game-server (default).
- Visual: optional `jftse.guardian.shield-pads.zone-file` → hooked client polls `stroke-quads.zone`. Server writes `pad -40 -40` / `pad 40 -40`, then `clear`.
- **Unhooked official clients do not see green.** Server still grants if they walk the (invisible) zone.
- Zone-file is **single-room**. Do not hardcode a `/workspace/...` path.

Config keys (`game-server` `application.properties`): `jftse.guardian.shield-pads.enabled` (default true), `delay-seconds` 10, `left-x/z` −40/−40, `right-x/z` 40/−40, `radius` 15, `zone-file` empty, `visual-skill-index` 9.

### 5.2 Wanted look (visual research, tutorial-pad branch — not pushed)

The wanted look is official **Movement-size stand pads** (primitive `0x512460` size-15) or custom `0x5105E0` boxes via the zone file.

**Not:** Stroke half-court tints (`0x548F70` two large rects), shoeprints (`A_TU_footmark_A`), or crystals.

### 5.3 NEW work — say this clearly

Current shield is **ONE hit then gone**.

A SeaWave volley (5, then 10, later 20) will **melt** someone who only has one absorb. Morning vanilla-axis hits were popup **9708** / **9042** on a padded Testmon. Solo Testmon otherwise dies in about **15 s**.

**Implementing “stand on green = ignore SeaWave while in the circle” is NEW work. It is not on `feature/guardian-shield-pads-20260817`.**

Reuse pad **positions and visuals**. **Extend the hit filter.** Do not pretend the one-shot shield is a safe zone.

Filter shape (see §6.4):

- Guardian ignore already on wavetest branch: `targetPosition > 9` + SeaWave → drop. That is **guardians**, not pads.
- Pads need a **position check**: if the player’s last court X/Z is inside a visible pad circle, drop that SeaWave hit the same way.
- Last court pos already lives in `GuardianShieldPads` (`lastPosByPlayerId`) via Rabbit. Reuse it.

Official clients without the green hook play Atlantis **blind**. Fight logic must still work if they cannot see the pads (they can still stand in the circle if they know the spots, or they take the hits).

---

## 6. Wave redirection

### 6.1 First principles

`S2CMatchplayUseSkill(byte attacker, byte target, byte skillId, byte seed, float xTarget, float zTarget, float yTarget)` writes those seven fields and **stops**. **No heading.**

Travel = client **first-frame dir**, then `D3DXVec3Normalize`, then integrate. Planted xyz is the **drop**. ShotRot=0 ⇒ dest does not steer. You cannot get a sideways wall from XML, ShotRot, TPosition, or a cloned skill id. The first-frame build is hardcoded to **packet skillId 27**.

Packet / script mapping:

| Packet | Client | Script | Screen (player at bottom) |
|---|---|---|---|
| `xTarget` | client X | `step.x` | left −X / right +X |
| `zTarget` | client Y (height) | `step.z` | height; 0 for confirmed/recipe spawns |
| `yTarget` | client Z (depth) | `step.y` | toward opponent = +Y = +client Z |

Sidelines ~**±55**. Baselines ~**±115**. `(0,0,0)` is **net-center**.

**Left-right is `step.x`.** `step.y` is along the court. `x=-200` is already off the left edge; `x=-500` looks the same (saturation), **not** a swapped axis.

### 6.2 Four directions

| Direction | Status | Lever |
|---|---|---|
| **LTR** left → right | **CONFIRMED** 18 Aug 2026 **17:43 PT** by Thewind | cave `8B CA` + `dir.x=+1.0` |
| **RTL** right → left | **RECIPE**, not taped | same cave, `BA 00 00 80 BF` (−1.0) |
| **UTD** far baseline → player | **VANILLA AXIS** | forced `dir.z=±1.0` is recipe, **sign unconfirmed** |
| **DTU** player → far baseline | **VANILLA AXIS**, opposite | same, opposite `dir.z`, sign unconfirmed |

### 6.3 Confirmed LTR cave (use this if the fight uses LTR waves)

| Item | Value |
|---|---|
| Live exe sha256 | `cf551df8bd42f32676f8b01e54496a1b74473c90ddfa1354288f6545dea92f7c` |
| Cave | `mov ecx, edx` / **`8B CA`**. `dir.z` = old X-spread. `dir.x` = +1.0 |
| **Do not** | `xor ecx, ecx` / **`31 C9`** (Z-zero). Thewind said that look is wrong. Z-zero sha `967cc05871330521c9c7dc0ade4f9f5989fce5797d81362174c10c985ff71f3f` |
| Spawn | **5 waves, 5 s apart, attacker=4, `xyz=(-200, 0, 0)`** |
| Rejected spawns | `x=+200`, `x=-60` |
| LAA-only backup (no cave) | `eebd71a1b19eca60101a195c49999d29600e6cdd6302c4a6001fb98043f91162` |
| Official (no LAA, no cave) | `5477f0827acae66976403aecd2e9ebffeb4fa28da1fedae5f9541ec25e336c31` |

Cave site: file `0x0EA115` jmp to cave `0x2E5C58`. Cave keys on `cmp [ebp+8], 0x1B` (skillId 27).

**Exe change needs a client restart.** Servers do not. Launch helper `scripts/apply-ft-client-patches.py` currently only reapplies LAA and **must not wipe the cave** (current stub does not write the cave region). `apply_ltr_patch.py` / `bytes.txt` still encode **`31 C9`** — do not “fix” the live exe with that script.

RTL / UTD / DTU need a **new cave + client restart**. Do not invent those hashes.

### 6.4 Hits and the players-only filter

SeaWaves hit **anyone in the foam band**. Facing and spawn-behind do not matter.

Atlantis herding will hurt **anyone in the lane**, including Testmon on a pad, **unless you add a safe-zone filter**.

On `test/wave-origin-directions` `3b11ffc3`, `SpellHitsTargetHandler`:

```
if (game instanceof MatchplayGuardianGame
        && spellHitsTargetExt.getTargetPosition() > 9
        && isSeaWaveHit(skill, skillId)) {
    return;
}
```

`targetPosition > 9` is the **guardian** slot range (boss 10, adds 11, 12). That is **not** a pad check.

**Players-only SeaWave filter needs a game-server rebuild to be live.** The branch has it; running jars may be older.

### 6.5 `-wavetest` / scripts

- `command/wavetest.js` + `event/3_wavetest_autopad.js` must stay in sync.
- Waiting room: `-wavetest` writes `/tmp/jftse-wavetest.arm`, chat “Armed…”. In-match: fires at once. Guardian only.
- After every script edit: **`-reloadScripts`**. Events stay stale in memory otherwise. Wait for “Scripts reloaded”.
- HP pad **30000**. Client HP is a **short**; 99999 overflows.
- **Live copies may still be at x=-500** (confirmed this session: wavetest worktree src is five × `x: -500`; JFTSE `target/dist` is a mixed experimental volley). **Restore −200** for the confirmed LTR look.
- These two scripts do **not** replace `guardian-phase/10`.

Morning origin-findings line “Left-to-right travel is not possible” is **superseded** by the LTR cave.

---

## 7. How to combine green pads + redirected SeaWaves

This is the V2 hook Thewind asked for on 19 Aug. None of it is implemented as a fight.

1. **Bring in** shield-pad code from `feature/guardian-shield-pads-20260817` (`5d984d51`) onto the new branch (cherry-pick / reimplement if taken-over `development` does not have it — it does not; `development` is `568fc3ec`).
2. **Keep** the two pads at (−40, −40) / (40, −40), r=15, Movement-size / zone-file boxes. Not Stroke tints.
3. **Add** SeaWave-on-pad ignore: if player last court pos is inside a **visible** pad circle, drop the SeaWave hit. Do **not** consume a one-shot shield for this. Standing on green during a volley must keep working for the whole volley.
4. **Herd** with redirected SeaWave (LTR confirmed: dummy 4, packet 27, `xyz=(-200,0,0)`, live exe `cf551df8…` / `8B CA`). Anyone **not** in a pad in the foam lane takes the hit — including Testmon.
5. Thewind’s draft also used “safe zones left and right” for **big blizzard**. Same pads can serve both jobs if you decide they should; that decision is **untested**. Do not invent extra pad coordinates unless Thewind asks.
6. Unhooked clients: no green. Logic must still be consistent (absorb/ignore by position, not by whether the quad drew).

---

## 8. Lessons learned (do not repeat these)

Dedicated section. These are live-session facts, 17–18 Aug 2026.

### Record / video

- Record **FIRST**, confirm the file is **growing**, **THEN** `-wavetest`. 90s+ takes.
- Compress ~**960p crf 28 +faststart** or chat will not play. Files **>~8–19 MB fail** (19 MB take would not play; clips were remade).
- Do not start the recorder after the foam. Prior clips started late or after the waves.

### Scripts / client

- **`-reloadScripts` after every script edit.** Events stay stale.
- Exe / cave change = **restart the client**. Script change = reload only. Servers do not restart for the cave.
- Launch helper must not wipe the cave.
- Confirm live exe **sha256 after launch**. Do not trust a file you did not hash post-boot.

### Room / UI (DISPLAY :10, 1280×800 session — session-specific clicks)

- **START stays grey until 3 empty slots are locked.**
- Click the **TOP** of START (~**821, 655**). The Linux dock covers the bottom. A low click opens the host browser.
- Skip intro ~(**959, 62**).
- **Never** Guardian shortcut. Path: **Match Play → CREATE ROOM → Guardian Battle**.
- Account `test` / Testmon.

### Boot

- Splash stuck on **Initializing** = leftover Wine **or** no AC TCP.
- Real AC is TCP **3724**. Healthy: AC `channelActive` within ~15 s, register `result:0`, heartbeats every ~**10 s**.
- Check **client + AC + auth logs on every boot**.

### Display / tools

- `computerUse` / screenshots are **DISPLAY :10 only**.

### Waves — mistakes already made

- **Do not trust** a video-model “no foam / still vanilla”. Thewind already corrected LTR (17:43 PT).
- **Do not flip spawn to +X** because “player is on the right” if they meant “the first take was right”. Confirmed take is **x=-200**.
- **Do not zero Z-spread** (`31 C9`). The fan is the confirmed LTR look. Foam can show at both baselines.
- Do not treat x=-200 vs x=-500 looking the same as an axis swap.
- Origin-findings “LTR is not possible” is stale.
- Players-only SeaWave filter is on the branch; **running jars may be older** — rebuild to see it.
- Official clients without the green hook play Atlantis **blind** — fight logic must still work.

---

## 9. Ops (short)

| Item | Value |
|---|---|
| Auth | TCP **5894** |
| Game | TCP **5895** |
| Relay | TCP **5896** |
| Chat | TCP **5897** |
| AC | TCP **3724** (docker-compose maps 3724:3724) |
| MySQL | **3306**, user/pass `jftse`/`jftse`, db `fantasytennis` |
| RabbitMQ | **5672** |
| Client | Proton **GE 11-1**, `/workspace/jftse-work/client/FantaTennis.exe` |
| `ServerInfo.ini` | `127.0.0.1:5894` |

Check client + AC + auth logs on every boot.

---

## 10. What NOT to touch

- Do **not** overwrite `guardian-phase/10/` `{1,2,3,4}_*.js` until Thewind merges.
- Do **not** commit the dirty `battlemon` checkout.
- Do **not** force-push.
- Do **not** clone onto a new machine unless Thewind asks.
- Do **not** treat PRs **#1 / #3 / #4** as the fight.
- Do **not** install the Z-zero cave (`31 C9`) or run stale `apply_ltr_patch.py` / `bytes.txt`.
- Do **not** invent RTL/UTD/DTU hashes or claim those looks live.
- Do **not** clone FieldItem / new skill id and expect LTR — cave keys on packet 27.
- Do **not** treat one-shot shield as a SeaWave safe zone.
- Do **not** implement the agent’s three-act rewrite as if it were Thewind’s spec.
- Do **not** hardcode `/workspace/...` as `zone-file`.
- Do **not** skip `-reloadScripts`.
- Do **not** trust ClickUp over GitHub.

---

## 11. Sources actually read for this handoff

- `/workspace/jftse-work/docs/seawave-four-directions-2026-08-18.md`
- `/workspace/jftse-work/docs/seawave-ltr-first-cave-2026-08-18.md`
- `/workspace/jftse-work/JFTSE-wavetest/docs/seawave-origin-findings-2026-08-18.md`
- `/workspace/jftse-work/JFTSE-shield-pads/docs/guardian-shield-pads-20260817.md`
- `/workspace/jftse-work/evidence/seawave-ltr/README.md`
- Map 10 scripts in `/workspace/jftse-work/JFTSE/game-server/src/main/resources/scripts/guardian-phase/10/`
- Transcript `/home/box/sand-data/agent-transcripts/b6352f6f-e679-43e5-aca3-e41ddf5fe74a/…jsonl` (Thewind 01:53 PT draft + 02:16 / 07:15 / 07:27 / 23:38 / 00:14 follow-ups)
- Agent memory `/home/box/agent-data/agents/b6352f6f-e679-43e5-aca3-e41ddf5fe74a/memory/log/2026-08.md`
- GitHub API open PRs + `git ls-remote` / fetch of `ThewindMom/JFTSE`
- `SpellHitsTargetHandler` diff on `3b11ffc3`
- `MatchplayGuardianGame.loadAdvancedBossGuardianMode` + `ScriptManagerFactory` groupPath
- Live `wavetest.js` copies (src −500; dist mixed)

Related notes (do not treat morning origin-findings travel claim as current): four-directions PDF, LTR first-cave PDF.

---

## 12. Implementation checklist (do these in order)

1. **Take over** upstream `sstokic-tgm/JFTSE` `development` onto `ThewindMom/JFTSE` `development`. **No merge.** Take it over. (Both were `568fc3ec` when this was written; re-read remotes first.)
2. **Branch** e.g. `feat/atlantis-v2-green-wave` from that taken-over `development`.
3. **Bring in** (cherry-pick or reimplement) shield-pad code from `feature/guardian-shield-pads-20260817` **`5d984d51`** if it is not already on the new `development`. Do not use the unpushed local `79980f93` as the GitHub tip.
4. **Confirm the live exe** is the first LTR cave (`sha256 = cf551df8bd42f32676f8b01e54496a1b74473c90ddfa1354288f6545dea92f7c`, cave+6 = `8B CA`) **if** the fight uses LTR waves. RTL/UTD need a new cave + client restart. Launch helper must not wipe the cave.
5. **Add SeaWave-on-pad ignore (safe zone).** If the player’s last court pos is inside a visible pad circle, drop SeaWave hits. The existing `targetPosition > 9` filter is the **guardian** one; pads need a **position check**. This is **new work**. Rebuild game-server so the filter is actually live. One-shot `shieldActive` is **not** enough for a volley.
6. **Write new phase scripts under `guardian-phase/10-v2/`.** Do **not** delete old `guardian-phase/10/`. Filename-sort still decides phase order inside the folder.
7. **Wire map 10 / `BOSS_BATTLE_V2` to load v2** only after Thewind confirms, or behind a flag. Today the loader only accepts `groupPath == "10"`. `10-v2` will be ignored until you change that.
8. **`-wavetest` for direction proofs** (restore scripts to five × `xyz=(-200,0,0)` + `-reloadScripts`). Then scripted volleys in the fight (5, then 10, tunable). Dummy attacker **4**, packet skillId **27**.
9. **Validate live with Testmon.** Record first (file growing), then fire. HP pad 30000 for wavetest; **do not melt the player in 5 seconds** in the real fight. Lock 3 empty slots, START top edge, skip intro, never Guardian shortcut.
10. **Commit and push the feature branch** to `ThewindMom/JFTSE`. Do not force-push. Do not commit battlemon dirt. Box `gh` has no login — push from the home PC or a Cloud agent that already has GitHub. **Do not clone** onto a new machine unless Thewind asks.

After that: tell Thewind it is done. Do not merge to `development` unless asked.

---

## 13. Quick copy block

```
fork           ThewindMom/JFTSE
upstream       sstokic-tgm/JFTSE
development    568fc3ec   (both, 17 Aug 12:12 PT)   TAKE OVER, do not merge
shield pads    feature/guardian-shield-pads-20260817  5d984d51  +10  NO PR
tutorial pads  feature/twinkle-town-green-tutorial-pad-20260817  NEVER PUSHED
wavetest       test/wave-origin-directions            3b11ffc3  +4   NO PR
               SeaWave ignore guardians: targetPosition > 9
PRs            #1 invite   #3 LTR docs   #4 four-dir docs   (not the fight)
old fight      guardian-phase/10/  four files   KEEP
new fight      guardian-phase/10-v2/            WRITE HERE
V2             UNTESTED DRAFT  18 Aug 01:53 PT  Thewind
               30s strip guardian spells, 35s strip player shields/heals
               5 waves, 5s, 10 waves (tunable; may be too easy)
               immune until both adds dead; blizzard+5 waves; 2 min crab
               full-HP add revive; player heal 20%; then 100% heal, shields off
               30s big-blizz + inferno/5s; 50s charge UNKNOWN; megawave ~20
               stun 5s; add revive; enrage waves+blizz faster
green          pads (-40,-40)/(40,-40) r=15  10s after onStart
               CURRENT: one-shot shield. V2 SAFE ZONE = NEW FILTER
LTR            sha cf551df8…  cave 8B CA  spawn (-200,0,0) attacker=4 skill 27
               NOT 31 C9. NOT x=+200. Live scripts may still say x=-500.
account        test / test / Testmon
do not         commit battlemon, force-push, clone unasked, overwrite 10/
```
