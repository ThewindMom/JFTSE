# Atlantis V2 — green pads + LTR SeaWave (2026-08-19)

Branch: `feat/atlantis-v2-green-wave` from taken-over `development` `568fc3ec`.
Old fight `guardian-phase/10/` is **untouched**. New scripts live in `guardian-phase/10-v2/`.

## How to turn V2 on

Default is **off**. Map 10 / `BOSS_BATTLE_V2` still loads Echoes / Maelstrom / Leviathan / Abyssal Reckoning.

Enable one of:

```
# game-server application.properties (restart game-server)
jftse.guardian.atlantis.v2.enabled=true

# or env
JFTSE_ATLANTIS_V2=true

# or JVM
-Djftse.guardian.atlantis.v2.enabled=true
```

After script edits: `-reloadScripts`. After the Java flag / filter change: rebuild game-server.

## What this branch brings in

- Shield pads from `feature/guardian-shield-pads-20260817` @ `5d984d51` (GitHub tip, not the unpushed local extra).
- Wavetest + guardian SeaWave ignore from `test/wave-origin-directions` @ `3b11ffc3`.
- **New:** stand in a **visible** pad circle → SeaWave hits are dropped. Does **not** consume the one-shot `shieldActive`.
- **New:** `10-v2` four-phase fight, LTR dummy-4 SeaWaves at `xyz=(-200,0,0)`, packet 27.
- Wavetest restored to five × `(-200,0,0)` (the confirmed LTR spawn). Live copies on other trees may still say `-500`.

Pads stay at **(-40,-40)** and **(40,-40)**, r=15, 10s after `onStart`. Optional `jftse.guardian.shield-pads.zone-file` for hooked clients. Unhooked official clients do not see green; the filter still works if they stand in the circle.

## Tuned draft (Thewind 18 Aug 01:53 PT, untested)

Kept the beats. Changed only what would melt Testmon in 5 seconds:

| Beat | Draft | This branch |
|---|---|---|
| Strip guardian spells | +30s | +30s |
| Strip player shields/heals | +35s | +35s (server drops those skill hits) |
| First volley | 5 waves | 5, 2.5s apart |
| Gap | 5s | ~5s then second volley |
| Second volley | 10 waves | 10, 2.5s apart |
| Intra-volley spacing | unspecified | **2.5s** (wavetest used 5s; 5s×10 is too long, 0s melts) |
| Crab window | 2 min | 2 min after a 30s waves-only beat |
| Add revive | 100% | 100% |
| Post-revive heal | 20% | 20% |
| Big-blizz dwell | 30s Storm 62 + Inferno 35 / 5s | same |
| Charge | ~50s, animation UNKNOWN | 50s wait, no fake animation |
| Megawave | 20 SeaWaves | 20 at 1.2s (no megawave skill exists) |
| Stun | 5s | 5s, support skills back on |
| Enrage | faster waves + blizzard, no support | 6s wave / 14s blizzard |

Green pads are the safe zone for every LTR volley and for the Storm dwell.

## Live client

LTR is a **client exe cave**, not in git. Confirmed look: cave `8B CA`, sha `cf551df8bd42f32676f8b01e54496a1b74473c90ddfa1354288f6545dea92f7c`. Do not apply `31 C9`. Official unhooked client still completes the fight; they just cannot see the pads.

Native walkthrough is left to Thewind (this session).
