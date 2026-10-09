/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 */
package org.odisee.document

import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.web.context.request.RequestContextHolder

import java.security.Principal

/**
 * {@code GET /template/{name}} lists fields and revisions for the logged-in user.
 * The caller is the security context, the same source {@code POST /document/generate} uses.
 */
class TemplateController {

    def show() {
        boolean errorResponse = false
        try {
            Principal caller = DocumentController.requireCaller(DocumentController.callerFromContext(request.userPrincipal))
            Map catalog = TemplateCatalog.describe(caller.name, params.name?.toString())
            response.status = 200
            response.contentType = 'application/json'
            response.outputStream << TemplateCatalog.toJson(catalog)
            response.outputStream.flush()
        } catch (Throwable e) {
            errorResponse = true
            log.error e.message, e
            HttpStatuses.apply(response, e)
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

    private static void skipView() {
        def attributes = RequestContextHolder.getRequestAttributes()
        if (attributes instanceof GrailsWebRequest) {
            attributes.renderView = false
        }
    }

}
