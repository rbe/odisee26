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
import org.odisee.api.OdiseeException
import org.odisee.io.FileHelper
import org.odisee.io.OdiseePath
import org.odisee.xml.XmlHelper
import org.odisee.shared.OdiseeConstant
import org.odisee.ooo.connection.OfficeConnectionFactory
import groovy.xml.XmlUtil
import org.springframework.beans.factory.InitializingBean

import java.nio.file.Files
import java.nio.file.Path

class RequestService implements InitializingBean {

    static scope = 'singleton'

    private OfficeConnectionFactory officeConnectionFactory

    private final OdiinstReloader odiinstReloader = new OdiinstReloader()

    OfficeConnectionFactory getOfficeConnectionFactory() {
        refreshPool()
        officeConnectionFactory
    }

    /**
     * Read {@code etc/odiinst} again. A changed file replaces the pool in this JVM.
     */
    @Override
    void afterPropertiesSet() {
        refreshPool()
    }

    synchronized void refreshPool() {
        final Path odiinstPath = OdiseePath.ODISEE_HOME.resolve(OdiseePath.S_ETC_ODIINST)
        if (!Files.exists(odiinstPath)) {
            throw new IllegalStateException('Cannot setup Office connection factory, please check instance configuration',
                    new OdiseeException('No odiinst found', OdiseeException.SERVER_ERROR))
        }
        final String text = odiinstPath.toFile().getText(OdiseeConstant.S_UTF8)
        final Map groups = odiinstReloader.update(text)
        if (groups == null) {
            return
        }
        try {
            officeConnectionFactory = OfficeConnectionFactory.load(groups)
            odiinstReloader.markApplied(text)
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
            def active = arg.xml.request[requestNumber]
            if (active instanceof GPathResult) {
                xmlString = XmlHelper.asString((GPathResult) active)
            } else if (active instanceof org.w3c.dom.Node) {
                xmlString = XmlHelper.asString((org.w3c.dom.Node) active)
            } else {
                throw new OdiseeException('Cannot read request XML', OdiseeException.BAD_REQUEST)
            }
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
        refreshPool()
        use(OdiseeXmlCategory) {
            // requestNumber = 0 as file contains only one request
            arg.result = requestXMLFile.toDocument(officeConnectionFactory, 0, [dryRun: arg.dryRun == true])
            if (!arg.dryRun && !arg.result) {
                log.error "${requestXMLFile.fileName.toString()}/${arg.activeIndex}:" +
                        " Got no result, maybe all instances in the requested group are unwilling to perform?"
            }
        }
    }

}
