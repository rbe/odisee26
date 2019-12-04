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

import org.odisee.api.OdiseeException
import org.odisee.debug.WallTime
import org.odisee.io.Compression
import org.odisee.io.OdiseePath
import org.odisee.xml.XmlHelper
import org.w3c.dom.Element

import java.security.Principal

class DocumentController {

    OdiseeService odiseeService

    public static final Principal principal = new Principal() {
        @Override
        String getName() {
            return "odisee";
        }
    }

    def generate() {
        final WallTime wallTime = new WallTime()
        if (OdiseePath.ODISEE_PROFILE) {
            wallTime.start()
        }
        try {
            final InputStream decompressedInputStream = Compression.decompress(request.inputStream)
            final Element xml = XmlHelper.convertToXmlElement(decompressedInputStream)
            if (null != xml) {
                final Document document = processXmlRequest(/*request.userPrincipal*/ principal, xml)
                if (null == document) {
                    throw new OdiseeException('Cannot send stream, no document')
                } else {
                    DocumentStreamer.stream(response, document)
                }
            } else {
                throw new OdiseeException('Invalid or missing XML request')
            }
        } catch (e) {
            processThrowable(e)
        } finally {
            // Prevent Grails from rendering generate.gsp (it does not exist)
            response.outputStream.close()
            if (OdiseePath.ODISEE_PROFILE) {
                wallTime.stop()
                log.info "Document generation took ${wallTime.diff()} ms (wall clock)"
            }
        }
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
            response.reset()
            response.status = 400
            if (null != msg) {
                response.outputStream << String.format('%s%n', msg)
            }
            response.outputStream.flush()
        } catch (e) {
            log.error 'Could not send error message to client', e
        }
    }

}
