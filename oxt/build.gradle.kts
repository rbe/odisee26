import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.testing.Test
import java.util.Properties

plugins {
    java
}

group = rootProject.group
version = rootProject.version

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.named<Jar>("jar") {
    enabled = false
}

val fallbackVersion = rootProject.extra["odiseeBaseVersion"] as String
val overrideAddons = providers.gradleProperty("odisee.override.addonsxcu").isPresent
val gitTag = providers.exec {
    commandLine("git", "describe", "--tags", "--abbrev=0", "--match", "v[0-9]*")
    workingDir(rootProject.projectDir)
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim() }

fun extensionName(dir: File): String {
    val props = Properties()
    val file = dir.resolve("extension.properties")
    if (file.isFile) {
        file.reader(Charsets.UTF_8).use { props.load(it) }
    }
    val raw = props.getProperty("ext.name")?.takeIf { it.isNotBlank() } ?: dir.name
    return raw.replace(" ", "").replace("-", "")
}

fun taskName(name: String, dev: Boolean): String {
    val base = name.replaceFirstChar { it.lowercaseChar() } + "Oxt"
    return if (dev) "${base}Dev" else base
}

fun registerMetadata(dir: File, name: String): TaskProvider<JavaExec> {
    val metadataDir = layout.buildDirectory.dir("extension-metadata/$name")
    val updateFile = layout.projectDirectory.file("${dir.name}/dist/${name}-update.xml")
    return tasks.register<JavaExec>("${name.replaceFirstChar { it.lowercaseChar() }}ExtensionMetadata") {
        group = "Odisee"
        description = "Write $name LibreOffice extension descriptors"
        classpath = sourceSets.named("main").get().runtimeClasspath
        mainClass.set("org.odisee.oxt.ExtensionDescriptors")
        inputs.dir(dir.resolve("ext"))
        val props = dir.resolve("extension.properties")
        if (props.isFile) {
            inputs.file(props)
        }
        inputs.property("gitTag", gitTag)
        inputs.property("fallbackVersion", fallbackVersion)
        inputs.property("overrideAddons", overrideAddons)
        outputs.dir(metadataDir)
        outputs.file(updateFile)
        args(
            dir.absolutePath,
            metadataDir.get().asFile.absolutePath,
            updateFile.asFile.absolutePath,
            gitTag.get(),
            fallbackVersion,
            overrideAddons.toString(),
        )
    }
}

fun registerZip(dir: File, name: String, dev: Boolean, metadata: TaskProvider<JavaExec>): TaskProvider<Zip> {
    val metadataDir = layout.buildDirectory.dir("extension-metadata/$name")
    val archive = if (dev) "${name}_dev.oxt" else "${name}.oxt"
    return tasks.register<Zip>(taskName(name, dev)) {
        group = "Odisee"
        description = "Build the ${if (dev) "development" else "production"} LibreOffice extension ($archive)"
        archiveFileName.set(archive)
        destinationDirectory.set(layout.projectDirectory.dir("${dir.name}/dist"))
        includeEmptyDirs = false
        isReproducibleFileOrder = true
        isPreserveFileTimestamps = false
        duplicatesStrategy = DuplicatesStrategy.FAIL
        dependsOn(metadata)
        from(dir.resolve("ext")) {
            if (overrideAddons) {
                exclude("etc/Addons.xcu")
            }
        }
        from(metadataDir) {
            include("META-INF/manifest.xml", "description.xml", "etc/Addons.xcu")
        }
        filesMatching("bin/**") {
            permissions {
                unix("r-xr-xr-x")
            }
        }
    }
}

val extensionDirs = layout.projectDirectory.asFile.listFiles { file ->
    file.isDirectory && file.resolve("ext").isDirectory
}?.sortedBy { it.name } ?: emptyList()

val production = mutableListOf<TaskProvider<Zip>>()
val development = mutableListOf<TaskProvider<Zip>>()
extensionDirs.forEach { dir ->
    val name = extensionName(dir)
    val metadata = registerMetadata(dir, name)
    production.add(registerZip(dir, name, false, metadata))
    development.add(registerZip(dir, name, true, metadata))
}

tasks.register("buildExtension") {
    group = "Odisee"
    description = "Build production LibreOffice extensions"
    dependsOn(production)
}

tasks.register("buildExtensionDev") {
    group = "Odisee"
    description = "Build development LibreOffice extensions"
    dependsOn(development)
}

tasks.register("world") {
    group = "Odisee"
    description = "Build development and production LibreOffice extensions"
    dependsOn("buildExtension", "buildExtensionDev")
}

tasks.named<Delete>("clean") {
    extensionDirs.forEach { delete(it.resolve("dist")) }
}

tasks.named("assemble") {
    setDependsOn(listOf(tasks.named("buildExtension")))
}

val odiseeDir = extensionDirs.first { it.name == "Odisee" }
val odiseeName = extensionName(odiseeDir)

tasks.named<Test>("test") {
    dependsOn("buildExtension", "buildExtensionDev")
    useJUnit()
    systemProperty("odisee.oxt", odiseeDir.resolve("dist/${odiseeName}.oxt").absolutePath)
    systemProperty("odisee.dev", odiseeDir.resolve("dist/${odiseeName}_dev.oxt").absolutePath)
    systemProperty("odisee.update", odiseeDir.resolve("dist/${odiseeName}-update.xml").absolutePath)
    systemProperty("odisee.ext", odiseeDir.resolve("ext").absolutePath)
    systemProperty("odisee.gitTag", gitTag.get())
    systemProperty("odisee.versionFallback", fallbackVersion)
}
