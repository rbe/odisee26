---
name: review
description: Run and review Odisee tests. Use when adding a test, running Gradle or make test targets, or reviewing a change against docs/BUGS.md.
---

# Review

Write a test for a code change. Run it. Report the test name and the error. Do not weaken a test to skip it.

`make test` is `./gradlew :webservice:test` and does not start a container. `make test-office`, `make test-minio`, `make test-keycloak`, and `make test-java` start their own containers and do not use the compose stack. `./gradlew test --tests` selects tests in every project. A project with no matching test succeeds.

The basis tests are the wave 5 table in `docs/WAVES.md`. F12 is in `docs/FEATURES.md`. Do not copy the table. CI unit tests that do not start a container are `.github/workflows/test.yml`.

New bugs go in `docs/BUGS.md`. B25's stricter allow-list stays open until a change closes it.

A fixed bug's "how" can be stale. B21 and B31 describe HTTP Basic and `etc/users`. Current login is bearer tokens and Keycloak.

A change that adds a password file, a second config channel, or a shared `var/template` is a regression.
