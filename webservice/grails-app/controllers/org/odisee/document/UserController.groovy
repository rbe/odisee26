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
import org.odisee.io.Compression
import org.odisee.security.UserCredentials
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.context.request.RequestContextHolder

/**
 * {@code POST /user} creates a password-file entry and the user's directories.
 * Only role {@code admin} may call it.
 */
class UserController {

    UserAccountService userAccountService

    def create() {
        boolean errorResponse = false
        try {
            boolean admin = SecurityContextHolder.context?.authentication?.authorities?.any { it.authority == 'ROLE_ADMIN' } as boolean
            DocumentController.requireAdmin(request.userPrincipal, admin)
            UserCredentials credentials = readCredentials()
            String name = userAccountService.create(credentials.name, credentials.password)
            response.status = 201
            response.outputStream << String.format("User '%s' created.%n", name)
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

    private UserCredentials readCredentials() {
        String contentType = request.contentType?.toLowerCase() ?: ''
        if (contentType.contains('form')) {
            return UserCredentials.fromForm(request.getParameter('name'), request.getParameter('password'))
        }
        byte[] body = Compression.readLimited(request.inputStream)
        UserCredentials.fromBody(request.contentType, body)
    }

    private static void skipView() {
        def attributes = RequestContextHolder.getRequestAttributes()
        if (attributes instanceof GrailsWebRequest) {
            attributes.renderView = false
        }
    }

}
