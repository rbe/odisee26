package org.odisee.document

import groovy.xml.DOMBuilder
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.odisee.api.OdiseeException
import org.odisee.io.OdiseePath
import org.w3c.dom.Element

import java.nio.file.Files
import java.nio.file.Path

/**
 * Tests 1-3 against LibreOffice listening on 127.0.0.1:2002.
 * {@code ./gradlew libreOfficeTest} starts that process in Docker and mounts
 * {@code ODISEE_HOME} at the same path inside the container.
 */
class GenerationBasisTest extends GroovyTestCase {

    private static final int PORT = Integer.getInteger('odisee.libreoffice.port', 2002)

    private OdiseeService service

    private java.security.Principal caller = [getName: { 'odisee' }] as java.security.Principal

    @Override
    protected void setUp() {
        Socket socket = new Socket()
        try {
            socket.connect(new InetSocketAddress('127.0.0.1', PORT), 2000)
        } catch (IOException e) {
            fail("LibreOffice is not accepting connections on 127.0.0.1:${PORT}. Run ./gradlew libreOfficeTest")
        } finally {
            socket.close()
        }
        Path home = OdiseePath.ODISEE_HOME
        Files.createDirectories(home.resolve('etc'))
        Files.write(home.resolve('etc/odiinst'),
                "odi1|127.0.0.1|${PORT}|/usr/lib/libreoffice||nologo|true\n".getBytes('UTF-8'))
        Path templates = OdiseePath.ODISEE_VAR.resolve('template')
        Files.createDirectories(templates)
        MinimalTemplate.write(templates.resolve('Letter.ott'))

        RequestService requests = new RequestService()
        requests.afterPropertiesSet()
        service = new OdiseeService()
        service.templateService = new TemplateService()
        service.requestService = requests
        service.storageService = new StorageService()
        PdfService pdf = new PdfService()
        PostProcessService post = new PostProcessService()
        post.storageService = service.storageService
        post.pdfService = pdf
        service.postProcessService = post
    }

    void test1_twoCallsDoNotShareDocuments() {
        List first = service.generateDocument(caller, request('One', 'alpha-field'))
        List second = service.generateDocument(caller, request('Two', 'beta-field'))

        assertEquals(1, first.size())
        assertEquals(1, second.size())
        assertFalse(first.is(second))
        assertTrue(pdfText(first[0].bytes).contains('alpha-field'))
        assertTrue(pdfText(second[0].bytes).contains('beta-field'))
        assertFalse(pdfText(second[0].bytes).contains('alpha-field'))
    }

    void test2_twoRequestsStayInOrderAndMerge() {
        String xml = '''<odisee>
  <request name="Left">
    <template name="Letter" outputFormat="pdf"/>
    <instructions><userfield name="Hallo">left-side</userfield></instructions>
  </request>
  <request name="Right">
    <template name="Letter" outputFormat="pdf"/>
    <instructions><userfield name="Hallo">right-side</userfield></instructions>
  </request>
  <post-process><action type="merge-results"/></post-process>
</odisee>'''
        List documents = service.generateDocument(caller, parse(xml))

        assertEquals(3, documents.size())
        assertEquals('Left.pdf', documents[0].filename)
        assertEquals('Right.pdf', documents[1].filename)
        assertTrue(pdfText(documents[0].bytes).contains('left-side'))
        assertTrue(pdfText(documents[1].bytes).contains('right-side'))
        String merged = pdfText(documents[2].bytes)
        assertTrue(merged.contains('left-side'))
        assertTrue(merged.contains('right-side'))
    }

    void test3_badInstructionDoesNotSaveAndOfficeStaysUsable() {
        String xml = '''<odisee>
  <request name="Bad">
    <template name="Letter" outputFormat="pdf"/>
    <instructions><explode name="x">nope</explode></instructions>
  </request>
</odisee>'''
        try {
            service.generateDocument(caller, parse(xml))
            fail('unsupported instruction')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.UNPROCESSABLE, e.httpStatus)
        }
        Path documents = OdiseePath.ODISEE_VAR.resolve('document')
        if (Files.exists(documents)) {
            Files.walk(documents).withCloseable { stream ->
                assertFalse(stream.any { it.fileName.toString() == 'Bad.pdf' })
            }
        }

        List recovered = service.generateDocument(caller, request('After', 'still-open'))
        assertEquals(1, recovered.size())
        assertTrue(pdfText(recovered[0].bytes).contains('still-open'))
    }

    private static Element request(String name, String value) {
        parse("""<odisee>
  <request name="${name}">
    <template name="Letter" outputFormat="pdf"/>
    <instructions><userfield name="Hallo">${value}</userfield></instructions>
  </request>
</odisee>""")
    }

    private static Element parse(String xml) {
        DOMBuilder.parse(new StringReader(xml)).documentElement
    }

    private static String pdfText(byte[] bytes) {
        PDDocument document = PDDocument.load(bytes)
        try {
            new PDFTextStripper().getText(document)
        } finally {
            document.close()
        }
    }

}
