# Atlantis V2 — live map 10 fight (2026-08-19)

Branch: `feat/atlantis-v2-green-wave` from taken-over `development` `568fc3ec`.

V2 **is** the Atlantis boss. There is no flag, no `10-v2` folder, and no archived old fight.

| Path | Role |
|---|---|
| `game-server/src/main/resources/scripts/guardian-phase/10/` | The only Atlantis scripts. Loaded automatically, same as Monslava map 7/8. |
| `AtlantisV2Rules` | Single source of truth for timings, skill IDs, LTR spawn, hit filters |

Map 10 / `BOSS_BATTLE_V2` loads group `"10"` at match start through `MatchplayGuardianGame.loadAdvancedBossGuardianMode()`. Restart game-server after a rebuild. No `-reloadScripts` is required for a normal start.

The boss always spawns with two distinct, randomly rotated members of the lizard family (Dolizard, Penlizard, Belizard, Holizard, Silizard, or Elizard); random-guardian mode cannot replace them with non-lizards.

## Fight

1. Green Tide — only the adds use SeaWave or Blizzard until either reaches 50% HP; Royal Lizard is silent. Guardian attacks then stop for repeating three-wave volleys with 1s spacing and randomized 4–5s rests. Once one add dies, the volleys grow to five waves. Every wave independently randomizes lateral X and its safe enemy-court depth. Once both adds die, Royal Lizard uses SeaWave, HomingBall, or Blizzard until 90% HP, then only HomingBall plus the same randomized five-wave pattern until 70% HP. Storm, Water Pillar, Magma, and Inferno are absent from Phase 1.
2. Twin Tides — both attendants return at 60% HP through separate rebirth casts targeting their original left and right battle slots, and shield Royal Lizard. Within a 10% health difference both attendants remain vulnerable; at 10% or more, only the lower-health attendant is protected until players bring down the higher one. A 10% / 20% / 30% imbalance escalates the randomized safe-court pattern from 3 SeaWaves to 5 waves plus Water Pillar, then 8 waves plus Water Pillar and Blizzard. Imbalance volleys begin every 12s. Below 10%, both attendants become vulnerable and must die within 10s; failure revives the dead attendant at 30% HP in its original slot. Heals are 20%, shields are enabled, and Royal Lizard casts Homing Ball every 10s.
3. Rising Tide — Royal Lizard is vulnerable from 70% to 25% HP and begins at Maximum Tide. Tide level is controlled only by tennis points: every player-side ball loss raises it by one, and every ball won against Royal Lizard lowers it by one. Direct damage and boss HP do not change the Tide. Low Tide uses Homing Ball every 10s, Rising adds Water Pillar every 15s, High adds Blizzard every 20s, and Maximum adds Storm every 30s. Casts are staggered by at least 2s. Heals remain at 20%, shields are enabled, and SeaWave, Magma, and Inferno are absent.
4. The Drowned Crown — Royal Lizard rises from 25% to 35% HP during a 5s calm and revives both attendants at 30% through separate rebirth casts targeting their original left and right battle slots. Every player-side ball loss heals Royal Lizard by 2% and each living attendant by 8%. Killing attendants permanently gives Royal Lizard Blizzard, then Storm; burning the boss to 5% first makes it consume each survivor for 5% HP. Once alone, Royal Lizard uses cumulative 8s/12s/16s/24s attacks. At 5%, healing ends, ten enemy-court waves form the Last Tide, and the final attack cadence becomes 6s/10s/12s/18s.

From Phase 2 onward, Tidal Convergence prevents a permanent heal/shield bunker without disabling support skills. Every 5s the server checks live court positions. If at least two living players remain within 25 court units, chat gives a 3s warning. The server then recalculates every remaining connected cluster and casts one coordinate-targeted Water Pillar at each cluster's center; spreading cancels the cast. Separate clusters receive separate pillars, so a distant player cannot pull the punishment into empty court space. The post-cast cooldown shortens through the fight: 18s in Twin Tides, 15s in Rising Tide, and 12s in The Drowned Crown. This uses existing server position messages and native Water Pillar packets; it adds no client patch or invisible damage.

SeaWave in Phases 1, 2, and 4 is cast as an actorless `S2CMatchplayUseSkill(4, 4, 27, ...)`: dummy attacker 4, target 4, skill **28** / packet **27**. The live-client packet trace established that negative depth is the near court behind the players, so every encounter wave independently chooses only from the positive enemy-court depths `50`, `75`, and `100` while also randomizing lateral X. There is no server path that selects a rear-court origin, Java SeaWave helper, or forced damage; collision, damage, travel, and tumble use the normal client-reported behavior. Waves within each volley are 1s apart.

The support exemption is announced by player name only. It emits no Firework, heal pulse, crystal, or other visual marker. The independently selected Phase 1.3 support player keeps healing and shields through the 90%–70% section. Area-heal and area-shield collision reports arrive independently from affected ally clients with synthetic attacker slot 4; the server associates those reports with the selected player's authorized cast so nearby allies receive the effect, while non-selected players still cannot cast support during suppression. The distributed executable is stock and has no Atlantis Magma hook, and the server does not reject the retail stage's synthetic Magma packet. The encounter scripts themselves never cast Magma.

## Axes (do not use the PDF Y/Z labels)

UseSkill writes `(xTarget, zTarget, yTarget)`. Client dest-copy at `0x4EF370` stores that order into instance `+0x4C / +0x50 / +0x54`. D3D is Y-up.

| Packet field | Memory | Client D3D | Court |
|---|---|---|---|
| `xTarget` | dest[0] | **X** | sidelines, left / right |
| `zTarget` | dest[1] | **Y** | height |
| `yTarget` | dest[2] | **Z** | baseline ↔ net |

Court feet already use that X/Z pair. Spawn `Point(20, -75)` / `(-20, -75)` — Java `y` is client **Z**, player half.

Origin-findings live drops match this if you keep the systems separate: packet `(-150,0,0)` sat on a sideline; packet `(0,0,-150)` sat on the player baseline. Those are packet X and packet **Y**, which is client Z.

Wrong: `zTarget → client Z`, or `yTarget → client Y`, or “script Y is up-down on screen” if that is heard as D3D Y. D3D Y is height. Screen up/down (baseline↔net) is D3D **Z** / packet `yTarget`.

LTR/RTL is still client **X**. Vanilla (no cave) still travels client **Z**.

Shield and heal strips are independent flags. During Twin Tides and Rising Tide, heals are 20% and shields are enabled. The Drowned Crown keeps shields enabled throughout, permits full healing during its 5s calm, then returns healing to 20% for the fight.

## Tests

- `AtlantisV2RulesTest` — loader, SeaWave identity, guardian-target ignore, independent shield/heal strip, heal 20%, spawn contract
- `AtlantisV2ScriptIntegrationTest` — live folder is only the four V2 scripts; old Echoes/Maelstrom/Leviathan/Abyssal files are absent
- `AtlantisV2ScriptHarnessTest` — Graal eval of all four phases: randomized safe enemy-court SeaWave depths, slot-targeted rebirths, revive HP, 20% heal window, delayed enrage

Native client walkthrough is left to Thewind. The distributed executable is the stock/no-directional-cave build (`SHA-256 5477f0827acae66976403aecd2e9ebffeb4fa28da1fedae5f9541ec25e336c31`); do not install the experimental LTR/RTL or rear-origin caves. Official unhooked clients stay on the vanilla client-Z axis while the server pins Atlantis origins to the enemy court.
