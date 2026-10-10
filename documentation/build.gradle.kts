plugins {
    id("org.asciidoctor.jvm.convert") version "4.0.5"
    base
}

repositories {
    mavenCentral()
}

tasks.named<org.asciidoctor.gradle.jvm.AsciidoctorTask>("asciidoctor") {
    // Includes such as UsersGuide.adoc sit next to Odisee.adoc.
    baseDirFollowsSourceDir()
    sources {
        include("Odisee.adoc")
    }
    setOutputDir(layout.buildDirectory.dir("docs"))
    attributes(
        mapOf(
            "build-gradle" to file("build.gradle.kts"),
            "endpoint-url" to "https://odisee.org",
            "source-highlighter" to "rouge",
            "imagesdir" to "./images",
            "toc" to "left",
            "icons" to "font",
            "setanchors" to "",
            "idprefix" to "",
            "idseparator" to "-",
            "docinfo1" to "",
        ),
    )
}

tasks.named("assemble") {
    dependsOn(tasks.named("asciidoctor"))
}
