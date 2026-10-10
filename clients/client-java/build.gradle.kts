import org.gradle.api.tasks.bundling.Jar

plugins {
    java
    id("com.github.bjornvester.xjc") version "1.9.1"
}

group = rootProject.group
version = rootProject.version

repositories {
    mavenCentral()
}

dependencies {
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.4")
    implementation("jakarta.activation:jakarta.activation-api:2.1.4")
    implementation("org.glassfish.jaxb:jaxb-runtime:4.0.6")
    implementation("org.slf4j:slf4j-api:2.0.17")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.apache.pdfbox:pdfbox:3.0.8")
    testImplementation("org.springframework.security:spring-security-crypto:6.4.5")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
}

tasks.named<Test>("test") {
    exclude("**/OdiseeClientLocalTest.class")
}

tasks.register<Test>("javaClientOfficeTest") {
    group = "Odisee"
    description = "Java client Hallo request against a local server and LibreOffice"
    val webserviceProject = project(":webservice")
    dependsOn(
        webserviceProject.tasks.named("startLibreOffice"),
        webserviceProject.tasks.named("bootJar"),
        tasks.named("testClasses"),
    )
    val testSourceSet = sourceSets.named("test").get()
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    include("**/OdiseeClientLocalTest.class")
    useJUnit()
    maxParallelForks = 1
    val bootJar = webserviceProject.tasks.named("bootJar", Jar::class)
    systemProperty(
        "odisee.home",
        webserviceProject.layout.buildDirectory.dir("odisee-test-home").get().asFile.absolutePath,
    )
    systemProperty("odisee.serverJar", bootJar.get().archiveFile.get().asFile.absolutePath)
    systemProperty("odisee.libreoffice.port", "2002")
    systemProperty(
        "odisee.serverLog",
        layout.buildDirectory.file("java-client-server.log").get().asFile.absolutePath,
    )
}

extensions.configure<com.github.bjornvester.xjc.XjcExtension>("xjc") {
    xsdDir.set(layout.projectDirectory.dir("src/main/schema"))
    bindingFiles.from(layout.projectDirectory.file("src/main/schema/bindings.xjb"))
}
