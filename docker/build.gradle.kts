plugins {
    base
}

apply(from = rootProject.file("gradle/docker-executable.gradle.kts"))

val archLinuxVersion = "base"
val openSuseVersion = "15.6"
val dockerExecutable = extra["dockerExecutable"] as String

tasks.register<Copy>("prepareArchLinuxLibreOfficeDocker") {
    group = "Odisee"
    description = "Stage the Arch Linux LibreOffice image context"
    from("src/main/docker/archlinux-libreoffice")
    into(layout.buildDirectory.dir("docker/archlinux-libreoffice"))
}

tasks.register<Exec>("buildArchLinuxLibreOfficeImage") {
    group = "Odisee"
    description = "Build odisee/archlinux-libreoffice. Requires Docker. Not part of the default build."
    dependsOn("prepareArchLinuxLibreOfficeDocker")
    workingDir(layout.buildDirectory.dir("docker/archlinux-libreoffice"))
    commandLine(dockerExecutable, "build", "-t", "odisee/archlinux-libreoffice:$archLinuxVersion", ".")
}

tasks.register<Copy>("prepareOpenSuseLibreOfficeDocker") {
    group = "Odisee"
    description = "Stage the openSUSE LibreOffice image context"
    from("src/main/docker/opensuse-libreoffice")
    into(layout.buildDirectory.dir("docker/opensuse-libreoffice"))
}

tasks.register<Exec>("buildOpenSuseLibreOfficeImage") {
    group = "Odisee"
    description = "Build odisee/opensuse-libreoffice. Requires Docker. Not part of the default build."
    dependsOn("prepareOpenSuseLibreOfficeDocker")
    workingDir(layout.buildDirectory.dir("docker/opensuse-libreoffice"))
    commandLine(dockerExecutable, "build", "-t", "odisee/opensuse-libreoffice:$openSuseVersion", ".")
}

tasks.register("buildAllDockerImages") {
    group = "Odisee"
    description = "Build the optional LibreOffice base images."
    dependsOn("buildArchLinuxLibreOfficeImage", "buildOpenSuseLibreOfficeImage")
}
