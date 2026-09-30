# Halloween Arena: remaining work

Status on 2026-09-30. This is a test setup, not a live server. The runtime
checks below used an earlier build of `feat/halloween-2026-arena` against a
cloned database. The final seal-isolation fix passed unit tests and the build;
native-client and protocol-bot matches were not rerun after it.
[HALLOWEEN-ARENA-SERVER-SETUP.md](HALLOWEEN-ARENA-SERVER-SETUP.md) has the SQL
and implementation details. The native evidence PDF is a local-only lab
artifact at `.amp/in/artifacts/halloween-arena-native/HALLOWEEN-ARENA-EVIDENCE.pdf`,
not a published repository document.

## 1. Implementation delivered to PR 11

The implementation below is committed and pushed to
[PR 11](https://github.com/ThewindMom/JFTSE/pull/11) in
[the implementation commit](https://github.com/ThewindMom/JFTSE/commit/24c3b39cabfd4244cc7225a2403c0631fce6d11b).
The PR is not merged, and the event is not deployed to a live server.

Script (`game-server/src/main/resources/scripts/guardian-phase/6/1_halloween_arena.js`):

- The "Big Inferno incoming!" announcement is removed.
- The phase returns `END_PHASE` when every guardian is dead, like
  `7/2_hbPhase2.js`.
- Witches revive only while Hell Blood is alive.
- Intro line 2 is shorter so that it fits the chat box:
  "Equipped Heal/Shield are sealed. Crystal Heal, Shield, Revive work."
- Small Inferno, Big Inferno, Trick-or-Treat and the rally heal read the living
  players at the moment they fire. This is the same pattern as the Atlantis
  `castGuardianSkill` silence and polymorph targeting
  (`10/1_echoes_of_the_deep.js`). A player who dies between two Small Inferno
  rounds is skipped from the next round on.
- The script no longer sets guardian max health. Health comes from the Arena
  guardian rows (section 3).

Database: the scenario-3 SQL in `HALLOWEEN-ARENA-SERVER-SETUP.md`, including
the Arena guardian rows (boss 103, Witches 101 and 102).

Server (Java):

- `HardModeCommand` and `RandomModeCommand`: client map 6 is added to the same
  deny list that Monslava (7, 8) and Atlantis (10) use. The room gets
  "Hard mode is not allowed on this map".
- New `matchplay/guardian/HalloweenArenaRules.java` holds the Arena rules.
  `isArena` is true only for a Guardian match on client map 6.
  The Heal/Shield seal applies to the whole Arena match. `isArenaBossStage`
  adds `bossBattleActive` and is used only by the crystal pool.
  `isSealedQuickItem` lists the Heal and Shield `Item_Quick` indices.
- `MatchplayGuardianModeHandler.onPrepare`: while the Arena match loads, each
  player gets a match-only quick-slot list with Heal and Shield items set to 0.
  The stock client builds its HUD from this list, so those slots are empty for
  the whole match. `onStart` sends the chat line "Halloween Arena: equipped
  Heal/Shield are sealed this match." `onEnd` sends the real list back, and
  `GameManager.handleRoomPlayerChanges` does the same when a player leaves an
  Arena match. The database slots are never changed.
- `PlayerUseSkillHandler`: a server-side backstop. An equipped Heal or Shield
  used from a quick slot on Arena is rejected before the cooldown check and
  before the item is consumed. Teammates never see the cast. The caster gets
  the chat notice "Equipped Heal/Shield are sealed in the Arena." and the
  unchanged item count.
- `SpellHitsTargetHandler`: for 4 seconds after a rejected use, heal hits of
  that caster and skill are ignored and the server health is sent back to the client.
  Shielded hits on the covered players deal full damage. Shield covers the
  caster. Area Shield covers positions 0 to 3. Crystal Heal lifts only its
  caster's seal. Valid crystal Shields remain effective for their play time,
  even when another player attempts a sealed equipped cast.
- `PlayerPickingUpCrystalHandler`: Arena boss-stage crystals give Revive (index 4) at 35%
  when a teammate is dead. Every other roll gives Heal (index 0) at 70% and
  Shield (index 9) at 30%.
- `MatchplayGuardianModeHandler`: winning the Arena boss stage gives each
  active player one Halloween Coin. The existing anti-cheat rule still applies:
  a boss clear in under 60 seconds gets no reward.
- `MatchplayGuardianGame.createGuardianBattleState`: the standard 1.5 health
  multiplier for four players is skipped on Arena, so health stays linear as
  in the scope brief.
- New unit test `HalloweenArenaRulesTest` (12 tests, including overlapping
  crystal and rejected equipped casts).

## 2. Decisions for the owner

These were chosen during implementation. Change them if they are wrong.

- **Halloween Coin item:** Pumpkin, material item 264 (product 4099). It is the
  ingredient of the Pumpkin Hat and Mask recipes. If the event needs a
  different item, change `HALLOWEEN_COIN_ITEM_INDEX` and
  `HALLOWEEN_COIN_CATEGORY` in `HalloweenArenaRules`.
- **Random mode is blocked too.** Random mode replaces the guardian roster, so
  it would remove the Witches that the script needs.
- **Food heals count as Heal.** The sealed list is SmallHeal, BigHeal, the four
  range heals, FullHeal, Fast SmallHeal, Cookie, Pie, Rice Cake and Juice
  (skill IDs 1, 2, 16 to 19, 23, 24, 30, 31, 33, 39). Shield is skill ID 10,
  Area Shield is skill ID 20.

## 3. Guardian health

Fixed like the other bosses: health comes from the database, not the script.
The script no longer calls `setMaxHealth`. The scenario-3 SQL adds
Arena-only copies of Hell Blood (`BossGuardian` 103), Windra (`Guardian` 101)
and Thundera (`Guardian` 102) with Arena `hpBase` and `hpPer`. The client
health bars now match the server (native run g4: 13000, 3830 and 3830 with two
players). Devaberg keeps rows 31 and 36 unchanged.
Health follows the scope brief for 1 to 4 players: Hell Blood 7000 + 3000 per
player, each Witch 2230 + 800 per player (four players: 19000 and 5430). The
server skips its standard 1.5 multiplier for four players on Arena only; other
bosses keep it. To retune, change `hpBase` and `hpPer` on the copies.

## 4. Heal/Shield seal

Equipped Heal and Shield are sealed for the whole Arena match. The crystal
pool stays boss-stage only.

The stock client builds its in-match quick-slot HUD from the quick-slot list
(`S2CInventoryWearQuickAnswer`, 0x1BD9) it holds when the match loads. A list
sent during the match is ignored (native run g7: Q/W/E stayed filled and still
fired). So the server sends a match-only list in `onPrepare`, with Heal and
Shield set to 0, and sends the real list back at match end or when the player
leaves. The database slots are never changed.

What the player sees (official client, native run g9):

- The chat shows "Halloween Arena: equipped Heal/Shield are sealed this
  match." at match start.
- Q, W and E (Healing Deluxe, Shield, Area Shield) are empty from the first
  frame of the guardian stage to the end of the boss stage. R (Revive, 494)
  stays.
- Pressing Q, W or E does nothing: no animation, no counter change, no packet
  to the server (no seal log line). The database counts stay the same.

The server-side rejection and the 4-second hit filter stay as a backstop for a
client that sends the use anyway. That path is proven with protocol bots, not
with the official client, which no longer sends it.

- The backstop seal lasts 4 seconds (`SEALED_EFFECT_MS`). Shield lasts 3
  seconds, and the client reports heal hits about 150 ms after the use.

## 5. Verification gaps

The final seal-isolation fix passed all 19 game-server tests (including 12
Halloween rules tests) and the JDK 21 affected-reactor package build. Three
overlap regression tests failed before the fix. Native-client and protocol-bot
matches were not rerun after it; the results below are from earlier builds.

Proven in the native official client (run g2): the `-hard` and `-random`
refusals, all three intro lines without truncation, the seal notice and log
line for Shield, RangeHeal4 and Area Shield, unchanged item counts, the match
end and +1 Pumpkin for both players. Run g9 (before the final fix): the match-start
notice, empty Heal/Shield slots in both stages with Revive kept, no effect
from Q/W/E, and unchanged database counts.

- Chicken and Confused visuals in the native client were not proven.
- Movement Speed: the client shows the wing status icon for about 20 seconds.
  The character's speed itself was not measured.
- Witch kills, Inferno cadence and the rally heal were verified on the server
  and with protocol bots, not by native players.
- No clear within three minutes by a team of native players only.

## Not a Halloween issue

- The match ends on the next reported hit after all guardians die, not
  instantly. The Monslava boss scripts behave the same way.
- Every match end logs `PhaseManager is not running`. This comes from the
  existing framework and also happens with the Monslava scripts.
