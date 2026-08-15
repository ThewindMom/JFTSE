# Native Battlemon AI level selection

## Result

The validated native Fantasy Tennis client selects a pet AI profile by exact
AI-profile level. JFTSE must not run TennisAI in Java or silently rewrite
persisted levels. A later constructor-level trace proved that the actor passes
constant AI profile level 1 independently from the displayed/persisted pet
level. Battlemon participation and newly awarded pet EXP therefore use the
complete 1–250 progression rather than stopping at level 13.

This conclusion combines two evidence classes:

1. **Observed native runtime:** JFTSE sent `PotekoTest` with level byte `0x0B`
   in `0x151B`; the unmodified client rendered `Level : 11`.
2. **Static client reverse engineering:** `TennisAIPetBasic` and
   `TennisAIPetDouble` pass an AI-profile argument to the shared `TennisAIMgr`
   lookup, which searches the selected `AI_PetA`…`K` vector for an exact key.
   Pet actor construction supplies that argument as constant 1 independently.

The executable does not clamp its requested AI-profile level. A missing profile
follows the native `AI Level(%d) requested not found for nPetModel(%d)` path.
Twelve actor constructors around `0x534e00–0x535300`, however, request profile
level 1 rather than the displayed pet level. The former level-13 policy and
4,807 EXP cap were based on conflating these domains and have been removed.

## Inconclusive level-14 runtime experiment

The fixture set player 2's pet to level 14 and cumulative EXP 4,808 while the
control owner's pet remained level 1. Both owners selected their pets and the
server completed room and relay admission. At 2026-08-14 13:28:42 UTC both
relay clients registered and both game clients received:

```text
SMSGStartGame result=0
```

The level-14 owner disconnected at 13:28:44, less than two seconds later. The
control client stopped producing relay traffic and timed out at 13:29:43. The
captured client screens show a blank level-14 process and a Wine `Program
Error`/failed debugger attachment on the control process. No gameplay frame was
reached. Because the level-1 control was also unhealthy, this experiment does
not establish that level 14 caused the failure or provide a clean surviving
control. It is consistent with the static missing-profile risk but is not
independent runtime proof of that risk.

Two setup recordings were captured at verified 60/1 FPS, but they ended before
the decisive start and therefore are not presented as crash evidence. The
available runtime evidence is the synchronized server packet/disconnect
timeline and the post-failure native screenshots.

## Historical level-13 policy experiment

After implementing the compatibility boundary, a second run swapped the
fixture: player 2 was the level-1 control and player 3 owned the level-14 pet.
The unmodified client displayed `BoongaTest` as level 14 with cumulative EXP
4,808 and next threshold 1,085. The level-1 owner created a Battlemon room; the
level-14 owner then received `Could not enter the room`, while the server sent
`SMSGRoomJoin result=65526`. The control owner remained healthy in the room.

Both desktops were captured at 1280×800, 60/1 FPS for 300 seconds (18,000
frames each). This only validates the now-removed server rejection. It does not
prove that level 14 caused the earlier two-client initialization failure and is
not a reason to reject level-14 pets.

## Final progression-boundary run

A fresh emulator image built from `battlemon` was exercised with independent
level-14/4,808-EXP and level-250/1,408,515-EXP fixtures. The unmodified client
rendered both levels and allowed both pets to be selected without disconnecting.
This final run is retained as native screenshots and packet regressions. The
associated 60 FPS capture stopped at login before UI automation and is not used
to claim the pet dialogs. The earlier level-14 policy experiment did capture
the level-14/4,808-EXP dialog at 1280×800, 60/1 FPS for 300 seconds and 18,000
frames (SHA-256
`c1732bf5c175e612df322606764741bc7971847b818a37e56391a999f37fb2cb`).
Together these establish native parsing and UI selection at both progression
boundaries. They do not claim a completed level-250 match or that displayed
progression changes the constructor's constant AI profile.

## Exact native contract

Validated executable:

```text
FantaTennis.exe SHA-256
5477f0827acae66976403aecd2e9ebffeb4fa28da1fedae5f9541ec25e336c31
```

The client initializes twelve adjacent profile vectors from the default AI file
and `AI_PetA.ini` through `AI_PetK.ini`. Each owned-pet file loaded 13 records,
with exact keys `Level1` through `Level13`. Runtime memory inspection showed
different record hashes for low and higher levels (for example, model 1:
`Level3=acd4d976250f…`, `Level6=a607518735d3…`, and
`Level11=d840c3258bb8…`). This confirms that these are distinct loaded profiles,
not aliases to one fixed pattern.

The lookup at native VA `0x004F9BD0` receives:

```text
[ebp+0x08] requested level
[ebp+0x0c] destination AI object
[ebp+0x10] nPetModel
```

It selects the model vector, walks records in 88-byte steps, compares each
record's first integer to the requested level, and copies the matching record's
gates, probabilities, timings, and movement values into the AI object. The
Basic call site is at `0x004F4289`; the Double call site is at `0x004F4CDC`.

## Controlled level-11 observation

The fixture changed only persisted `Pet.level` for pet ID 2 from 3 to 11.
`expPoints`, type, STR, STA, DEX, and WIL stayed unchanged. At
`2026-08-13T21:59:23.436Z`, game-server emitted:

```text
Packet id=0x151B
... 50 00 6F 00 74 00 65 00 6B 00 6F 00 54 00 65 00 73 00 74 00 00 00
0B 03 01 00 00 C8 00 00 00 0F 0F 0F 0F ...
^^ level 11
```

The same running unmodified client then displayed:

```text
Name : PotekoTest
Level : 11
```

This proves the client accepted JFTSE's higher level independently of the four
primary statistics. Combined with the constructor-to-lookup dataflow above, it
establishes that native pet AI initialization requests `Level11`, rather than a
fixed profile or a Java-side decision tree.

## Reproduction

Run the checked-in verifier against the exact client and a game-server packet
log containing the controlled pet-data response:

```bash
python3 scripts/verify_native_pet_ai_level.py /path/to/FantaTennis.exe \
  --packet-log /path/to/game-server-packets.log \
  --pet-name PotekoTest \
  --expected-level 11
```

The verifier checks executable provenance, all eleven owned-pet resource paths,
the `Level%d` formatter, loader loop, exact-level lookup, missing-level path,
both pet-AI call sites, and the named pet's level byte in `0x151B`.

## Scope and non-claims

- This proves native selection of an exact loaded `[LevelN]` profile; it does
  not reimplement or fully describe lob, smash, prediction, or delay decisions.
- AI profile levels above 13 remain unestablished, but displayed pet levels do
  not select those profiles. The level-14 runtime experiment was inconclusive.
- `AI_Default` records beyond 13 are not evidence of an owned `AI_PetA`…`K`
  fallback.
- No pet stat growth, evolution, HP growth, or Java AI is inferred or added.
