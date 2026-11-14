package org.odisee.document

import groovy.test.GroovyTestCase
import groovy.xml.DOMBuilder
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.odisee.api.OdiseeException
import org.odisee.io.FileHelper
import org.odisee.io.TenantPaths

import java.nio.file.Files
import java.nio.file.Path

class PostProcessServiceTest extends GroovyTestCase {

    private PostProcessService service = new PostProcessService()

    void testUnknownActionIsRejected() {
        OdiseeException error = runAction('<action type="delete-everything"/>')
        assertEquals(OdiseeException.BAD_REQUEST, error.httpStatus)
        assertTrue(error.message.contains('delete-everything'))
    }

    void testMergeInputCannotEscapeTheVarDirectory() {
        OdiseeException error = runAction('''
            <action type="merge-with">
                <input filename="../../etc/passwd"/>
            </action>
        ''', [output: ['/tmp/generated.pdf']])
        assertEquals(OdiseeException.BAD_REQUEST, error.httpStatus)
        assertTrue(error.message.contains('outside'))
    }

    void testMergeInputStaysUnderTheUserWorkDirectory() {
        Path resolved = service.mergeInput([principal: [getName: { 'ada' }] as java.security.Principal], 'merge/AGB.pdf')

        assertTrue(resolved.normalize().startsWith(TenantPaths.workDir('ada').normalize()))
        assertTrue(resolved.toString().endsWith("merge${File.separator}AGB.pdf"))
    }

    void testDecomposeFilenamePreservesDirectoryDotsAndMultiDotNames() {
        Map dottedDir = FileHelper.decomposeFilename('/home/odisee/.local/share/odisee-2.6.0/var/user/ada/output/Report.v2.pdf')
        assertEquals('/home/odisee/.local/share/odisee-2.6.0/var/user/ada/output/Report.v2', dottedDir.name)
        assertEquals('pdf', dottedDir.ext)

        Map noExtInDottedDir = FileHelper.decomposeFilename('/home/odisee/.local/share/odisee/Letter')
        assertEquals('/home/odisee/.local/share/odisee/Letter', noExtInDottedDir.name)
        assertEquals('xxx', noExtInDottedDir.ext)
    }

    void testMergeFailureThrows422InsteadOfAppendingNullDocument() {
        Path tempRoot = Files.createTempDirectory('odisee-merge-fail')
        Path userWork = TenantPaths.workDir('mergefailuser')
        try {
            Files.createDirectories(userWork)
            Path invalidPdf = userWork.resolve('corrupt.pdf')
            Files.writeString(invalidPdf, 'not-a-pdf')
            Path generatedPdf = tempRoot.resolve('generated.pdf')
            writeSinglePagePdf(generatedPdf)

            PostProcessService realService = new PostProcessService(
                    pdfService: new PdfService(),
                    storageService: new StorageService()
            )
            def document = DOMBuilder.parse(new StringReader('''
                <odisee><request><post-process>
                    <action type="merge-with">
                        <result-placeholder/>
                        <input filename="corrupt.pdf"/>
                    </action>
                </post-process></request></odisee>
            '''))
            Map arg = [
                    xml        : document.documentElement,
                    activeIndex: 0,
                    result     : [output: [generatedPdf.toString()]],
                    document   : [],
                    principal  : [getName: { 'mergefailuser' }] as java.security.Principal
            ]
            try {
                realService.postProcessRequest(arg)
                fail('Expected corrupt PDF merge to fail with 422')
            } catch (OdiseeException e) {
                assertEquals(OdiseeException.UNPROCESSABLE, e.httpStatus)
            }
            assertTrue(arg.document.isEmpty())
        } finally {
            tempRoot.toFile().deleteDir()
            TenantPaths.userRoot('mergefailuser').toFile().deleteDir()
        }
    }

    void testMergeWithPreservesDotsInParentDirectoryPath() {
        Path tempRoot = Files.createTempDirectory('odisee-merge-dots')
        Path dottedOutputDir = tempRoot.resolve('.local').resolve('odisee-2.6.0')
        Path userWork = TenantPaths.workDir('mergedotsuser')
        try {
            Files.createDirectories(dottedOutputDir)
            Files.createDirectories(userWork.resolve('pdf'))
            Path generatedPdf = dottedOutputDir.resolve('Invoice.2026.pdf')
            Path appendixPdf = userWork.resolve('pdf/AGB.pdf')
            writeSinglePagePdf(generatedPdf)
            writeSinglePagePdf(appendixPdf)

            PostProcessService realService = new PostProcessService(
                    pdfService: new PdfService(),
                    storageService: new StorageService()
            )
            def document = DOMBuilder.parse(new StringReader('''
                <odisee><request><post-process>
                    <action type="merge-with">
                        <result-placeholder/>
                        <input filename="pdf/AGB.pdf"/>
                    </action>
                </post-process></request></odisee>
            '''))
            Map arg = [
                    xml        : document.documentElement,
                    activeIndex: 0,
                    result     : [output: [generatedPdf.toString()]],
                    document   : [],
                    principal  : [getName: { 'mergedotsuser' }] as java.security.Principal
            ]

            realService.postProcessRequest(arg)

            Path expectedMerged = dottedOutputDir.resolve('Invoice.2026_merged.pdf')
            assertTrue(Files.isRegularFile(expectedMerged))
            assertEquals(1, arg.document.size())
            assertNotNull(arg.document[0])
            assertEquals('Invoice.2026_merged.pdf', arg.document[0].filename)
            PDDocument loaded = Loader.loadPDF((byte[]) arg.document[0].bytes)
            try {
                assertEquals(2, loaded.numberOfPages)
            } finally {
                loaded.close()
            }
        } finally {
            tempRoot.toFile().deleteDir()
            TenantPaths.userRoot('mergedotsuser').toFile().deleteDir()
        }
    }

    private static void writeSinglePagePdf(Path target) {
        PDDocument pdf = new PDDocument()
        try {
            pdf.addPage(new PDPage())
            pdf.save(target.toFile())
        } finally {
            pdf.close()
        }
    }

    private OdiseeException runAction(String actionXml, Map result = null) {
        def document = DOMBuilder.parse(new StringReader("""
            <odisee><request><post-process>${actionXml}</post-process></request></odisee>
        """))
        Map arg = [
                xml        : document.documentElement,
                activeIndex: 0,
                result     : result,
                principal  : [getName: { 'odisee' }] as java.security.Principal
        ]
        try {
            service.postProcessRequest(arg)
            fail(actionXml)
            return null
        } catch (OdiseeException e) {
            return e
        }
    }

}
