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

tasks.named<Test>("test") {
    useJUnit()
}
