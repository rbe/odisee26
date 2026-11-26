# JVM tooling

## `make jvm-tooling`

`make jvm-tooling` sets up a new Gradle project in one run. It does these things in order:

1. If neither `build.gradle` nor `build.gradle.kts` exists, it writes `build.gradle` from `templates/build.gradle`.
2. If neither `settings.gradle` nor `settings.gradle.kts` exists, it writes `settings.gradle` from `templates/settings.gradle` with the project name `NAME`. The default of `NAME` is the repository name of the remote `origin`, else the directory name.
3. If `config/checkstyle/checkstyle.xml` is missing, it writes that file from `templates/checkstyle.xml`.
4. If `gradlew` is missing, it runs `make wrapper`, which writes the Gradle wrapper.
5. It adds `build/` and `.gradle/` to `.gitignore`.
6. It runs `make format-fix`.

## After the first run

Set the project name in `settings.gradle`. Set the group and the version in `build.gradle`. Gradle names the JAR after them: `make build` writes `build/libs/<name>-<version>.jar`.

A project that ships an image or a runnable JAR removes the comment marks from the `jar { manifest }` block of `build.gradle` and names its main class. Without it, `java -jar` fails with "no main manifest attribute".

Commit `gradlew`, `gradlew.bat`, and `gradle/wrapper/`.

## The templates

`templates/build.gradle`, `templates/settings.gradle`, and `templates/checkstyle.xml` are the build file, the settings file, and the Checkstyle config that the commands of `templates/Makefile.gradle` need. If the project has none of these files, `r-project-setup` copies them to `build.gradle`, `settings.gradle`, and `config/checkstyle/checkstyle.xml`.
