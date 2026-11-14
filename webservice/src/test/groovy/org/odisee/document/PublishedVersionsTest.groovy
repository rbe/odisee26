package org.odisee.document

import groovy.test.GroovyTestCase

/**
 * A publish keeps the version it just wrote and the one immediately before it.
 */
class PublishedVersionsTest extends GroovyTestCase {

    void testDistributionKeepsCurrentAndFormer() {
        String records = tsv(
                '101|odisee-2.6.0-10-linux-x86_64',
                '102|odisee-2.6.0-11-linux-x86_64',
                '103|odisee-2.6.0-12-linux-x86_64',
                '104|build-outputs',
                '105|odisee-2.6.0-9-linux-x86_64',
                '106|java-client-office-reports',
                '107|test-reports',
        )
        assertEquals(['101', '105'] as Set, ids('artifacts', '2.6.0-12', records))
    }

    void testDistributionKeepsANewerRun() {
        String records = tsv(
                '1|odisee-2.6.0-9-linux-x86_64',
                '2|odisee-2.6.0-99-linux-x86_64',
                '3|odisee-2.6.0-100-linux-x86_64',
                '4|odisee-2.6.0-101-linux-x86_64',
        )
        assertEquals(['1'] as Set, ids('artifacts', '2.6.0-100', records))
    }

    void testFirstDistributionDeletesNothing() {
        String records = tsv('7|odisee-2.6.0-1-linux-x86_64')
        assertEquals([] as Set, ids('artifacts', '2.6.0-1', records))
    }

    void testImageKeepsCurrentFormerAndLatest() {
        String records = tsv(
                '1|2.6.0-13',
                '2|2.6.0-12',
                '3|2.6.0-10,latest',
                '4|2.6.0-9',
                '5|latest',
                '6|2.6.0-8,v2.6.0',
        )
        assertEquals(['4'] as Set, ids('images', '2.6.0-13', records))
    }

    void testImageDeletesOnlyOlderRunTags() {
        String records = tsv(
                '1|2.6.0-12,latest',
                '2|2.6.0-11',
                '3|2.6.0-10',
                '4|2.6.0-2',
        )
        assertEquals(['3', '4'] as Set, ids('images', '2.6.0-12', records))
    }

    void testRunNumbersSortNumerically() {
        String records = tsv(
                '1|2.6.0-9',
                '2|2.6.0-99',
                '3|2.6.0-100',
        )
        assertEquals(['1'] as Set, ids('images', '2.6.0-100', records))
    }

    void testFeatureBranchDispatchDoesNotEvictMasterVersions() {
        String imageRecords = tsv(
                '1|2.6.0-12',
                '2|2.6.0-11,latest',
                '3|2.6.0-10',
                '4|2.6.0-9',
        )
        assertEquals([] as Set, ids('images', '2.6.0-12', imageRecords, 'refs/heads/feature/fix-container-cli-ci'))
        assertEquals([] as Set, ids('images', '2.6.0-12', imageRecords, 'refs/pull/52/merge'))
        assertEquals(['3', '4'] as Set, ids('images', '2.6.0-12', imageRecords, 'refs/heads/master'))
        assertEquals(['3', '4'] as Set, ids('images', '2.6.0-12', imageRecords, 'refs/tags/v2.6.0'))

        String artifactRecords = tsv(
                '101|odisee-2.6.0-10-linux-x86_64',
                '102|odisee-2.6.0-11-linux-x86_64',
                '103|odisee-2.6.0-12-linux-x86_64',
        )
        assertEquals([] as Set, ids('artifacts', '2.6.0-12', artifactRecords, 'refs/heads/feature/fix-container-cli-ci'))
        assertEquals(['101'] as Set, ids('artifacts', '2.6.0-12', artifactRecords, 'refs/heads/master'))
        assertEquals(['101'] as Set, ids('artifacts', '2.6.0-12', artifactRecords, 'refs/tags/v2.6.0'))
    }

    void testGhImagesHandlesHttp404OnNewPackageCleanly() {
        File work = java.nio.file.Files.createTempDirectory('odisee-gh-404').toFile()
        try {
            writeFakeGh(work, 1, '', 'gh: Package not found. (HTTP 404)')
            Map result = runPipeline(work, 'users/rbe', 'odisee26', '2.6.0-1', 'refs/heads/master')
            assertEquals(result.err, 0, result.code)
            assertEquals([] as Set, result.lines)
        } finally {
            work.deleteDir()
        }
    }

    void testGhImagesPropagatesNon404ErrorsAndListsExistingVersions() {
        File work = java.nio.file.Files.createTempDirectory('odisee-gh-list').toFile()
        try {
            writeFakeGh(work, 1, '', 'gh: Bad credentials (HTTP 401)')
            Map failed = runPipeline(work, 'users/rbe', 'odisee26', '2.6.0-12', 'refs/heads/master')
            assertTrue(failed.err, failed.code != 0)
            assertTrue(failed.err, failed.err.contains('HTTP 401'))

            String payload = tsv(
                    '11|2.6.0-12,latest',
                    '12|2.6.0-11',
                    '13|2.6.0-10',
            )
            writeFakeGh(work, 0, payload, '')
            Map listed = runPipeline(work, 'rbe', 'odisee26', '2.6.0-12', 'refs/heads/master')
            assertEquals(listed.err, 0, listed.code)
            assertEquals(['13'] as Set, listed.lines)
        } finally {
            work.deleteDir()
        }
    }

    private static String tsv(String... rows) {
        rows.collect { String row -> row.replace('|', '\t') }.join('\n')
    }

    private static Set<String> ids(String mode, String version, String records, String ref = null) {
        File script = new File(repoRoot(), '.github/scripts/published-versions.sh')
        List<String> command = ['bash', script.absolutePath, mode, version]
        if (ref != null) {
            command << ref
        }
        Process process = command.execute()
        OutputStream stdin = process.outputStream
        stdin.write((records.endsWith('\n') ? records : records + '\n').getBytes('UTF-8'))
        stdin.close()
        String err = process.errorStream.getText('UTF-8')
        String out = process.inputStream.getText('UTF-8')
        int code = process.waitFor()
        assertEquals(err, 0, code)
        out.readLines().findAll { String line -> line.trim() } as Set
    }

    private static void writeFakeGh(File dir, int versionsExit, String stdout, String stderr) {
        File outFile = new File(dir, 'gh.out')
        File errFile = new File(dir, 'gh.err')
        outFile.setText(stdout, 'UTF-8')
        errFile.setText(stderr, 'UTF-8')
        File gh = new File(dir, 'gh')
        gh.setText("""#!/usr/bin/env bash
set -eu
if [[ "\$*" == *"users/"*"--jq .type"* ]]; then
  printf 'User\\n'
  exit 0
fi
cat '${outFile.absolutePath}'
cat '${errFile.absolutePath}' >&2
exit ${versionsExit}
""", 'UTF-8')
        gh.setExecutable(true)
    }

    private static Map runPipeline(File dir, String target, String pkg, String version, String ref) {
        File script = new File(repoRoot(), '.github/scripts/published-versions.sh')
        ProcessBuilder builder = new ProcessBuilder([
                'bash', '-c',
                'set -euo pipefail; bash "$1" gh-images "$2" "$3" | bash "$1" images "$4" "$5"',
                '--',
                script.absolutePath, target, pkg, version, ref,
        ])
        builder.environment().put('PATH', (dir.absolutePath + ':/usr/bin:/bin'))
        Process process = builder.start()
        String out = process.inputStream.getText('UTF-8')
        String err = process.errorStream.getText('UTF-8')
        int code = process.waitFor()
        [code: code, out: out, err: err, lines: out.readLines().findAll { it.trim() } as Set]
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
