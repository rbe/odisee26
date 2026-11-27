---
id: H-2026-11-27-help-and-platform-tests
title: The tests match make help and the two-platform image
status: closed
adrs: []
skills: [r-develop, r-review, r-jvm]
roadmap: []
proposals: []
---

# The tests match make help and the two-platform image

## Plan

After RalfSkills v0.8.0, `make test` failed 2 of 264 tests on `master`.

## Develop

`MakefileHelpTest` lists the 65 targets of `make help` in their order, with `multiarch-host` from `container.mk`. `docs/pipeline-rules.md` says `make publish-container` pushes one manifest for linux/amd64 and linux/arm64. The comment at `Makefile:79` no longer says the host installs a JDK.

## Review

`make check-docs` passed. `make test` did not run in the cloud container: Maven Central answered HTTP 429. CI of the pull request runs it. `make lint`, `make format`, and `make editorconfig` are still stubs ([W-2](../ROADMAP.md#w-2)).

## Release

One pull request on `claude/project-thread-hbconl`.
