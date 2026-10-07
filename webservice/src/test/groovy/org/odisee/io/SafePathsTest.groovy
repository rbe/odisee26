package org.odisee.io

import org.odisee.api.OdiseeException

import java.nio.file.Files
import java.nio.file.Path

class SafePathsTest extends GroovyTestCase {

    private Path root

    @Override
    protected void setUp() {
        root = Files.createTempDirectory('odisee-var')
    }

    @Override
    protected void tearDown() {
        root?.toFile()?.deleteDir()
    }

    void testRelativeMergePathStaysUnderTheRoot() {
        Path resolved = SafePaths.resolveInside(root, 'merge/AGB.pdf')

        assertTrue(resolved.normalize().startsWith(root.normalize()))
        assertTrue(resolved.toString().endsWith("merge${File.separator}AGB.pdf"))
    }

    void testAbsolutePathAndParentSegmentsAreRejected() {
        assertRejected('/etc/passwd')
        assertRejected('../secret')
    }

    private void assertRejected(String relative) {
        try {
            SafePaths.resolveInside(root, relative)
            fail(relative)
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.BAD_REQUEST, e.httpStatus)
        }
    }

}
