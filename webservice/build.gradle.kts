import org.gradle.api.tasks.SourceSetContainer
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    id("org.apache.grails.gradle.grails-web") version "7.2.4"
    id("org.apache.grails.gradle.grails-gsp") version "7.2.4"
}

apply(from = rootProject.file("gradle/docker-executable.gradle.kts"))

val dockerExecutable = extra["dockerExecutable"] as String

val grailsVersion: String by project
val libreOfficeVersion: String by project

group = rootProject.group
version = rootProject.version

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.apache.grails:grails-bom:$grailsVersion"))

    implementation("org.apache.grails:grails-core")
    implementation("org.apache.grails:grails-rest-transforms")
    implementation("org.apache.grails:grails-databinding")
    implementation("org.apache.grails:grails-services")
    implementation("org.apache.grails:grails-url-mappings")
    implementation("org.apache.grails:grails-interceptors")
    implementation("org.apache.grails:grails-web-boot")
    implementation("org.apache.grails:grails-gsp")
    implementation("org.apache.grails:grails-layout")
    implementation("org.apache.grails:grails-logging")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-logging")
    implementation("org.springframework.boot:spring-boot-starter-tomcat")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.apache.groovy:groovy-dateutil")
    implementation("org.apache.groovy:groovy-xml")

    // 26.2 publishes one jar. The old juh/jurt/ridl/unoil coordinates are empty stubs.
    implementation("org.libreoffice:libreoffice:$libreOfficeVersion")
    implementation("org.libreoffice:unoloader:$libreOfficeVersion")
    implementation("org.apache.pdfbox:pdfbox:3.0.8")
    implementation("software.amazon.awssdk:s3:2.55.14")
    implementation("software.amazon.awssdk:url-connection-client:2.55.14")

    runtimeOnly("org.springframework.boot:spring-boot-starter-tomcat")

    testImplementation("org.apache.grails:grails-testing-support-web")
    testImplementation("org.apache.groovy:groovy-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
}

val sourceSets = extensions.getByType(SourceSetContainer::class.java)

tasks.named<BootRun>("bootRun") {
    jvmArgs("-Dspring.output.ansi.enabled=always", "-XX:TieredStopAtLevel=1", "-Xmx1024m")
    sourceResources(sourceSets.getByName("main"))
    val springProfilesActive = "spring.profiles.active"
    systemProperty(springProfilesActive, System.getProperty(springProfilesActive))
    systemProperty("ODISEE_HOME", file("src/main/docker").absolutePath)
}

val odiseeTestHome = layout.buildDirectory.dir("odisee-test-home")

tasks.named<Test>("test") {
    dependsOn(tasks.named("compileTestGroovy"))
    systemProperty("ODISEE_HOME", odiseeTestHome.get().asFile.absolutePath)
    // Talks to LibreOffice. Run it with libreOfficeTest.
    exclude("**/GenerationBasisTest.class")
    // Talks to MinIO. Run it with minioTest.
    exclude("**/MinioStoreTest.class")
    doFirst {
        odiseeTestHome.get().asFile.mkdirs()
    }
}

tasks.register<Exec>("buildLibreOfficeImage") {
    group = "Odisee"
    description = "Build the headless LibreOffice image used by libreOfficeTest"
    workingDir(file("src/test/docker/libreoffice"))
    commandLine(dockerExecutable, "build", "-t", "odisee-libreoffice-test:local", ".")
}

tasks.register<Exec>("startLibreOffice") {
    group = "Odisee"
    description = "Run LibreOffice in Docker on port 2002, with ODISEE_HOME mounted at the same path"
    dependsOn("buildLibreOfficeImage")
    val homeProvider = odiseeTestHome
    doFirst {
        homeProvider.get().asFile.mkdirs()
    }
    val homePath = odiseeTestHome.get().asFile.absolutePath
    val dockerBin = "'" + dockerExecutable.replace("'", "'\\''") + "'"
    commandLine(
        "bash",
        "-lc",
        """
        set -eu
        home='$homePath'
        mkdir -p "${'$'}home"
        $dockerBin rm -f odisee-lo-test >/dev/null 2>&1 || true
        $dockerBin run -d --name odisee-lo-test --network host \
            -v "${'$'}home:${'$'}home" \
            odisee-libreoffice-test:local
        for i in ${'$'}(seq 1 90); do
            if bash -c 'echo >/dev/tcp/127.0.0.1/2002' 2>/dev/null; then
                exit 0
            fi
            sleep 1
        done
        echo 'LibreOffice did not open port 2002' >&2
        $dockerBin logs odisee-lo-test >&2 || true
        exit 1
        """.trimIndent(),
    )
}

val minioImage = "alpine/minio:RELEASE.2025-10-15T17-29-55Z"

tasks.register<Exec>("startMinio") {
    group = "Odisee"
    description = "Run MinIO in Docker on 127.0.0.1:9000 for minioTest"
    val dockerBin = "'" + dockerExecutable.replace("'", "'\\''") + "'"
    val image = minioImage
    commandLine(
        "bash",
        "-lc",
        """
        set -eu
        $dockerBin rm -f odisee-minio-test >/dev/null 2>&1 || true
        $dockerBin run -d --name odisee-minio-test \
            -p 127.0.0.1:9000:9000 \
            -e MINIO_ROOT_USER=odisee \
            -e MINIO_ROOT_PASSWORD=odisee-secret \
            $image \
            server /tmp/minio --console-address :9001
        for i in ${'$'}(seq 1 60); do
            if command -v curl >/dev/null 2>&1; then
                curl -fsS http://127.0.0.1:9000/minio/health/live >/dev/null && exit 0
            elif command -v python3 >/dev/null 2>&1; then
                python3 -c 'import urllib.request; urllib.request.urlopen("http://127.0.0.1:9000/minio/health/live", timeout=2)' && exit 0
            elif bash -c 'echo >/dev/tcp/127.0.0.1/9000' 2>/dev/null; then
                exit 0
            fi
            sleep 1
        done
        echo 'MinIO did not open http://127.0.0.1:9000/minio/health/live' >&2
        $dockerBin logs odisee-minio-test >&2 || true
        exit 1
        """.trimIndent(),
    )
}

tasks.register<Test>("minioTest") {
    group = "Odisee"
    description = "Store one object in MinIO and read it back. Not part of the default test task."
    dependsOn("startMinio", "testClasses")
    val testSourceSet = sourceSets.getByName("test")
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    include("**/MinioStoreTest.class")
    systemProperty("ODISEE_HOME", odiseeTestHome.get().asFile.absolutePath)
    systemProperty("odisee.minio.endpoint", "http://127.0.0.1:9000")
    systemProperty("odisee.minio.region", "us-east-1")
    systemProperty("odisee.minio.bucket", "odisee")
    systemProperty("odisee.minio.accessKey", "odisee")
    systemProperty("odisee.minio.secret", "odisee-secret")
    doFirst {
        odiseeTestHome.get().asFile.mkdirs()
    }
}

tasks.register<Test>("libreOfficeTest") {
    group = "Odisee"
    description = "Tests 1-3 against LibreOffice in Docker"
    dependsOn("startLibreOffice", "testClasses")
    val testSourceSet = sourceSets.getByName("test")
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    include("**/GenerationBasisTest.class")
    systemProperty("ODISEE_HOME", odiseeTestHome.get().asFile.absolutePath)
    systemProperty("odisee.libreoffice.port", "2002")
}

val dockerBuildDir = layout.buildDirectory.dir("docker/odisee")

tasks.register<Copy>("prepareOdiseeDocker") {
    group = "Odisee"
    description = "Stage the service image context"
    dependsOn(tasks.named("bootJar"))
    from("src/main/docker")
    from(tasks.named("bootJar"))
    into(dockerBuildDir)
}

tasks.register<Exec>("buildOdiseeImage") {
    group = "Odisee"
    description = "Build the Odisee service image. Requires Docker. Not part of the default build."
    dependsOn("prepareOdiseeDocker")
    workingDir(dockerBuildDir)
    commandLine(dockerExecutable, "build", "-t", "odisee/webservice:$version", ".")
}
