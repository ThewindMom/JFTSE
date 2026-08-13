# Club Match documentation and evidence

The authoritative publication is the [native validation report](native-validation-report.md) and its [PDF](native-validation-report.pdf). It records the official-client Warfare Basic lifecycle proven against `feature/club-match-mode` commit `5a29788bd46f97cdc69306b0cffdf84a49ad96d6`, while preserving explicit completion gaps and retail non-claims.

## Status at a glance

| Claim | Confidence | Evidence |
|---|---|---|
| Warfare initialization `0x2700`→`0x2701`, state 3 | **Observed, genuine client** | [Final report](native-validation-report.md) |
| Native Basic create maps mode 0 to type-6 create/list/info | **Observed, genuine client** | [Final report](native-validation-report.md) and [curated evidence](native-evidence/) |
| Opposing-guild join, ready/countdown/designated start, both relay joins, sustained gameplay and points | **Observed with two genuine clients** | [Final report](native-validation-report.md) and [decoded evidence](native-evidence/protocol/) |
| Non-tied expiry sends one `0x26FC 00` per client; guest shows Defeat; room return succeeds | **Observed; host winner UI incomplete** | [Final report](native-validation-report.md) |
| Tied 1–1 expiry sends no result and returns to room | **Observed current JFTSE compatibility behavior; not retail semantics** | [Tie evidence](native-evidence/protocol/tie-deadline-packets.txt) |
| Countdown guest disconnect cancels, preserves host `MASTER`, relists and permits reconnect/rejoin | **Observed** | [Final report](native-validation-report.md) |
| Final JDK 21 release gates | **62 tests green; 11-module package success** | [Test/package evidence](native-evidence/tests/) |
| Warfare Pet, winner panel, remaining rollback/deletion cases, rewards and metagame | **Missing or not proven** | [Prominent non-claims matrix](native-validation-report.md#missing--not-proven--non-claims) |

## Navigation

- [`native-validation-report.md`](native-validation-report.md) / [`native-validation-report.pdf`](native-validation-report.pdf) — authoritative final report and inspected PDF.
- [`native-evidence/`](native-evidence/) — final safe curated screenshots, decoded protocol excerpts, build logs, provenance, DB reset, and cleanup proof.
- [`implementation-report.md`](implementation-report.md) — historical implementation draft, superseded for validation status.
- [`mini-pc-final-validation.md`](mini-pc-final-validation.md) — historical 2026-08-12 partial run and client-side join gate, superseded by the completed continuation.
- [`partial-green-handoff.md`](partial-green-handoff.md) — historical pre-mini-PC handoff.
- [`final-evidence/`](final-evidence/) — earlier safe mini-PC evidence retained for historical traceability.

## Provenance and integrity

The final safe archive remains outside the repository rather than duplicating a tarball in Git. It is 4,036,544 bytes, contains 28 evidence files and 10 audited screenshots, and has SHA-256:

```text
3606bf09de966a29e03fdaafe917e7c36146dcc056ac4b1d50f008ef209e2577
```

Raw authentication captures, credentials, client binaries/prefixes, jars, crash frames, and unsafe logs are excluded. The tracked derivative was checked for fixture credentials and sensitive content before publication.
