/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 *
 * rbe, 02.02.15, 18:35
 */
package eu.artofcoding.odisee.document

import eu.artofcoding.odisee.OdiseeException
import eu.artofcoding.odisee.OdiseePath
import eu.artofcoding.odisee.server.OdiseeConstant
import groovy.xml.dom.DOMCategory

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class TemplateService {

    void extractTemplateFromRequest(final Map arg) {
        use(DOMCategory) {
            def request = arg.xml.request[arg.activeIndex]
            def template = request.template[0]
            if (!request.'@id') {
                final String dateBasedId = new Date().format('yyyyMMdd-HHmmss_SSSS')
                request.setAttribute(OdiseeConstant.S_ID, dateBasedId)
            }
            arg.id = request.'@id'.toString()
            arg.template = template.'@name'
            arg.revision = template.'@revision' ?: OdiseeConstant.S_LATEST
            if (!arg.revision || arg.revision == OdiseeConstant.S_LATEST) {
                arg.revision = 1
                template.setAttribute(OdiseeConstant.S_REVISION, arg.revision.toString())
            }
        }
    }

    void checkPaths(final Map arg) {
        use(DOMCategory) {
            def request = arg.xml.request[arg.activeIndex]
            def template = request.template[0]
            template.setAttribute('path', arg.templateFile.toAbsolutePath().toString())
            if (!template.'@outputPath') {
                template.setAttribute('outputPath', arg.documentDir.toString())
            }
            if (request.'@name') {
                arg.documentName = request.'@name'
            } else {
                arg.documentName = "${arg.template}_rev${arg.revision}_id${arg.id}"
            }
        }
    }

    void copyTemplateToRequest(Map arg) {
        arg.documentDir = arg.requestDir
        arg.templateDir = Paths.get("${OdiseePath.ODISEE_VAR}", OdiseeConstant.S_TEMPLATE)
        arg.revision = 1
        Path localTemplate = arg.templateDir.resolve("${arg.template}.ott")
        if (!Files.exists(localTemplate)) {
            localTemplate = arg.templateDir.resolve("${arg.template}_rev${arg.revision}.ott")
        }
        final boolean templateExists = Files.exists(localTemplate)
        if (templateExists) {
            arg.templateFile = localTemplate
        } else {
            throw new OdiseeException("Odisee: Template '${arg.template}' does not exist for user '${arg.principal.name}'")
        }
    }

}
