import java.io.File

fun resolvePodmanExecutable(): String {
    findProperty("podman.executable")?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    System.getenv("PODMAN_EXECUTABLE")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }

    val path = System.getenv("PATH").orEmpty()
    val names = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        listOf("podman.exe", "podman.cmd", "podman")
    } else {
        listOf("podman")
    }
    path.split(File.pathSeparator)
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .flatMap { dir -> names.asSequence().map { name -> File(dir, name) } }
        .firstOrNull { it.isFile && it.canExecute() }
        ?.let { return it.absolutePath }

    listOf("/opt/homebrew/bin/podman", "/usr/local/bin/podman")
        .firstOrNull { File(it).isFile && File(it).canExecute() }
        ?.let { return it }

    return "podman"
}

extra["podmanExecutable"] = resolvePodmanExecutable()
