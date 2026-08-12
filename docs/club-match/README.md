# Club Match evidence package

This directory is a review package for the dirty `feature/club-match-mode` working tree. It does not assert that the implementation is complete. The healthy mini-PC validation made no production or test source changes after reconstructing the supplied overlays, and no PDF has been generated.

## Status at a glance

| Claim | Confidence | Evidence |
|---|---|---|
| Native BasicMode quick-create sends C2S `0x138F` with `roomType=0`, `mode=0` | **Observed, genuine client (red and corrected runs)** | Corrected-run manifest in [`partial-green-handoff.md`](partial-green-handoff.md) and the safe derivative in [`final-evidence/`](final-evidence/) |
| The old server returned room type 0 and the genuine Ready button sent ordinary `0x1775` | **Observed, genuine client (red run)** | Historical runtime archive retained outside the repository |
| Client dispatch selects Club Match `0x26F7` for room types 6/7 | **Statically inferred** | Historical reverse-engineering archive retained outside the repository |
| Warfare identity is stored/S2C room type 6, wire mode 0 | **Observed, genuine client (corrected create/list/info run)** | `0x138A`, `0x177A`, and `0x138E` evidence recorded in [`partial-green-handoff.md`](partial-green-handoff.md) |
| Warfare Pet is room type 7, mode 1 | **Statically inferred; intentionally unsupported** | Ghidra logs and rejection tests |
| Corrected identity mapping tests | **Unit-tested: 21/21 focused run passed** | Checksummed historical build archive retained outside the repository |
| Final continuation tree | **Unit-tested: 50/50 focused; 57/57 full reactor; blocker review clean** | [`mini-pc-final-validation.md`](mini-pc-final-validation.md) and final provenance evidence |
| Corrected type-7 service on 5901/9901 registered `0x26F7`, `0x26F9`, `0x26FB` | **Deployed in genuine-client executor** | Checksummed package/journal evidence in [`partial-green-handoff.md`](partial-green-handoff.md) |
| Fresh corrected non-GM room create produced type 6/mode 0 and max play time 5 | **Observed, official native client on mini-PC** | [`mini-pc-final-validation.md`](mini-pc-final-validation.md); [`final-evidence/`](final-evidence/) |
| Fresh calibrated guest room click emitted C2S `0x138B` | **Blocked before protocol emission** | Client displayed `The Club Match is over.`; safe TCP 5901 capture and server journal contain no `0x138B` |
| Genuine client sends corrected `0x26F7` and completes the lifecycle | **Not yet verified** | The nominal green PCAP contains only startup/network traffic |

The final mini-PC run proves corrected room identity. It also proves that a fresh, visually calibrated click on the current room is blocked inside the official guest client before C2S `0x138B`. This is not a server join rejection. **There is still no genuine-client corrected `0x26F7` capture, lifecycle green claim, or final PDF.**

## Navigation

- [`implementation-report.md`](implementation-report.md) — implementation, protocol, lifecycle, tests, deployment, limitations, and exact green-validation plan.
- [`mini-pc-final-validation.md`](mini-pc-final-validation.md) — authoritative final native verdict, exact packets, calibrated client-side join gate, evidence classes, and explicit non-claims.
- [`partial-green-handoff.md`](partial-green-handoff.md) — historical pre-mini-PC handoff. Its stale-click diagnosis is superseded by the final validation.
- [`final-evidence/`](final-evidence/) — safe curated packet/UI/DB/service evidence from the mini-PC run.

## Provenance and integrity

The complete historical runtime archive remains outside the repository because its original captures include disposable authentication traffic. Its SHA-256 is:

```text
ffb6fcc76e6933c5d84ae145b40a803d9f19f31166d89a91187707efd4676efb
```

It contains 288 screenshots, 12 original captures, decoded traces, reverse-engineering logs, and build/runtime evidence. Those materials remain available in the checksummed handoff rather than being published. This repository includes only the final mini-PC report and its curated TCP-5901-only derivative, which was checked for fixture credentials and unrelated traffic before publication.

The related source handoff archive has SHA-256 `f59f7952135287bf6e7b30f6eff5db3c67f6f80a0360ad38409f7869313bc468`.
