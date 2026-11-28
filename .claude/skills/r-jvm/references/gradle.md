# Gradle

The Gradle wrapper is the Gradle the project runs. Versions live in the version catalog when the project has one. The template build file has them inline.

`make build` and `make test` call the wrapper. A workflow step calls `make`, not the wrapper.

`make format` runs google-java-format through Spotless (`spotlessCheck`). `make lint` runs Checkstyle (`checkstyleMain`).

## Coverage and changed tests

`make coverage` runs JaCoCo's XML through diff-cover. It also runs `jacoco_cover_map.py` from `r-project-report`. That script reads the JaCoCo execution data and writes the coverage map: each test class, and the source files that ran in its JVM.

The test task sets `forkEvery = 1`, so each test class has its own JVM and its own entry in the coverage map. With one JVM for all tests, `make test-changed` runs every test.

If the build has several modules or Groovy trees, diff-cover does not find the sources. `jacoco_repo_paths.py REPORT.xml OUTPUT.xml` of `r-project-report` writes a copy of the report with the path of each source file from the repository root. diff-cover then reads that copy.

`jacoco_summary.py` of `r-project-report` writes `build/summaries/coverage.md` from `jacocoTestReport.xml`, for the report job of `r-github-pipeline`.

`make test-changed` gives the test classes that `changed_tests.py --print` names to Gradle with `--tests`.
