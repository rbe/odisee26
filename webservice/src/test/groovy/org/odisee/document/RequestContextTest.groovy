package org.odisee.document

class RequestContextTest extends GroovyTestCase {

    void testEachCallHasItsOwnDocumentList() {
        Map first = RequestContext.create()
        Map second = RequestContext.create()

        first.document << 'one'
        RequestContext.resetForNextRequest(first)

        assertEquals(['one'], first.document)
        assertFalse(first.document.is(second.document))
        assertTrue(second.document.isEmpty())
        assertNull(first.result)
        assertNull(first.template)
    }

}
