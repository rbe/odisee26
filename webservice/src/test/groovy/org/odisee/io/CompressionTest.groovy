package org.odisee.io

import groovy.test.GroovyTestCase
import org.odisee.api.OdiseeException

class CompressionTest extends GroovyTestCase {

    void testGzipRoundTrip() {
        byte[] zipped = Compression.zip('hello'.bytes)
        String decoded = Compression.decompress(new ByteArrayInputStream(zipped)).getText('UTF-8')

        assertEquals('hello', decoded)
    }

    void testShortBodyIsReturnedUnchanged() {
        InputStream decoded = Compression.decompress(new ByteArrayInputStream([65] as byte[]))

        assertEquals(65, decoded.read())
        assertEquals(-1, decoded.read())
    }

    void testLimitedGzipRoundTrip() {
        byte[] zipped = Compression.zip('hello'.bytes)

        assertEquals('hello', new String(Compression.readLimited(new ByteArrayInputStream(zipped)), 'UTF-8'))
    }

    void testCompressedBodyIsCapped() {
        try {
            Compression.readLimited(new ByteArrayInputStream(('x' * 200).bytes), 50, 1000)
            fail('compressed cap')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.BAD_REQUEST, e.httpStatus)
            assertTrue(e.message.contains('Compressed request exceeds 50'))
        }
    }

    void testDecompressedBodyIsCapped() {
        byte[] zipped = Compression.zip(('x' * 5000).bytes)

        try {
            Compression.readLimited(new ByteArrayInputStream(zipped), 1024 * 1024, 100)
            fail('plain cap')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.BAD_REQUEST, e.httpStatus)
            assertTrue(e.message.contains('Request body exceeds 100'))
        }
    }

    void testBadGzipIsBadRequest() {
        byte[] fake = [0x1f, 0x8b, 0, 0, 0, 0, 0, 0, 0, 0] as byte[]

        try {
            Compression.readLimited(new ByteArrayInputStream(fake), 100, 100)
            fail('gzip')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.BAD_REQUEST, e.httpStatus)
            assertTrue(e.message.contains('not valid gzip'))
        }
    }

}
