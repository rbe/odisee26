# Agent instructions

Keep the local checkout fast-forwarded to `origin/master`. Do not merge feature branches into it.

Document decisions in the existing product docs: `docs/WAVES.md`, `docs/FEATURES.md`, `docs/BUGS.md`, and the AsciiDoc guides under `documentation/`. Do not add a second wave log.

Write tests for code changes. Run them. Report a failure with the test name and the error. Do not weaken a test to skip it.

Write in concise, pinpointed language.

The book under `documentation/` stays AsciiDoc. `README.md` is the GitHub landing page and the file `packageDistribution` copies into the zip.

Management is `odictl` only. `odictl` stays bash. Bash is always installed in the Odisee service image. Product HTTP stays: generate, jobs, templates, and `/ready`. `POST /user`, `POST /callback-host`, and `POST /bucket` are to be replaced by `odictl` subcommands. That replacement is decided and not implemented.

Configuration the server reads stays in `$ODISEE_HOME/etc`, one directory mounted into every replica. The server does not grow a second config channel.

Do not add agent-only files beyond `AGENTS.md` and `.cursor/rules/odisee.mdc`.
