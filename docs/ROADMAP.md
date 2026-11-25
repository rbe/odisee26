# Roadmap

The things to do. A row is a feature, a defect, or a question, and is not done. `record` deletes the row when the slice closes. Its number is never issued again. The proposal cell is a link or empty.

| Id | Kind | Title | Proposal |
| --- | --- | --- | --- |
| <a id="w-1"></a>W-1 | defect | A request name, a macro name, a macro library, and a macro language pass a stricter allow-list than "no separator and no `..`" (old label B25, partial in wave 4) | |
| <a id="w-2"></a>W-2 | defect | No formatter: `make format` and `make format-fix` are stubs. The `jvm` skill names google-java-format for Java | |
| <a id="w-3"></a>W-3 | defect | No linter: `make lint` is a stub. The `jvm` skill names Checkstyle for Java and CodeNarc for Groovy | |
| <a id="w-4"></a>W-4 | question | Which target is the one integration entry? Today `test-office`, `test-minio`, `test-keycloak`, and `test-java` each start containers, and `make integration` is a stub | |
| <a id="w-5"></a>W-5 | defect | Delete the root `sdlc.mk`. No makefile includes it since the Makefile includes `.claude/skills/github-pipeline/sdlc.mk` | |
| <a id="w-6"></a>W-6 | defect | No `.editorconfig`: `make editorconfig` fails. The template of `project-setup` sets 2-space indent, and the Groovy and Java files use 4 spaces | |
| <a id="w-7"></a>W-7 | defect | No diff coverage: `COVERAGE_CMD` and `TEST_CHANGED_CMD` are unset, so `make coverage` and `make test-changed` fail. JaCoCo already writes XML in `make test` | |
