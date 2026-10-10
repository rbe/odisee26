package org.odisee.document

import com.sun.net.httpserver.HttpServer
import groovy.json.JsonSlurper
import groovy.test.GroovyTestCase
import groovy.xml.DOMBuilder
import org.odisee.api.OdiseeException
import org.odisee.io.TenantPaths
import org.w3c.dom.Element

import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.security.Principal
import java.util.concurrent.Executor

class JobApiTest extends GroovyTestCase {

    private List<String> users = []

    @Override
    protected void tearDown() {
        users.each { String name ->
            TenantPaths.userRoot(name).toFile().deleteDir()
        }
        Files.deleteIfExists(CallbackAllowList.location())
    }

    void testCallbackBelongsOnTheJobNotTheSynchronousPost() {
        assertNull(DocumentController.callbackForGenerate('https://hooks.example/done'))
        assertEquals('https://hooks.example/done', DocumentController.callbackForJob('https://hooks.example/done'))
        assertNull(DocumentController.callbackForJob('  '))
    }

    void testJobListIsAPrivateJsonFile() {
        String ada = user('jobada')
        String bee = user('jobbee')
        Map created = JobStore.create(ada, 'job-1', 'https://hooks.example/done')
        assertEquals('queued', created.status)
        assertEquals('https://hooks.example/done', created.callback)

        Path file = JobStore.file(ada)
        assertEquals('jobs.json', file.fileName.toString())
        assertTrue(file.toString().contains("${File.separator}user${File.separator}${ada}${File.separator}"))
        assertFalse(Files.exists(TenantPaths.userRoot(ada).parent.parent.resolve('jobs.json')))
        assertEquals('job-1', new JsonSlurper().parse(file.toFile()).jobs[0].id)

        Map reread = JobStore.find(ada, 'job-1')
        assertEquals('queued', reread.status)
        assertNull(JobStore.find(bee, 'job-1'))
        try {
            JobStore.require(bee, 'job-1')
            fail('bee can see ada')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.NOT_FOUND, e.httpStatus)
        }
    }

    void testSubmitReturnsAnIdAndGetReturnsStatusInstructionAndFile() {
        String ada = user('jobrun')
        JobService jobs = service(ada) { Document document ->
            document.filename = 'Letter.pdf'
            document.bytes = 'pdf-bytes'.getBytes('UTF-8')
            [document]
        }
        jobs.executor = inline()
        String id = jobs.submit(principal(ada), request(), null)

        Map job = JobStore.require(ada, id)
        assertEquals('succeeded', job.status)
        assertNull(job.failedInstruction)
        byte[] file = JobStore.fileBytes(ada, job)
        assertEquals('pdf-bytes', new String(file, 'UTF-8'))
        Map body = new JsonSlurper().parseText(JobResponses.statusBody(job, file))
        assertEquals('succeeded', body.status)
        assertNull(body.failedInstruction)
        assertEquals('pdf-bytes', new String(Base64.decoder.decode(body.file.toString()), 'UTF-8'))
        assertEquals(JobResponses.ACCEPTED, 202)
        assertTrue(JobResponses.accepted(id).contains(id))
    }

    void testFailedInstructionIsStoredOnTheJob() {
        String ada = user('jobfail')
        JobService jobs = service(ada) {
            throw new OdiseeException('Document instructions failed: cell Sheet1 B2: missing', OdiseeException.UNPROCESSABLE)
        }
        jobs.executor = inline()
        String id = jobs.submit(principal(ada), request(), null)

        Map job = JobStore.require(ada, id)
        assertEquals('failed', job.status)
        assertEquals('cell Sheet1 B2: missing', job.failedInstruction)
        assertNull(job.file)
        Map body = new JsonSlurper().parseText(JobResponses.statusBody(job, null))
        assertEquals('cell Sheet1 B2: missing', body.failedInstruction)
        assertNull(body.file)
    }

    void testQueuedJobIsOnDiskBeforeItRuns() {
        String ada = user('jobqueue')
        JobService jobs = service(ada) { Document document ->
            document.filename = 'Letter.pdf'
            document.bytes = 'later'.getBytes('UTF-8')
            [document]
        }
        jobs.executor = new Executor() {
            @Override
            void execute(Runnable command) {
            }
        }
        String id = jobs.submit(principal(ada), request(), 'https://hooks.example/done')

        Map job = JobStore.find(ada, id)
        assertEquals('queued', job.status)
        assertEquals('https://hooks.example/done', job.callback)
        assertTrue(Files.isRegularFile(JobStore.file(ada)))
    }

    void testOffListCallbackIsNotPosted() {
        String ada = user('joboff')
        boolean posted = false
        JobService jobs = service(ada) { Document document ->
            document.filename = 'Letter.pdf'
            document.bytes = 'pdf'.getBytes('UTF-8')
            [document]
        }
        jobs.executor = inline()
        jobs.poster = { URI uri, String body -> posted = true }
        CallbackAllowList.add('bee', '203.0.113.5')
        CallbackAllowList.add(ada, 'hooks.example')
        jobs.submit(principal(ada), request(), 'http://203.0.113.5/done')

        assertFalse(posted)
        assertFalse(CallbackAllowList.permits(ada, 'http://203.0.113.5/done'))
        assertTrue(CallbackAllowList.permits('bee', 'http://203.0.113.5/done'))
        assertTrue(CallbackAllowList.permits(ada, 'https://hooks.example/done'))
    }

    void testAllowListedCallbackIsPosted() {
        String ada = user('jobhook')
        HttpServer server = HttpServer.create(new InetSocketAddress('127.0.0.1', 0), 0)
        List<String> methods = []
        List<String> bodies = []
        server.createContext('/done') { exchange ->
            methods << exchange.requestMethod
            bodies << new String(exchange.requestBody.bytes, 'UTF-8')
            exchange.sendResponseHeaders(204, -1)
            exchange.close()
        }
        server.start()
        try {
            CallbackAllowList.add(ada, '127.0.0.1')
            JobService jobs = service(ada) { Document document ->
                document.filename = 'Letter.pdf'
                document.bytes = 'pdf'.getBytes('UTF-8')
                [document]
            }
            jobs.executor = inline()
            String id = jobs.submit(principal(ada), request(), "http://127.0.0.1:${server.address.port}/done")

            assertEquals(['POST'], methods)
            assertTrue(bodies[0].contains('"status":"succeeded"'))
            assertTrue(bodies[0].contains("/odisee/document/jobs/${id}"))
        } finally {
            server.stop(0)
        }
    }

    void testCallbackHostIsPerUser() {
        assertTrue(CallbackAllowList.add('ada', 'Hooks.Example'))
        assertFalse(CallbackAllowList.add('ada', 'hooks.example'))
        assertTrue(CallbackAllowList.add('bee', 'hooks.example'))
        assertTrue(CallbackAllowList.hosts(CallbackAllowList.location(), 'ada').contains('hooks.example'))
        assertTrue(CallbackAllowList.permits('ada', 'https://HOOKS.example/done'))
        assertTrue(CallbackAllowList.permits('bee', 'https://hooks.example/done'))
        assertFalse(CallbackAllowList.permits('cara', 'https://hooks.example/done'))
        try {
            CallbackAllowList.add('ada', 'https://hooks.example/done')
            fail('url is not a host')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.BAD_REQUEST, e.httpStatus)
        }
        assertFalse(CallbackAllowList.permits('ada', 'file:///tmp/x'))
        assertFalse(CallbackAllowList.permits('ada', 'not a url'))
    }

    void testSynchronousGenerateDoesNotWriteAJobList() {
        String name = user('jobgen')
        OdiseeService service = new OdiseeService()
        service.templateService = new TemplateService()
        service.requestService = new RequestService()
        try {
            service.generateDocument(principal(name), request())
            fail('missing template')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.NOT_FOUND, e.httpStatus)
        }
        assertFalse(Files.exists(JobStore.file(name)))
    }

    private String user(String name) {
        users << name
        name
    }

    private static Principal principal(String name) {
        [getName: { name }] as Principal
    }

    private static Element request() {
        DOMBuilder.parse(new StringReader('''<odisee>
  <request name="One">
    <template name="Letter" outputFormat="pdf"/>
    <instructions><userfield name="Hallo">x</userfield></instructions>
  </request>
</odisee>''')).documentElement
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

}
