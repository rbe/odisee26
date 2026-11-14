---
name: release
description: Package and publish Odisee. Use when building the zip, the service image, a GitHub Release, or a v* tag.
---

# Release

`README.md` is the GitHub landing page. `packageDistribution` copies it into the zip. The book under `documentation/` stays AsciiDoc. Generated HTML goes in the distribution under `docs/`.

A local build writes `build/distributions/odisee-2.6.0-linux-x86_64.zip`. CI appends the workflow run number.

A `v*` tag publishes the zip. `master` and a `v*` tag push `2.6.0-<run>` and `latest`. A `v*` tag also pushes `${GITHUB_REF_NAME}` to `ghcr.io/rbe/odisee26`. A manual run of another ref pushes only `2.6.0-<run>`. Pull requests do not publish. A publish keeps the current artifact and one former version. `latest` stays. `make pull` and `make run` require `VERSION`.

Read `README.md`, the `Makefile` header, `.github/workflows/build.yml`, and `.github/workflows/image.yml`. Do not copy those files here. `.github/scripts/published-versions.sh` keeps the current published version and one former version.
