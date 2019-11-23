/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 *
 * rbe, 16.03.17, 19:31
 */

package eu.artofcoding.grails.helper

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

final class HttpStream {

    private HttpStream() {
        throw new AssertionError();
    }

    /**
     * Stream content (using gzip compression).
     * @param request
     * @param response
     * @param content
     */
    static void stream(final HttpServletRequest request, final HttpServletResponse response, final byte[] content) {
        // Does user's browser accept gzip encoding?
        boolean gzipCompressionAccepted = request.getHeader('Accept-Encoding').contains('gzip')
        if (content) {
            if (gzipCompressionAccepted) {
                final byte[] z = Compression.zip(content)
                if (z) {
                    response.setHeader('Content-Encoding', 'gzip')
                    response.contentLength = z.length
                    response.outputStream << z
                }
            } else {
                response.contentLength = content.length
                response.outputStream << content
            }
        }
    }

    /**
     * Stream an image, set headers accordingly.
     * @param response
     * @param image
     */
    static void streamImage(final HttpServletResponse response, final File file) {
        try {
            if (file.exists() && file.canRead()) {
                final Map decomposedFilename = FileHelper.decomposeFilename(file)
                String contentType
                switch (decomposedFilename.ext) {
                    case 'tif':
                        contentType = "tiff"
                        break
                    case 'jpg':
                        contentType = "jpeg"
                        break
                    default:
                        contentType = decomposedFilename.ext.toLowerCase()
                }
                response.contentType = "image/${contentType}"
                // BUG Chrome 16.0.912.75: No comma in filename
                final String fname = decomposedFilename.name.replaceAll(',', '')
                response.setHeader("Content-Disposition", "inline; filename=${fname}.${contentType}")
                response.contentLength = file.size()
                response.outputStream << file.bytes
            } else {
                // TODO Stream error image
                //response.outputStream << Files.readAllBytes()
            }
        } catch (Exception e) {
            e.printStackTrace()
            // TODO Stream error image
            //response.outputStream << Files.readAllBytes()
        } finally {
            response.outputStream.flush()
        }
    }

}
