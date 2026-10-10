import java.net.HttpURLConnection
import java.net.URI

plugins {
    base
}

group = rootProject.group
version = rootProject.version

fun fetchLibrary(url: String, dest: java.io.File) {
    if (dest.isFile && dest.length() > 1024) {
        return
    }
    println("Downloading ${dest.name}")
    dest.parentFile.mkdirs()
    val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
    connection.setRequestProperty("User-Agent", "Odisee-build")
    connection.connectTimeout = 30000
    connection.readTimeout = 120000
    connection.instanceFollowRedirects = true
    val code = connection.responseCode
    if (code != 200) {
        throw GradleException("Failed to download $url: HTTP $code")
    }
    dest.outputStream().use { out ->
        connection.inputStream.use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) {
                    break
                }
                out.write(buf, 0, n)
            }
        }
    }
    val header = ByteArray(2)
    dest.inputStream().use { input ->
        if (input.read(header) != 2) {
            throw GradleException("Downloaded ${dest.name} is empty")
        }
    }
    if (header[0] != 0x50.toByte() || header[1] != 0x4B.toByte()) {
        dest.delete()
        throw GradleException("${dest.name} is not a jar. The download URL may have returned an HTML page.")
    }
}

tasks.register("buildExtension") {
    group = "Odisee"
    description = "Build the LibreOffice extension (Odisee.oxt)"
    outputs.file("Odisee/dist/Odisee.oxt")
    doLast {
        val antlibDir = file("antlib")
        fetchLibrary(
            "https://repo1.maven.org/maven2/ant-contrib/ant-contrib/1.0b3/ant-contrib-1.0b3.jar",
            antlibDir.resolve("ant-contrib-1.0b3.jar"),
        )
        fetchLibrary(
            "https://downloads.sourceforge.net/project/xmltask/xmltask/1.16/xmltask.jar",
            antlibDir.resolve("xmltask.jar"),
        )
        ant.withGroovyBuilder {
            "ant"(
                mapOf(
                    "antfile" to "build.xml",
                    "target" to "world-production",
                    "dir" to file("Odisee"),
                ),
            )
        }
    }
}

tasks.named("assemble") {
    dependsOn("buildExtension")
}
