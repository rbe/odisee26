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
import org.odisee.shared.OdiseeConstant
import org.w3c.dom.Element

import java.nio.file.Files
import java.nio.file.Paths
import java.security.Principal

import static org.odisee.io.OdiseePath.ODISEE_USER

class OdiseeService {

    static scope = 'singleton'

    TemplateService templateService

    RequestService requestService

    StorageService storageService

    PostProcessService postProcessService

    private final Map<String, Object> emptyArg = [
            principal      : null,
            xml            : null,
            activeIndex    : -1,
            uniqueRequestId: '',
            requestDir     : null,
            odiseeRequest  : null,
            documentName   : null,
            templateDir    : null,
            documentDir    : null,
            templateFile   : null,
            id             : null,
            template       : null,
            revision       : 1,
            document       : [],
            data           : null,
            bytes          : null,
            filename       : null,
            result         : []
    ]

    private static void resetRequest(final Map arg) {
        [
                OdiseeConstant.S_ID, OdiseeConstant.S_TEMPLATE, OdiseeConstant.S_REVISION,
                'documentName',
                'templateDir', 'templateFile',
                'documentDir', 'document', 'data', 'bytes', 'filename'
        ].each {
            arg.remove(it)
        }
    }

    def deepcopy(orig) {
        bos = new ByteArrayOutputStream()
        oos = new ObjectOutputStream(bos)
        oos.writeObject(orig); oos.flush()
        bin = new ByteArrayInputStream(bos.toByteArray())
        ois = new ObjectInputStream(bin)
        return ois.readObject()
    }

    /**
     * Generate a document using document service and OOo service.
     * @param arg Map: xml: an XML request (see request.xsd in Odisee).
     * @return List with generated OooDocument instance(s).
     */
    List<Document> generateDocument(final Principal principal, final Element xml) {
        Map<String, Object> arg = (Map<String, Object>) emptyArg.clone()
        final GString userDocumentDir = "${ODISEE_USER}/${OdiseeConstant.S_DOCUMENT}"
        arg.uniqueRequestId = UUID.randomUUID()
        arg.requestDir = Paths.get(userDocumentDir, arg.uniqueRequestId.toString())
        Files.createDirectories(arg.requestDir)
        arg.principal = principal
        arg.xml = xml
        requestService.extractRequestAndSaveToDisk(arg, OdiseeConstant.MINUS_ONE)
        use(DOMCategory) {
            arg.xml.'request'.eachWithIndex { request, i ->
                arg.activeIndex = i
                if (i > 0) resetRequest(arg)
                templateService.extractTemplateFromRequest(arg)
                templateService.copyTemplateToRequest(arg)
                templateService.checkPaths(arg)
                requestService.processSingleRequest(arg)
                postProcessService.postProcessRequest(arg)
                arg.result.output.each { file ->
                    if (!arg.document) arg.document = []
                    arg.document << storageService.createDocument(data: file)
                }
            }
        }
        postProcessService.postProcessOdisee(arg)
        log.info "Generated ${arg.document?.size() ?: 0} document(s)"
        arg.document
    }

}
