# Pipeline rules

The rules of the pipeline stand once, in the skills `r-project-setup` (the Make contract) and `r-github-pipeline` (the GitHub contract, the publish rules with `AUTO_PUBLISH`, versions, cleanup, and retention). This file does not repeat them. It holds only the facts of this repository and its divergences.

## This repository

| | |
| --- | --- |
| Image | `ghcr.io/rbe/odisee26` |
| Package | `rbe/odisee26` |
| Default branch | `master` |
| Toolchain | Gradle wrapper in Podman, `eclipse-temurin:21-jdk` (`gradle/Containerfile`). The container-starting targets add the Podman socket of the host |
| Release | `publish-github` attaches `odisee-<version>-linux-x86_64.zip` |
| Local project version | `2.6.0` in `build.gradle.kts`. Publish does not read it |

`make setup`, `make build`, and `make test` are the Gradle calls. The workflow checks out, uploads and downloads artifacts, and calls `make`. `make login` reads `REGISTRY_USER` and `REGISTRY_TOKEN` and runs `podman login ghcr.io`. `build` stages `build/dist/application.jar` and `build/dist/odisee-linux-x86_64.zip`. `test` needs `setup`, so it runs beside `build`. `test-java` is the LibreOffice proof. It needs `setup` and does not gate either publish job.

The published version is `make version`. A repository tag `v2.6.0` plus run `42` is `2.6.0-42`. This pipeline does not create that git tag. Pushing the tag does not publish. After the tag exists, start `publish-container` or `publish-github` from the Actions tab, or push the default branch when `AUTO_PUBLISH` is `true`.

## Divergences

Each entry names the shared rule, what differs, and why.

- Rule: the toolchain runs in a pinned image. Here: every target runs the Gradle wrapper in Podman, in the image of `gradle/Containerfile` (`eclipse-temurin:21-jdk` plus make, git, and the podman client). The host needs no JDK. `test-java`, `test-office`, `test-minio`, `test-keycloak`, `stack`, `stop`, `rm`, and `clean-images` run `GRADLE_PODMAN`. That is the same image with the Podman socket of the host and the host network. `make podman-socket` starts the socket when it is off. Why: those Gradle tasks start LibreOffice, MinIO, and Keycloak through that socket and connect to their published ports on 127.0.0.1. The container that runs Gradle can start other containers on the host, so these targets stay out of `make test`.
- Rule: there is a `docs` job when the repo renders documentation. Here: there is no `docs` job. `packageDistribution` runs `:documentation:asciidoctor` and stores the HTML in the zip under `docs/`. Why: the book is contents of the release, and a `docs` job would render it a second time.
- Rule: `publish-github` releases the tested JAR. Here: `publish-github` releases the Linux zip. The zip holds the service, `odictl`, the Java client, the LibreOffice extension, and the HTML book. The JavaFX jar stays out of the zip. Why: that zip is the installable product. The image is the other delivery.
- Rule: `publish-container` pushes one image built from the tested JAR. Here: it builds one manifest for `linux/amd64` and `linux/arm64` with `podman build --platform`, `qemu-user-binfmt`, `--layers`, and `--cache-from`. A failed cache read deletes the partial manifest and builds again. There is no `--cache-to`. The `Containerfile` is `webservice/src/main/container/Containerfile`. There is no root `Containerfile`. The context is that tree, without `var/`, plus the tested boot JAR named `webservice.jar`, and `.containerignore` copied in. Why: the image is Ubuntu with headless Writer, Calc, and Impress. The copies are named files, so `.claude/`, `CLAUDE.md`, and `docs/` are not layers. The HTML book stays in the zip.
- Rule: `test` is the only test job, and it gates publish. Here: `test-java` runs `:clients:client-java:javaClientOfficeTest` beside `test`. It needs `setup`. `publish-container` and `publish-github` do not need it. `make ci` does not run it. Why: the proof starts LibreOffice in Podman. A failure still lets a publish that already passed `build` and `test` push. `report` still records the result.
- Rule: laptop-only targets are outside the job graph. Here: `stack`, `user`, `token`, `pull`, `run`, `stop`, `rm`, `clean-images`, `test-office`, `test-minio`, and `test-keycloak` stay in the Makefile. No job calls them. Why: they are the local operator commands. Podman remains the only engine.
- Rule: `make ci` runs `setup build test docs report`. Here: `SDLC_CI` is `setup build test report`. The `ci` comment inside `sdlc.mk` still names `docs`. `make help` prints the targets that actually run. Why: there is no `docs` target to call.
- Rule: the project includes `sdlc.mk`, and `sdlc.mk` includes `container.mk` for `image` and `publish-container`. Here: the Makefile sets `CONTAINER_MK := 1` before the include, so `container.mk` adds no target. The Makefile defines `image` and `publish-container` itself. `make help` still prints the `image-ci` line of `container.mk`, and that target does not exist. Why: `container.mk` builds and pushes one single-arch image. This repository pushes one multi-arch manifest with `podman manifest push --all`.
- Rule: `editorconfig`, `format`, `lint`, `integration`, and `coverage` run real tools. Here: they are the stubs of `contract.mk` and fail. `SDLC_CI` does not run them. Roadmap rows [W-2](ROADMAP.md#w-2), [W-3](ROADMAP.md#w-3), [W-4](ROADMAP.md#w-4), [W-6](ROADMAP.md#w-6), and [W-7](ROADMAP.md#w-7) hold the work. Why: the migration to the skills of rbe/ralfskills adds no formatter, no linter, and no refactor.
