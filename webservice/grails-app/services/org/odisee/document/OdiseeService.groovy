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
package org.odisee.document

import groovy.xml.dom.DOMCategory
import org.odisee.api.OdiseeException
import org.odisee.io.TenantPaths
import org.odisee.shared.OdiseeConstant
import org.w3c.dom.Element

import java.nio.file.Files
import java.nio.file.Path
import java.security.Principal

class OdiseeService {

    static scope = 'singleton'

    TemplateService templateService

    RequestService requestService

    StorageService storageService

    PostProcessService postProcessService

    /**
     * Generate a document using document service and OOo service.
     * @param arg Map: xml: an XML request (see request.xsd in Odisee).
     * @return List with generated OooDocument instance(s).
     */
    List<Document> generateDocument(final Principal principal, final Element xml) {
        generateDocument(principal, xml, false)
    }

    /**
     * A dry run resolves instructions and does not leave a file under the user's output directory.
     */
    List<Document> generateDocument(final Principal principal, final Element xml, final boolean dryRun) {
        if (principal == null || principal.name == null || principal.name.trim().isEmpty()) {
            throw new OdiseeException('Authentication required', OdiseeException.UNAUTHORIZED)
        }
        String user = TenantPaths.requireUserName(principal.name)
        Map<String, Object> arg = RequestContext.create()
        arg.uniqueRequestId = UUID.randomUUID()
        arg.dryRun = dryRun
        arg.principal = principal
        arg.xml = xml
        Path scratch = null
        try {
            if (dryRun) {
                scratch = Files.createTempDirectory('odisee-dry-run')
                arg.requestDir = scratch
            } else {
                arg.requestDir = TenantPaths.outputDir(user).resolve(arg.uniqueRequestId.toString())
                Files.createDirectories(arg.requestDir)
            }
            requestService.extractRequestAndSaveToDisk(arg, OdiseeConstant.MINUS_ONE)
            use(DOMCategory) {
                arg.xml.'request'.eachWithIndex { request, i ->
                    arg.activeIndex = i
                    if (i > 0) RequestContext.resetForNextRequest(arg)
                    arg.dryRun = dryRun
                    templateService.extractTemplateFromRequest(arg)
                    templateService.copyTemplateToRequest(arg)
                    templateService.checkPaths(arg)
                    requestService.processSingleRequest(arg)
                    if (!dryRun) {
                        postProcessService.postProcessRequest(arg)
                        arg.result.output.each { file ->
                            if (!arg.document) arg.document = []
                            arg.document << storageService.createDocument(data: file)
                        }
                    }
                }
            }
            if (!dryRun) {
                postProcessService.postProcessOdisee(arg)
            }
            log.info "Generated ${arg.document?.size() ?: 0} document(s)"
            dryRun ? [] : arg.document
        } finally {
            if (scratch != null) {
                scratch.toFile().deleteDir()
            }
        }
    }

}
