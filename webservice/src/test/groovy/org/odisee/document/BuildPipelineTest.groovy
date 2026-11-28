package org.odisee.document

// id: ADR-0007

import groovy.test.GroovyTestCase

/**
 * The workflow contract is tested in RalfSkills. These tests check the facts of odisee26.
 * {@code ./gradlew build} stays one command and does not start a container.
 */
class BuildPipelineTest extends GroovyTestCase {

    void testContainersPublishOnlyUserPortsAbove1024() {
        File root = repoRoot()
        List<Integer> hostPorts = []
        ['Makefile', 'webservice/build.gradle.kts'].each { String path ->
            new File(root, path).text.findAll(/-p (?:127\.0\.0\.1:)?(\d+):\d+/) { String all, String port ->
                hostPorts << (port as Integer)
            }
        }
        new File(root, 'webservice/src/main/container/compose.yaml').text.findAll(/(?m)^\s+- "(?:127\.0\.0\.1:)?(\d+):\d+"/) { String all, String port ->
            hostPorts << (port as Integer)
        }
        assertTrue(hostPorts.toString(), hostPorts.containsAll([2002, 8080, 8180, 9000, 9001]))
        hostPorts.each { Integer port ->
            assertTrue(port.toString(), port > 1024)
        }
    }

    void testWorkflowDoesNotBuildVbnet() {
        String yaml = workflow()
        assertFalse(yaml.contains('windows-latest'))
        assertFalse(yaml.contains('buildSolution'))
    }

    void testLocalBuildPackagesAndDoesNotStartAContainer() {
        File root = repoRoot()
        String gradle = new File(root, 'build.gradle.kts').text
        assertTrue(gradle.contains('val odiseeBaseVersion = "2.6.0"'))
        assertTrue(gradle.contains('ODISEE_BUILD_NUMBER'))
        assertTrue(gradle.contains('"$odiseeBaseVersion-$odiseeBuildNumber"'))
        assertTrue(gradle.contains('tasks.named("build")'))
        assertTrue(gradle.contains('dependsOn("packageDistribution")'))
        assertFalse(gradle.contains('startLibreOffice'))
        assertFalse(gradle.contains('startStack'))
        assertFalse(gradle.contains('javaClientOfficeTest'))

        String podman = new File(root, 'gradle/podman-executable.gradle.kts').text
        assertTrue(podman.contains('podman.executable'))
        assertTrue(podman.contains('PODMAN_EXECUTABLE'))
        assertTrue(podman.contains('/opt/homebrew/bin/podman'))
        assertTrue(podman.contains('/usr/local/bin/podman'))
        assertTrue(podman.contains('return "podman"'))

        String web = new File(root, 'webservice/build.gradle.kts').text
        assertTrue(web.contains('$podmanBin compose'))
        assertTrue(web.contains('compose.yaml'))
        assertTrue(web.contains('exclude("**/GenerationBasisTest.class")'))
        assertTrue(web.contains('exclude("**/MinioStoreTest.class")'))
        assertTrue(web.contains('exclude("**/KeycloakClientCredentialsTest.class")'))

        String client = new File(root, 'clients/client-java/build.gradle.kts').text
        assertTrue(client.contains('exclude("**/OdiseeClientLocalTest.class")'))

        String settings = new File(root, 'settings.gradle.kts').text
        assertTrue(settings.contains('include(":clients:client-javafx")'))
        assertFalse(gradle.contains('client-javafx'))
        assertTrue(gradle.contains(':oxt:buildExtension'))
        assertTrue(gradle.contains('oxt/Odisee/dist/Odisee.oxt'))

        String oxt = new File(root, 'oxt/build.gradle.kts').text
        assertTrue(oxt.contains('tasks.register<Zip>(taskName(name, dev))'))
        assertTrue(oxt.contains('"${name}.oxt"'))
        assertTrue(oxt.contains('"${name}_dev.oxt"'))
        assertTrue(oxt.contains('git'))
        assertTrue(oxt.contains('describe'))
        assertTrue(oxt.contains('v[0-9]*'))
        assertTrue(oxt.contains('buildExtensionDev'))
        assertTrue(oxt.contains('tasks.register("world")'))
        assertTrue(oxt.contains('extension.properties'))
        assertTrue(oxt.contains('bin/**'))
        assertFalse(oxt.contains('inc-build-number'))
        assertFalse(oxt.contains('substringBefore'))
        assertFalse(oxt.contains('world-production'))
        assertFalse(oxt.contains('ant-contrib'))
        assertFalse(oxt.contains('xmltask'))
        assertFalse(new File(root, 'oxt/Odisee/build.xml').exists())
        assertFalse(new File(root, 'oxt/build-base.xml').exists())

        String properties = new File(root, 'gradle.properties').text
        assertFalse(properties.contains('--add-exports'))
    }

    void testServiceAndTestContainerfilesInstallWriterCalcAndImpress() {
        File root = repoRoot()
        String serviceContainerfile = new File(root, 'webservice/src/main/container/Containerfile').getText('UTF-8')
        String testContainerfile = new File(root, 'webservice/src/test/container/libreoffice/Containerfile').getText('UTF-8')

        [serviceContainerfile, testContainerfile].each { String containerfile ->
            int copyIndex = containerfile.indexOf('COPY ')
            assertTrue(copyIndex > 0)
            ['libreoffice-writer', 'libreoffice-calc', 'libreoffice-impress'].each { String pkg ->
                int pkgIndex = containerfile.indexOf(pkg)
                assertTrue("Missing ${pkg}", pkgIndex > 0 && pkgIndex < copyIndex)
            }
            assertFalse(containerfile.contains('Calc and Impress are not installed'))
            assertFalse(containerfile.contains('Headless Writer only'))
        }
    }

    void testDistributionAndContainerOmitLegacySharedVarDirectories() {
        File root = repoRoot()
        String rootGradle = new File(root, 'build.gradle.kts').getText('UTF-8')
        String webGradle = new File(root, 'webservice/build.gradle.kts').getText('UTF-8')
        String containerfile = new File(root, 'webservice/src/main/container/Containerfile').getText('UTF-8')
        List<String> legacyDirs = ['var/template', 'var/document', 'var/merge', 'var/image', 'var/request']
        List<String> runtimeDirs = ['var/deploy/fonts', 'var/log', 'var/profile', 'var/tmp']

        runtimeDirs.each { String dir ->
            assertTrue("build.gradle.kts missing ${dir}", rootGradle.contains("\"${dir}\""))
            assertTrue("Containerfile missing ${dir}", containerfile.contains("\${ODISEE_HOME}/${dir}"))
        }
        legacyDirs.each { String dir ->
            assertFalse("build.gradle.kts still creates ${dir}", rootGradle.contains("\"${dir}\""))
            assertFalse("Containerfile still creates ${dir}", containerfile.contains("\${ODISEE_HOME}/${dir}"))
        }
        int stageTask = webGradle.indexOf('tasks.register<Copy>("prepareOdiseeImage")')
        assertTrue(stageTask > 0)
        String stageBlock = webGradle.substring(stageTask, webGradle.indexOf('\n}', stageTask) + 2)
        assertTrue(stageBlock, stageBlock.contains('exclude("var/**")'))
        File stagedContext = new File(root, 'webservice/build/container/odisee')
        if (stagedContext.isDirectory()) {
            assertFalse('prepareOdiseeImage staged var/', new File(stagedContext, 'var').exists())
        }

        File distDir = new File(root, 'build/distributions')
        File[] zips = distDir.isDirectory() ? distDir.listFiles({ File d, String name -> name.endsWith('.zip') } as FilenameFilter) : null
        if (zips != null && zips.length > 0) {
            java.util.zip.ZipFile zip = new java.util.zip.ZipFile(zips[0])
            try {
                Set<String> entries = zip.entries().collect { it.name } as Set
                runtimeDirs.each { String dir ->
                    assertTrue("Zip missing ${dir}/.keep", entries.contains("${dir}/.keep".toString()))
                }
                legacyDirs.each { String dir ->
                    assertFalse("Zip still contains ${dir}", entries.any { it == dir || it.startsWith("${dir}/") })
                }
            } finally {
                zip.close()
            }
        }
    }

    private static String workflow() {
        new File(repoRoot(), '.github/workflows/ci.yml').text
    }

    private static File repoRoot() {
        File dir = new File(System.getProperty('user.dir'))
        while (dir != null && !new File(dir, 'settings.gradle.kts').isFile()) {
            dir = dir.parentFile
        }
        assertNotNull(dir)
        dir
    }
}
