# Battlemon retail static recovery

Date: 2026-08-15

This document records what can be established from the exact retail client and
its decrypted resources without assigning server authority to client-owned
behavior. The executable used for disassembly has SHA-256
`5477f0827acae66976403aecd2e9ebffeb4fa28da1fedae5f9541ec25e336c31`.

## Level progression and AI profiles

`LevelExp_Pet` is a cumulative displayed/persisted EXP progression with levels
1–250. `AI_PetA` through `AI_PetK` are separate AI-difficulty profiles with
sections `Level1` through `Level13`; `AI_Default` has 60 profiles. They are not
the pet EXP table.

The executable's AI loader and lookup are at approximately `0x4fa3e0–0x4fad30`
and `0x4f9940–0x4f9db0`. Twelve pet actor-construction paths around
`0x534e00–0x535300` pass the constant AI level `1`. The lookup diagnostic
`AI Level(%d) requested not found for nPetModel(%d)` therefore describes the
independent constructor argument, not the persisted pet level.

Consequences:

- level 14 is not the first unsupported pet progression level;
- the server must retain the complete 1–250 EXP table;
- the server must not add an autonomous AI loop or derive AI difficulty from
  displayed level without contrary packet evidence; and
- level changes do not allocate primary stats because `Item_PetChar` has no
  STR/STA/DEX/WIL progression columns.

## Newly recovered relay packet `0x3332`

The native relay builder at `0x52be79` and parser at `0x5319de` establish a
25-byte inner packet (8-byte header plus 17-byte body). The exact body layout is:

| Body offset | Width | Proven representation |
|---:|---:|---|
| 0 | 1 | byte |
| 1 | 1 | byte |
| 2 | 1 | byte |
| 3 | 1 | byte |
| 4 | 4 | IEEE-754 float |
| 8 | 2 | uint16/bit-preserving short |
| 10 | 1 | byte |
| 11 | 2 | uint16/bit-preserving short |
| 13 | 2 | uint16/bit-preserving short |
| 15 | 2 | uint16/bit-preserving short |

The builder is selected for internal relay-object type 5. `0x32C9` is selected
for type 1 and `0x32CA` for type 6 in the same routine. This proves packet
identity and serialization only. Field meanings, sender authority, actor
ownership, replay behavior, and the semantic source of body byte 0 remain
unresolved. Registering `0x3332` as a decoded handler would turn an unknown
packet into a server-authoritative mutation path without proof. It remains
unregistered and is never queued for server mutation. Live two-client Guardian
testing later proved that dropping opaque relay traffic only for owned-pet
sessions breaks development-compatible synchronization. The relay therefore
forwards well-framed `0x3332` unchanged only within the authenticated,
non-spectating relay session, matching `origin/development`.

## PET_ITEM table

The retail resource contains exactly indices 1–14 and 16–23. Index 15 is absent.

| Indices | Resource effect | `MaxUse` |
|---|---|---:|
| 1–4 | STR/STA/DEX/WIL +1 | 10 |
| 5–8 | STR/STA/DEX/WIL +2 | 10 |
| 9–12 | STR/STA/DEX/WIL +5 | 10 |
| 13 | current life +1 day | 300 |
| 14 | maximum life +5 days | 50 |
| 16–19 | hunger +5/+10/+20/+50 | 50 |
| 20–22 | energy +5/+10/+20 | 50 |
| 23 | hunger +50 and energy +50 | 50 |

The effect values match `BattlemonLifecycleServiceImpl` and all present indices
have parameterized service coverage. Native runs already prove indices 1, 13,
14, and 23 plus rejection without consumption at signed-byte stat value 127.
The resource does not prove whether `MaxUse` is a per-item use count, a resulting
value cap, or UI metadata; no durable per-item-use counter exists on the retail
wire evidence. It must not be implemented by guessing. Index 15 remains rejected
without consumption.

A byte-aware comparison of every `Item_PetChar` row found no additional species
or numeric columns outside the localized descriptions. Those descriptions are
internally inconsistent across regions and even across individual species. For
example, Goliath has 200 HP in `Desc_uk` and 280 in `Desc_th`; Korean and
Traditional Chinese rows advertise energy 999 and life 365 while the UK rows
advertise energy 50/100 and life 30/60 or 60/120. The currently supported server
matrix follows the values already native-validated by this project: Pikaro has
180 HP, 50 energy, 100 hunger, 30 initial life and 60 maximum life; types 1–8
have 100 energy, 150 hunger, 60 initial life and 120 maximum life, with existing
per-species HP values. Region-dependent marketing text is not sufficient
evidence to rewrite a working species row or claim one historical balance set.

## Client-owned hit and AI behavior

`CombatSkillA` through `CombatSkillG` are byte-equivalent in mechanics. They
define a three-step local combo:

| Step | animation | move self/enemy | hit tuples | transition |
|---:|---:|---|---|---|
| 0 | 8 | 0/0 | `(-1,1,80,1000)`, `(0,1,100,1000)`, `(1,1,300,1000)` | frames 0–30 → 1 |
| 1 | 9 | 1/1 | prior three plus `(0,0,100,1000)` | frames 0–30 → 2 |
| 2 | 10 | 1/1 | `(-1,1,70,1000)`, `(0,1,90,1000)`, `(1,1,300,1000)`, `(0,0,90,1000)` | end |

`CombatSkillMonA` contains only step 0. These tables define animation, movement,
hit geometry/timing, and combo transitions inside the native simulation. They
do not establish a server damage packet or authorize client-reported damage.

`Ini3_PetAHit` through `Ini3_PetKHit` contain the complete species-specific
serve, movement, casting, evasion, spin, hit-plan, and ball-control constants.
Examples include `MaxRunSpeed` from 38.0 to 65.0, `Serve_Time` from 0.5714 to
0.752, and shared `ADD_ITEM_RATE=0.71`. `AI_Pet*` independently contain target
and action choices such as tackle, charge, smash, prediction, direction, slice,
lob, net play, skill-shot chances, crystal pickup radius, and reaction delays.
This is direct evidence that autonomous targeting and timing are client-owned.

The global 65-entry `FieldItem_Skills_Ini3.xml` is already imported into the
server `Skill` table with database IDs equal to client index + 1. Live read-only
comparison showed rows 1–65 present and the sampled damage/rate/property/target
fields matching the XML. This table supplies server combat values; the pet hit
plans do not replace it.

## Lifecycle boundary

No decrypted resource supplies hunger decay, energy decay, or original-server
revive expiry timing. `ETC/Pet` only proves `MinSpeedPercent=30` at zero hunger
and full speed from `MaxSpeedStomach=85` upward.

The implemented lifecycle therefore remains an explicit compatibility policy:
hunger −1 and energy −4 per elapsed 24-hour duration; zero hunger or expiry is
terminal, while zero energy only blocks participation. Revive restores species
energy/hunger and sets expiry to `now + min(lifeMax, 300 days)`. Lifecycle tests
use a fixed injected `Clock`; host/container time must never be changed for an
experiment.

## Remaining discriminating experiments

1. Capture `0x3332` from both owner endpoints and correlate each field with a
   single controlled input/state change before considering a handler.
2. Native-positive test PET_ITEM indices 2–12 and 16–22, then test every
   species cap and determine `MaxUse` by repeated controlled use.
3. Compare known-S0 dead-pet revive results from a historical retail server;
   the current emulator cannot reveal the original server's expiry formula.
4. Levels 14 and 250 now render and can be selected in the final native build.
   Record levels 60, 128, and 249 in otherwise identical completed matches and
   compare actor initialization and relay traces. This extends the static and
   boundary evidence without reintroducing a cap.
5. Keep ranked/matchmaking, alternate topologies, and historical balance out of
   the compatibility implementation until an authoritative server trace or
   historical build establishes them.
