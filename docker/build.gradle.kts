plugins {
    base
}

apply(from = rootProject.file("gradle/docker-executable.gradle.kts"))

val ubuntuVersion = "26.04"
val dockerExecutable = extra["dockerExecutable"] as String

tasks.register<Copy>("prepareUbuntuLibreOfficeDocker") {
    group = "Odisee"
    description = "Stage the Ubuntu LibreOffice image context"
    from("src/main/docker/ubuntu-libreoffice")
    into(layout.buildDirectory.dir("docker/ubuntu-libreoffice"))
}

tasks.register<Exec>("buildUbuntuLibreOfficeImage") {
    group = "Odisee"
    description = "Build odisee/ubuntu-libreoffice. Requires Docker. Not part of the default build."
    dependsOn("prepareUbuntuLibreOfficeDocker")
    workingDir(layout.buildDirectory.dir("docker/ubuntu-libreoffice"))
    commandLine(dockerExecutable, "build", "-t", "odisee/ubuntu-libreoffice:$ubuntuVersion", ".")
}

tasks.register("buildAllDockerImages") {
    group = "Odisee"
    description = "Build the optional LibreOffice base image."
    dependsOn("buildUbuntuLibreOfficeImage")
}
