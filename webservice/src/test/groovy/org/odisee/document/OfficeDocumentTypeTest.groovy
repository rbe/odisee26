package org.odisee.document

import groovy.test.GroovyTestCase

class OfficeDocumentTypeTest extends GroovyTestCase {

    void testSpreadsheetDocumentExtension() {
        assertEquals('ots', OfficeDocumentType.SPREADSHEET.templateExtension)
        assertEquals('ods', OfficeDocumentType.SPREADSHEET.documentExtension)
    }

}
