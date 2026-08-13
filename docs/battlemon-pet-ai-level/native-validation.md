# Native Battlemon AI level selection

## Result

The validated native Fantasy Tennis client selects a pet AI profile by exact
pet level. JFTSE must send the real level; it must not run TennisAI in Java and
must not clamp levels to 13.

This conclusion combines two evidence classes:

1. **Observed native runtime:** JFTSE sent `PotekoTest` with level byte `0x0B`
   in `0x151B`; the unmodified client rendered `Level : 11`.
2. **Static client reverse engineering:** both `TennisAIPetBasic` and
   `TennisAIPetDouble` pass their level argument directly to the shared
   `TennisAIMgr` lookup. That lookup searches the selected `AI_PetA`…`K`
   vector for an 88-byte record whose first integer equals the requested level.

The executable does not clamp the requested level. A missing record follows the
native `AI Level(%d) requested not found for nPetModel(%d)` path and returns
failure. Consequently, levels above 13 remain deliberately unmapped for owned
pet profiles until a native match establishes some separate fallback.

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
- It does not claim a valid owned-pet mapping for levels 14–250.
- `AI_Default` records beyond 13 are not evidence of an owned `AI_PetA`…`K`
  fallback.
- No pet stat growth, evolution, HP growth, or Java AI is inferred or added.
