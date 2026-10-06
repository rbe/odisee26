package org.odisee.document

import org.odisee.api.OdiseeException

import java.net.InetSocketAddress

class OdiinstParserTest extends GroovyTestCase {

    void testPortsComeFromTheFile() {
        String text = '''
# comment

odi1|10.0.0.1|2005|/usr/lib/libreoffice||nologo|true
odi2|10.0.0.2|2010|/usr/lib/libreoffice||nologo|true
'''
        List<String[]> rows = OdiinstParser.parse(text)
        List<InetSocketAddress> addresses = OdiinstParser.addresses(rows)

        assertEquals(2, addresses.size())
        assertEquals('10.0.0.1', addresses[0].hostString)
        assertEquals(2005, addresses[0].port)
        assertEquals('10.0.0.2', addresses[1].hostString)
        assertEquals(2010, addresses[1].port)
    }

    void testInvalidLineIsRejected() {
        try {
            OdiinstParser.parse('odi1|127.0.0.1')
            fail('short line')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.SERVER_ERROR, e.httpStatus)
        }
    }

}
