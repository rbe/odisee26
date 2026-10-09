package org.odisee.document

import groovy.test.GroovyTestCase
import org.odisee.api.OdiseeException

class FormatOptionsTest extends GroovyTestCase {

    void testPdfVersionTaggedPdfAndWatermark() {
        Map<String, Object> data = FormatOptions.filterData([
                [name: 'pdf-version', value: 'PDF/A-1'],
                [name: 'tagged', value: 'true'],
                [name: 'watermark', value: 'DRAFT']
        ])

        assertEquals(1, data.SelectPdfVersion)
        assertEquals(Boolean.TRUE, data.UseTaggedPDF)
        assertEquals('DRAFT', data.Watermark)
    }

    void testPdfaSuffixRemainsTheFallback() {
        assertTrue(FormatOptions.pdfaFallback('Letter.pdfa', [:]))
        assertFalse(FormatOptions.pdfaFallback('Letter.pdfa', [SelectPdfVersion: 1]))
        assertFalse(FormatOptions.pdfaFallback('Letter.pdf', [:]))
        assertTrue(FormatOptions.pdfFamily('Letter.pdf'))
        assertTrue(FormatOptions.pdfFamily('Letter.pdfa'))
        assertFalse(FormatOptions.pdfFamily('Letter.odt'))
    }

    void testUnknownOptionIsNotAFilterProperty() {
        Map<String, Object> data = FormatOptions.filterData([
                [name: 'copies', value: '2']
        ])

        assertTrue(data.isEmpty())
    }

    void testUnknownPdfVersionIs400() {
        try {
            FormatOptions.pdfVersion('nope')
            fail('version')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.BAD_REQUEST, e.httpStatus)
        }
    }

}
