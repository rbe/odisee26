/*
 * odisee-client-java
 * odisee-client-java
 * Copyright (C) 2011-2013 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Alle Rechte vorbehalten. Nutzung unterliegt Lizenzbedingungen.
 * All rights reserved. Use is subject to license terms.
 *
 * rbe, 16.05.13 09:01
 */

package org.odisee.client;

import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class OdiseeClientTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(OdiseeClientTest.class);

    private static final String SERVICE_BASE_URL = "http://service3.odisee.de";

    private static final String GZIP_SERVICE_BASE_URL = "http://service3.odisee.de:81";

    @Test @Ignore
    public void halloOdisee() throws IOException, OdiseeClientException {
        // Create Odisee client
        final String url = String.format("%s/odisee/document/generate", SERVICE_BASE_URL);
        // TODO final String odiseeUsername = "odisee";
        //TODO final String odiseePassword = "odisee";
        final OdiseeClient client = new OdiseeClient(url/*, odiseeUsername, odiseePassword*/);
        // Set values
        client.createRequest("HalloOdisee", OutputFormat.PDF);
        client.setArchive(false, true);
        client.setUserfield("Hallo", "Odisee");
        // Save request to disk
        final Path path = Files.createTempFile(Paths.get("target"), "odisee_", ".xml");
        client.saveRequestTo(path);
        LOGGER.info("Compressed XML request saved to {}", path);
        // Send request
        final byte[] document = client.process(false);
        final File file = new File(path.getParent().toFile(), String.format("%s.pdf", path.getFileName()));
        Files.write(Path.of(file.toURI()), document);
        LOGGER.info("Document saved to {}", file);
        // Document generated?
        assertNotNull(document);
        assertTrue("Document size >= 100 bytes", document.length > 100);
    }

    @Test @Ignore
    public void compressedHalloOdisee() throws IOException, OdiseeClientException {
        // Create Odisee client
        final String url = String.format("%s/odisee/document/generate", GZIP_SERVICE_BASE_URL);
        // TODO final String odiseeUsername = "odisee";
        // TODO final String odiseePassword = "odisee";
        final OdiseeClient client = new OdiseeClient(url/*, odiseeUsername, odiseePassword*/);
        // Set values
        client.createRequest("HalloOdisee", OutputFormat.PDF);
        client.setArchive(false, true);
        client.setUserfield("Hallo", "Odisee");
        // Save request to disk
        final Path path = Files.createTempFile(Paths.get("target"), "odisee_", ".xml.gz");
        client.saveCompressedRequestTo(path);
        LOGGER.info("Compressed XML request saved to {}", path);
        // Send request
        final byte[] document = client.process(true);
        final File file = new File(path.getParent().toFile(), String.format("%s.pdf", path.getFileName()));
        Files.write(Path.of(file.toURI()), document);
        LOGGER.info("Document saved to {}", file);
        // Document generated?
        assertNotNull(document);
        assertTrue("Document size >= 100 bytes", document.length > 100);
    }

}
