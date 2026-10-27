import org.gradle.api.tasks.SourceSetContainer
import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    id("org.apache.grails.gradle.grails-web") version "7.2.4"
}

apply(from = rootProject.file("gradle/podman-executable.gradle.kts"))

val podmanExecutable = extra["podmanExecutable"] as String

val grailsVersion: String by project
val libreOfficeVersion: String by project

group = rootProject.group
version = rootProject.version

repositories {
    mavenCentral()
}

// No GSP views, no encodeAs calls, no database, and no respond().
configurations.configureEach {
    exclude(group = "org.apache.grails", module = "grails-gsp")
    exclude(group = "org.apache.grails", module = "grails-layout")
    exclude(group = "org.apache.grails", module = "grails-codecs")
    exclude(group = "org.apache.grails", module = "grails-datasource")
    exclude(group = "org.apache.grails", module = "grails-rest-transforms")
}

dependencies {
    implementation(platform("org.apache.grails:grails-bom:$grailsVersion"))

    implementation("org.apache.grails:grails-core")
    implementation("org.apache.grails:grails-converters")
    implementation("org.apache.grails:grails-databinding")
    implementation("org.apache.grails:grails-services")
    implementation("org.apache.grails:grails-url-mappings")
    implementation("org.apache.grails:grails-interceptors")
    implementation("org.apache.grails:grails-web-boot")
    implementation("org.apache.grails:grails-logging")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-logging")
    implementation("org.springframework.boot:spring-boot-starter-tomcat")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
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
    systemProperty("ODISEE_HOME", file("src/main/container").absolutePath)
}

val odiseeTestHome = layout.buildDirectory.dir("odisee-test-home")

tasks.named<Test>("test") {
    dependsOn(tasks.named("compileTestGroovy"))
    systemProperty("ODISEE_HOME", odiseeTestHome.get().asFile.absolutePath)
    // Talks to LibreOffice. Run it with libreOfficeTest.
    exclude("**/GenerationBasisTest.class")
    // Talks to MinIO. Run it with minioTest.
    exclude("**/MinioStoreTest.class")
    // Talks to Keycloak. Run it with keycloakTest.
    exclude("**/KeycloakClientCredentialsTest.class")
    doFirst {
        odiseeTestHome.get().asFile.mkdirs()
    }
}

tasks.register<Exec>("buildLibreOfficeImage") {
    group = "Odisee"
    description = "Build the headless LibreOffice image used by libreOfficeTest"
    workingDir(file("src/test/container/libreoffice"))
    commandLine(podmanExecutable, "build", "-t", "odisee-libreoffice-test:local", ".")
}

tasks.register<Exec>("startLibreOffice") {
    group = "Odisee"
    description = "Run LibreOffice in Podman on port 2002, with ODISEE_HOME mounted at the same path"
    dependsOn("buildLibreOfficeImage")
    val homeProvider = odiseeTestHome
    doFirst {
        homeProvider.get().asFile.mkdirs()
    }
    val homePath = odiseeTestHome.get().asFile.absolutePath
    val podmanBin = "'" + podmanExecutable.replace("'", "'\\''") + "'"
    commandLine(
        "bash",
        "-lc",
        """
        set -eu
        home='$homePath'
        mkdir -p "${'$'}home"
        $podmanBin rm -f odisee-lo-test >/dev/null 2>&1 || true
        # Publish the port. --network host stays inside the Podman machine on macOS,
        # so 127.0.0.1:2002 on the Mac never opens. The home is mounted at the same
        # path so the file URL the server sends is the file LibreOffice opens.
        $podmanBin run -d --name odisee-lo-test \
            -p 127.0.0.1:2002:2002 \
            -v "${'$'}home:${'$'}home" \
            odisee-libreoffice-test:local
        for i in ${'$'}(seq 1 90); do
            if python3 -c 'import socket; s=socket.create_connection(("127.0.0.1",2002),2); s.close()' 2>/dev/null \
                || nc -z 127.0.0.1 2002 2>/dev/null; then
                exit 0
            fi
            sleep 1
        done
        echo 'LibreOffice did not open port 2002' >&2
        $podmanBin logs odisee-lo-test >&2 || true
        exit 1
        """.trimIndent(),
    )
}

val minioImage = "alpine/minio:RELEASE.2025-10-15T17-29-55Z"

tasks.register<Exec>("startMinio") {
    group = "Odisee"
    description = "Run MinIO in Podman on 127.0.0.1:9000 for minioTest"
    val podmanBin = "'" + podmanExecutable.replace("'", "'\\''") + "'"
    val image = minioImage
    commandLine(
        "bash",
        "-lc",
        """
        set -eu
        $podmanBin rm -f odisee-minio-test >/dev/null 2>&1 || true
        $podmanBin run -d --name odisee-minio-test \
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
        $podmanBin logs odisee-minio-test >&2 || true
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

// Same tag as src/main/container/compose.yaml.
val keycloakImage = "quay.io/keycloak/keycloak:26.8.0"

tasks.register<Exec>("startKeycloak") {
    group = "Odisee"
    description = "Run Keycloak in Podman on 127.0.0.1:8180 for keycloakTest"
    val podmanBin = "'" + podmanExecutable.replace("'", "'\\''") + "'"
    val image = keycloakImage
    val realm = file("src/main/container/keycloak/odisee-realm.json").absolutePath.replace("'", "'\\''")
    commandLine(
        "bash",
        "-lc",
        """
        set -eu
        $podmanBin rm -f odisee-keycloak-test >/dev/null 2>&1 || true
        $podmanBin run -d --name odisee-keycloak-test \
            -p 127.0.0.1:8180:8080 \
            -e KC_BOOTSTRAP_ADMIN_USERNAME=admin \
            -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin \
            -v '$realm:/opt/keycloak/data/import/odisee-realm.json:ro' \
            $image \
            start-dev --import-realm
        for i in ${'$'}(seq 1 180); do
            if command -v curl >/dev/null 2>&1; then
                curl -fsS http://127.0.0.1:8180/realms/odisee/.well-known/openid-configuration >/dev/null && exit 0
            elif command -v python3 >/dev/null 2>&1; then
                python3 -c 'import urllib.request; urllib.request.urlopen("http://127.0.0.1:8180/realms/odisee/.well-known/openid-configuration", timeout=2)' && exit 0
            fi
            sleep 1
        done
        echo 'Keycloak did not open the odisee realm' >&2
        $podmanBin logs odisee-keycloak-test >&2 || true
        exit 1
        """.trimIndent(),
    )
}

tasks.register<Test>("keycloakTest") {
    group = "Odisee"
    description = "Obtain one client-credentials token from Keycloak and map it to an Odisee user. Not part of the default test task."
    dependsOn("startKeycloak", "testClasses")
    val testSourceSet = sourceSets.getByName("test")
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    include("**/KeycloakClientCredentialsTest.class")
    systemProperty("ODISEE_HOME", odiseeTestHome.get().asFile.absolutePath)
    systemProperty("odisee.keycloak.issuer", "http://127.0.0.1:8180/realms/odisee")
    systemProperty("odisee.keycloak.tokenUrl", "http://127.0.0.1:8180/realms/odisee/protocol/openid-connect/token")
    systemProperty("odisee.keycloak.admin", "admin")
    systemProperty("odisee.keycloak.adminPassword", "admin")
    doFirst {
        odiseeTestHome.get().asFile.mkdirs()
    }
}

tasks.register<Test>("libreOfficeTest") {
    group = "Odisee"
    description = "Tests 1-3 against LibreOffice in Podman"
    dependsOn("startLibreOffice", "testClasses")
    val testSourceSet = sourceSets.getByName("test")
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    include("**/GenerationBasisTest.class")
    systemProperty("ODISEE_HOME", odiseeTestHome.get().asFile.absolutePath)
    systemProperty("odisee.libreoffice.port", "2002")
}

val imageBuildDir = layout.buildDirectory.dir("container/odisee")

tasks.register<Copy>("prepareOdiseeImage") {
    group = "Odisee"
    description = "Stage the service image context"
    dependsOn(tasks.named("bootJar"))
    from("src/main/container")
    from(tasks.named("bootJar"))
    into(imageBuildDir)
}

tasks.register<Exec>("buildOdiseeImage") {
    group = "Odisee"
    description = "Build the Odisee service image. Requires Podman. Not part of the default build."
    dependsOn("prepareOdiseeImage")
    workingDir(imageBuildDir)
    commandLine(
        podmanExecutable,
        "build",
        "-t", "odisee/odisee:$version",
        "-t", "odisee/webservice:$version",
        ".",
    )
}

tasks.register<Exec>("startStack") {
    group = "Odisee"
    description = "Build the Odisee image and start odisee, minio, and keycloak. Not part of the default build."
    dependsOn("buildOdiseeImage")
    val podmanBin = "'" + podmanExecutable.replace("'", "'\\''") + "'"
    val composeDir = file("src/main/container").absolutePath.replace("'", "'\\''")
    val imageVersion = version.toString().replace("'", "'\\''")
    commandLine(
        "bash",
        "-lc",
        """
        set -eu
        export VERSION='$imageVersion'
        $podmanBin compose -p odisee --project-directory '$composeDir' -f '$composeDir/compose.yaml' up -d
        for i in ${'$'}(seq 1 180); do
            minio=0
            keycloak=0
            ready=0
            if command -v curl >/dev/null 2>&1; then
                curl -fsS http://127.0.0.1:9000/minio/health/live >/dev/null && minio=1 || true
                curl -fsS http://127.0.0.1:8180/realms/odisee/.well-known/openid-configuration >/dev/null && keycloak=1 || true
                code=${'$'}(curl -sS -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/odisee/ready || true)
                if [ "${'$'}code" = 200 ]; then ready=1; fi
            elif bash -c 'echo >/dev/tcp/127.0.0.1/8080' 2>/dev/null \
                && bash -c 'echo >/dev/tcp/127.0.0.1/9000' 2>/dev/null \
                && bash -c 'echo >/dev/tcp/127.0.0.1/8180' 2>/dev/null; then
                exit 0
            fi
            if [ "${'$'}minio" = 1 ] && [ "${'$'}keycloak" = 1 ] && [ "${'$'}ready" = 1 ]; then
                exit 0
            fi
            sleep 1
        done
        echo 'odisee, minio, or keycloak did not become ready' >&2
        $podmanBin compose -p odisee --project-directory '$composeDir' -f '$composeDir/compose.yaml' ps >&2 || true
        $podmanBin compose -p odisee --project-directory '$composeDir' -f '$composeDir/compose.yaml' logs --tail 80 >&2 || true
        exit 1
        """.trimIndent(),
    )
}
