import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.testing.Test

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

// Semantic project version. CI appends -run to the artifact version only.
val extensionVersion = version.toString().substringBefore('-')

val extensionMetadataDir = layout.buildDirectory.dir("extension-metadata")
val updateDescriptor = layout.projectDirectory.file("Odisee/dist/Odisee-update.xml")

val extensionMetadata = tasks.register("extensionMetadata") {
    inputs.property("version", extensionVersion)
    outputs.dir(extensionMetadataDir)
    outputs.file(updateDescriptor)
    doLast {
        val root = extensionMetadataDir.get().asFile
        root.deleteRecursively()
        root.resolve("META-INF").mkdirs()
        root.resolve("META-INF/manifest.xml").writeText(manifestXml())
        root.resolve("description.xml").writeText(descriptionXml(extensionVersion))
        val update = updateDescriptor.asFile
        update.parentFile.mkdirs()
        update.writeText(updateXml(extensionVersion))
    }
}

tasks.register<Zip>("buildExtension") {
    group = "Odisee"
    description = "Build the LibreOffice extension (Odisee.oxt)"
    archiveFileName.set("Odisee.oxt")
    destinationDirectory.set(layout.projectDirectory.dir("Odisee/dist"))
    includeEmptyDirs = false
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
    dependsOn(extensionMetadata)
    from(layout.projectDirectory.dir("Odisee/ext"))
    from(extensionMetadataDir) {
        include("META-INF/manifest.xml", "description.xml")
    }
}

tasks.named<Delete>("clean") {
    delete(layout.projectDirectory.dir("Odisee/dist"))
}

tasks.named("assemble") {
    setDependsOn(listOf(tasks.named("buildExtension")))
}

tasks.named<Test>("test") {
    dependsOn("buildExtension")
    useJUnit()
    systemProperty("odisee.oxt", layout.projectDirectory.file("Odisee/dist/Odisee.oxt").asFile.absolutePath)
    systemProperty("odisee.update", updateDescriptor.asFile.absolutePath)
    systemProperty("odisee.version", extensionVersion)
    systemProperty("odisee.ext", layout.projectDirectory.dir("Odisee/ext").asFile.absolutePath)
}

fun xmlAttr(value: String): String =
    value.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace("\"", "&quot;")

fun manifestXml(): String = """
    <?xml version="1.0" encoding="UTF-8"?>
    <manifest:manifest xmlns:manifest="http://openoffice.org/2001/manifest">
        <manifest:file-entry manifest:full-path="basic/Odisee/" manifest:media-type="application/vnd.sun.star.basic-library"/>
        <manifest:file-entry manifest:full-path="Paths.xcu" manifest:media-type="application/vnd.sun.star.configuration-data"/>
        <manifest:file-entry manifest:full-path="etc/pkg-description.txt" manifest:media-type="application/vnd.sun.star.package-bundle-description"/>
    </manifest:manifest>
""".trimIndent() + "\n"

fun descriptionXml(extensionVersion: String): String {
    val version = xmlAttr(extensionVersion)
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <description xmlns="http://openoffice.org/extensions/description/2006" xmlns:dep="http://openoffice.org/extensions/description/2006" xmlns:xlink="http://www.w3.org/1999/xlink">
            <identifier value="vnd.artofcoding.odisee.Odisee"/>
            <version value="$version"/>
            <dependencies>
                <OpenOffice.org-minimal-version value="3.3" dep:name="OpenOffice.org 3.3"/>
            </dependencies>
            <display-name>
                <name lang="en">Odisee</name>
                <name lang="de">Odisee</name>
            </display-name>
            <publisher>
                <name xlink:href="http://www.odisee.org" lang="en">art of coding UG</name>
            </publisher>
            <icon>
                <default xlink:href="images/odisee/Odisee_Signet_24x32.png"/>
            </icon>
            <update-information>
                <src xlink:href="https://www.odisee.org/update/oxt/Odisee-update.xml"/>
            </update-information>
            <registration>
                <simple-license accept-by="admin" default-license-id="ID0" suppress-on-update="true">
                    <license-text xlink:href="etc/LICENSE.txt" lang="en" license-id="ID0"/>
                </simple-license>
            </registration>
        </description>
    """.trimIndent() + "\n"
}

fun updateXml(extensionVersion: String): String {
    val version = xmlAttr(extensionVersion)
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <description xmlns="http://openoffice.org/extensions/update/2006" xmlns:xlink="http://www.w3.org/1999/xlink">
            <identifier value="vnd.artofcoding.odisee.Odisee"/>
            <version value="$version"/>
            <update-download>
                <src xlink:href="https://www.odisee.org/update/oxt/Odisee.oxt"/>
            </update-download>
        </description>
    """.trimIndent() + "\n"
}
