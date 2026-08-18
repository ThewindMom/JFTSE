# JFTSE SeaWave LTR — first working cave (2026-08-18)

Hand-off note. Thewind confirmed **17:43 PT / 15:43 UTC, 18 August 2026** that this look matches the first successful tape.

See the PDF next to this file for bytes, hashes, and the reproduce checklist.

## Confirmed

- **Cave:** first LTR (`mov ecx, edx` / `8B CA`). `dir.z` = old X-spread. `dir.x` = +1.0. **Do not zero Z.**
- **Live exe sha256:** `cf551df8bd42f32676f8b01e54496a1b74473c90ddfa1354288f6545dea92f7c`
- **Spawn:** 5 waves, 5s apart, attacker=4, `xyz=(-200, 0, 0)` “net center, further left”
- Foam is a **vertical band** traveling **sideline → sideline (LTR)**

## Axes

- Packet floats: `xTarget, zTarget, yTarget`. Left-right is **X**. Y is baseline↔net.
- Client sidelines ~**±55**. `x=-200` is already past the left edge, so `x=-500` does not look further left.

## Rejected

- Z-spread-zero cave (`31 C9` / sha `967cc058…`)
- Spawn `x=+200` and `x=-60`

Exe change needs a client restart. After script edits: `-reloadScripts`.
