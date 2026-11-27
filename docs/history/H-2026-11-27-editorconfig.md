---
id: H-2026-11-27-editorconfig
title: The project has an .editorconfig
status: closed
adrs: []
skills: [r-develop, r-review, r-project-setup]
roadmap: [W-6]
proposals: []
---

# The project has an .editorconfig

## Plan

`make editorconfig` ran without a project `.editorconfig` (W-6). The owner asked for the file in a new pull request.

## Develop

`.editorconfig` starts from the template of `r-project-setup`. With the template only, editorconfig-checker reported 2463 findings in 164 files. Java keeps 2 spaces, because google-java-format writes 2 spaces. Gradle files use 4 spaces. Java, Groovy, XML, AsciiDoc, and `odictl` set no indent size, because they align columns. Files that LibreOffice, Visual Studio, or third parties write set no property. Ten files lost trailing whitespace, and `oracle.sql` uses spaces instead of tabs.

## Review

`make editorconfig`, `format`, `lint`, `test` (0 failures) and `coverage` passed. `make integration` did not run: the cloud container has no Podman socket.

## Release

One pull request on `claude/project-thread-hbconl`.
