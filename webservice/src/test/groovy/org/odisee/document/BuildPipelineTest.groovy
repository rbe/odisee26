package org.odisee.document

import groovy.test.GroovyTestCase

/**
 * CI is one workflow. Jobs check out, move artifacts, and call make.
 * {@code ./gradlew build} stays one command and does not start a container.
 */
class BuildPipelineTest extends GroovyTestCase {

    private static final String SDLC_SHA256 = '45c6cf23664d030c61f0fedd2d0639cd9dc2dc7bb03c14167dce61fed9d495d6'

    void testWorkflowMatchesTheCommonSdlc() {
        File root = repoRoot()
        File workflowDir = new File(root, '.github/workflows')
        String[] workflowFiles = workflowDir.list()
        assertEquals(['ci.yml'] as Set, workflowFiles as Set)

        String yaml = workflow()
        Map<String, String> jobs = jobs(yaml)
        assertEquals(
                ['setup', 'build', 'test', 'test-java', 'publish-container', 'publish-github', 'report'] as Set,
                jobs.keySet() as Set,
        )
        assertEquals('report', (jobs.keySet() as List)[-1])

        String head = yaml.substring(0, yaml.indexOf('\njobs:\n'))
        assertTrue(head.contains('name: CI'))
        assertTrue(head.contains("branches: ['**']"))
        assertTrue(head.contains('workflow_dispatch:'))
        assertTrue(head.contains('options: [setup, build, test, test-java, publish-container, publish-github, report]'))
        assertFalse(head.contains('pull_request'))
        assertFalse(head.contains('tags:'))
        assertFalse(head.contains('contents: write'))
        assertFalse(head.contains('packages: write'))
        assertTrue(head.contains('contents: read'))
        assertTrue(head.contains('IMAGE: ghcr.io/rbe/odisee26'))
        assertTrue(head.contains('SDLC_DEFAULT_BRANCH: ${{ github.event.repository.default_branch }}'))
        assertTrue(head.contains('JAVA_VERSION: "21"'))
        assertTrue(head.contains('cancel-in-progress: ${{ github.event_name == \'push\' && github.ref != format(\'refs/heads/{0}\', github.event.repository.default_branch) }}'))

        assertFalse(jobs.setup.contains('\n    needs:'))
        assertTrue(jobs.build.contains('needs: setup'))
        assertTrue(jobs.test.contains('needs: setup'))
        assertTrue(jobs['test-java'].contains('needs: setup'))
        assertTrue(jobs['publish-container'].contains('needs: [build, test]'))
        assertTrue(jobs['publish-github'].contains('needs: [build, test]'))
        assertTrue(jobs.report.contains('needs: [setup, build, test, test-java, publish-container, publish-github]'))
        assertTrue(jobs.report.contains('if: ${{ !cancelled() }}'))
        jobs.each { String name, String body ->
            assertTrue(name, body.contains('runs-on: ubuntu-26.04'))
            assertFalse(name, body.contains('ubuntu-latest'))
            assertNull(name, body.find(/(?m)^\s*needs:.*\breport\b/))
        }
        assertFalse(yaml.contains('ubuntu-latest'))
        assertFalse(yaml.contains('refs/tags'))
        assertFalse(yaml.contains('softprops/'))
        assertFalse(yaml.contains('setup-java'))
        assertFalse(yaml.contains('published-versions'))
        assertFalse(yaml.contains('2.6.0'))
        assertFalse(yaml.contains('.gradle'))
        assertFalse(yaml.contains('**/build/'))
        assertFalse(yaml.contains('actions/checkout@v4'))
        assertFalse(yaml.contains('actions/upload-artifact@v4'))
        assertFalse(yaml.contains('actions/download-artifact@v4'))

        String publishOnDefault = "github.ref == format('refs/heads/{0}', github.event.repository.default_branch)"
        assertTrue(jobs['publish-container'].contains("github.event_name == 'push'"))
        assertTrue(jobs['publish-container'].contains(publishOnDefault))
        assertTrue(jobs['publish-container'].contains("inputs.job == 'publish-container'"))
        assertFalse(jobs['publish-container'].contains('test-java'))
        assertTrue(jobs['publish-github'].contains("github.event_name == 'push'"))
        assertTrue(jobs['publish-github'].contains(publishOnDefault))
        assertTrue(jobs['publish-github'].contains("inputs.job == 'publish-github'"))
        assertFalse(jobs['publish-github'].contains('test-java'))
        assertTrue(jobs['test-java'].contains('fromJSON(\'["test-java","report"]\')'))
        assertFalse(jobs['test-java'].contains('publish-container'))
        assertTrue(jobs.build.contains('fetch-depth: 0'))
        assertTrue(jobs.build.contains('fetch-tags: true'))
        assertTrue(jobs['publish-container'].contains('name: dist'))
        assertTrue(jobs['publish-container'].contains('path: build/dist'))
        assertTrue(jobs['publish-github'].contains('name: dist'))
        assertTrue(yaml.contains('name: dist'))
        assertTrue(jobs['publish-container'].contains('packages: write'))
        assertTrue(jobs['publish-github'].contains('contents: write'))
        assertTrue(jobs.setup.contains('actions: write'))
        assertTrue(jobs.setup.contains('run: make cleanup-actions-artifacts'))
        assertTrue(jobs.setup.contains('run: make setup'))
        assertTrue(jobs.build.contains('run: make build'))
        assertTrue(jobs.test.contains('run: make test'))
        assertTrue(jobs['test-java'].contains('run: make test-java'))
        assertTrue(jobs['publish-container'].contains('run: make login'))
        assertTrue(jobs['publish-container'].contains('run: make publish-container'))
        assertTrue(jobs['publish-container'].contains('run: make cleanup-container-versions'))
        assertTrue(jobs['publish-github'].contains('run: make publish-github'))
        assertTrue(jobs['publish-github'].contains('run: make cleanup-github-releases'))
        assertTrue(jobs.report.contains('run: make report'))
        assertTrue(jobs.report.contains('JOB_RESULT_TEST_JAVA:'))

        yaml.readLines().each { String line ->
            def uses = (line =~ /^\s*- uses:\s*(\S+)\s*$/)
            if (uses) {
                String action = uses[0][1]
                assertTrue(line, action == 'actions/checkout@v7'
                        || action == 'actions/upload-artifact@v7'
                        || action == 'actions/download-artifact@v8')
            }
            def run = (line =~ /^\s*run:\s*(.*)$/)
            if (run) {
                assertTrue(line, run[0][1] ==~ /make [a-z0-9-]+/)
            }
        }
        assertUploads(yaml)

        assertFalse(new File(root, '.github/workflows/build.yml').exists())
        assertFalse(new File(root, '.github/workflows/test.yml').exists())
        assertFalse(new File(root, '.github/workflows/image.yml').exists())
        assertFalse(new File(root, '.github/actions/prepare/action.yml').exists())
        assertFalse(new File(root, '.github/scripts/published-versions.sh').exists())

        byte[] sdlc = new File(root, 'sdlc.mk').bytes
        String hash = java.security.MessageDigest.getInstance('SHA-256').digest(sdlc).encodeHex().toString()
        assertEquals(SDLC_SHA256, hash)

        String makefile = new File(root, 'Makefile').text
        int includeAt = makefile.indexOf('\ninclude sdlc.mk\n')
        int setupAt = makefile.indexOf('\nsetup:')
        assertTrue(includeAt > 0)
        assertTrue(setupAt > includeAt)
        assertTrue(makefile.contains('IMAGE ?= ghcr.io/rbe/odisee26'))
        assertTrue(makefile.contains('GRADLE ?= ./gradlew'))
        assertTrue(makefile.contains('publish-tags'))
        assertTrue(makefile.contains('--platform linux/amd64,linux/arm64'))
        assertTrue(makefile.contains('qemu-user-binfmt'))
        assertTrue(makefile.contains('--cache-from'))
        assertFalse(makefile.contains('--cache-to'))
        assertFalse(makefile.contains('GITHUB_REF_NAME'))
        assertFalse(makefile.contains('published-versions.sh'))
        assertFalse(makefile.contains('docker build'))

        String ignore = new File(root, '.containerignore').text
        ['.claude/', 'AGENTS.md', 'docs/', '.cursor/', 'CLAUDE.md'].each { String entry ->
            assertTrue(entry, ignore.readLines().contains(entry))
        }
        assertTrue(makefile.contains('cp .containerignore build/image-context/.containerignore'))

        String rules = new File(root, 'docs/pipeline-rules.md').text
        assertTrue(rules.contains('test-java'))
        assertTrue(rules.contains('linux/amd64'))
        assertTrue(rules.contains('linux/arm64'))
        assertTrue(rules.contains('no `docs` job'))
        assertTrue(rules.contains('GRADLE=./gradlew'))
        assertTrue(rules.contains('.claude/'))
        assertTrue(rules.contains('AGENTS.md'))
        assertTrue(rules.contains('HTTP 404'))
        assertTrue(rules.contains('next-lower run'))
    }

    void testVersionRefusesAMissingTag() {
        ProcessBuilder builder = new ProcessBuilder(['make', 'version'])
        builder.directory(repoRoot())
        builder.environment().remove('GITHUB_RUN_NUMBER')
        Process process = builder.start()
        byte[] outBytes = null
        byte[] errBytes = null
        Thread outThread = Thread.start { outBytes = process.inputStream.readAllBytes() }
        Thread errThread = Thread.start { errBytes = process.errorStream.readAllBytes() }
        boolean finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
        outThread.join(5000)
        errThread.join(5000)
        String combined = new String(outBytes ?: new byte[0], 'UTF-8') + new String(errBytes ?: new byte[0], 'UTF-8')
        assertTrue(combined, finished)
        assertTrue(combined, process.exitValue() != 0)
        assertTrue(combined, combined.contains('Refusing to invent a version.'))
        assertFalse(combined, combined.contains('2.6.0'))
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

    private static void assertUploads(String yaml) {
        int uploads = 0
        List<String> lines = yaml.readLines()
        for (int i = 0; i < lines.size(); i++) {
            if (!lines[i].contains('uses: actions/upload-artifact@')) {
                continue
            }
            uploads++
            String name = null
            Integer days = null
            for (int j = i + 1; j < lines.size() && j < i + 25; j++) {
                String line = lines[j]
                if (line =~ /^\s*- /) {
                    break
                }
                def named = (line =~ /^\s*name:\s*(\S+)\s*$/)
                if (named && name == null) {
                    name = named[0][1]
                }
                def kept = (line =~ /retention-days:\s*(\d+)/)
                if (kept) {
                    days = Integer.valueOf(kept[0][1])
                    break
                }
            }
            assertNotNull("upload has no name near line ${i + 1}", name)
            assertNotNull("upload ${name} has no retention-days", days)
            if (name == 'report') {
                assertEquals(name, 14, days.intValue())
            } else {
                assertEquals(name, 1, days.intValue())
            }
        }
        assertTrue(uploads >= 7)
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
