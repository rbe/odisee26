package org.odisee.document

class OfficeDocumentTypeTest extends GroovyTestCase {

    void testSpreadsheetDocumentExtension() {
        assertEquals('ots', OfficeDocumentType.SPREADSHEET.templateExtension)
        assertEquals('ods', OfficeDocumentType.SPREADSHEET.documentExtension)
    }

}
