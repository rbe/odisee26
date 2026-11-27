<purpose>
This file gives the JVM facts that `r-component-upgrade` needs. It adds no step.
</purpose>

<rules>
- The JDK is `JDK_IMAGE` in the Makefile. The Makefile of the pipeline also has `JRE_IMAGE`, and the Containerfile has the same `JRE_IMAGE` default.
- Gradle is `GRADLE_VERSION` in the Makefile. `GRADLE_IMAGE` reads it. The wrapper in `gradle/wrapper/` is the Gradle that the project runs.
- A library or a plugin is in the build file. The version catalog holds it when the project has one.
- To change the Gradle version, set `GRADLE_VERSION` and run `make wrapper`. Commit `gradlew`, `gradlew.bat`, and `gradle/wrapper/`.
- The tag of `GRADLE_IMAGE` ends in a JDK number, such as `-jdk25`. A change of the JDK major version changes `JDK_IMAGE`, `JRE_IMAGE`, and that tag in the same slice.
- A build file or a catalog entry changes a library version. The collection has no target that writes a lock file. `make setup` resolves the test classpath and writes nothing.
- A Groovy or Grails component follows the same pins. The collection names no other pin for them.
</rules>
