/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 *
 * rbe, 16.03.17, 19:52
 */
package org.odisee.document

import com.sun.org.apache.xerces.internal.dom.DeferredNode
import org.odisee.io.FileHelper
import org.odisee.xml.XmlHelper
import org.odisee.shared.OdiseeConstant
import org.odisee.ooo.connection.OfficeConnectionFactory
import groovy.xml.XmlUtil
import org.springframework.beans.factory.InitializingBean

import java.nio.file.Files
import java.nio.file.Path

class RequestService implements InitializingBean {

    private OfficeConnectionFactory officeConnectionFactory

    @Override
    void afterPropertiesSet() {
        final List<String> odiinst = OdiseeInstance.instance.readOdiinst()
        try {
            final String localhost = odiinst[0][1]
            final int portbase = 2001
            officeConnectionFactory = OfficeConnectionFactory.getInstance(OdiseeConstant.S_GROUP0, localhost, portbase, odiinst.size())
        } catch (e) {
            throw new IllegalStateException('Cannot setup Office connection factory, please check instance configuration', e)
        }
    }

    /**
     * Extract a single XML request and save it to file (a Odisee XML request can contain more than one request).
     * @param requestNumber Request# to work with, -1 is the whole file.
     * @return File Reference to generated XML file.
     */
    Path extractRequestAndSaveToDisk(final Map arg, final int requestNumber) {
        final Path requestXMLFile
        final String xmlString
        // Make XML string
        if (requestNumber == OdiseeConstant.MINUS_ONE) {
            final Path requestDir = (Path) arg.requestDir
            requestXMLFile = requestDir.resolve("${arg.uniqueRequestId}.xml" as String)
            xmlString = XmlUtil.serialize(arg.xml)
        } else {
            // Just save active request including <odisee> element
            final String filename = String.format('%s_%d.xml', arg.uniqueRequestId, requestNumber)
            final Path documentDir = (Path) arg.documentDir
            requestXMLFile = documentDir.resolve(filename)
            final DeferredNode deferredNode = (DeferredNode) arg.xml.request[requestNumber]
            xmlString = XmlHelper.asString(deferredNode)
        }
        Files.createDirectories(requestXMLFile.parent)
        FileHelper.writeUTF8(requestXMLFile, xmlString)
        requestXMLFile
    }

    /**
     * Save XML request to disk, process request and set arg.result.
     */
    void processSingleRequest(final Map arg) {
        final Path requestXMLFile = extractRequestAndSaveToDisk(arg, arg.activeIndex)
        use(OdiseeXmlCategory) {
            // requestNumber = 0 as file contains only one request
            arg.result = requestXMLFile.toDocument(officeConnectionFactory, 0)
            if (!arg.result) {
                final String group = 'group0'
                def filename = requestXMLFile.fileName.toString()
                log.error "Odisee: ${ filename}/${arg.activeIndex}: Got no result, maybe all instances in group '${group}' are unwilling to perform?"
            }
        }
    }

}
