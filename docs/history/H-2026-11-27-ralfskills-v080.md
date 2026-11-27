---
id: H-2026-11-27-ralfskills-v080
title: Move to RalfSkills v0.8.0 and drop the own multi-arch image code
status: closed
adrs: []
skills: [r-plan, r-develop, r-review, r-release, r-record, r-github-pipeline, r-jvm, r-project-setup]
roadmap: []
proposals: []
---

# Move to RalfSkills v0.8.0 and drop the own multi-arch image code

## Plan

The owner decided that odisee26 uses `container.mk` for `image` and `publish-container`, and that the Mac runs only the Odisee image, so the Brewfile goes.

## Develop

`apm.yml` pins the tag `v0.8.0` and no longer lists `r-upgrade-java-components`. The Makefile sets `IMAGE_CONTEXT`, `CONTAINERFILE`, `IMAGE_PREPARE`, and `IMAGE_BUILD_ARGS`. It no longer defines `CONTAINER_MK`, `image`, or `_publish-container`. Two Groovy tests now assert that new text. The Brewfile and its two documentation lines are gone. The language skill is `r-jvm`.

## Review

`make -n image` and `make -n publish-container` show the expected commands. The divergence about the own image is deleted from `docs/pipeline-rules.md`. The result of each gate is in the pull request report.

## Release

Not invoked: the owner merges the pull request.
