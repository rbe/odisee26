package org.odisee.document

import groovy.test.GroovyTestCase

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Stop, remove, and image cleanup touch only the Odisee test containers,
 * the compose project, and the images those commands built or pulled.
 * A missing container or image is success.
 */
class OdiseeContainerCleanupTest extends GroovyTestCase {

    private static final List<String> TEST_CONTAINERS = [
            'odisee-lo-test',
            'odisee-minio-test',
            'odisee-keycloak-test',
    ]

    private static final List<String> ODISEE_IMAGES = [
            'odisee-libreoffice-test:local',
            'alpine/minio:RELEASE.2025-10-15T17-29-55Z',
            'quay.io/keycloak/keycloak:26.8.0',
            'odisee/odisee:2.6.0',
            'odisee/webservice:2.6.0',
    ]

    void testMissingContainersAndImagesAreSuccess() {
        File work = Files.createTempDirectory('odisee-podman').toFile()
        try {
            RunResult stop = run(0, work, [:], 'stop')
            assertTrue(stop.log, commands(stop.log, 'stop').isEmpty())
            assertTrue(stop.log, commands(stop.log, 'rm').isEmpty())
            assertTrue(stop.log, commands(stop.log, 'rmi').isEmpty())
            assertTrue(stop.log, stop.log.contains('compose -p odisee'))
            assertTrue(stop.log, stop.log.contains(' stop'))

            RunResult removed = run(0, work, [:], 'rm')
            TEST_CONTAINERS.each { String name ->
                assertTrue(removed.log, removed.log.contains("rm -f ${name}"))
            }
            assertTrue(removed.log, removed.log.contains(' down'))
            assertTrue(removed.log, commands(removed.log, 'rmi').isEmpty())

            RunResult images = run(0, work, [:], 'images', ODISEE_IMAGES)
            assertTrue(images.log, commands(images.log, 'rmi').isEmpty())
            assertFalse(images.log, images.log.contains('prune'))
        } finally {
            work.deleteDir()
        }
    }

    void testStopAndRemoveOnlyTheOdiseeContainers() {
        File work = Files.createTempDirectory('odisee-podman').toFile()
        try {
            Map<String, String> present = [PODMAN_PRESENT_CONTAINERS: TEST_CONTAINERS.join('\n')]
            RunResult stop = run(0, work, present, 'stop')
            assertEquals(TEST_CONTAINERS, commands(stop.log, 'stop'))
            assertTrue(stop.log, stop.log.contains('--project-directory'))
            assertTrue(stop.log, stop.log.contains('compose.yaml stop'))

            RunResult removed = run(0, work, present, 'rm')
            assertEquals(TEST_CONTAINERS, commands(removed.log, 'rm'))
            assertTrue(removed.log, removed.log.contains('compose.yaml down'))
            assertFalse(removed.log, removed.log.contains(' -v'))
            assertFalse(removed.log, removed.log.contains('--rmi'))
        } finally {
            work.deleteDir()
        }
    }

    void testImagesRemovesOnlyTheRequestedTags() {
        File work = Files.createTempDirectory('odisee-podman').toFile()
        try {
            List<String> present = ['odisee-libreoffice-test:local', 'ubuntu:26.04', 'ghcr.io/rbe/odisee26:latest']
            RunResult images = run(0, work, [PODMAN_PRESENT_IMAGES: present.join('\n')], 'images', ODISEE_IMAGES)
            assertEquals(['odisee-libreoffice-test:local'], commands(images.log, 'rmi'))
            assertFalse(images.log.contains('ubuntu:26.04') && images.log.contains('rmi ubuntu'))
            assertFalse(images.log, commands(images.log, 'rmi').contains('ubuntu:26.04'))
            assertFalse(images.log, commands(images.log, 'rmi').contains('ghcr.io/rbe/odisee26:latest'))
            assertTrue(images.log, commands(images.log, 'rm').isEmpty())
        } finally {
            work.deleteDir()
        }
    }

    void testRemovalFailureWhenTheContainerOrImageRemains() {
        File work = Files.createTempDirectory('odisee-podman').toFile()
        try {
            RunResult stop = run(null, work, [
                    PODMAN_PRESENT_CONTAINERS: 'odisee-lo-test',
                    PODMAN_FAIL: 'stop',
            ], 'stop')
            assertTrue(stop.err + stop.out, stop.code != 0)

            RunResult removed = run(null, work, [
                    PODMAN_PRESENT_CONTAINERS: 'odisee-minio-test',
                    PODMAN_FAIL: 'rm',
            ], 'rm')
            assertTrue(removed.err + removed.out, removed.code != 0)

            RunResult images = run(null, work, [
                    PODMAN_PRESENT_IMAGES: 'odisee/odisee:2.6.0',
                    PODMAN_FAIL: 'rmi',
            ], 'images', ['odisee/odisee:2.6.0'])
            assertTrue(images.err + images.out, images.code != 0)
        } finally {
            work.deleteDir()
        }
    }

    void testGradleTasksUseThePodmanLookup() {
        String gradle = Files.readString(repoRoot().toPath().resolve('webservice').resolve('build.gradle.kts'))
        int helper = gradle.indexOf('fun odiseeContainerCommand')
        assertTrue(helper > 0)
        String tail = gradle.substring(helper)
        assertTrue(tail.contains('podmanExecutable'))
        assertTrue(tail.contains('gradle/odisee-containers.sh'))
        assertTrue(tail.contains('tasks.register<Exec>("stopOdisee")'))
        assertTrue(tail.contains('tasks.register<Exec>("removeOdiseeContainers")'))
        assertTrue(tail.contains('tasks.register<Exec>("cleanOdiseeImages")'))
        assertTrue(tail.contains('group = "Odisee"'))
        assertTrue(tail.contains('odiseeContainerCommand("stop")'))
        assertTrue(tail.contains('odiseeContainerCommand("rm")'))
        assertTrue(tail.contains('dependsOn("removeOdiseeContainers")'))
        assertTrue(tail.contains('odiseeContainerCommand("images"'))
        assertTrue(tail.contains('"odisee-libreoffice-test:local"'))
        assertTrue(tail.contains('minioImage'))
        assertTrue(tail.contains('keycloakImage'))
        assertTrue(tail.contains('"odisee/odisee:$version"'))
        assertTrue(tail.contains('"odisee/webservice:$version"'))
        assertFalse(tail.contains('image prune'))
        assertFalse(tail.contains('system prune'))
    }

    private static List<String> commands(String log, String command) {
        log.readLines().findAll { String line ->
            line == command || line.startsWith(command + ' ')
        }.collect { String line ->
            List<String> parts = line.split(' ').toList()
            parts[parts.size() - 1]
        }
    }

    private static RunResult run(Integer expected, File work, Map<String, String> env, String action, List<String> images = []) {
        File fake = new File(work, 'podman')
        fake.text = fakePodman()
        fake.setExecutable(true)
        File log = new File(work, 'log')
        List<String> command = [
                'bash',
                new File(repoRoot(), 'gradle/odisee-containers.sh').absolutePath,
                action,
                fake.absolutePath,
                work.absolutePath,
                '2.6.0',
                *images,
        ]
        ProcessBuilder builder = new ProcessBuilder(command)
        builder.directory(work)
        builder.environment().put('PODMAN_LOG', log.absolutePath)
        builder.environment().put('PODMAN_PRESENT_CONTAINERS', env.get('PODMAN_PRESENT_CONTAINERS', ''))
        builder.environment().put('PODMAN_PRESENT_IMAGES', env.get('PODMAN_PRESENT_IMAGES', ''))
        builder.environment().put('PODMAN_FAIL', env.get('PODMAN_FAIL', ''))
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
        RunResult result = new RunResult(
                code: finished ? process.exitValue() : -1,
                out: new String(outBytes ?: new byte[0], StandardCharsets.UTF_8),
                err: new String(errBytes ?: new byte[0], StandardCharsets.UTF_8),
                log: log.isFile() ? log.getText('UTF-8') : '',
        )
        assertTrue(result.out + result.err + result.log, finished)
        if (expected != null) {
            assertEquals(result.out + result.err + result.log, expected.intValue(), result.code)
        }
        result
    }

    private static String fakePodman() {
        '''#!/usr/bin/env bash
set -eu
{
  printf '%s' "$1"
  for arg in "${@:2}"; do
    printf ' %s' "$arg"
  done
  printf '\\n'
} >> "$PODMAN_LOG"
cmd=$1
shift
present() {
  printf '%s\\n' "$2" | grep -Fxq -- "$1"
}
case "$cmd" in
  container)
    sub=$1
    name=$2
    if [ "$sub" = exists ]; then
      present "$name" "$PODMAN_PRESENT_CONTAINERS"
      exit $?
    fi
    ;;
  image)
    sub=$1
    name=$2
    if [ "$sub" = exists ]; then
      present "$name" "$PODMAN_PRESENT_IMAGES"
      exit $?
    fi
    ;;
  rm)
    name=$2
    if [ "$PODMAN_FAIL" = rm ]; then
      exit 1
    fi
    present "$name" "$PODMAN_PRESENT_CONTAINERS"
    exit $?
    ;;
  stop)
    if [ "$PODMAN_FAIL" = stop ]; then
      exit 1
    fi
    exit 0
    ;;
  rmi)
    if [ "$PODMAN_FAIL" = rmi ]; then
      exit 1
    fi
    exit 0
    ;;
  compose)
    if [ "$PODMAN_FAIL" = compose ]; then
      exit 1
    fi
    exit 0
    ;;
esac
exit 0
'''
    }

    private static File repoRoot() {
        File dir = new File(System.getProperty('user.dir'))
        while (dir != null && !new File(dir, 'settings.gradle.kts').isFile()) {
            dir = dir.parentFile
        }
        assertNotNull(dir)
        dir
    }

    private static final class RunResult {
        int code
        String out
        String err
        String log
    }
}
