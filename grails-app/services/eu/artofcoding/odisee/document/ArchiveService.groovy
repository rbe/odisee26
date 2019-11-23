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
import eu.artofcoding.odisee.server.OdiseeConstant
import groovy.xml.dom.DOMCategory

class ArchiveService {

    StorageService storageService

    PostProcessService postProcessService

    void database(Map arg) {
        use(DOMCategory) {
            def request = arg.xml.request[arg.activeIndex]
            def archive = request.archive[0]
            if (log.debugEnabled && archive) {
                log.debug "Odisee: archive: files=${archive.'@files'}, database=${archive.'@database'}"
            } else {
                log.debug 'Odisee: No archive tag found'
            }
            Document document = null
            arg.result.output.each { k, v ->
                try {
                    document = storageService.addDocument(
                            instanceOf: [name: arg.template, revision: arg.revision],
                            filename: k.name,
                            odiseeXmlRequest: arg.xmlString,
                            data: v
                    )
                    if (document) {
                        arg.document << document
                        if (log.debugEnabled) {
                            log.debug "Odisee: Document archived: ${document}"
                        }
                    } else {
                        log.error "Odisee: Couldn't archive document from request ${request.'@name'}"
                    }
                } catch (e) {
                    log.error "Odisee: Couldn't archive document ${document}", e
                }
            }
        }
    }

    void processSingleResult(Map arg) {
        if (arg.result) {
            // Archive generated document(s)?
            use(DOMCategory) {
                def request = arg.xml.request[arg.activeIndex]
                def archive = request.archive[0]
                // Archive in database: request XML as string, generated document as BLOB?
                if (archive?.'@database' == OdiseeConstant.S_TRUE) {
                    database(arg)
                } else {
                    // Just create generated document as OooDocument bean.
                    arg.result.output.each { file ->
                        arg.document << storageService.createDocument(data: file)
                    }
                }
            }
        } else {
            throw new OdiseeException('Odisee: No result!')
        }
    }

}
