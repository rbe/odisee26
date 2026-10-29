package org.odisee.writer

import groovy.test.GroovyTestCase

class OdiseeFileFormatTest extends GroovyTestCase {

    void testDocxIsTheWholeSuffix() {
        assertEquals('docx', OdiseeFileFormat.extensionOf('HalloOdisee.DOCX'))
        assertEquals('pdf', OdiseeFileFormat.extensionOf('Letter.pdf'))
        assertEquals('MS Word 2007 XML', OdiseeFileFormat.get('docx').filter)
        assertEquals('writer_pdf_Export', OdiseeFileFormat['pdf'].filter)
    }

}
