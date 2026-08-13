# Club Match final native evidence

This is the safe, decisive derivative of the 2026-08-13 final validation archive. See the [native validation report](../native-validation-report.md) for claim boundaries, captions, and the complete missing/non-claims matrix.

- `screenshots/`: 10 audited official-client frames; no login UI, credentials, black/crash frames, or redundant calibration captures.
- `protocol/`: decoded lifecycle, relay, tie, disconnect, and readiness excerpts; no raw authentication capture.
- `tests/`: exact JDK 21 focused, full-reactor, and package logs.
- `provenance/`: source, official-client, and final jar hashes.
- `db/` and `cleanup/`: disposable fixture and resource postchecks.
- [`SHA256SUMS`](SHA256SUMS): hashes for every evidence file in this directory except the manifest itself.

Intentionally excluded: raw authentication/AC PCAP, credential-bearing logs, client binaries and prefixes, deployed jars, database volumes, fixture secrets, crash/black frames, and the external tar archive.
