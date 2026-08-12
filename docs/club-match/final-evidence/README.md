# Mini-PC final evidence

This is the safe, curated subset of the final isolated native run. The full
authentication/anti-cheat capture and full authentication journals are excluded
because they contain credentials or replayable session material.

- `native/` contains the decisive two-client screenshots only.
- `protocol/` contains decoded safe excerpts and a time/port-limited PCAPNG for
  TCP 5901 from 2026-08-12 10:09:20Z through 10:12:10Z. It excludes login,
  authentication, and anti-cheat periods.
- `db/` contains task-local disposable fixture state before fresh auth and at
  the final client-side join gate.
- `services/` contains listeners, dedicated handler/queue evidence, and process
  ownership.
- `build/` contains the exact JDK 21 focused test/package logs and JAR hashes.
- `source/` contains a patch and byte-exact overlay against bundle HEAD
  `54dd3cacf7f0c58ab0c6a542416fb5be56b1a44b`.
- `provenance/` contains input, branch, client, and isolation records.

The authoritative result is partial green: corrected room creation passed, but
the official guest client blocked a calibrated click on the fresh room with
`The Club Match is over.` before emitting C2S `0x138B`. Ready and lifecycle
validation were therefore not reached.
