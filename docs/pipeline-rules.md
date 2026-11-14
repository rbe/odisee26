# Pipeline rules

The shared part is the same in every repository. Change it everywhere or nowhere. This repository's facts go under "This repository" and "Divergences".

## Shared rules

The Makefile is the interface. A laptop and CI run the same targets, and a target does the same thing in both places. The workflow calls `make` and moves artifacts. It holds no other logic. Podman is the only container engine. The toolchain runs in a pinned image, so the host needs make, Podman, git, and gh.

`sdlc.mk` at the repository root is the shared makefile. It is byte-identical to the canonical copy. The project Makefile sets `IMAGE`, `PACKAGE`, and `KEEP_VERSIONS`, includes `sdlc.mk`, and defines the project targets.

There is one workflow, `.github/workflows/ci.yml`. Jobs, in order: `setup`, then `build`, `test`, and `docs` beside each other, then `publish-container` (needs `build` and `test`), then `report`. A repository that ships a release asset adds `publish-github`, which also needs `build` and `test`. Each job runs the make target with its name.

`publish-*` runs on a push to the default branch and when `workflow_dispatch` selects it. A tag push does not start a run. Any other branch runs through `report` and does not publish. A manual publish runs `build` and `test` first. In-progress runs are cancelled only off the default branch.

The published version is the nearest `vMAJOR.MINOR.PATCH` tag, without the `v`, plus `-` and the GitHub run number. A default-branch publish also tags `latest`, then keeps that run and the next-lower run number on the same `MAJOR.MINOR.PATCH` line. A manual publish from any other ref tags the version only: it does not move `latest` and cleanup deletes nothing. There is no `edge`, `sha-*`, or bare version tag. No such tag, or a nearer pre-release tag, fails the publish. Cleanup does not delete a GHCR version that still carries `latest` or any tag other than `MAJOR.MINOR.PATCH-<run>`. A missing GHCR package (HTTP 404) is an empty list, so the first publish's cleanup does not fail the job. A skipped or failed publish deletes nothing.

Every upload sets `retention-days`: 1 for intermediates, 14 for `report` and rendered docs. `setup` starts with `cleanup-actions-artifacts`, which deletes this repository's other artifacts once they are an hour old. The current run's artifacts stay, and so do `report` and `docs`.

`report` is last. It still runs when an earlier job fails or is skipped. It writes `build/report/index.md`.

Packages and images contain the tested build only. `.containerignore` keeps out `.claude/`, `.cursor/`, `AGENTS.md`, `CLAUDE.md`, and `docs/`.

## This repository

| | |
| --- | --- |
| Image | `ghcr.io/rbe/odisee26` |
| Package | `rbe/odisee26` |
| Default branch | `master` |
| Toolchain | Gradle wrapper on the host JDK, Java 21 |
| Release | `publish-github` attaches `odisee-<version>-linux-x86_64.zip` |
| Local project version | `2.6.0` in `build.gradle.kts`. Publish does not read it |

`make setup`, `make build`, and `make test` are the Gradle calls. The workflow checks out, uploads and downloads artifacts, and calls `make`. `make login` reads `REGISTRY_USER` and `REGISTRY_TOKEN` and runs `podman login ghcr.io`. `build` stages `build/dist/application.jar` and `build/dist/odisee-linux-x86_64.zip`. `test` needs `setup`, so it runs beside `build`. `test-java` is the LibreOffice proof. It needs `setup` and does not gate either publish job.

The published version is `make version`. A repository tag `v2.6.0` plus run `42` is `2.6.0-42`. This pipeline does not create that git tag. Pushing the tag does not publish. After the tag exists, start `publish-container` or `publish-github` from the Actions tab, or push the default branch.

## Divergences

Each entry names the shared rule, what differs, and why.

- Rule: the toolchain runs in a pinned image. Here: `GRADLE=./gradlew` inside `make setup`, `make build`, `make test`, and `make test-java`. Those targets install OpenJDK 21 when `java` is not 21. The workflow has no Gradle step and no `setup-java` step. Why: Gradle 8.14.5 does not start on a newer JDK, and the office proof and the local stack talk to Podman on the host. Containerising Gradle would hide that socket.
- Rule: there is a `docs` job when the repo renders documentation. Here: there is no `docs` job. `packageDistribution` runs `:documentation:asciidoctor` and stores the HTML in the zip under `docs/`. Why: the book is contents of the release, and a `docs` job would render it a second time.
- Rule: `publish-github` releases the tested JAR. Here: `publish-github` releases the Linux zip. The zip holds the service, `odictl`, the Java client, the LibreOffice extension, and the HTML book. The JavaFX jar stays out of the zip. Why: that zip is the installable product. The image is the other delivery.
- Rule: `publish-container` pushes one image built from the tested JAR. Here: it builds one manifest for `linux/amd64` and `linux/arm64` with `podman build --platform`, `qemu-user-binfmt`, `--layers`, and `--cache-from`. A failed cache read deletes the partial manifest and builds again. There is no `--cache-to`. The `Containerfile` is `webservice/src/main/container/Containerfile`. There is no root `Containerfile`. The context is that tree, without `var/`, plus the tested boot JAR named `webservice.jar`, and `.containerignore` copied in. Why: the image is Ubuntu with headless Writer, Calc, and Impress. The copies are named files, so `.claude/`, `AGENTS.md`, and `docs/` are not layers. The HTML book stays in the zip.
- Rule: `test` is the only test job, and it gates publish. Here: `test-java` runs `:clients:client-java:javaClientOfficeTest` beside `test`. It needs `setup`. `publish-container` and `publish-github` do not need it. `make ci` does not run it. Why: the proof starts LibreOffice in Podman. A failure still lets a publish that already passed `build` and `test` push. `report` still records the result.
- Rule: laptop-only targets are outside the job graph. Here: `stack`, `user`, `token`, `pull`, `run`, `stop`, `rm`, `clean-images`, `test-office`, `test-minio`, and `test-keycloak` stay in the Makefile. No job calls them. Why: they are the local operator commands. Podman remains the only engine.
- Rule: `make ci` runs `setup build test docs report`. Here: `SDLC_CI` is `setup build test report`. The `ci` comment inside `sdlc.mk` still names `docs`. `make help` prints the targets that actually run. Why: there is no `docs` target to call.
