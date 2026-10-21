package org.odisee.document

import groovy.test.GroovyTestCase

/**
 * GitHub Actions splits the Linux build into jobs that pass the compiled outputs.
 * {@code ./gradlew build} stays one command and does not start Docker.
 */
class BuildPipelineTest extends GroovyTestCase {

    void testLinuxJobsPassBuildOutputs() {
        Map<String, String> jobs = jobs(workflow())
        assertEquals(
                ['setup', 'build', 'test', 'package', 'java-client-office', 'image'] as Set,
                jobs.keySet() as Set,
        )

        String yaml = workflow()
        assertTrue(yaml.contains('workflow_dispatch:'))
        assertTrue(yaml.contains('pull_request:'))
        assertTrue(yaml.contains('branches: [master]'))
        assertTrue(yaml.contains('actions/checkout@v5'))
        assertTrue(yaml.contains('actions/setup-java@v5'))
        assertTrue(yaml.contains('actions/upload-artifact@v6'))
        assertTrue(yaml.contains('actions/download-artifact@v7'))
        assertTrue(yaml.contains('softprops/action-gh-release@v3'))
        assertFalse(yaml.contains('actions/checkout@v4'))
        assertFalse(yaml.contains('actions/setup-java@v4'))
        assertFalse(yaml.contains('actions/upload-artifact@v4'))
        assertFalse(yaml.contains('actions/download-artifact@v4'))
        assertFalse(yaml.contains('action-gh-release@v2'))
        assertTrue(yaml.contains('JAVA_VERSION: "21"'))
        assertTrue(yaml.contains('JAVA_DISTRIBUTION: temurin'))
        assertTrue(yaml.contains('ODISEE_BUILD_NUMBER: ${{ github.run_number }}'))
        assertTrue(yaml.contains('BUILD_OUTPUTS: build-outputs'))
        assertTrue(yaml.contains('DISTRIBUTION_ZIP: build/distributions/odisee-*-linux-x86_64.zip'))
        assertFalse(yaml.contains('name: odisee-linux-x86_64'))
        assertFalse(yaml.contains('java-version: "21"'))
        jobs.each { String name, String body ->
            assertTrue(name, body.contains('java-version: ${{ env.JAVA_VERSION }}'))
            assertTrue(name, body.contains('distribution: ${{ env.JAVA_DISTRIBUTION }}'))
        }

        String setup = jobs.setup
        assertTrue(setup.contains('runs-on: ubuntu-latest'))
        assertTrue(setup.contains('java-version: ${{ env.JAVA_VERSION }}'))
        assertTrue(setup.contains('distribution: ${{ env.JAVA_DISTRIBUTION }}'))
        assertTrue(setup.contains('cache: gradle'))
        assertTrue(setup.contains('./gradlew --no-daemon :dependencies :webservice:dependencies :clients:client-java:dependencies :documentation:dependencies'))
        assertFalse(setup.contains('assemble'))
        assertFalse(setup.contains('testClasses'))
        assertFalse(setup.contains('upload-artifact'))

        String build = jobs.build
        assertTrue(build.contains('needs: setup'))
        assertTrue(build.contains('./gradlew --no-daemon assemble testClasses integrationTestClasses'))
        assertTrue(build.contains('name: ${{ env.BUILD_OUTPUTS }}'))
        assertTrue(build.contains('include-hidden-files: true'))
        assertTrue(build.contains('build/ci-empty-dirs.txt'))
        assertFalse(build.contains('packageDistribution'))
        assertFalse(build.contains(':webservice:test'))
        assertFalse(build.contains('javaClientOfficeTest'))

        String test = jobs.test
        assertTrue(test.contains('needs: build'))
        assertTrue(test.contains('actions/download-artifact@v7'))
        assertTrue(test.contains('name: ${{ env.BUILD_OUTPUTS }}'))
        assertTrue(test.contains('mkdir -p "$dir"'))
        assertTrue(test.contains('./gradlew --no-daemon :webservice:test :clients:client-java:test :webservice:integrationTest :test'))
        assertFalse(test.contains('javaClientOfficeTest'))
        assertFalse(test.contains('libreOfficeTest'))
        assertFalse(test.contains('minioTest'))
        assertFalse(test.contains('keycloakTest'))
        assertFalse(test.contains('startStack'))

        String packaged = jobs['package']
        assertTrue(packaged.contains('needs: [build, test]'))
        assertTrue(packaged.contains('actions/download-artifact@v7'))
        assertTrue(packaged.contains('mkdir -p "$dir"'))
        assertTrue(packaged.contains('./gradlew --no-daemon :printVersion packageDistribution'))
        assertTrue(packaged.contains('build/odisee-version.txt'))
        assertTrue(packaged.contains('name: odisee-${{ env.ODISEE_VERSION }}-linux-x86_64'))
        assertTrue(packaged.contains('${{ env.DISTRIBUTION_ZIP }}'))
        assertTrue(packaged.contains("startsWith(github.ref, 'refs/tags/v')"))

        String image = jobs['image']
        assertTrue(image.contains('needs: test'))
        assertTrue(image.contains("github.ref == 'refs/heads/master'"))
        assertTrue(image.contains("startsWith(github.ref, 'refs/tags/v')"))
        assertTrue(image.contains('packages: write'))
        assertTrue(image.contains('./gradlew --no-daemon :printVersion :webservice:prepareOdiseeDocker'))
        assertTrue(image.contains('build/odisee-version.txt'))
        assertTrue(image.contains('ghcr.io'))
        assertTrue(image.contains('docker push "${image}:${version}"'))
        assertFalse(image.contains('startStack'))
        assertFalse(image.contains('buildOdiseeImage'))

        String office = jobs['java-client-office']
        assertTrue(office.contains('needs: build'))
        assertTrue(office.contains('actions/download-artifact@v7'))
        assertTrue(office.contains('mkdir -p "$dir"'))
        assertTrue(office.contains('./gradlew --no-daemon :clients:client-java:javaClientOfficeTest'))
    }

    void testWorkflowDoesNotBuildVbnet() {
        String yaml = workflow()
        assertFalse(yaml.contains('windows-latest'))
        assertFalse(yaml.contains('buildSolution'))
    }

    void testLocalBuildPackagesAndDoesNotStartDocker() {
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

        String web = new File(root, 'webservice/build.gradle.kts').text
        assertTrue(web.contains('exclude("**/GenerationBasisTest.class")'))
        assertTrue(web.contains('exclude("**/MinioStoreTest.class")'))
        assertTrue(web.contains('exclude("**/KeycloakClientCredentialsTest.class")'))

        String client = new File(root, 'clients/client-java/build.gradle.kts').text
        assertTrue(client.contains('exclude("**/OdiseeClientLocalTest.class")'))
    }

    private static String workflow() {
        new File(repoRoot(), '.github/workflows/build.yml').text
    }

    private static Map<String, String> jobs(String yaml) {
        Map<String, String> jobs = [:]
        String name = null
        StringBuilder body = new StringBuilder()
        boolean inJobs = false
        yaml.readLines().each { String line ->
            if (!inJobs) {
                if (line == 'jobs:') {
                    inJobs = true
                }
                return
            }
            def matcher = (line =~ /^  ([A-Za-z0-9_-]+):\s*$/)
            if (matcher) {
                if (name != null) {
                    jobs[name] = body.toString()
                }
                name = matcher[0][1]
                body = new StringBuilder()
            } else if (name != null) {
                body.append(line).append('\n')
            }
        }
        if (name != null) {
            jobs[name] = body.toString()
        }
        jobs
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
