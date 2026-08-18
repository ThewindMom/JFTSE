# JFTSE SeaWave — moving the wave in four directions (2026-08-18)

Hand-off for another JFTSE person. First principles first, then the recipes.
Facts only. Unconfirmed items are marked **UNCONFIRMED** / **RECIPE** / **UNKNOWN**.
No invented hashes, addresses, or live results.

**Author:** ThewindMom / JFTSE
**Written:** 18 August 2026, Europe/Berlin (PT). Live exe + hashes re-read on this box ~19:40 PT.
**Do not commit or push from this note.**

Related notes (do not treat the morning origin-findings travel claim as current):

- `JFTSE-wavetest/docs/seawave-origin-findings-2026-08-18.md` — xyz plants the drop; vanilla travel is baseline↔net. The line “Left-to-right travel is not possible” is **superseded** by the LTR cave.
- `docs/seawave-ltr-first-cave-2026-08-18.md` — confirmed first LTR cave + (−200, 0, 0) spawn.
- `evidence/seawave-ltr/README.md` — client RE (mover, ShotRot, skillId 27 first-frame).
- `evidence/seawave-ltr/bytes.txt` — **stale**: still encodes the rejected Z-zero cave (`31 C9`).
- `evidence/seawave-ltr/first-ltr-restore.txt` — restore `31 C9` → `8B CA`.
- `evidence/seawave-ltr/apply_ltr_patch.py` — **stale**: will install Z-zero if run on a clean exe.

---

## Status at a glance

| Direction | Screen | Travel axis | Status | Tonight |
|---|---|---|---|---|
| **LTR** left → right | left → right | +client X / script +X | **CONFIRMED** 18 Aug 2026 17:43 PT by Thewind | live cave + tape |
| **RTL** right → left | right → left | −client X / script −X | **RECIPE**, not live-confirmed | same cave, float −1.0; not taped tonight |
| **UTD** far baseline → player | top → bottom (toward camera) | −client Z / script −Y | **VANILLA AXIS** | already travels this axis; *which way* depends on attacker Z. Forced-cave sign **UNCONFIRMED** |
| **DTU** player → far baseline | bottom → top (away from camera) | +client Z / script +Y | **VANILLA AXIS**, opposite sign | same as UTD, opposite `dir.z`. Forced-cave sign **UNCONFIRMED** |

**UNKNOWN (do not guess live):** RTL live look; forced UTD/DTU cave signs on tape; whether flipping attacker is enough for vanilla UTD vs DTU without a cave.

---

## 1. First principles

The UseSkill packet has **no heading**.

Constructor (live Java):

```
S2CMatchplayUseSkill(byte attacker, byte target, byte skillId, byte seed,
                     float xTarget, float zTarget, float yTarget)
```

It writes those seven fields and stops. There is no yaw, no travel enum, no “direction” byte.

What actually travels is entirely a **client first-frame direction vector**, then normalize, then integrate.

From the 18 August RE note (`evidence/seawave-ltr/README.md`):

1. Consume `0x4EF1E0` copies packet skillId, skill object, ShotSpeed, ShotRot. Targeting 12/13 copies the three floats into instance+0x4C/+0x50/+0x54 and sets +0x58=1. Still no heading float.
2. Tick `0x4F0CA0` walks the projectile list and calls mover `0x4E9CE0` (ShotType 3/6/7 only).
3. **First frame** (skillId 27 at `cmp [ebp+8], 0x1B` / VA `0x4E9D91`) builds `dir` for shot index 0..10.
4. `D3DXVec3Normalize` writes the unit vector into projectile+0x54/+0x58/+0x5C.
5. Later ticks skip the index switch (`projectile+0x10 != 0` → `0x4EA133`) and **freeze** that dir.
6. Every tick at `0x4EA2CD`:

```
pos.x += dir.x * (projectile+0x50) * dt
pos.y += dir.y * (projectile+0x50) * dt
pos.z += dir.z * (projectile+0x50) * dt
```

`projectile+0x50` is `ShotSpeed + index*35` on first frame for skill 27 (RE note; not re-debugged tonight).

Planted xyz is the **drop**. With ShotRot=0 it is not a steering target. Changing xyz never flips heading.

### Two levers (this is the whole trick)

After normalize, one horizontal component dominates. That is the travel axis. The **other** horizontal component is the 11-crest **spread** (wall width or fan).

1. **Which component is large after normalize** = travel axis.
2. **Sign of that component** = which way on that axis.
3. The leftover horizontal component stays the 11-crest spread.

Vanilla: `|dir.z|` dominates → travel baseline↔net; `dir.x = −0.5…+0.5` is full-width wall along X.

LTR cave: `|dir.x|` dominates (`+1.0`) → travel sideline↔sideline; old X-spread is moved onto `dir.z` → crests fan along the court.

You cannot get a sideways wall from XML, ShotRot, TPosition, or a cloned skill id. The first-frame build is hardcoded to **packet skillId 27**.

### Worked normalize (arithmetic, not a live dump)

Attacker Z was not dumped, so vanilla `|dir.z|` is unknown. The point is the ratio. If `|dir.z|` is large versus the 0.5 spread, normalize leaves travel on Z and shrinks the X-spread into a unit vector. LTR numbers below use only the documented floats (`+1.0` travel, `−0.5…+0.5` fan). They are math, not a taped measurement.

| Setup | Before normalize | After normalize | What you see |
|---|---|---|---|
| Vanilla, `|Z| ≫ 0.5` | `(spread, 0, attackerZ)` | `dir.z ≈ sign(attackerZ)`, `dir.x ≈ spread/|Z|` | Full-width wall. Travel baseline↔net. Sign = attacker. |
| LTR center crest | `(+1.0, 0, 0)` | `(+1.0, 0, 0)` | Pure +X. Band travels left → right. |
| LTR edge crest | `(+1.0, 0, ±0.5)` length √1.25 ≈ 1.118 | `(+0.894, 0, ±0.447)` | Still mostly +X. Z-fan is why foam can show at both baselines. Do not zero Z. |
| RTL edge crest (recipe) | `(−1.0, 0, ±0.5)` | `(−0.894, 0, ±0.447)` | Same fan, travel −X. Not taped tonight. |
| Forced UTD (recipe) | `(spread, 0, −1.0)` | mostly −Z, small X | Full-width wall toward player. Sign inferred, not taped. |
| Forced DTU (recipe) | `(spread, 0, +1.0)` | mostly +Z, small X | Full-width wall toward far baseline. Sign inferred, not taped. |
| Rejected Z-zero LTR | `(+1.0, 0, 0)` every crest | `(+1.0, 0, 0)` every crest | Thin net-line. Thewind rejected this look. |

Registers at the cave site (VA `0x4EA111`): edx = dir.x, eax = dir.y, ecx = dir.z. Stores: `[esp+0x18]=edx`, `[esp+0x1C]=eax`, `[esp+0x20]=ecx`, then jmp `0x4EA125`. LTR/RTL rewrite edx (travel on X) and copy old edx into ecx (fan on Z). A forced UTD/DTU cave would leave edx alone (keep the X-spread) and rewrite ecx only.

---

## 2. Coordinate systems (do not mix them)

Client world is D3D Y-up. Court constants from `notes/guardian-shot-ai-re.md` (net Z=0, baselines about **±115**, sidelines about **±55**). Origin-findings live drops at X=±150 already sat on the visible sideline; Y=±150 sat on the baselines. Sideline saturation: x=−200 is already past the left edge, so x=−500 looks the same (confirmed tonight for LTR).

| Name | Axis | Meaning | Scale (from notes) |
|---|---|---|---|
| client X | sidelines | screen left / right | ~±55 |
| client Y | height | up from the court | not a travel axis for SeaWave |
| client Z | baselines | along the court; net = 0 | ~±115 |

Packet floats (`xTarget, zTarget, yTarget`) map as:

| Packet field | = client | = JFTSE script | Screen (player at bottom, looking at opponent) |
|---|---|---|---|
| `xTarget` | client X | `step.x` | Left = −X, Right = +X |
| `zTarget` | client Y (height) | `step.z` | height; 0 for every confirmed / recipe spawn here |
| `yTarget` | client Z (depth) | `step.y` | Up the screen = toward opponent = +Y (script) = +client Z. Down the screen = toward player = −Y (script) = −client Z |

`wavetest.js` builds the packet as `new S2CMatchplayUseSkill(attacker, 4, 27, seed, step.x, step.z, step.y)`. Chat labels print `xyz=(x, z, y)` in that same order.

Origin-findings (vanilla, live): (0,0,0) is **net-center**, not under the player. +Y = far / opponent baseline. −Y = player / near baseline. ±X = sidelines. Dummy 4 and dummy 5 both fire.

Player at −Y looking +Y: **+X is their right**. LTR with `dir.x = +1.0` was taped traveling left → right, so that screen mapping is live-confirmed.

---

## 3. Vanilla first-frame (skillId 27 only)

At first-frame dir build, for shot index 0..10:

```
dir.x = hardcoded spread  −0.5, −0.4, …, 0, …, +0.5    (11 crests)
dir.y = FPU leftover (~0)
dir.z = [esp+0x30]   ; attacker-related Z from 0x524C30
then D3DXVec3Normalize
```

Normalize of (small X, 0, large Z) is why vanilla foam is a **full-width band traveling baseline↔net**. ShotCnt=11 is the 11 crests.

FieldItem XML (`FieldItem_Skills_Ini3.xml`, ID 27, Name SeaWave), read tonight:

| Field | Value |
|---|---|
| ID / Name | 27 / SeaWave |
| ShotType | 6 |
| ShotCnt | 11 |
| Targeting | 13 |
| TPosition | 0.0, 0.0, 150.0 |
| ShotSpeed | 100.0 |
| ShotRot | 0.0 |

Origin-findings also wrote “Skill table id 28”. That wording is **not re-verified** against a non-FieldItem Skill table tonight. Packet skillId is 27. The cave keys on `0x1B` = 27. Use 27.

### Why a FieldItem clone cannot go sideways

- The 11-piece spread and this dir build run only when **packet skillId == 27**.
- A new skill id **loses** the 11-crest spread.
- TPosition is unused for ShotType 6 (falls through to a zero vec at spawn `0x4EF726`). `(0,0,150)` matches the baked knockback axis from the live test, not motion.
- Changing ShotRot / TPosition in server XML does not rotate SeaWave. Client table is encrypted `Res/Script/PubETC/Ini3.res` → `Fielditem_INI3.set`.

### ShotRot is turn-rate, not yaw

At `0x4EA1A7`: `dt * ShotRot` (degrees/second) vs `acos(dot(dir, dest−pos)) * 57.29577`, then rotate by that many degrees converted to rad (`0.017453`, `fsincos`). Dest is the target-player pos (Y forced 0 for skill 27) unless targeting 12/13, in which case dest is the planted xyz.

SeaWave ShotRot=0 → max turn 0 → **dir never steers**. Planted xyz does **not** aim the wave. ShotRot=90 would only enable steering toward dest; it would not rotate the wall.

Knockback X is still forced 0 for skill 27 at `0x4F1C91` (not patched). Expect travel-axis motion with the old along-court pop. LTR knockback feel was **not** re-measured tonight.

---

## 4. Four directions

### 4.1 Left → right (LTR) — CONFIRMED

Confirmed **17:43 PT / 15:43 UTC, 18 August 2026** by Thewind. Foam is a **vertical band** traveling **sideline → sideline (left → right)**. Crests fan along the court (foam can show at both baselines). That fan is the look. **Do not xor ecx (`31 C9`)**. Thewind said the Z-zero look is wrong.

Cave (skillId 27 only), after edx/eax/ecx = (dir.x, dir.y, dir.z) at VA `0x4EA111`:

```
mov ecx, edx            ; 8B CA   dir.z = old dir.x  (−0.5…+0.5 fan)
mov edx, 0x3F800000     ; BA 00 00 80 3F   dir.x = +1.0
; original three stores, then jmp 0x4EA125 (normalize + integrate)
```

| Item | Value (verified on live exe ~19:40 PT) |
|---|---|
| Site VA / file | `0x4EA115` / `0x0EA115` |
| Site bytes | `E9 3E BB 1F 00  90 90 90 90 90 90 90 90 90` (jmp `0x6E5C58` + 9 nops) |
| Cave VA / file | `0x6E5C58` / `0x2E5C58` |
| Cave bytes (30) | `83 7D 08 1B 75 07 8B CA BA 00 00 80 3F 89 54 24 18 89 44 24 1C 89 4C 24 20 E9 AF 44 E0 FF` |
| `.text` VirtualSize | file `0x220`: was `0x2E4C58` → now `0x2E4C80`. SectionAlignment `0x1000` already maps the page. SizeOfImage unchanged. |
| Index-fail path | `0x4EA123` `dd d8` (fstp st0) is **not** touched |
| Live sha256 | `cf551df8bd42f32676f8b01e54496a1b74473c90ddfa1354288f6545dea92f7c` |

Confirmed spawn: **5 waves, 5 s apart, attacker=4, xyz=(−200, 0, 0)** “net center, further left”. Packet skillId 27. Target byte 4. Seed random 0..126.

```
{ attacker: 4, x: -200, z: 0, y: 0, guess: "net center, further left" }  × 5
delay = 500 + i * 5000 ms
new S2CMatchplayUseSkill(4, 4, 27, seed, -200, 0, 0)
```

Sideline scale ~±55: x=−200 is already off the left edge. x=−500 looks the same (saturation), **not** a swapped axis. x=−500 was **not** the confirmed take (ltr-neg500.mp4 exists, 17:45 PT; Thewind has not confirmed −500).

Rejected LTR experiments (do not “improve” back into these):

- Z-spread-zero cave `xor ecx, ecx` (`31 C9`), sha `967cc05871330521c9c7dc0ade4f9f5989fce5797d81362174c10c985ff71f3f`. On disk as `evidence/seawave-ltr/FantaTennis.exe.ltr` and in `apply_ltr_patch.py` / `bytes.txt`. Restored `31 C9` → `8B CA` at 17:29 PT (`first-ltr-restore.txt`).
- Spawn x=+200 (ltr-player-right*.mp4). Wrong side.
- Spawn x=−60 (ltr-net-center*.mp4). Not the look.

Reference tapes (under `evidence/seawave-ltr/`, mtimes PT = UTC+2): `ltr-net-left3.mp4` 15:59 PT (first tape they pointed at); `ltr-firstcave-neg200.mp4` 17:41 PT (restore; confirmed 17:43 PT).

### 4.2 Right → left (RTL) — RECIPE, not live-confirmed

Same cave, same `8B CA` (keep the Z-fan). Change only the travel float:

```
BA 00 00 80 3F    ; +1.0 LTR   →
BA 00 00 80 BF    ; −1.0 RTL
```

Spawn should be on the **right** if you want the band to start on the player’s right and sweep left. Try **x=+200** (past the right edge, same saturation logic as −200 on the left) or **x=+55** (right sideline). Which of those two looks better is **UNKNOWN** — not taped tonight.

State clearly: **RTL was not taped tonight.** Do not claim a live look. If the wall travels the wrong way after the float swap, you have the wrong sign; the RE note already said to flip `3F`↔`BF` if LTR went the wrong way. LTR `+1.0` is the confirmed sign, so `−1.0` is the honest RTL recipe.

### 4.3 Up → down (far baseline → player / toward camera) — VANILLA AXIS

Vanilla (no LTR cave) already travels baseline↔net. Screen: **top → bottom**.

Travel is **client Z / script Y**. The sign of vanilla `dir.z` comes from the **attacker** (`0x524C30`), not from planted xyz. ShotRot=0 ⇒ dest does not steer. Planted Y moves the drop along the court; it does **not** flip heading.

Coordinate notes (origin-findings + guardian-shot-ai-re): −Y = player / near baseline = −client Z. So **−client Z is toward the player / down the screen**. A forced cave that ignores attacker would set:

```
dir.z = −1.0          ; travel toward player  (UTD), IF the coordinate mapping holds
dir.x = old X-spread  ; wall stays full-width along the sidelines
dir.y unchanged
```

That sign is **inferred from the coordinate system**, not taped tonight. Dummy-4 world position was not dumped. Whether vanilla dummy-4 already points this way is **UNKNOWN**.

If the live wall goes the wrong way, flip `dir.z` to `+1.0`.

Honest summary if you cannot prove the sign live:

> Vanilla already travels this axis. Which way depends on attacker Z. A forced cave would set `dir.z` to ±1.0 and keep the X-spread. Sign must be proven live. Whether flipping attacker is enough for vanilla UTD vs DTU without a cave is UNKNOWN.

Exact 30-byte UTD cave encoding (jne displacement, nops) was **not assembled tonight**. Do not invent a hash for a UTD exe. Register-level recipe only: leave edx (X-spread) alone; `mov ecx, 0xBF800000` (`B9 00 00 80 BF`) for −1.0, or `B9 00 00 80 3F` for +1.0; same site jmp to the same padding; skillId 27 only.

To test vanilla UTD/DTU **without** a cave: revert to the LAA-only backup (see hashes), keep skillId 27, plant Y on the far baseline (script `y = +115` or `+150`) or the near baseline (`y = −115` or `−150`), and watch which way the wall walks. That does **not** prove you can *choose* the sign — it only shows what dummy-4’s attacker Z is doing.

### 4.4 Down → up (player → far baseline / away from camera) — VANILLA AXIS, opposite sign

Same as §4.3, opposite `dir.z`. Screen: **bottom → top**.

Forced-cave recipe: `dir.z = +1.0` (if +client Z is toward the far baseline — coordinate notes say yes), keep `dir.x` = old X-spread. Sign **UNCONFIRMED** live.

---

## 5. SHA-256 (verified on this box 18 Aug 2026 ~19:40 PT)

All four files are 3 801 088 bytes.

| File | Role | SHA-256 |
|---|---|---|
| `client/FantaTennis.exe` | **LIVE** first LTR cave (`8B CA`, +1.0). Use this for LTR. | `cf551df8bd42f32676f8b01e54496a1b74473c90ddfa1354288f6545dea92f7c` |
| `client/FantaTennis.exe.seawave-ltr.bak` | LAA-only backup (pre-cave). Site is the original 14-byte stores. Cave region is zeros. VirtualSize `0x2E4C58`. LAA characteristic set (`0x123`). | `eebd71a1b19eca60101a195c49999d29600e6cdd6302c4a6001fb98043f91162` |
| `evidence/seawave-ltr/FantaTennis.exe.ltr` | **DO NOT USE.** Z-spread-zero cave (`31 C9`). | `967cc05871330521c9c7dc0ade4f9f5989fce5797d81362174c10c985ff71f3f` |
| `client/FantaTennis.exe.official` | Stock official (no LAA, no cave). Characteristics `0x103`. Reference only. | `5477f0827acae66976403aecd2e9ebffeb4fa28da1fedae5f9541ec25e336c31` |

Official vs LAA-only differ by PE `IMAGE_FILE_LARGE_ADDRESS_AWARE` (0x20).

Launch helper `scripts/apply-ft-client-patches.py` is an official-test stub: it **only reapplies LAA** and skips invite caves. It does not write the cave region. It must not wipe the LTR cave — the current stub does not. An exe change still needs a **client restart**. Servers do not need a restart for the cave.

`apply_ltr_patch.py` / `bytes.txt` still encode `31 C9`. Do not “fix” the live exe with that script.

---

## 6. Scripts, HP pad, waiting-room arm

Live fire path is `game-server/target/dist/scripts/`. Source copies live under `game-server/src/main/resources/scripts/`.

| Path | Role |
|---|---|
| `…/dist/scripts/command/wavetest.js` | `-wavetest`. Waiting room → writes `/tmp/jftse-wavetest.arm`, chat “Armed. START a guardian match; volley fires after the intro.” In-match → fires at once. Guardian only. |
| `…/dist/scripts/event/3_wavetest_autopad.js` | On `MP_GAME_ANIM_SKIP_END`: pad HP, then if the arm file exists, delete it and fire the same volley. |

**At write time (~19:40 PT) the live scripts are not the confirmed −200 take.**

- src command + src autopad: five steps all `x: -500` “net line, further left (−500)”.
- dist command + dist autopad: mixed experimental steps (`x: -500`, `y: ±500`, `y: -200`, `y: 0`).

To reproduce tonight’s confirmed LTR look: put **both** dist files (and src, if you care about sync) back to five copies of `{ attacker: 4, x: -200, z: 0, y: 0, guess: "net center, further left" }`, then `-reloadScripts`. Chat should show “Reloading commands… / Reloading events… / Scripts reloaded”. Events stay stale in memory otherwise.

These two scripts do **not** replace `guardian-phase/10`.

### HP pad

Client HP is a **short**. 99999 overflows. Pad is 30000.

In-match path (live JS): iterate `game.getPlayerBattleStates()` with `.iterator()` (not `.get(i)`), `setMaxHealth(30000)`, `getCurrentHealth().set(30000)`, `setDead(false)`, then `S2CMatchplayDealDamage(0, 30000, 0, 1, 0.0, 0.0)` (heal skill 1) to sync the bar. Autopad also pads on `MP_GAME_ANIM_SKIP_END` before GuardianAttackTask. The bar may still draw 200/200; popups are real hits. Solo Testmon otherwise dies in about 15 s (origin-findings). Morning 9-drop run: WAVE 1 popup 9708, WAVE 4 popup 9042 — those were vanilla-axis hits, **not** re-measured for LTR.

`-wavetest` / `-reloadScripts`: waiting-room arm vs in-match immediate fire, as above. Guardian Battle Mode only.

---

## 7. Ops (tonight’s session)

| Item | Value |
|---|---|
| Client | `FantaTennis.exe` via Proton **GE-Proton11-1** (`proton-ge11/version`) |
| Auth / game / relay / chat | TCP 5894 / 5895 / 5896 / 5897 |
| AC | Real AC on TCP **3724** (docker-compose maps 3724:3724). Need a live TCP session, not a leftover Wine process |
| Splash “Initializing…” | Leftover Wine **or** no AC TCP. Healthy boot: AC channelActive within ~15 s, then register result:0, heartbeats ~10 s |
| Account | test / Testmon (Lv 01) used tonight |
| Guardian room | Lock the **3 empty slots** before START enables |
| Skip intro | ~top-right. On DISPLAY :10 at 1280×800 the working click tonight was (959, 62) — session-specific, not a client constant |
| START vs Linux dock | Dock covers the bottom UI. Click the **top edge** of START. Tonight: ~(821, 655) on 1280×800. A low click opens the host browser |
| Exe vs scripts | Cave change = **restart the client**. Script change = `-reloadScripts`, no client restart. Launch helper may only set LAA |

---

## 8. UNKNOWN / not re-verified

- RTL live look. Not taped tonight.
- Forced UTD/DTU cave signs. Coordinate notes imply `dir.z = −1.0` toward the player and `+1.0` toward the far baseline. Must be proven live.
- Whether flipping attacker is enough for vanilla UTD vs DTU without a cave. Dummy-4 world position was not dumped.
- Fielditem_INI3.set was not decrypted. Data-clone is still a dead end for travel.
- LTR knockback feel. `0x4F1C91` X-zero is still in the exe.
- Whether x=−500 is “more left but same look” beyond saturation. Not confirmed as the take.
- ShotSpeed / per-crest `+ index*35` and dest-Y force (VA `0x4EA14F`) come from the morning RE note; not re-read in a debugger tonight.
- Which Proton prefix Proton will load if someone launches from a different helper. Confirm live exe sha256 **after** launch.

---

## 9. How to fire each direction (checklist)

One page. Goal: pick a direction, set the matching lever, fire skillId 27, watch travel. If a box fails, stop — do not “tune” spawn to hide a wrong cave.

### Common (every direction)

- [ ] Guardian Battle Mode. Lock the 3 empty slots. START ~top edge (~821, 655 on the 1280×800 :10 session). Skip intro ~top-right.
- [ ] `-reloadScripts` after every script edit. Wait for “Scripts reloaded”.
- [ ] Waiting room: `-wavetest` → “Armed…”. In-match: `-wavetest` fires now. Both are fine.
- [ ] HP pad 30000 (client HP is a short). Autopad chat: “HP padded to 30000…”.
- [ ] Packet skillId **27**. Attacker 4. No new skill id. A FieldItem clone will not pick up the cave and will not go sideways.
- [ ] Record **before** START.
- [ ] Exe change → restart client. Launch helper must not wipe the cave (current stub only sets LAA).
- [ ] After launch: `sha256sum` the live exe. Do not trust a file you did not hash post-boot.

### A. LTR (confirmed)

- [ ] Live sha256 = `cf551df8bd42f32676f8b01e54496a1b74473c90ddfa1354288f6545dea92f7c`
- [ ] File `0x2E5C58` contains `8B CA` at cave+6, then `BA 00 00 80 3F`. **Not** `31 C9`.
- [ ] File `0x0EA115` = `E9 3E BB 1F 00` + 9 nops.
- [ ] Five steps `{ attacker: 4, x: -200, z: 0, y: 0 }` “net center, further left”. Not −500, not +200, not −60.
- [ ] Watch: vertical band, walks left → right, foam may show at both baselines. Labels `xyz=(-200, 0, 0)`.
- [ ] If it is a thin net-line → you have the Z-zero cave. If it is a sideline-spanning wall walking baseline↔net → vanilla (no cave / cave not loaded).

### B. RTL (recipe only — not taped tonight)

- [ ] Same cave as LTR except `BA 00 00 80 BF` (−1.0). Keep `8B CA`.
- [ ] Restart client. Hash will **not** be cf551df8… (do not invent the new hash; hash after you write).
- [ ] Spawn on the right: try `x=+200` or `x=+55`. Which is better is UNKNOWN.
- [ ] Watch: vertical band, walks right → left. If it still goes LTR, the float did not land or the old exe is still loaded.
- [ ] Do not record this as confirmed. Mark the tape RECIPE.

### C. UTD (vanilla axis, toward player / toward camera)

- [ ] **No LTR cave.** Use LAA-only backup sha `eebd71a1b19eca60101a195c49999d29600e6cdd6302c4a6001fb98043f91162`, **or** a forced cave that sets `dir.z = −1.0` and keeps the X-spread (encoding not assembled tonight).
- [ ] Planted Y moves the drop, not the heading. Put the drop on the far baseline (`step.y = +150` or `+115`) if you want to *see* it walk toward you. That does not force the sign.
- [ ] Watch: full-width wall, screen top → bottom. If it walks the other way, vanilla attacker Z is the other sign — that is the UNKNOWN.
- [ ] Do not claim a forced-cave sign without a tape.

### D. DTU (vanilla axis, away from camera)

- [ ] Same as UTD, opposite `dir.z` (`+1.0` if you force it).
- [ ] Plant on the near baseline (`step.y = −150` or `−115`) if you want to *see* it walk away. Still does not force the sign.
- [ ] Watch: full-width wall, screen bottom → top.
- [ ] Whether flipping attacker (dummy 4 vs 5, or a real guardian on the far side) is enough without a cave is **UNKNOWN**.

---

## 10. Quick copy block (LTR confirmed take only)

```
live sha256   cf551df8bd42f32676f8b01e54496a1b74473c90ddfa1354288f6545dea92f7c
LAA backup    eebd71a1b19eca60101a195c49999d29600e6cdd6302c4a6001fb98043f91162
Z-zero WRONG  967cc05871330521c9c7dc0ade4f9f5989fce5797d81362174c10c985ff71f3f
official      5477f0827acae66976403aecd2e9ebffeb4fa28da1fedae5f9541ec25e336c31

site 0x0EA115  E9 3E BB 1F 00  90×9     jmp 0x6E5C58
cave 0x2E5C58  83 7D 08 1B 75 07 8B CA BA 00 00 80 3F
               89 54 24 18 89 44 24 1C 89 4C 24 20 E9 AF 44 E0 FF
look bytes     cave+6 = 8B CA   (31 C9 is rejected)
RTL recipe     BA 00 00 80 BF instead of …3F     NOT TAPED TONIGHT
spawn          5 × attacker=4  xyz=(-200, 0, 0)  skillId 27  5 s
```

Sources read tonight: live `client/FantaTennis.exe` + backups, `S2CMatchplayUseSkill.java`, dist/src `wavetest.js`, `evidence/seawave-ltr/{README.md,bytes.txt,first-ltr-restore.txt,apply_ltr_patch.py}`, `docs/seawave-ltr-first-cave-2026-08-18.md`, `JFTSE-wavetest/docs/seawave-origin-findings-2026-08-18.md`, `FieldItem_Skills_Ini3.xml` SeaWave ID 27, `notes/guardian-shot-ai-re.md` court constants, `scripts/apply-ft-client-patches.py`, `proton-ge11/version`, `first-ltr-restore.txt`.
