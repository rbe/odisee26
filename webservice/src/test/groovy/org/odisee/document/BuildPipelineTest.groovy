package org.odisee.document

import groovy.test.GroovyTestCase

/**
 * GitHub Actions splits the Linux build into jobs that pass the compiled outputs.
 * {@code ./gradlew build} stays one command and does not start a container.
 */
class BuildPipelineTest extends GroovyTestCase {

    void testLinuxJobsPassBuildOutputs() {
        Map<String, String> jobs = jobs(workflow())
        assertEquals(
                ['setup', 'build', 'values', 'test', 'package', 'java-client-office', 'image'] as Set,
                jobs.keySet() as Set,
        )

        String yaml = workflow()
        String prepare = prepareAction()
        String testWorkflow = calledWorkflow('test.yml')
        String imageWorkflow = calledWorkflow('image.yml')
        assertTrue(yaml.contains('workflow_dispatch:'))
        assertTrue(yaml.contains('pull_request:'))
        assertTrue(yaml.contains('branches: [master]'))
        assertFalse(testWorkflow.contains('workflow_dispatch:'))
        assertFalse(testWorkflow.contains('pull_request:'))
        assertFalse(imageWorkflow.contains('workflow_dispatch:'))
        assertFalse(imageWorkflow.contains('pull_request:'))
        assertTrue(testWorkflow.contains('workflow_call:'))
        assertTrue(imageWorkflow.contains('workflow_call:'))
        assertFalse(testWorkflow.contains('2.6.0'))
        assertFalse(imageWorkflow.contains('2.6.0'))
        assertFalse(testWorkflow.contains('JAVA_VERSION: "21"'))
        assertFalse(imageWorkflow.contains('JAVA_VERSION: "21"'))
        assertFalse(prepare.contains('actions/checkout@'))
        assertTrue(prepare.contains('actions/setup-java@v5'))
        assertTrue(prepare.contains('actions/download-artifact@v7'))
        assertTrue(prepare.contains('mkdir -p "$dir"'))
        assertTrue(prepare.contains('cache: gradle'))
        assertTrue(yaml.contains('actions/upload-artifact@v6'))
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
        assertFalse(yaml.contains('ubuntu-latest'))
        ['setup', 'build', 'package', 'java-client-office'].each { String name ->
            String body = jobs[name]
            assertTrue(name, body.contains('runs-on: ubuntu-26.04'))
            assertTrue(name, body.contains('uses: actions/checkout@v5'))
            assertTrue(name, body.indexOf('uses: actions/checkout@v5') < body.indexOf('uses: ./.github/actions/prepare'))
            assertTrue(name, body.contains('uses: ./.github/actions/prepare'))
            assertTrue(name, body.contains('java-version: ${{ env.JAVA_VERSION }}'))
            assertTrue(name, body.contains('java-distribution: ${{ env.JAVA_DISTRIBUTION }}'))
        }

        String setup = jobs.setup
        assertTrue(setup.contains('./gradlew --no-daemon :dependencies :webservice:dependencies :clients:client-java:dependencies :clients:client-javafx:dependencies :documentation:dependencies'))
        assertFalse(setup.contains('build-outputs:'))
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

        String values = jobs.values
        assertTrue(values.contains('java-version: ${{ steps.emit.outputs.java-version }}'))
        assertTrue(values.contains('distribution-zip: ${{ steps.emit.outputs.distribution-zip }}'))
        assertTrue(values.contains('echo "java-version=${JAVA_VERSION}"'))
        assertTrue(values.contains('echo "odisee-build-number=${ODISEE_BUILD_NUMBER}"'))
        assertFalse(values.contains('uses: ./.github/workflows/'))

        String test = jobs.test
        assertTrue(test.contains('needs: [build, values]'))
        assertTrue(test.contains('uses: ./.github/workflows/test.yml'))
        assertTrue(test.contains('java-version: ${{ needs.values.outputs.java-version }}'))
        assertTrue(test.contains('java-distribution: ${{ needs.values.outputs.java-distribution }}'))
        assertTrue(test.contains('odisee-build-number: ${{ needs.values.outputs.odisee-build-number }}'))
        assertTrue(test.contains('build-outputs: ${{ needs.values.outputs.build-outputs }}'))
        assertTrue(test.contains('distribution-zip: ${{ needs.values.outputs.distribution-zip }}'))
        assertFalse(test.contains('env.JAVA_VERSION'))
        assertTrue(testWorkflow.contains('uses: actions/checkout@v5'))
        assertTrue(testWorkflow.indexOf('uses: actions/checkout@v5') < testWorkflow.indexOf('uses: ./.github/actions/prepare'))
        assertTrue(testWorkflow.contains('build-outputs: ${{ inputs.build-outputs }}'))
        assertTrue(testWorkflow.contains('./gradlew --no-daemon :webservice:test :clients:client-java:test :clients:client-javafx:test :webservice:integrationTest :test'))
        assertFalse(testWorkflow.contains('javaClientOfficeTest'))
        assertFalse(testWorkflow.contains('libreOfficeTest'))
        assertFalse(testWorkflow.contains('minioTest'))
        assertFalse(testWorkflow.contains('keycloakTest'))
        assertFalse(testWorkflow.contains('startStack'))

        String packaged = jobs['package']
        assertTrue(packaged.contains('needs: [build, test]'))
        assertTrue(packaged.contains('build-outputs: ${{ env.BUILD_OUTPUTS }}'))
        assertTrue(packaged.contains('./gradlew --no-daemon :printVersion packageDistribution'))
        assertTrue(packaged.contains('build/odisee-version.txt'))
        assertTrue(packaged.contains('name: odisee-${{ env.ODISEE_VERSION }}-linux-x86_64'))
        assertTrue(packaged.contains('${{ env.DISTRIBUTION_ZIP }}'))
        assertTrue(packaged.contains("startsWith(github.ref, 'refs/tags/v')"))

        String image = jobs['image']
        assertTrue(image.contains('needs: [test, values]'))
        assertTrue(image.contains('java-version: ${{ needs.values.outputs.java-version }}'))
        assertTrue(image.contains('distribution-zip: ${{ needs.values.outputs.distribution-zip }}'))
        assertFalse(image.contains('env.JAVA_VERSION'))
        assertTrue(image.contains("github.ref == 'refs/heads/master'"))
        assertTrue(image.contains("startsWith(github.ref, 'refs/tags/v')"))
        assertTrue(image.contains('packages: write'))
        assertTrue(image.contains('secrets: inherit'))
        assertTrue(image.contains('uses: ./.github/workflows/image.yml'))
        assertTrue(imageWorkflow.contains('uses: actions/checkout@v5'))
        assertTrue(imageWorkflow.indexOf('uses: actions/checkout@v5') < imageWorkflow.indexOf('uses: ./.github/actions/prepare'))
        assertTrue(imageWorkflow.contains('./gradlew --no-daemon :printVersion :webservice:prepareOdiseeImage'))
        assertTrue(imageWorkflow.contains('build/odisee-version.txt'))
        assertTrue(imageWorkflow.contains('webservice/build/container/odisee'))
        assertTrue(imageWorkflow.contains('ghcr.io'))
        assertTrue(imageWorkflow.contains('apt-get install -y podman qemu-user-binfmt'))
        assertFalse(yaml.contains('qemu-user-static'))
        assertFalse(imageWorkflow.contains('qemu-user-static'))
        assertTrue(imageWorkflow.contains('podman build'))
        assertTrue(imageWorkflow.contains('--platform linux/amd64,linux/arm64'))
        assertTrue(imageWorkflow.contains('--manifest "${image}:${version}"'))
        assertTrue(imageWorkflow.contains('--layers'))
        assertTrue(imageWorkflow.contains('cache_repo="ghcr.io/${repo}/cache"'))
        assertTrue(imageWorkflow.contains('--cache-from "${cache_repo}"'))
        assertTrue(imageWorkflow.contains('--cache-to "${cache_repo}"'))
        assertFalse(imageWorkflow.contains('${image}:cache'))
        assertTrue(imageWorkflow.contains('podman manifest rm "${image}:${version}"'))
        assertTrue(imageWorkflow.contains('podman manifest push --all "${image}:${version}"'))
        assertTrue(imageWorkflow.contains('podman manifest push --all "${image}:${version}" "${image}:latest"'))
        assertTrue(imageWorkflow.contains('podman manifest push --all "${image}:${version}" "${image}:${GITHUB_REF_NAME}"'))
        assertFalse(imageWorkflow.contains('buildx'))
        assertFalse(imageWorkflow.contains('tonistiigi/binfmt'))
        assertFalse(imageWorkflow.contains('darwin'))
        assertFalse(imageWorkflow.contains('startStack'))
        assertFalse(imageWorkflow.contains('buildOdiseeImage'))

        String office = jobs['java-client-office']
        assertTrue(office.contains('needs: build'))
        assertTrue(office.contains('build-outputs: ${{ env.BUILD_OUTPUTS }}'))
        assertTrue(office.contains('apt-get install -y podman'))
        assertTrue(office.contains('./gradlew --no-daemon :clients:client-java:javaClientOfficeTest'))
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
    }

    private static String workflow() {
        new File(repoRoot(), '.github/workflows/build.yml').text
    }

    private static String calledWorkflow(String name) {
        new File(repoRoot(), ".github/workflows/${name}").text
    }

    private static String prepareAction() {
        new File(repoRoot(), '.github/actions/prepare/action.yml').text
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
