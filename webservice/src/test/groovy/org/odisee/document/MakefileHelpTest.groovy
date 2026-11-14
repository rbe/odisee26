package org.odisee.document

import groovy.test.GroovyTestCase

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * The root Makefile is the frontend. {@code make help} lists every target.
 * The how-to stays in the comment block at the top of that file.
 */
class MakefileHelpTest extends GroovyTestCase {

    private static final List<String> TARGETS = [
            'setup',
            'build',
            'test',
            'test-java',
            'test-office',
            'test-minio',
            'test-keycloak',
            'login',
            'image',
            'publish-container',
            'publish-github',
            'stack',
            'user',
            'token',
            'pull',
            'run',
            'stop',
            'rm',
            'clean-images',
            'clean',
            'publish-tags',
            'help',
            'version',
            'ci',
            'report',
            'cleanup-container-versions',
            'cleanup-github-releases',
            'cleanup-actions-artifacts',
    ]

    void testMakeAndHelpListEveryTarget() {
        String help = make(0, ['help']).out
        String bare = make(0, []).out
        assertEquals(help, bare)
        List<String> lines = help.readLines().findAll { it.startsWith('  ') }
        assertEquals(TARGETS.size(), lines.size())
        assertTrue(help.contains('The Makefile is the interface'))
        assertTrue(help.contains('Podman is the only container engine'))
        assertTrue(help.contains('make ci runs setup build test report. It does not publish.'))
        TARGETS.eachWithIndex { String target, int index ->
            String line = lines[index]
            assertTrue(line, line.startsWith('  ' + target + ' '))
            String explanation = line.substring(target.length() + 2).trim()
            assertTrue(line, explanation.length() > 0)
        }
        assertTrue(help.contains('NAME defaults to press'))
        assertTrue(help.contains('Prints the client id and secret'))
        assertTrue(help.contains('CLIENT_ID and CLIENT_SECRET'))
        assertTrue(help.contains('Does not start a container'))
        assertTrue(help.contains('2.6.0-42'))
        assertTrue(help.contains('/opt/homebrew/bin/podman'))
        assertTrue(help.contains('/usr/local/bin/podman'))
    }

    void testHowToStaysInTheMakefileComment() {
        String makefile = Files.readString(repoRoot().toPath().resolve('Makefile'))
        int split = makefile.indexOf('.DEFAULT_GOAL')
        assertTrue(split > 0)
        String howTo = makefile.substring(0, split)
        [
                'make stack',
                'make user',
                'make token',
                'Authorization: Bearer',
                'http://127.0.0.1:8080/odisee/document/generate',
                'IntelliJ',
                'startStack',
                'Odisee group',
                'webservice/src/test/resources/intellij/',
                'localhost:8080',
                'bearer token',
                'ODISEE_KEYCLOAK_ISSUER=http://127.0.0.1:8180/realms/odisee',
                'ODISEE_KEYCLOAK_ADMIN=admin',
                'ODISEE_KEYCLOAK_ADMIN_PASSWORD=admin',
                'client id and secret',
                'http://127.0.0.1:8180/realms/odisee/protocol/openid-connect/token',
                'CLIENT_ID',
                'CLIENT_SECRET',
                'The workflow is CI',
                '.github/workflows/ci.yml',
                'vMAJOR.MINOR.PATCH',
                '2.6.0-<run>',
                'A missing tag fails the publish',
                'does not create a git tag',
                'latest is not moved by a branch run',
                '.claude/',
                'AGENTS.md',
                'podman pull ghcr.io/rbe/odisee26:$(VERSION)',
                'podman run -it -p 8080:8080 ghcr.io/rbe/odisee26:$(VERSION)',
                '2.6.0-42',
                './gradlew :webservice:test',
                ':webservice:libreOfficeTest',
                ':webservice:minioTest',
                ':webservice:keycloakTest',
                ':clients:client-java:javaClientOfficeTest',
                'start their own containers',
                'compose stack from make stack',
                'make stop',
                ':webservice:stopOdisee',
                'odisee-lo-test',
                'odisee-minio-test',
                'odisee-keycloak-test',
                'make rm',
                ':webservice:removeOdiseeContainers',
                'podman rm -f',
                'podman compose down',
                'A missing container is success',
                'make clean-images',
                ':webservice:cleanOdiseeImages',
                'odisee-libreoffice-test:local',
                'alpine/minio:RELEASE.2025-10-15T17-29-55Z',
                'quay.io/keycloak/keycloak:26.8.0',
                'A missing image is success',
                'Other images on the machine stay',
                '/opt/homebrew/bin/podman',
                '/usr/local/bin/podman',
                '/bin/bash is 3.2',
                'brew install bash',
        ].each { String fact ->
            assertTrue(fact, howTo.contains(fact))
        }
        String readme = Files.readString(repoRoot().toPath().resolve('README.md'))
        assertTrue(readme.contains('make help'))
        assertFalse(readme.contains('protocol/openid-connect/token'))
    }

    void testTargetsWrapTheExistingCommands() {
        String user = make(0, ['-n', 'user']).out
        assertTrue(user, user.contains('ODISEE_KEYCLOAK_ISSUER=http://127.0.0.1:8180/realms/odisee'))
        assertTrue(user, user.contains('ODISEE_KEYCLOAK_ADMIN=admin'))
        assertTrue(user, user.contains('ODISEE_KEYCLOAK_ADMIN_PASSWORD=admin'))
        assertTrue(user, user.contains('webservice/src/main/container/bin/odictl user "press"'))

        assertTrue(make(0, ['-n', 'stack']).out.contains('./gradlew :webservice:startStack'))
        String test = make(0, ['-n', '_test']).out
        assertTrue(test, test.contains('./gradlew --no-daemon --console=plain :webservice:test :clients:client-java:test :clients:client-javafx:test :oxt:test :webservice:integrationTest :test jacocoTestReport'))
        assertTrue(make(0, ['-n', 'test-office']).out.contains('./gradlew :webservice:libreOfficeTest'))
        assertTrue(make(0, ['-n', 'test-minio']).out.contains('./gradlew :webservice:minioTest'))
        assertTrue(make(0, ['-n', 'test-keycloak']).out.contains('./gradlew :webservice:keycloakTest'))
        assertTrue(make(0, ['-n', '_test-java']).out.contains('./gradlew --no-daemon --console=plain :clients:client-java:javaClientOfficeTest'))
        String image = make(0, ['-n', 'image']).out
        assertTrue(image, image.contains('podman build --platform linux/amd64,linux/arm64'))
        assertTrue(image, image.contains('qemu-user-binfmt'))
        String publish = make(0, ['-n', '_publish-container']).out
        assertTrue(publish, publish.contains('publish-tags'))
        assertTrue(publish, publish.contains('podman manifest push --all'))
        assertFalse(publish, publish.contains('GITHUB_REF_NAME'))
        assertTrue(make(0, ['-n', 'stop']).out.contains('./gradlew :webservice:stopOdisee'))
        assertTrue(make(0, ['-n', 'rm']).out.contains('./gradlew :webservice:removeOdiseeContainers'))
        assertTrue(make(0, ['-n', 'clean-images']).out.contains('./gradlew :webservice:cleanOdiseeImages'))

        String pull = make(0, ['-n', 'pull', 'VERSION=2.6.0-42']).out
        assertTrue(pull.contains('podman pull "ghcr.io/rbe/odisee26:2.6.0-42"'))
        String run = make(0, ['-n', 'run', 'VERSION=2.6.0-42']).out
        assertTrue(run.contains('podman run -it -p 8080:8080 "ghcr.io/rbe/odisee26:2.6.0-42"'))

        String token = make(0, ['-n', 'token', 'CLIENT_ID=press', 'CLIENT_SECRET=s3cret']).out
        assertTrue(token.contains('http://127.0.0.1:8180/realms/odisee/protocol/openid-connect/token'))
        assertTrue(token.contains('client_id=${CLIENT_ID}'))
        assertFalse(token.contains('s3cret'))
    }

    void testTokenAndPullFailInOneLineWhenInputIsMissing() {
        MakeResult token = make(2, ['token'])
        assertEquals('', token.out)
        assertOneLine(token.err, 'CLIENT_ID and CLIENT_SECRET are required')
        assertFalse(token.err.contains('client_secret'))

        MakeResult pull = make(2, ['pull'])
        assertEquals('', pull.out)
        assertOneLine(pull.err, 'VERSION is required, for example 2.6.0-42')
        assertFalse(pull.err.contains('podman pull'))
    }

    void testSetupResolvesDependenciesAndDoesNotPublish() {
        String dry = make(0, ['-n', '_setup']).out
        assertTrue(dry, dry.contains('ensure-jdk'))
        assertTrue(dry, dry.contains('./gradlew --no-daemon --console=plain :dependencies :webservice:dependencies :clients:client-java:dependencies :clients:client-javafx:dependencies :documentation:dependencies'))
        assertFalse(dry, dry.contains('podman push'))
        assertFalse(dry, dry.contains('packageDistribution'))
    }

    private static void assertOneLine(String err, String message) {
        String text = err.trim()
        assertFalse(text, text.contains('\n'))
        assertTrue(text, text.contains(message))
    }

    private static MakeResult make(Integer expected, List<String> args) {
        ProcessBuilder builder = new ProcessBuilder(['make', *args])
        builder.directory(repoRoot())
        builder.environment().remove('CLIENT_ID')
        builder.environment().remove('CLIENT_SECRET')
        builder.environment().remove('VERSION')
        builder.environment().remove('NAME')
        Process process = builder.start()
        byte[] outBytes = null
        byte[] errBytes = null
        Thread outThread = Thread.start { outBytes = process.inputStream.readAllBytes() }
        Thread errThread = Thread.start { errBytes = process.errorStream.readAllBytes() }
        boolean finished = process.waitFor(30, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
        }
        outThread.join(5000)
        errThread.join(5000)
        MakeResult result = new MakeResult(
                code: finished ? process.exitValue() : -1,
                out: new String(outBytes ?: new byte[0], StandardCharsets.UTF_8),
                err: new String(errBytes ?: new byte[0], StandardCharsets.UTF_8),
        )
        assertTrue(result.out + result.err, finished)
        if (expected != null) {
            assertEquals(result.out + result.err, expected.intValue(), result.code)
        }
        result
    }

    private static File repoRoot() {
        File dir = new File(System.getProperty('user.dir'))
        while (dir != null && !new File(dir, 'settings.gradle.kts').isFile()) {
            dir = dir.parentFile
        }
        assertNotNull(dir)
        dir
    }

    private static final class MakeResult {
        int code
        String out
        String err
    }
}
