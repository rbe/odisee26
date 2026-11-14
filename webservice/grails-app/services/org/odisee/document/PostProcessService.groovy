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

import groovy.util.logging.Log
import groovy.xml.dom.DOMCategory
import org.odisee.api.OdiseeException
import org.odisee.io.FileHelper
import org.odisee.io.SafePaths
import org.odisee.io.TenantPaths
import org.w3c.dom.Element

import java.nio.file.Path
import java.nio.file.Paths

@Log
class PostProcessService {

    static scope = 'singleton'

    static transactional = false

    StorageService storageService

    PdfService pdfService

    /**
     * <action type="merge-with">
     */
    void processMergeWith(Map arg, Element actionElement) {
        if (!arg.result?.output) {
            throw new OdiseeException('Cannot merge document, no document was generated', OdiseeException.UNPROCESSABLE)
        }
        String generatedFile = arg.result.output[0]?.toString()
        if (!generatedFile) {
            throw new OdiseeException('Cannot merge document, no document was generated', OdiseeException.UNPROCESSABLE)
        }
        Map decomposedFilename = FileHelper.decomposeFilename(generatedFile)
        Path destPdfFile = Paths.get("${decomposedFilename.name}_merged.pdf")
        List<Path> pdfFiles = []
        use(DOMCategory) {
            actionElement.'*'.each {
                if (it.name() == 'result-placeholder') {
                    pdfFiles << Paths.get(generatedFile)
                } else {
                    String filename = it.'@filename'.toString()
                    pdfFiles << mergeInput(arg, filename)
                }
            }
        }
        Path mergedPdf = pdfService.mergeDocuments(destPdfFile, pdfFiles)
        if (mergedPdf == null) {
            throw new OdiseeException('Cannot merge PDF documents', OdiseeException.UNPROCESSABLE)
        }
        Document mergedDocument = storageService.createDocument(data: mergedPdf)
        if (mergedDocument == null) {
            throw new OdiseeException("Cannot read merged PDF '${mergedPdf.fileName}'", OdiseeException.UNPROCESSABLE)
        }
        if (!arg.document) arg.document = []
        arg.document << mergedDocument
    }

    /**
     * <action type="merge-results">
     */
    void processMergeResults(Map arg, Element actionElement) {
        if (!arg.result?.output) {
            throw new OdiseeException('Cannot merge results, no document was generated', OdiseeException.UNPROCESSABLE)
        }
        String generatedFile = arg.result.output[0]?.toString()
        if (!generatedFile) {
            throw new OdiseeException('Cannot merge results, no document was generated', OdiseeException.UNPROCESSABLE)
        }
        Map decomposedFilename = FileHelper.decomposeFilename(generatedFile)
        Path destPdfFile = Paths.get("${decomposedFilename.name}_merged.pdf")
        List<Path> pdfFiles = []
        use(DOMCategory) {
            arg.document.each { d ->
                pdfFiles << Paths.get(arg.documentDir as String, d.filename as String)
            }
        }
        Path mergedPdf = pdfService.mergeDocuments(destPdfFile, pdfFiles)
        if (mergedPdf == null) {
            throw new OdiseeException('Cannot merge PDF documents', OdiseeException.UNPROCESSABLE)
        }
        Document mergedDocument = storageService.createDocument(data: mergedPdf)
        if (mergedDocument == null) {
            throw new OdiseeException("Cannot read merged PDF '${mergedPdf.fileName}'", OdiseeException.UNPROCESSABLE)
        }
        if (!arg.document) arg.document = []
        arg.document << mergedDocument
    }

    void processActions(Map arg, actions) {
        use(DOMCategory) {
            actions.each { action ->
                dispatchAction(arg, action)
            }
        }
    }

    void postProcessRequest(Map arg) {
        use(DOMCategory) {
            def postProcess = arg.xml.request[arg.activeIndex].'post-process'
            postProcess.'*'.each { action ->
                dispatchAction(arg, action)
            }
        }
    }

    void postProcessOdisee(Map arg) {
        use(DOMCategory) {
            def postProcess = arg.xml.'post-process'
            postProcess.'*'.each { action ->
                dispatchAction(arg, action)
            }
        }
    }

    Path mergeInput(Map arg, String filename) {
        String user = TenantPaths.requireUserName(arg.principal?.name?.toString())
        SafePaths.resolveInside(TenantPaths.workDir(user), filename)
    }

    private void dispatchAction(Map arg, action) {
        String type = action.'@type'?.toString()?.trim()
        if (!type) {
            throw new OdiseeException('Post-process action is missing a type', OdiseeException.BAD_REQUEST)
        }
        switch (type) {
            case 'merge-with':
                processMergeWith(arg, action)
                break
            case 'merge-results':
                processMergeResults(arg, action)
                break
            default:
                throw new OdiseeException("Unsupported post-process action '${type}'", OdiseeException.BAD_REQUEST)
        }
    }

}
