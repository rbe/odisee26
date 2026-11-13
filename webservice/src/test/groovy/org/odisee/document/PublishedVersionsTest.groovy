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

    private static String tsv(String... rows) {
        rows.collect { String row -> row.replace('|', '\t') }.join('\n')
    }

    private static Set<String> ids(String mode, String version, String records) {
        File script = new File(repoRoot(), '.github/scripts/published-versions.sh')
        Process process = ['bash', script.absolutePath, mode, version].execute()
        OutputStream stdin = process.outputStream
        stdin.write((records.endsWith('\n') ? records : records + '\n').getBytes('UTF-8'))
        stdin.close()
        String err = process.errorStream.getText('UTF-8')
        String out = process.inputStream.getText('UTF-8')
        int code = process.waitFor()
        assertEquals(err, 0, code)
        out.readLines().findAll { String line -> line.trim() } as Set
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
