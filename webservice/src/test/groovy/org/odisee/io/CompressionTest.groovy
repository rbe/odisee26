package org.odisee.io

import groovy.test.GroovyTestCase

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

}
