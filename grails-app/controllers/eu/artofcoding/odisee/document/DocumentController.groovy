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

package eu.artofcoding.odisee.document

import eu.artofcoding.grails.helper.Compression
import eu.artofcoding.grails.helper.WallTime
import eu.artofcoding.grails.helper.XmlHelper
import eu.artofcoding.odisee.OdiseeException
import eu.artofcoding.odisee.OdiseePath
import org.w3c.dom.Element

import java.security.Principal

class DocumentController {

    /**
     * The Odisee service.
     */
    OdiseeService odiseeService

    /**
     * The before-request-interceptor:
     * Don't cache our response.
     */
    def beforeInterceptor = {
        response.setHeader('Cache-Control', 'no-cache,no-store,must-revalidate,max-age=0')
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
                final Principal principal = new Principal() {
                    @Override
                    String getName() {
                        return "odisee";
                    }
                }
                final Document document = processXmlRequest(/*request.userPrincipal*/principal, xml)
                if (null == document) {
                    throw new OdiseeException('Odisee: Cannot send stream, no document')
                } else {
                    DocumentStreamer.stream(response, document)
                }
            } else {
                throw new OdiseeException('Odisee: Invalid or missing XML request')
            }
        } catch (e) {
            processThrowable(e)
        } finally {
            // Prevent Grails from rendering generate.gsp (it does not exist)
            response.outputStream.close()
            if (OdiseePath.ODISEE_PROFILE) {
                wallTime.stop()
                log.info "Odisee: Document processing took ${wallTime.diff()} ms (wall clock)"
            }
        }
    }

    private Document processXmlRequest(final Principal principal, final Element xml) {
        try {
            final List<Document> documents = odiseeService.generateDocument(principal, xml)
            if (null != documents && documents.size() > 0) {
                final Document document = documents.last()
                return document
            } else {
                throw new OdiseeException('Odisee: Document generation failed')
            }
        } catch (e) {
            throw new OdiseeException("Odisee: Document generation failed", e)
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
            log.error 'Odisee: Could not send error message to client', e
        }
    }

}
