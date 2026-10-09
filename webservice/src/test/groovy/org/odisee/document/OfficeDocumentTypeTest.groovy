package org.odisee.document

import groovy.test.GroovyTestCase

class OfficeDocumentTypeTest extends GroovyTestCase {

    void testSpreadsheetDocumentExtension() {
        assertEquals('ots', OfficeDocumentType.SPREADSHEET.templateExtension)
        assertEquals('ods', OfficeDocumentType.SPREADSHEET.documentExtension)
        assertEquals('otp', OfficeDocumentType.PRESENTATION.templateExtension)
        assertEquals('odp', OfficeDocumentType.PRESENTATION.documentExtension)
        assertEquals('impress_pdf_Export', OfficeDocumentType.PRESENTATION.pdfExportFilter)
    }

}
