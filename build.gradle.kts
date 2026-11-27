import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.plugins.quality.CodeNarcExtension
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    jacoco
    java
    id("com.diffplug.spotless") version "8.11.0"
}

group = "org.odisee"

// Local project version. Publish uses `make version` (the nearest vMAJOR.MINOR.PATCH
// tag plus GITHUB_RUN_NUMBER) and does not read this value. ODISEE_BUILD_NUMBER
// still renames a local build, for example 2.6.0-42.
val odiseeBaseVersion = "2.6.0"
extra["odiseeBaseVersion"] = odiseeBaseVersion
val odiseeBuildNumber = System.getenv("ODISEE_BUILD_NUMBER")?.trim()?.takeIf { it.isNotEmpty() }
version = if (odiseeBuildNumber == null) odiseeBaseVersion else "$odiseeBaseVersion-$odiseeBuildNumber"

tasks.register("printVersion") {
    group = "Odisee"
    description = "Write the project version to build/odisee-version.txt"
    val resolved = version.toString()
    val versionFile = layout.buildDirectory.file("odisee-version.txt")
    inputs.property("version", resolved)
    outputs.file(versionFile)
    doLast {
        versionFile.get().asFile.writeText(resolved)
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

jacoco {
    toolVersion = "0.8.13"
}

tasks.named<JacocoReport>("jacocoTestReport") {
    reports {
        xml.required.set(true)
    }
}

// google-java-format is the one formatter of Java. Groovy and Kotlin files are not formatted.
// Spotless checks the Java files of every project in one place. Generated sources are under build/.
repositories {
    mavenCentral()
}

spotless {
    java {
        target("*/src/**/*.java", "clients/*/src/**/*.java")
        targetExclude("**/build/**")
        googleJavaFormat("1.28.0")
    }
}

subprojects {
    // Checkstyle is the one linter of Java. The rules are in config/checkstyle/checkstyle.xml.
    plugins.withId("java") {
        apply(plugin = "checkstyle")
        extensions.configure<CheckstyleExtension> {
            toolVersion = "14.4.0"
            configFile = rootProject.file("config/checkstyle/checkstyle.xml")
        }
        // Checkstyle reads hand-written files only, not the generated XML binding sources.
        tasks.withType<Checkstyle>().configureEach {
            exclude("**/generated/**", "**/build/**")
        }
        apply(plugin = "jacoco")
        extensions.configure<JacocoPluginExtension> {
            toolVersion = "0.8.13"
        }
        // One JVM for each test class, so the coverage map of make coverage names the tests that cover a file.
        tasks.named<Test>("test") {
            setForkEvery(1)
        }
    }
    // CodeNarc is the one linter of Groovy. The rules are in config/codenarc/codenarc.groovy.
    plugins.withId("groovy-base") {
        apply(plugin = "codenarc")
        extensions.configure<CodeNarcExtension> {
            toolVersion = "3.6.0"
            configFile = rootProject.file("config/codenarc/codenarc.groovy")
            maxPriority1Violations = 0
            maxPriority2Violations = 0
            maxPriority3Violations = 0
        }
    }
    plugins.withId("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        }
    }
    // A --tests filter is applied to every project. A project with no match still succeeds.
    tasks.withType<Test>().configureEach {
        filter {
            isFailOnNoMatchingTests = false
        }
    }
}

// One XML report for the whole build. diff-cover reads it in make coverage.
// Each project with the jacoco plugin adds its execution data, sources, and classes.
val jacocoDiffReport = tasks.register<JacocoReport>("jacocoDiffReport") {
    group = "Odisee"
    description = "Write build/reports/jacoco/diff/jacocoDiff.xml from the unit tests of every project"
    reports {
        xml.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/diff/jacocoDiff.xml"))
        html.required.set(false)
    }
}

subprojects {
    val unitTested = this
    plugins.withId("jacoco") {
        jacocoDiffReport.configure {
            dependsOn(unitTested.tasks.named("test"))
            executionData.from(unitTested.layout.buildDirectory.file("jacoco/test.exec"))
            sourceDirectories.from(unitTested.files("src/main/java", "src/main/groovy"))
            classDirectories.from(
                unitTested.fileTree(unitTested.layout.buildDirectory.dir("classes")) {
                    include("java/main/**", "groovy/main/**")
                },
            )
        }
    }
}

tasks.register("osInfo") {
    group = "Odisee"
    description = "Show information about the operating system"
    doLast {
        if (Os.isFamily(Os.FAMILY_WINDOWS)) {
            println("*** Windows")
        }
        if (Os.isFamily(Os.FAMILY_MAC)) {
            println("*** macOS")
        }
    }
}

tasks.register<Zip>("packageDistribution") {
    group = "Odisee"
    description = "Zip the Linux x86_64 runtime: service, Java client, extension, and docs"
    archiveBaseName.set("odisee")
    archiveClassifier.set("linux-x86_64")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    dependsOn(
        ":webservice:bootJar",
        ":clients:client-java:jar",
        ":oxt:buildExtension",
        ":documentation:asciidoctor",
    )

    val packageLayout = layout.buildDirectory.dir("package-layout")
    val varDirs = listOf(
        "var/deploy/fonts",
        "var/log",
        "var/profile",
        "var/tmp",
    )
    doFirst {
        val root = packageLayout.get().asFile
        root.deleteRecursively()
        varDirs.forEach { rel ->
            val dir = root.resolve(rel)
            dir.mkdirs()
            dir.resolve(".keep").writeText("")
        }
    }

    into("bin") {
        from("webservice/src/main/container/bin")
        filePermissions {
            unix("rwxr-xr-x")
        }
    }
    into("etc") {
        from("webservice/src/main/container/etc")
    }
    from(packageLayout)
    from("webservice/build/libs/webservice-$version.jar") {
        rename { "application.jar" }
    }
    into("client") {
        from("clients/client-java/build/libs/client-java-$version.jar")
    }
    into("extension") {
        from("oxt/Odisee/dist/Odisee.oxt")
    }
    into("docs") {
        from("documentation/build/docs")
    }
    from("LICENSE")
    from("LICENSE-Apache2.0")
    from("README.md")
}

tasks.named("build") {
    dependsOn("packageDistribution")
}
