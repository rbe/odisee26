package org.odisee.document

import groovy.json.JsonSlurper
import groovy.test.GroovyTestCase
import org.odisee.api.OdiseeException
import org.odisee.io.TenantPaths
import org.odisee.json.JsonRequest
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.w3c.dom.Element

import java.lang.reflect.Field
import java.nio.file.Files
import java.security.Principal
import java.util.concurrent.Executor

/**
 * F7. stream is the default. store and both use the caller's configured bucket.
 * The request cannot name another one. No MinIO.
 */
class DeliveryTest extends GroovyTestCase {

    private final List<String> users = []

    @Override
    protected void setUp() {
        Files.deleteIfExists(BucketFile.location())
    }

    @Override
    protected void tearDown() {
        users.each { String name ->
            TenantPaths.userRoot(name).toFile()?.deleteDir()
        }
        Files.deleteIfExists(BucketFile.location())
    }

    void testDefaultStreamIsUnchanged() {
        Document document = document()
        RecordingStorage storage = new RecordingStorage()
        MockHttpServletResponse response = new MockHttpServletResponse()

        Delivery.respond(response, 'ada', request(null), document, storage)

        assertBytes(document.bytes, response.contentAsByteArray)
        assertEquals('application/pdf', response.contentType)
        assertNull(response.getHeader(Delivery.OBJECT_HEADER))
        assertEquals(0, storage.puts.size())
        assertEquals(Delivery.STREAM, Delivery.mode(request(null)))
    }

    void testStoreRejectsAUserWithNoBucket() {
        RecordingStorage storage = new RecordingStorage()
        try {
            Delivery.respond(new MockHttpServletResponse(), 'nobucket', request('store'), document(), storage)
            fail('store without a bucket')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.UNPROCESSABLE, e.httpStatus)
            assertTrue(e.message.contains('nobucket'))
        }
        assertEquals(0, storage.puts.size())
    }

    void testStoreWritesAndTheBodyIsNotTheFile() {
        configure('ada', 'ada-docs')
        Document document = document()
        RecordingStorage storage = new RecordingStorage()
        MockHttpServletResponse response = new MockHttpServletResponse()

        Delivery.respond(response, 'ada', request('store'), document, storage)

        assertEquals(1, storage.puts.size())
        Map put = storage.puts[0]
        assertEquals('ada-docs', put.bucket)
        assertEquals('application/pdf', put.contentType)
        assertEquals(document.bytes.length, put.length)
        assertBytes(document.bytes, (byte[]) put.read)
        assertTrue(((byte[]) put.shared).is(document.bytes))
        assertTrue(put.key ==~ /[0-9a-f-]{36}\/Letter\.pdf/)
        assertFalse(put.key.toString().contains('other-bucket'))
        assertEquals('application/json; charset=UTF-8', response.contentType)
        assertEquals(200, response.status)
        assertFalse(Arrays.equals(document.bytes, response.contentAsByteArray))
        assertFalse(response.contentAsString.contains('%PDF'))
        Map body = new JsonSlurper().parseText(response.contentAsString)
        assertEquals('ada-docs', body.bucket)
        assertEquals(put.key, body.key)
        assertNull(response.getHeader(Delivery.OBJECT_HEADER))
    }

    void testBothReturnsTheFileAndStores() {
        configure('ada', 'ada-docs')
        Document document = document()
        RecordingStorage storage = new RecordingStorage()
        MockHttpServletResponse response = new MockHttpServletResponse()

        Delivery.respond(response, 'ada', request('both'), document, storage)

        assertBytes(document.bytes, response.contentAsByteArray)
        assertEquals('application/pdf', response.contentType)
        assertEquals(1, storage.puts.size())
        assertEquals('ada-docs', storage.puts[0].bucket)
        assertTrue(((byte[]) storage.puts[0].shared).is(document.bytes))
        assertEquals('ada-docs/' + storage.puts[0].key, response.getHeader(Delivery.OBJECT_HEADER))
    }

    void testRequestCannotOverrideTheBucket() {
        configure('ada', 'ada-docs')
        RecordingStorage storage = new RecordingStorage()
        Document document = document()
        Delivery.respond(new MockHttpServletResponse(), 'ada', request('store'), document, storage)
        assertEquals('ada-docs', storage.puts[0].bucket)
        assertEquals(1, storage.puts.size())

        OdiseeException xml = rejectXml('''<odisee delivery="store">
  <request name="One">
    <template name="Letter" outputFormat="pdf"/>
    <instructions><userfield name="Hallo">x</userfield></instructions>
    <bucket>other-bucket</bucket>
  </request>
</odisee>''')
        assertEquals('The request cannot name a bucket', xml.message)

        try {
            JsonRequest.toElement('''
            {"delivery":"store","bucket":"other-bucket","endpoint":"http://evil","key":"client-key",
             "request":{"template":{"name":"Letter","outputFormat":"pdf"},"instructions":[]}}
            ''')
            fail('json bucket')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.BAD_REQUEST, e.httpStatus)
            assertEquals('The request cannot name a bucket', e.message)
        }

        MockHttpServletRequest http = new MockHttpServletRequest()
        http.setParameter('bucket', 'other-bucket')
        try {
            Delivery.rejectNamedStorage(http)
            fail('query bucket')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.BAD_REQUEST, e.httpStatus)
        }
        assertEquals(1, storage.puts.size())
        assertEquals('ada-docs', storage.puts[0].bucket)
    }

    void testBucketFileIsReadOnEachStore() {
        assertTrue(BucketFile.save('ada', 'http://127.0.0.1:9000', 'us-east-1', 'one', 'minio', 'minio-secret'))
        assertEquals('one', BucketFile.require('ada').bucket)
        assertFalse(BucketFile.save('ada', 'http://127.0.0.1:9000', 'us-east-1', 'two', 'minio', 'minio secret'))
        assertEquals('two', BucketFile.require('ada').bucket)
        assertEquals('minio secret', BucketFile.require('ada').secret)
        Files.writeString(BucketFile.location(), 'ada http://10.1.2.3:9000 us-east-1 three minio minio-secret\n')
        BucketFile.Record reread = BucketFile.require('ada')
        assertEquals('three', reread.bucket)
        assertEquals('http://10.1.2.3:9000', reread.endpoint)
    }

    void testUploadStreamsTheExistingBytes() {
        byte[] payload = '%PDF-1.4'.getBytes('UTF-8')
        ByteArrayInputStream input = new ByteArrayInputStream(payload)
        def body = S3ObjectStorage.streamBody(input, payload.length, 'application/pdf')
        InputStream stream = body.contentStreamProvider().newStream()
        assertTrue(stream instanceof ByteArrayInputStream)
        assertTrue(buffer(stream).is(payload))
    }

    void testJobRecordStoresTheObjectLocation() {
        String ada = user('jobstore')
        configure(ada, 'ada-docs')
        RecordingStorage storage = new RecordingStorage()
        boolean posted = false
        JobService jobs = service(ada) { Document document ->
            document.filename = 'Letter.pdf'
            document.bytes = '%PDF-job'.getBytes('UTF-8')
            [document]
        }
        jobs.executor = inline()
        jobs.objectStorage = storage
        jobs.poster = { URI uri, String body -> posted = true }
        String id = jobs.submit(principal(ada), request('store'), 'http://203.0.113.9/done')

        Map job = JobStore.require(ada, id)
        assertEquals('succeeded', job.status)
        assertEquals('ada-docs', job.bucket)
        assertEquals(storage.puts[0].key, job.key)
        Map onDisk = new JsonSlurper().parse(JobStore.file(ada).toFile()).jobs[0]
        assertEquals('ada-docs', onDisk.bucket)
        assertEquals(job.key, onDisk.key)
        assertTrue(JobStore.file(ada).toString().contains("${File.separator}${ada}${File.separator}"))
        assertFalse(onDisk.file.toString().contains('..'))
        assertFalse(posted)
        Map body = new JsonSlurper().parseText(JobResponses.statusBody(job, '%PDF-job'.getBytes('UTF-8')))
        assertEquals('ada-docs', body.bucket)
        assertEquals(job.key, body.key)
    }

    void testJobStoreRejectsAUserWithNoBucket() {
        String ada = user('jobnone')
        RecordingStorage storage = new RecordingStorage()
        JobService jobs = service(ada) { Document document ->
            document.filename = 'Letter.pdf'
            document.bytes = 'pdf'.getBytes('UTF-8')
            [document]
        }
        jobs.executor = inline()
        jobs.objectStorage = storage
        String id = jobs.submit(principal(ada), request('store'), null)

        Map job = JobStore.require(ada, id)
        assertEquals('failed', job.status)
        assertNull(job.bucket)
        assertEquals(0, storage.puts.size())
    }

    private static void assertBytes(byte[] expected, byte[] actual) {
        assertTrue(Arrays.equals(expected, actual))
    }

    private void configure(String user, String bucket) {
        BucketFile.save(user, 'http://127.0.0.1:9000', 'us-east-1', bucket, 'minio', 'minio-secret')
    }

    private static Document document() {
        Document document = new Document()
        document.filename = 'Letter.pdf'
        document.bytes = '%PDF-1.4 fake'.getBytes('UTF-8')
        document
    }

    private static Element request(String delivery) {
        String attr = delivery ? " delivery=\"${delivery}\"" : ''
        RequestSchema.parse("""<odisee${attr}>
  <request name="other-bucket">
    <template name="Letter" outputFormat="pdf"/>
    <instructions><userfield name="Hallo">other-bucket</userfield></instructions>
  </request>
</odisee>""".getBytes('UTF-8'))
    }

    private static OdiseeException rejectXml(String xml) {
        try {
            RequestSchema.parse(xml.getBytes('UTF-8'))
            fail('expected a 400')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.BAD_REQUEST, e.httpStatus)
            return e
        }
        null
    }

    private static byte[] buffer(InputStream stream) {
        Field field = ByteArrayInputStream.getDeclaredField('buf')
        field.accessible = true
        (byte[]) field.get(stream)
    }

    private String user(String name) {
        users << name
        name
    }

    private static Principal principal(String name) {
        [getName: { name }] as Principal
    }

    private static Executor inline() {
        new Executor() {
            @Override
            void execute(Runnable command) {
                command.run()
            }
        }
    }

    private static JobService service(String name, Closure generator) {
        JobService jobs = new JobService()
        jobs.odiseeService = new OdiseeService() {
            @Override
            List<Document> generateDocument(Principal principal, Element xml, boolean dryRun, String requestId) {
                assertEquals(name, principal.name)
                assertFalse(dryRun)
                assertNotNull(requestId)
                generator.call(new Document())
            }
        }
        jobs
    }

    static class RecordingStorage implements ObjectStorage {
        final List<Map> puts = []

        @Override
        void put(BucketFile.Record bucket, String key, String contentType, InputStream body, long length) {
            byte[] shared = body instanceof ByteArrayInputStream ? buffer(body) : null
            byte[] read = body.readAllBytes()
            puts << [bucket: bucket.bucket, key: key, contentType: contentType, length: length, shared: shared, read: read]
        }

        private static byte[] buffer(InputStream stream) {
            Field field = ByteArrayInputStream.getDeclaredField('buf')
            field.accessible = true
            (byte[]) field.get(stream)
        }
    }

}
