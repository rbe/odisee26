import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.bundling.Zip
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    jacoco
    java
}

group = "org.odisee"
version = "2.6"

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

subprojects {
    plugins.withId("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
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
        "var/document",
        "var/image",
        "var/log",
        "var/merge",
        "var/profile",
        "var/request",
        "var/template",
        "var/tmp",
    )
    doFirst {
        varDirs.forEach { rel ->
            val dir = packageLayout.get().asFile.resolve(rel)
            dir.mkdirs()
            dir.resolve(".keep").writeText("")
        }
    }

    into("bin") {
        from("webservice/src/main/docker/bin")
        filePermissions {
            unix("rwxr-xr-x")
        }
    }
    into("etc") {
        from("webservice/src/main/docker/etc")
    }
    from("webservice/src/main/docker/shell_profile") {
        rename { ".bash_profile" }
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
