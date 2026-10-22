import org.gradle.api.tasks.JavaExec

plugins {
    java
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = rootProject.group
version = rootProject.version

repositories {
    mavenCentral()
}

javafx {
    version = "21.0.13"
    modules = listOf("javafx.controls", "javafx.fxml")
}

dependencies {
    implementation(project(":clients:client-java"))
    compileOnly("jakarta.xml.bind:jakarta.xml.bind-api:4.0.4")
    testImplementation("junit:junit:4.13.2")
}

// Main extends javafx.application.Application. The java launcher then requires
// javafx.graphics as a named module. Jars left on the classpath produce
// "JavaFX runtime components are missing".
val javaFxModules = listOf("javafx.controls", "javafx.fxml")

fun isJavaFxRuntimeJar(file: java.io.File): Boolean {
    val name = file.name
    return file.isFile &&
        Regex("""javafx-.+-(linux|linux-aarch64|win|mac|mac-aarch64)\.jar""").matches(name)
}

tasks.withType<JavaExec>().configureEach {
    val exec = this
    doFirst {
        if (exec.jvmArgs.orEmpty().contains("--module-path")) {
            return@doFirst
        }
        val javafxJars = exec.classpath.filter { isJavaFxRuntimeJar(it) }
        val modulePath = javafxJars.asPath
        if (modulePath.isEmpty()) {
            throw GradleException("JavaFX jars are missing from the runtime classpath")
        }
        exec.classpath = exec.classpath.minus(javafxJars)
        exec.jvmArgs("--module-path", modulePath, "--add-modules", javaFxModules.joinToString(","))
    }
}

tasks.register<JavaExec>("run") {
    group = "application"
    description = "Start the JavaFX client. Opens a window."
    classpath = sourceSets.named("main").get().runtimeClasspath
    mainClass.set("eu.artofcoding.odisee.client.javafx.Main")
}

tasks.register<JavaExec>("javafxModules") {
    group = "verification"
    description = "Resolve JavaFX as named modules. Does not open a window."
    classpath = sourceSets.named("test").get().runtimeClasspath
    mainClass.set("eu.artofcoding.odisee.client.javafx.JavaFxModules")
}

tasks.named<Test>("test") {
    useJUnit()
    dependsOn("javafxModules")
}
