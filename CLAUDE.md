# Odisee

Odisee generates documents: a Grails service fills LibreOffice templates from an XML or JSON request and returns or stores the file.

`CLAUDE.md` is the only entry file. The skills are under `.claude/skills/`.

Stages, in order: `r-plan`, `r-develop`, `r-review`, `r-release`. A new or vague request starts at `r-plan`. A request that names a source file, a failing test, or a roadmap defect starts at `r-develop`. Other skills: `r-record`, `r-project-report`, `r-project-setup`, `r-github-pipeline`, `r-skills-review`, `r-component-upgrade`, `r-upgrade-java-components`, `r-onboard`, `r-migrate-docs`, `r-system-state-report` (say `state report`), `r-board-merge-report`, `r-board-merge`. Language skills: `r-jvm`, `r-shell`. Subskills `r-plan` asks and `r-review` checks: `r-security`, `r-api-design`, `r-observability`, `r-performance`, `r-privacy`, `r-ux-ui-design`. A project skill is an extra name listed here. If `CLAUDE.md` lists a project skill `<project>-<stage>`, the stage reads it after its own steps. The addendum holds only what is true for that project. There is no project skill.

This project ships as a container image and as a GitHub release of the Linux zip, with a manual publish.

JDK 21, Gradle 8.14.5, and Grails 7.2.4. Every Make target runs Gradle in Podman. The host needs no JDK. The targets that start containers from Gradle give it the Podman socket of the host. `docs/pipeline-rules.md` records this and the other divergences from the pipeline contract.

Fast-forward the local `master` checkout to `origin/master` before new work. Do not merge a feature branch into that checkout.

The general rules, the Agents section, and the communication contract stand once, in `rules.md` of `r-project-setup`. The next line imports that file. Do not copy it here.

@.claude/skills/r-project-setup/rules.md
