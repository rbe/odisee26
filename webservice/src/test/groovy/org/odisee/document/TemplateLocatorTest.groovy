package org.odisee.document

import org.odisee.api.OdiseeException

import java.nio.file.Files
import java.nio.file.Path

class TemplateLocatorTest extends GroovyTestCase {

    private Path dir

    @Override
    protected void setUp() {
        dir = Files.createTempDirectory('odisee-templates')
    }

    @Override
    protected void tearDown() {
        dir?.toFile()?.deleteDir()
    }

    void testLatestIsHighestNumericRevision() {
        Files.createFile(dir.resolve('Contract_rev9.ott'))
        Files.createFile(dir.resolve('Contract_rev10.ott'))

        Path found = TemplateLocator.locate(dir, 'Contract', 'LATEST')

        assertEquals('Contract_rev10.ott', found.fileName.toString())
        assertEquals('10', TemplateLocator.revisionOf(found))
    }

    void testUnversionedFileIsRevisionOne() {
        Files.createFile(dir.resolve('HalloOdisee.ott'))

        Path latest = TemplateLocator.locate(dir, 'HalloOdisee', null)
        Path revisionOne = TemplateLocator.locate(dir, 'HalloOdisee', '1')

        assertEquals('HalloOdisee.ott', latest.fileName.toString())
        assertEquals(latest, revisionOne)
        assertEquals('1', TemplateLocator.revisionOf(latest))
    }

    void testMissingRevisionIsNotFound() {
        Files.createFile(dir.resolve('HalloOdisee.ott'))
        try {
            TemplateLocator.locate(dir, 'HalloOdisee', '2')
            fail('revision 2 is not on disk')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.NOT_FOUND, e.httpStatus)
        }
    }

    void testTemplateNameCannotEscapeTheDirectory() {
        try {
            TemplateLocator.locate(dir, '../secret', 'LATEST')
            fail('template name escaped')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.BAD_REQUEST, e.httpStatus)
        }
    }

}
