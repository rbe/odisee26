/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 *
 * rbe, 16.03.17, 19:41
 */

package org.odisee.document

import org.grails.web.servlet.mvc.GrailsWebRequest
import org.odisee.api.OdiseeException
import org.odisee.debug.WallTime
import org.odisee.io.Compression
import org.odisee.io.OdiseePath
import org.odisee.json.JsonRequest
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.context.request.RequestContextHolder
import org.w3c.dom.Element

import org.odisee.io.SafePaths

import java.security.Principal

class DocumentController {

    OdiseeService odiseeService

    JobService jobService

    ObjectStorage objectStorage

    /**
     * Grails stores the request before Spring Security wraps it, so
     * {@code request.userPrincipal} stays null after a successful Basic login.
     * The security context is the caller. The request principal is the fallback.
     * No login is HTTP 401. A name that is not a single path segment is HTTP 400.
     * There is no hardcoded user.
     */
    static Principal callerFromContext(Principal requestPrincipal) {
        def authentication = SecurityContextHolder.context?.authentication
        if (authentication != null && authentication.authenticated) {
            String name = authentication.name?.toString()
            if (name != null && !name.trim().isEmpty() && name != 'anonymousUser') {
                return (Principal) authentication
            }
        }
        requestPrincipal
    }

    static Principal requireCaller(Principal caller) {
        if (caller == null || caller.name == null || caller.name.trim().isEmpty() || caller.name == 'anonymousUser') {
            throw new OdiseeException('Authentication required', OdiseeException.UNAUTHORIZED)
        }
        SafePaths.requireSimpleName(caller.name, 'user')
        caller
    }

    def generate() {
        final WallTime wallTime = new WallTime()
        if (OdiseePath.ODISEE_PROFILE) {
            wallTime.start()
        }
        boolean errorResponse = false
        try {
            final Principal caller = requireCaller(callerFromContext(request.userPrincipal))
            Delivery.rejectNamedStorage(request)
            final boolean dryRun = dryRunRequested(request.getParameter('dryRun'))
            final Element xml = readRequestElement()
            if (dryRun) {
                odiseeService.generateDocument(caller, xml, true)
                response.status = 200
                response.outputStream << String.format('Dry run completed.%n')
                response.outputStream.flush()
            } else {
                final Document document = processXmlRequest(caller, xml)
                if (null == document) {
                    throw new OdiseeException('Cannot send stream, no document')
                }
                Delivery.respond(response, caller.name, xml, document, storage())
            }
        } catch (e) {
            errorResponse = true
            processThrowable(e)
        } finally {
            // The error path already wrote the body through HttpStatuses.
            // Closing that stream again drops the message. Close only the
            // success path, which is what stops Grails rendering generate.gsp.
            skipView()
            if (!errorResponse) {
                try {
                    response.outputStream.close()
                } catch (IOException ignored) {
                }
            }
            if (OdiseePath.ODISEE_PROFILE) {
                wallTime.stop()
                log.info "Document generation took ${wallTime.diff()} ms (wall clock)"
            }
        }
    }

    private static void skipView() {
        def attributes = RequestContextHolder.getRequestAttributes()
        if (attributes instanceof GrailsWebRequest) {
            attributes.renderView = false
        }
    }

    /**
     * {@code POST /document/generate?dryRun=true} resolves instructions and does not save.
     */
    static boolean dryRunRequested(String value) {
        value != null && value.equalsIgnoreCase('true')
    }

    /**
     * {@code POST /document/jobs} returns 202 and a job id. The file is not this response.
     * A callback URL on this request is stored on the job. {@code POST /document/generate} does not read one.
     */
    def submitJob() {
        boolean errorResponse = false
        try {
            final Principal caller = requireCaller(callerFromContext(request.userPrincipal))
            Delivery.rejectNamedStorage(request)
            final Element xml = readRequestElement()
            final String callback = callbackForJob(request.getParameter('callback'))
            final String id = jobService.submit(caller, xml, callback)
            response.status = JobResponses.ACCEPTED
            response.contentType = 'application/json; charset=UTF-8'
            response.outputStream << JobResponses.accepted(id)
            response.outputStream.flush()
        } catch (e) {
            errorResponse = true
            processThrowable(e)
        } finally {
            skipView()
            if (!errorResponse) {
                try {
                    response.outputStream.close()
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * {@code GET /document/jobs/{id}} returns status, the failed instruction, and the file.
     * The job is read from the caller's own list.
     */
    def showJob() {
        boolean errorResponse = false
        try {
            final Principal caller = requireCaller(callerFromContext(request.userPrincipal))
            final String id = SafePaths.requireSimpleName(params.id?.toString(), 'job')
            final String user = org.odisee.io.TenantPaths.requireUserName(caller.name)
            final Map job = JobStore.require(user, id)
            final byte[] file = JobStore.fileBytes(user, job)
            response.status = 200
            response.contentType = 'application/json; charset=UTF-8'
            response.outputStream << JobResponses.statusBody(job, file)
            response.outputStream.flush()
        } catch (e) {
            errorResponse = true
            processThrowable(e)
        } finally {
            skipView()
            if (!errorResponse) {
                try {
                    response.outputStream.close()
                } catch (IOException ignored) {
                }
            }
        }
    }

    private ObjectStorage storage() {
        objectStorage ?: new S3ObjectStorage()
    }

    /**
     * The synchronous generate route does not keep a callback.
     * The job route does.
     */
    static String callbackForGenerate(String callback) {
        null
    }

    static String callbackForJob(String callback) {
        String value = callback?.trim()
        value ? value : null
    }

    private Element readRequestElement() {
        final byte[] body = Compression.readLimited(request.inputStream)
        final boolean jsonRequest = isJsonRequest(request.getContentType(), body)
        final Element xml = jsonRequest
                ? JsonRequest.toElement(new String(body, 'UTF-8'))
                : RequestSchema.parse(body)
        if (jsonRequest) {
            RequestSchema.validate(xml)
        }
        if (null == xml) {
            throw new OdiseeException(jsonRequest
                    ? 'Invalid or missing JSON request'
                    : 'Invalid or missing XML request')
        }
        xml
    }

    /**
     * JSON when Content-Type says so. XML content types stay XML.
     * A missing content type is JSON only when the body starts with '{'.
     */
    private static boolean isJsonRequest(final String contentType, final byte[] body) {
        final String ct = contentType?.toLowerCase() ?: ''
        if (ct.contains('json')) {
            return true
        }
        if (ct.contains('xml')) {
            return false
        }
        JsonRequest.looksLikeJson(body)
    }

    private Document processXmlRequest(final Principal principal, final Element xml) throws OdiseeException {
        final List<Document> documents = odiseeService.generateDocument(principal, xml)
        if (null != documents && documents.size() > 0) {
            return documents.last()
        } else {
            // TODO Return empty document
            throw new OdiseeException('Document generation failed')
        }
    }

    /**
     * Handle an exception: extract message and write response to client.
     * @param throwable The exception to handle.
     */
    private void processThrowable(final Throwable throwable) {
        try {
            String msg
            if (null != throwable) {
                msg = throwable.message
                log.error msg, throwable
            }
            HttpStatuses.apply(response, throwable)
        } catch (e) {
            log.error 'Could not send error message to client', e
        }
    }

}
