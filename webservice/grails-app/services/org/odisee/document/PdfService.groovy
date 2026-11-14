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

import org.apache.pdfbox.io.IOUtils
import org.apache.pdfbox.multipdf.PDFMergerUtility
import org.odisee.api.OdiseeException

import java.nio.file.Files
import java.nio.file.Path

/**
 * Provide services for dealing with PDF files.
 */
class PdfService {

    static scope = 'singleton'

    PdfService() {
    }

    /**
     * Merge multiple PDF files into a single one.
     * @param target
     * @param pdfFiles
     * @return
     */
    Path mergeDocuments(Path target, List<Path> pdfFiles) {
        if (target == null) {
            throw new OdiseeException('Cannot merge PDF documents: target path is missing', OdiseeException.UNPROCESSABLE)
        }
        if (!pdfFiles) {
            throw new OdiseeException('Cannot merge PDF documents: no input files specified', OdiseeException.UNPROCESSABLE)
        }
        PDFMergerUtility merger = new PDFMergerUtility()
        try {
            pdfFiles.each { Path p ->
                if (p == null || !Files.isRegularFile(p)) {
                    throw new OdiseeException("Merge input '${p}' does not exist", OdiseeException.UNPROCESSABLE)
                }
                merger.addSource(p.toFile())
            }
            if (target.parent != null) {
                Files.createDirectories(target.parent)
            }
            merger.destinationFileName = target.toAbsolutePath().toString()
            merger.mergeDocuments(IOUtils.createMemoryOnlyStreamCache())
            target
        } catch (OdiseeException e) {
            throw e
        } catch (e) {
            log.error "Could not merge ${pdfFiles.join(', ')} into ${target}", e
            OdiseeException ex = new OdiseeException("Could not merge PDF documents into '${target.fileName}': ${e.message ?: e.class.simpleName}", e)
            ex.httpStatus = OdiseeException.UNPROCESSABLE
            throw ex
        }
    }

}
