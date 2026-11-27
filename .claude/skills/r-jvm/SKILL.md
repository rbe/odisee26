---
name: r-jvm
description: Java, Groovy, Grails, and Gradle. Applies the JVM rules to a change. Use when r-jvm is installed and the files in hand are Java, Groovy, Grails, or Gradle. The stage skill still applies.
kind: language
files: [java, groovy, gradle, kts]
stages: [r-develop]
---

The general rules in `CLAUDE.md` apply.

<purpose>
This skill gives the Make targets and the code rules for Java, Groovy, Grails, and Gradle files.
</purpose>

<steps>
1. If the project has no Gradle build file or no `gradlew`, read `references/tooling.md`. Then run `make jvm-tooling`.
2. Read each reference that `<references>` names for the file.
3. Edit the files by those rules and the rules below.
</steps>

<rules>
- In a recipe, `$(GRADLE)` runs the Gradle wrapper in Podman. `make build` runs Gradle.
- Gradle runs in a container and reaches Podman through `$(GRADLE_PODMAN)`, so the host needs no Java toolchain. A Gradle task that starts containers runs through it, after `make podman-socket`.
- `make wrapper` writes the Gradle wrapper alone.
- The targets `wrapper`, `jvm-tooling`, and `podman-socket` are in `jvm.mk` beside this skill. The Makefile includes it after `project.mk` or `sdlc.mk`.
- If Maven Central answers HTTP 429, set `MAVEN_MIRROR`. `references/tooling.md` says how.
- The formatter of Java is google-java-format. The linter of Java is Checkstyle. The linter of Groovy is CodeNarc. `templates/Makefile.gradle` runs only Checkstyle, so a Groovy project sets `LINT_CMD` to add it.
- Source is in `src/main/java`. Tests are in `src/test/java`.
</rules>

<references>
- `references/tooling.md`: read it before `make jvm-tooling` or `make wrapper` runs, when you change `jvm.mk`, or when the project name, the group, or the version is the question.
- `references/java.md`: read it when the file is Java.
- `references/groovy.md`: read it when the file is Groovy.
- `references/grails.md`: read it when the file belongs to a Grails application.
- `references/gradle.md`: read it when the file is a Gradle file, or when the change touches `make coverage`, `make test-changed`, or a workflow step.
- `references/upgrade.md`: read it when `r-component-upgrade` updates a component of this language.
</references>
