# Atlantis V2 — live map 10 fight (2026-08-19)

Branch: `feat/atlantis-v2-green-wave` from taken-over `development` `568fc3ec`.

V2 **replaces** the live Atlantis boss. There is no flag.

| Path | Role |
|---|---|
| `game-server/src/main/resources/scripts/guardian-phase/10/` | **Live** V2 scripts (filename-sorted) |
| `game-server/src/main/resources/scripts/guardian-phase/10-legacy/` | Archived Echoes / Maelstrom / Leviathan / Abyssal Reckoning. Not loaded. |
| `AtlantisV2Rules` | Single source of truth for timings, skill IDs, LTR spawn, hit filters |

Map 10 / `BOSS_BATTLE_V2` loads group `"10"` only. After script edits: `-reloadScripts`. After Java filter changes: rebuild game-server.

## Fight

1. Green Tide — +30s strip guardian spells, +35s strip player shields/heals, 5 then 10 LTR SeaWaves
2. Crab Window — blizzard + 5 waves, 2 min crab, full-HP add revive, 20% player heal, shields stay off
3. Storm Charge — 30s Storm 62 + Inferno 35 / 5s, 50s charge (no fake animation), 20 SeaWaves
4. Abyssal Enrage — 5s stun/recovery with support on, silent add revive, enrage only after those adds die, then faster waves + blizzard with no support

SeaWave: dummy attacker **4**, packet **27**, `xyz=(-200,0,0)`. Intra-volley gap is **2.5s**.

Green pads (−40,−40) / (40,−40), r=15, 10s after `onStart` are SeaWave **safe zones** for the whole volley. That does not consume the one-shot shield. Unhooked official clients do not see green; standing in the circle still works.

Shield and heal strips are independent flags. After the crab revive, heals are 20% and shields stay off. Storm Charge keeps that split. Enrage turns both back on for the 5s stun, then strips both only after the revived adds die.

## Tests

- `AtlantisV2RulesTest` — loader, SeaWave identity, pad/guardian ignore, independent shield/heal strip, heal 20%, spawn contract
- `AtlantisV2ScriptIntegrationTest` — live folder is V2, legacy is archived, scripts bind Java rules
- `AtlantisV2SafeZoneIntegrationTest` — volley-long pad ignore, leave-pad + one-shot shield, not-visible pads
- `AtlantisV2ScriptHarnessTest` — Graal eval of all four phases: LTR packet fields, revive HP, 20% heal window, delayed enrage
- `GuardianShieldPadsTest` — existing pad machine + `isInsideVisiblePad`

Native client walkthrough is left to Thewind. Confirm live exe sha `cf551df8…` / cave `8B CA` before claiming LTR on screen.
