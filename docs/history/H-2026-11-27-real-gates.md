---
id: H-2026-11-27-real-gates
title: Format, lint, integration and coverage are real gates
status: closed
adrs: []
skills: [r-develop, r-review, r-jvm]
roadmap: [W-2, W-3, W-4, W-7]
proposals: []
---

# Format, lint, integration and coverage are real gates

## Plan

RalfSkills v0.8.1 runs `make gates` before each push. Four of the six gates were stubs (W-2, W-3, W-7). The owner chose `test-java` as the integration entry (W-4).

## Develop

Spotless with google-java-format formats Java. Checkstyle lints Java, and CodeNarc lints Groovy. JaCoCo and diff-cover write `build/coverage/diff.md`. A Sonnet agent wrote it. The orchestrator removed four CodeNarc exclusions and fixed 14 import findings. The owner chose to fix all 22 catch findings; each catch now names its exception types.

## Review

`make editorconfig`, `format`, `lint`, `test` (295 tests, 0 failures) and `coverage` passed. `make integration` did not run: the cloud container has no Podman socket.

## Release

One pull request on `claude/project-thread-hbconl`.
