# Agent instructions

Read the skill for the task before planning, editing, testing, or releasing.

- Planning, grilling, and architecture: `.cursor/skills/plan/SKILL.md`
- Develop: `.cursor/skills/develop/SKILL.md`
- Test and review: `.cursor/skills/review/SKILL.md`
- Release: `.cursor/skills/release/SKILL.md`

Standing constraints, one line each. The skill has the detail.

- Fast-forward the checkout to `origin/master`. Do not merge feature branches into it.
- Record decisions in `docs/WAVES.md`, `docs/FEATURES.md`, `docs/BUGS.md`, and the AsciiDoc book under `documentation/`. Do not add a second wave log.
- The book stays AsciiDoc. `README.md` stays the GitHub landing page.
- Write tests for code changes. Run them. Report a failure with the test name and the error. Do not weaken a test to skip it.
- Write in concise, pinpointed language.
- Management is `odictl` only. Product HTTP stays generate, jobs, templates, and `/ready`. The server reads `$ODISEE_HOME/etc` only. There is no second config channel and no password file.
- Odisee generates documents. The Makefile is the interface. Podman is the only container engine.
- Publish runs on the default branch or a manual run. The version is the nearest `vMAJOR.MINOR.PATCH` tag plus the run number. A missing tag fails the publish. The pipeline does not create a git tag.
- The service image excludes `.claude/`, `AGENTS.md`, and `docs/`.
- Agent-only files are this file, `.cursor/rules/odisee.mdc`, and `.cursor/skills/<name>/SKILL.md`.
