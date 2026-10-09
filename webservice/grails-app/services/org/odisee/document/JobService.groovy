/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 */
package org.odisee.document

import groovy.util.logging.Slf4j
import org.odisee.api.OdiseeException
import org.odisee.io.SafePaths
import org.odisee.io.TenantPaths
import org.w3c.dom.Element

import java.nio.file.Files
import java.nio.file.Path
import java.security.Principal
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

/**
 * Accepts a job, runs generation off the request thread, and stores the result
 * in the caller's {@code jobs.json}. The callback is posted only when its host
 * is on the server allow-list.
 */
@Slf4j
class JobService {

    static scope = 'singleton'

    OdiseeService odiseeService

    Executor executor

    Closure poster = { URI uri, String body -> CallbackPoster.post(uri, body) }

    private final ExecutorService pool = Executors.newCachedThreadPool({ Runnable runnable ->
        Thread thread = new Thread(runnable, 'odisee-job')
        thread.daemon = true
        thread
    } as ThreadFactory)

    JobService() {
        executor = pool
    }

    String submit(Principal principal, Element xml, String callback) {
        String user = TenantPaths.requireUserName(principal?.name)
        String id = UUID.randomUUID().toString()
        JobStore.create(user, id, callback)
        Principal caller = principal
        Element body = xml
        executor.execute {
            execute(caller, id, body)
        }
        id
    }

    void execute(Principal principal, String id, Element xml) {
        String user = TenantPaths.requireUserName(principal?.name)
        JobStore.update(user, id, [status: 'running'])
        try {
            List<Document> documents = odiseeService.generateDocument(principal, xml, false, id)
            Document document = documents ? documents.last() : null
            if (document == null || document.bytes == null || document.bytes.length == 0) {
                JobStore.update(user, id, [
                        status           : 'failed',
                        failedInstruction: null,
                        file             : null,
                        filename         : null
                ])
            } else {
                String relative = writeFile(user, id, document)
                JobStore.update(user, id, [
                        status           : 'succeeded',
                        failedInstruction: null,
                        file             : relative,
                        filename         : document.filename
                ])
            }
        } catch (OdiseeException e) {
            JobStore.update(user, id, [
                    status           : 'failed',
                    failedInstruction: JobResponses.failedInstruction(e),
                    file             : null,
                    filename         : null
            ])
        } catch (Throwable e) {
            log.error("Job ${id} failed", e)
            JobStore.update(user, id, [
                    status           : 'failed',
                    failedInstruction: null,
                    file             : null,
                    filename         : null
            ])
        }
        postCallback(user, id)
    }

    private void postCallback(String user, String id) {
        Map job = JobStore.find(user, id)
        String callback = job?.callback?.toString()
        if (!callback) {
            return
        }
        if (!CallbackAllowList.permits(callback)) {
            log.info("Job ${id} callback host is not on the allow-list")
            return
        }
        try {
            poster.call(new URI(callback), JobResponses.callbackBody(job))
        } catch (Exception e) {
            log.error("Job ${id} callback failed", e)
        }
    }

    private static String writeFile(String user, String id, Document document) {
        String filename = SafePaths.requireSimpleName(document.filename ?: 'document.bin', 'document name')
        Path dir = TenantPaths.outputDir(user).resolve(id)
        Files.createDirectories(dir)
        Path dest = dir.resolve(filename)
        Files.write(dest, document.bytes)
        "output/${id}/${filename}"
    }

}
