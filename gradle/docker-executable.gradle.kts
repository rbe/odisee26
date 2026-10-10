import java.io.File

fun resolveDockerExecutable(): String {
    findProperty("docker.executable")?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    System.getenv("DOCKER_EXECUTABLE")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }

    val path = System.getenv("PATH").orEmpty()
    val names = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        listOf("docker.exe", "docker.cmd", "docker")
    } else {
        listOf("docker")
    }
    path.split(File.pathSeparator)
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .flatMap { dir -> names.asSequence().map { name -> File(dir, name) } }
        .firstOrNull { it.isFile && it.canExecute() }
        ?.let { return it.absolutePath }

    listOf("/usr/local/bin/docker", "/opt/homebrew/bin/docker")
        .firstOrNull { File(it).isFile && File(it).canExecute() }
        ?.let { return it }

    return "docker"
}

extra["dockerExecutable"] = resolveDockerExecutable()
