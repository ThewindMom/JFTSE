# GUA-04 native validation

Date: 2026-08-16 (UTC)
Branch base: `origin/battlemon` at `47e634d7dc50022970c38131d1c4f3540c7d2ff0`

## Result

The native-client run disproved the working assumption that `0x18E9` and
`0x22F1` describe pet-only spells. A two-player Guardian match with **no pets**
produced both packets repeatedly. In this build, positions 0 and 1 were the two
human players, optional pet actors use owner position + 2, position 4 emitted
Guardian/serve traffic, and position 10 was a live enemy Guardian.

The server change therefore authorizes enemy-Guardian casts rather than
inventing a pet shield system:

* a scheduled server Guardian attack grants one `(actor, zero-based skill
  index)` cast for 15 seconds;
* only the room master may submit the corresponding `0x18E9` for a live enemy
  Guardian, and the grant is atomically consumed;
* player and pet actors retain the existing endpoint ownership and liveness
  checks;
* a valid cast creates a 15-second hit allowance keyed by actor and database
  skill ID; each valid target can consume it at most once;
* a Guardian hit may be reported only by the endpoint that owns the hit target
  (or by the master when the target is a Guardian);
* unknown actors, targets, skills, expired grants, duplicate casts, duplicate
  hits, and ungranted casts fail closed;
* damage and effect values continue to come from server `Skill` data. Client
  buff/effect fields are not accepted as authority.

No shield duration, shield state, or damage-suppression rule was implemented.
Those semantics were not established by this run.

## Native lab and capture rate

The executable was the PE32 i386 client at
`.amp/tmp/gua04/client/immutable/FantaTennis.exe`, SHA-256
`5477f0827acae66976403aecd2e9ebffeb4fa28da1fedae5f9541ec25e336c31`.
Each client used its own task-owned Win32 Wine prefix. The emulator services,
database, X servers, Wine processes, packet capture, and recordings all ran
inside the task orb; no external emulator or shared service was changed.

Dual 60 FPS capture was attempted, but the two Wine clients plus two encoders
saturated the four-vCPU orb. A short dual-30 calibration worked, while a longer
30 FPS run starved the clients. The highest stable simultaneous rate was 24 FPS.
The retained game recordings report exactly 24/1 average and nominal frame
rates: client A has 1,311 decoded frames and client B has 1,313.

## Authoritative no-pet run

The raw capture covers `2026-08-16T16:06:53.230160000Z` through
`2026-08-16T16:08:15.617549000Z`. It contains 3,562 TCP frames selected by the
capture filter: 413 on game port 5895 and 3,149 on relay port 5896. Both videos
were launched together for the same game interval. The pcap and server logs are
the exact-time authority; the videos provide visual corroboration but do not
contain an independently embedded UTC clock.

Retained files (workspace-relative):

| Evidence | Path | SHA-256 |
| --- | --- | --- |
| raw game + relay pcap | `.amp/in/artifacts/gua04/post/no-pet/native-game-5895-5896.pcap` | `bdfc06f7454c41b807ed4ba747e6c709494f57e0006ee08f39a95747fad25950` |
| client A, 24 FPS | `.amp/in/artifacts/gua04/post/no-pet/client-a-game-24fps.mkv` | `1c9c33d5760a30ec9e11803ec6d89075e37d531339702b4a238c6387d902065b` |
| client B, 24 FPS | `.amp/in/artifacts/gua04/post/no-pet/client-b-game-24fps.mkv` | `78021ffac7cd05bbc55457b98244a56701a2ed15a6fb35f66cd1c65c915303a2` |
| decoded wire table | `.amp/in/artifacts/gua04/decoded/no-pet-wire.tsv` | `679814279fb2e44c7d1f232e1eb7a8c62278c18b91b36362c9089860e2ac0c3c` |
| decoded skill/hit excerpt | `.amp/in/artifacts/gua04/decoded/no-pet-events.txt` | `19972a52738f7d9321865669d9fab8922a9fabdf8678fa336eca3f006fab547b` |
| game log | `.amp/in/artifacts/gua04/post/no-pet/game-rerun.log` | `0c30d79b8e0e0a40a9bf20c535ac2e88c3629079a8558d637b13cff03588d5e2` |
| relay log | `.amp/in/artifacts/gua04/post/no-pet/relay-rerun.log` | `2ddcfdffef0f8c9d592e018a7cfa52f66ac0944545b40e79c7690e49ca3722cb` |

### Correlated packet timeline

These are server UTC timestamps from the retained decoded excerpt. The raw TSV
preserves pcap epoch timestamps for packet-level correlation.

| UTC | Packet | Actor → target | Skill | Observation |
| --- | --- | --- | --- | --- |
| 16:06:55 | `0x18E9` | 10 → 0 | index 11 | enemy Guardian cast; proves traffic exists with zero pets |
| 16:07:03 | `0x18E9` | 10 → 0 | index 3 | a different server-granted cast |
| 16:07:11 | `0x18E9` | 10 → 0 | index 5 | cast |
| 16:07:12 | `0x22F1` | 10 → 0 | ID 6 | matching index-to-ID relation (`5 + 1`) and one-second cast-to-hit interval |
| 16:07:29 | `0x18E9` | 10 → 0 | index 10 | cast |
| 16:07:30 | `0x22F1` | 10 → 1 | ID 11 | first observed target for this area cast |
| 16:07:30 | `0x22F1` | 10 → 0 | ID 11 | second target; one cast can hit multiple distinct targets |
| 16:07:37 | `0x18E9` | 10 → 0 | index 11 | repeated cast of a previously observed skill |
| 16:07:39 | `0x22F1` | 10 → 1 | ID 12 | first distinct target |
| 16:07:39 | `0x22F1` | 10 → 0 | ID 12 | second distinct target |
| 16:07:54 | `0x18E9` | 10 → 0 | index 5 | repeated cast |
| 16:07:55 | `0x22F1` | 10 → 0 | ID 6 | repeated cast-to-hit relation |

`0x18E9.skillIndex` is zero-based while `0x22F1.skillId` and database `Skill.id`
are one-based. The capture establishes one hit per distinct target for the
observed area casts, not one hit total per cast. It does **not** establish that
the client-provided cast target is the complete hit set. For that reason the
allowance permits each server-validated live target once, while replaying the
same `(cast, target)` fails.

The many `0x22F1` packets with actor 4 and skill ID 0 are serve/ball mechanics,
not evidence of a pet or spell cast, and remain on the pre-existing sentinel
path.

## Controls and limits

The completed native control was two players, zero pets. Existing focused tests
cover zero, one, and two optional Guardian pets and keep dedicated Battlemon
rules unchanged. Two ordinary pet fixtures (types 1 and 9) were also created in
the isolated lab database for native controls. The native inventory displayed
the type-1 Pikaro fixture with its ordinary life/energy/hunger/stat fields; that
screen and the matching database rows are retained at
`.amp/in/artifacts/gua04/controls/ordinary-pet/client-a-inventory.png`
(`cfd3c101d6b9fd7d259257498853a0a3f1b6a213c14adeabda0c8f51a5e69873`)
and `.amp/in/artifacts/gua04/controls/ordinary-pet/db-fixtures.tsv`
(`8f9e92ffbc283cae3e70cbdaeca56fac0dbce56f52a068bff54fcd9bf17ec61a`).
These ordinary pets do not carry spell or shield metadata in this
protocol/data model. A second complete match with that pet attached was not
obtained, so the ordinary-pet control is inventory/database evidence rather
than a packet/video match matrix.

The requested shield matrix could not be claimed as completed. Static server
data associates database skill ID 9 (named `Miniam` in the imported table, while
one client-resource lookup suggested “Shield”) with some **enemy Guardians**
having `btItemId=3`, including Guardian map 4 at a 20% selection chance. The
successful native run used map 1, whose Guardians do not have skill 9. No
captured sequence established shield activation, owner/teammate/enemy shield
targeting, blocked damage, consumption, expiration, or duration. In particular,
there is no evidence in the repository, packet declarations, imported data, or
native zero-pet control that a “shield-capable pet” exists. Treating that phrase
as established would have fabricated semantics.

Accordingly, the following remain unknown and fail closed rather than being
implemented speculatively:

* whether skill 9 is in fact the visible shield in this client build;
* valid shield target classes;
* activation-to-expiration duration;
* whether a blocked hit consumes it, and whether consumption is partial;
* whether replay is purely visual or mutates combat state;
* any client buff fields associated with the effect.

Replay rejection is exact within one pending allowance: the same target cannot
consume it twice, and the allowance expires after 15 seconds. `0x22F1` carries
no cast nonce or seed, so a delayed byte-identical hit arriving after a later
legitimate cast of the same actor/skill is not distinguishable on the wire from
that later cast's real hit. No stronger cross-cast claim is made.

## Verification

Focused authorization/replay coverage is in `GameSessionTest` and
`BattlemonActorPolicyTest`. It exercises one-shot cast grants, grant expiry,
wrong actor/skill rejection, room-master authorization, target ownership,
one-hit-per-target atomic consumption, wrong-target rejection, and duplicate
replay rejection. The focused run passed 28/28 tests. The full `mvn test`
reactor passed all 11 modules and 152 tests with zero failures, errors, or
skips, finishing at `2026-08-16T16:35:54Z`. The retained logs are
`.amp/in/artifacts/gua04/tests/focused-tests.log`
(`d191648cd0ae4b589cb4827d0b5c54f740e7e099457ede966e562d8eb8f54989`)
and `.amp/in/artifacts/gua04/tests/full-reactor.log`
(`f5505796489ad103d7a7f20e6e92fafd7fce567b9d76ced0b5f97550b0b89192`).
