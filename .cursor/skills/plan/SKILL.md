---
name: plan
description: Plan a change, grill the scope, and apply Odisee architecture constraints. Use before a new wave, a feature, a bug record, or any change to odictl, HTTP routes, or $ODISEE_HOME/etc.
---

# Plan

Grill the scope before writing a wave. Ask until each answer is one pinpointed sentence.

- What document can the server still generate when the wave ends?
- What is in scope, and what is out on purpose?
- Which landed wave does this assume?
- Which feature ids and bug ids does it close or leave open?
- Does it change `odictl`, HTTP routes, or `$ODISEE_HOME/etc`?
- Which single existing file records it?

Do not open a plan in the project store.

Record a decision in `docs/WAVES.md`, `docs/FEATURES.md`, `docs/BUGS.md`, or the AsciiDoc book under `documentation/`. A new feature goes in `docs/FEATURES.md`. Do not add a second wave log. The wave log is `docs/WAVES.md`.

## Architecture

Management is `odictl` only. `odictl` stays bash. Bash is in the service image. `odictl user NAME` creates the Keycloak user, a confidential client whose id is `NAME`, and `var/user/NAME/template`, `var/user/NAME/work`, and `var/user/NAME/output`. It prints the client id and secret. There is no password file. The API client sends `Authorization: Bearer`. The client id is the Odisee user. `odictl callback-host USER HOST` appends a host for that user to `$ODISEE_HOME/etc/callback-hosts`. A host listed for one user does not allow another user's callback. `odictl bucket USERNAME ENDPOINT REGION BUCKET ACCESSKEY SECRET` writes `$ODISEE_HOME/etc/buckets`. The server reads `$ODISEE_HOME/etc` on use, including `etc/oidc`. One directory is mounted into every replica. Product HTTP stays generate, jobs, templates, and `/ready`.

The operator text is the Lifecycle section of `documentation/src/docs/asciidoc/AdministratorsGuide.adoc` and `documentation/src/docs/asciidoc/Multitenancy.adoc`. Do not paste them here.

Current login is wave 6 F9 in `docs/WAVES.md` and F9 in `docs/FEATURES.md`: OpenID Connect, no password file. Wave 4's password file and B21's bcrypt sentence are history.
