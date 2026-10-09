package org.odisee.document

import groovy.test.GroovyTestCase
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

    void testRevisionDirectoryHonorsLatestAndANumber() {
        Path rev = dir.resolve('Contract').resolve('rev')
        Files.createDirectories(rev)
        Files.createFile(rev.resolve('3.ott'))
        Files.createFile(rev.resolve('10.ott'))

        Path latest = TemplateLocator.locate(dir, 'Contract', 'LATEST')
        Path numbered = TemplateLocator.locate(dir, 'Contract', '10')

        assertEquals(rev.resolve('10.ott'), latest)
        assertEquals(latest, numbered)
        assertEquals('10', TemplateLocator.revisionOf(latest))
        assertEquals(['3', '10'], TemplateLocator.revisions(dir, 'Contract'))
    }

    void testMissingRevisionDirectoryEntryIsNotFound() {
        Path rev = dir.resolve('Contract').resolve('rev')
        Files.createDirectories(rev)
        Files.createFile(rev.resolve('3.ott'))
        try {
            TemplateLocator.locate(dir, 'Contract', '4')
            fail('revision 4 is not on disk')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.NOT_FOUND, e.httpStatus)
        }
    }

    void testFlatFilesStillResolveBesideTheRevisionDirectory() {
        Files.createFile(dir.resolve('Contract_rev2.ott'))
        Files.createFile(dir.resolve('Contract.ott'))
        Path rev = dir.resolve('Contract').resolve('rev')
        Files.createDirectories(rev)
        Files.createFile(rev.resolve('2.ott'))

        Path latest = TemplateLocator.locate(dir, 'Contract', 'LATEST')
        Path flatOnly = TemplateLocator.locate(dir, 'Contract', '1')
        Path fromDirectory = TemplateLocator.locate(dir, 'Contract', '2')

        assertEquals(rev.resolve('2.ott'), latest)
        assertEquals('Contract.ott', flatOnly.fileName.toString())
        assertEquals(rev.resolve('2.ott'), fromDirectory)
        assertEquals(['1', '2'], TemplateLocator.revisions(dir, 'Contract'))
    }

    void testHigherFlatRevisionBeatsTheDirectory() {
        Files.createFile(dir.resolve('Contract_rev12.ott'))
        Path rev = dir.resolve('Contract').resolve('rev')
        Files.createDirectories(rev)
        Files.createFile(rev.resolve('4.ott'))

        Path latest = TemplateLocator.locate(dir, 'Contract', 'LATEST')

        assertEquals('Contract_rev12.ott', latest.fileName.toString())
        assertEquals('12', TemplateLocator.revisionOf(latest))
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
