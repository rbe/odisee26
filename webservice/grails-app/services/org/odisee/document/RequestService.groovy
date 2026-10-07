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

import groovy.xml.slurpersupport.GPathResult
import org.odisee.io.FileHelper
import org.odisee.xml.XmlHelper
import org.odisee.shared.OdiseeConstant
import org.odisee.ooo.connection.OfficeConnectionFactory
import groovy.xml.XmlUtil
import org.springframework.beans.factory.InitializingBean

import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path

class RequestService implements InitializingBean {

    static scope = 'singleton'

    private OfficeConnectionFactory officeConnectionFactory

    OfficeConnectionFactory getOfficeConnectionFactory() {
        officeConnectionFactory
    }

    @Override
    void afterPropertiesSet() {
        final List odiinst = OdiseeInstance.instance.readOdiinst()
        try {
            final List<InetSocketAddress> addresses = OdiinstParser.addresses(odiinst)
            officeConnectionFactory = OfficeConnectionFactory.getInstance(OdiseeConstant.S_GROUP0, addresses)
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
            xmlString = XmlHelper.asString((GPathResult) arg.xml.request[requestNumber])
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
                log.error "${requestXMLFile.fileName.toString()}/${arg.activeIndex}:" +
                        " Got no result, maybe all instances in group '${group}' are unwilling to perform?"
            }
        }
    }

}
