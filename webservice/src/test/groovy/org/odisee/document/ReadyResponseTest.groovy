package org.odisee.document

import groovy.test.GroovyTestCase
import org.odisee.ooo.connection.PoolGauges

/**
 * /ready is 200 only when a slot accepts UNO, and the body is the pool gauges.
 */
class ReadyResponseTest extends GroovyTestCase {

    void testReadyIs200WithGauges() {
        PoolGauges gauges = new PoolGauges(2, 1, 80L, 4L, 1L)
        Captured response = apply(true, gauges)
        assertEquals(200, response.status)
        assertEquals('application/json', response.contentType)
        assertEquals(gauges.toJson(true), response.body)
    }

    void testNotReadyIs503() {
        Captured response = apply(false, PoolGauges.EMPTY)
        assertEquals(503, response.status)
        assertEquals('{"ready":false,"poolSize":0,"inUse":0,"generationMillis":0,"instructionFailures":0,"sofficeRestarts":0}', response.body)
    }

    private static Captured apply(boolean ready, PoolGauges gauges) {
        Captured captured = new Captured()
        ReadyResponse.apply(captured, ready, gauges)
        captured
    }

    private static final class Captured {
        int status
        String contentType
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream()

        void setStatus(int value) {
            status = value
        }

        void setContentType(String value) {
            contentType = value
        }

        OutputStream getOutputStream() {
            bytes
        }

        String getBody() {
            bytes.toString('UTF-8')
        }
    }

}
