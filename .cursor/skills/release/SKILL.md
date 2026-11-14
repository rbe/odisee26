---
name: release
description: Package and publish Odisee. Use when building the zip, the service image, a GitHub Release, or a v* tag.
---

# Release

`README.md` is the GitHub landing page. `packageDistribution` copies it into the zip. The book under `documentation/` stays AsciiDoc. Generated HTML goes in the distribution under `docs/`.

A local build writes `build/distributions/odisee-2.6.0-linux-x86_64.zip`.

CI is `.github/workflows/ci.yml`. `make publish-github` releases that zip. `make publish-container` pushes the image to `ghcr.io/rbe/odisee26`. The version is the nearest `vMAJOR.MINOR.PATCH` tag plus the run number. A missing tag fails the publish. The default branch also tags `latest`. A manual run from any other ref tags the version only and deletes nothing. A tag push does not publish. This workflow does not create a git tag.

`make pull` and `make run` require `VERSION`.

Read `README.md`, the `Makefile` header, `docs/pipeline-rules.md`, and `.github/workflows/ci.yml`. Do not copy those files here.
