# Pipeline rules

The shared part is the same in every repository. Change it everywhere or nowhere. This repository's facts go under "This repository" and "Divergences".

## Shared rules

The Make contract is the project-setup skill. Make is the only interface. A person or an agent does the work through Make. `help` is the default goal. A target does the same thing on a laptop and in CI. The required targets are `setup`, `build`, `test`, `editorconfig`, `format`, `lint`, `integration`, and `coverage`. `test-changed` comes with the include. It is not a CI target. Set `TEST_CHANGED_CMD` before the include. Add `docs` only when this repository renders docs, and `login` when it publishes an image. `format` and `lint` run the one existing formatter and the one existing linter the language skill names. `coverage` writes `build/coverage/diff.md` for the lines this change touched. It does not apply a repository-wide percentage. Gradle, npm, Podman, python, and tar run inside those targets. CI runs those same targets.

The GitHub contract is the github-pipeline skill. Podman is the only container engine. The toolchain runs in a pinned image, so the host needs make, Podman, and git. The GitHub CLI runs in Podman, in the image of `make gh-image`. The Node template pins Node 24. A Gradle project uses the JDK in its template. A project diverges by toolchain and writes that down. The project Makefile sets `IMAGE`, `PACKAGE`, and `KEEP_VERSIONS`, includes `.claude/skills/github-pipeline/sdlc.mk` before any target, and defines the project targets. That include already provides `help`, so this repository leaves `project.mk` out. There is no root copy of `sdlc.mk`.

There is one workflow, `.github/workflows/ci.yml`. A step is a checkout, an artifact upload or download, or `run: make <target>`. Jobs, in order: `setup`, then `build` and `test` beside each other (and `docs`, only when the repository renders docs), then `publish-container` (needs `build` and `test`), then `report`. A repository that releases a JAR adds `publish-github`, which also needs `build` and `test`. Each job runs the make target with its name.

`publish-*` runs on a push to the default branch and when `workflow_dispatch` selects it. A tag push does not start a run. Any other branch runs through `report` and does not publish. A manual publish runs `build` and `test` first. In-progress runs are cancelled only off the default branch.

The published version is the nearest `vMAJOR.MINOR.PATCH` tag, without the `v`, plus `-` and the GitHub run number. A default-branch publish also tags `latest`, then keeps that run and the next-lower run number on the same `MAJOR.MINOR.PATCH` line. A manual publish from any other ref tags the version only: it does not move `latest` and cleanup deletes nothing. There is no `edge`, `sha-*`, or bare version tag. No such tag, or a nearer pre-release tag, fails the publish. Cleanup does not delete a GHCR version that still carries `latest` or any tag other than `MAJOR.MINOR.PATCH-<run>`. A missing GHCR package (HTTP 404) is an empty list, so the first publish's cleanup does not fail the job. A skipped or failed publish deletes nothing.

Every upload sets `retention-days`: 1 for intermediates, 14 for `report` and rendered docs. `setup` starts with `cleanup-actions-artifacts`, which deletes this repository's other artifacts once they are an hour old. The current run's artifacts stay, and so do `report` and `docs`.

`report` is last. It still runs when an earlier job fails or is skipped. It writes `build/report/index.md`.

Packages and images contain the tested build only: a binary or a container image. `.containerignore` keeps out `.claude/`, `CLAUDE.md`, `docs/`, and `data/`. Example data ships only from `examples/` when the path and the name say it is an example.

## This repository

| | |
| --- | --- |
| Image | `ghcr.io/rbe/odisee26` |
| Package | `rbe/odisee26` |
| Default branch | `master` |
| Toolchain | Gradle wrapper in Podman, `eclipse-temurin:21-jdk` (`gradle/Containerfile`). The container-starting targets use the host JDK 21 |
| Release | `publish-github` attaches `odisee-<version>-linux-x86_64.zip` |
| Local project version | `2.6.0` in `build.gradle.kts`. Publish does not read it |

`make setup`, `make build`, and `make test` are the Gradle calls. The workflow checks out, uploads and downloads artifacts, and calls `make`. `make login` reads `REGISTRY_USER` and `REGISTRY_TOKEN` and runs `podman login ghcr.io`. `build` stages `build/dist/application.jar` and `build/dist/odisee-linux-x86_64.zip`. `test` needs `setup`, so it runs beside `build`. `test-java` is the LibreOffice proof. It needs `setup` and does not gate either publish job.

The published version is `make version`. A repository tag `v2.6.0` plus run `42` is `2.6.0-42`. This pipeline does not create that git tag. Pushing the tag does not publish. After the tag exists, start `publish-container` or `publish-github` from the Actions tab, or push the default branch.

## Divergences

Each entry names the shared rule, what differs, and why.

- Rule: the toolchain runs in a pinned image. Here: `make setup`, `make build`, and `make test` run the Gradle wrapper in Podman, in the image of `gradle/Containerfile` (`eclipse-temurin:21-jdk` plus make and git). `test-java`, `test-office`, `test-minio`, `test-keycloak`, `stack`, `stop`, `rm`, and `clean-images` run `GRADLE_HOST`, the wrapper on the host JDK 21. `test-java` installs OpenJDK 21 when `java` is not 21. Why: those Gradle tasks start LibreOffice, MinIO, and Keycloak with the podman of the host and connect to their published ports. Gradle inside a container would need the Podman socket of the host and the same network. Roadmap row [W-8](ROADMAP.md#w-8) holds that work.
- Rule: there is a `docs` job when the repo renders documentation. Here: there is no `docs` job. `packageDistribution` runs `:documentation:asciidoctor` and stores the HTML in the zip under `docs/`. Why: the book is contents of the release, and a `docs` job would render it a second time.
- Rule: `publish-github` releases the tested JAR. Here: `publish-github` releases the Linux zip. The zip holds the service, `odictl`, the Java client, the LibreOffice extension, and the HTML book. The JavaFX jar stays out of the zip. Why: that zip is the installable product. The image is the other delivery.
- Rule: `publish-container` pushes one image built from the tested JAR. Here: it builds one manifest for `linux/amd64` and `linux/arm64` with `podman build --platform`, `qemu-user-binfmt`, `--layers`, and `--cache-from`. A failed cache read deletes the partial manifest and builds again. There is no `--cache-to`. The `Containerfile` is `webservice/src/main/container/Containerfile`. There is no root `Containerfile`. The context is that tree, without `var/`, plus the tested boot JAR named `webservice.jar`, and `.containerignore` copied in. Why: the image is Ubuntu with headless Writer, Calc, and Impress. The copies are named files, so `.claude/`, `CLAUDE.md`, and `docs/` are not layers. The HTML book stays in the zip.
- Rule: `test` is the only test job, and it gates publish. Here: `test-java` runs `:clients:client-java:javaClientOfficeTest` beside `test`. It needs `setup`. `publish-container` and `publish-github` do not need it. `make ci` does not run it. Why: the proof starts LibreOffice in Podman. A failure still lets a publish that already passed `build` and `test` push. `report` still records the result.
- Rule: laptop-only targets are outside the job graph. Here: `stack`, `user`, `token`, `pull`, `run`, `stop`, `rm`, `clean-images`, `test-office`, `test-minio`, and `test-keycloak` stay in the Makefile. No job calls them. Why: they are the local operator commands. Podman remains the only engine.
- Rule: `make ci` runs `setup build test docs report`. Here: `SDLC_CI` is `setup build test report`. The `ci` comment inside `sdlc.mk` still names `docs`. `make help` prints the targets that actually run. Why: there is no `docs` target to call.
- Rule: the project includes `sdlc.mk`, and `sdlc.mk` includes `container.mk` for `image` and `publish-container`. Here: the Makefile sets `CONTAINER_MK := 1` before the include, so `container.mk` adds no target. The Makefile defines `image` and `publish-container` itself. `make help` still prints the `image-ci` line of `container.mk`, and that target does not exist. Why: `container.mk` builds and pushes one single-arch image. This repository pushes one multi-arch manifest with `podman manifest push --all`.
- Rule: there is no root copy of `sdlc.mk`. Here: the old root `sdlc.mk` is still in the repository. No makefile includes it. Roadmap row [W-5](ROADMAP.md#w-5) deletes it. Why: setup mode of `github-pipeline` leaves the bytes of a root `sdlc.mk` and names it.
- Rule: `editorconfig`, `format`, `lint`, `integration`, and `coverage` run real tools. Here: they are the stubs of `contract.mk` and fail. `SDLC_CI` does not run them. Roadmap rows [W-2](ROADMAP.md#w-2), [W-3](ROADMAP.md#w-3), [W-4](ROADMAP.md#w-4), [W-6](ROADMAP.md#w-6), and [W-7](ROADMAP.md#w-7) hold the work. Why: the migration to the skills of rbe/ralfskills adds no formatter, no linter, and no refactor.
