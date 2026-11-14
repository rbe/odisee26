---
name: develop
description: Implement a change in odisee26. Use when editing server, client, container, or extension code, including git on master.
---

# Develop

Fast-forward the checkout to `origin/master` before new work. Do not merge a feature branch into that checkout.

JDK 21, Gradle 8.14.5, Grails 7.2.4. `./gradlew build` compiles, runs the unit tests, writes the zip, and does not start a container.

The root `Makefile` is the frontend. Read its header. Run `make help`. Do not invent a second how-to.

Write in concise, pinpointed language.

Keep these decisions:

- A new `RequestContext` per call.
- `SafePaths` keeps names inside their root.
- `TemplateLocator` and `OutputFormats` are the lookup and the format decision.
- Writer, Calc, and Impress keep separate instruction sets.
- Do not add an HTTP admin route.
- Do not add a config file the server reads outside `$ODISEE_HOME/etc`.
- The JavaFX jar stays out of the Linux zip.
- The VB.NET client is not a CI job.

The before/after is the Refactorings section of `docs/WAVES.md`. Do not paste that code here.
