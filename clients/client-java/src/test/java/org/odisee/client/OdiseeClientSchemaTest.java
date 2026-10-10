package org.odisee.client;

import org.junit.Test;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

/**
 * The Java client is generated from the same v2 schema the server validates.
 */
public class OdiseeClientSchemaTest {

    @Test
    public void marshalledRequestMatchesThePublishedSchema() throws Exception {
        final OdiseeClient client = new OdiseeClient("http://127.0.0.1:8080/document/generate");
        client.createRequest("HalloOdisee", OutputFormat.PDF);
        client.setUserfield("Hallo", "Odisee");
        client.mergeDocumentAtEnd(Path.of("pdf/AGB.pdf"));
        final Path out = Files.createTempFile("odisee", ".xml");
        client.saveRequestTo(out);
        final String xml = Files.readString(out);

        assertTrue(xml.contains("http://xmlns.odisee.de/v2/request"));
        assertTrue(xml.contains("outputFormat=\"PDF\""));
        assertTrue(xml.contains(">Odisee<"));

        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(Path.of("src/main/schema/request.xsd").toFile())
                .newValidator()
                .validate(new StreamSource(out.toFile()));

        assertArrayEquals(
                Files.readAllBytes(Path.of("../../webservice/src/main/resources/xml/v2/request.xsd")),
                Files.readAllBytes(Path.of("src/main/schema/request.xsd")));
    }

    @Test
    public void deliveryIsOnTheV2Request() throws Exception {
        final OdiseeClient client = new OdiseeClient("http://127.0.0.1:8080/odisee/document/generate");
        client.createRequest("Letter", OutputFormat.PDF);
        client.setUserfield("Hallo", "Odisee");
        client.delivery("store");
        final Path out = Files.createTempFile("odisee", ".xml");
        client.saveRequestTo(out);
        final String xml = Files.readString(out);
        assertTrue(xml.contains("delivery=\"store\""));
        assertTrue(xml.contains("http://xmlns.odisee.de/v2/request"));

        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(Path.of("src/main/schema/request.xsd").toFile())
                .newValidator()
                .validate(new StreamSource(out.toFile()));

        client.useJson();
        final Path json = Files.createTempFile("odisee", ".json");
        client.saveRequestTo(json);
        assertTrue(Files.readString(json).contains("\"delivery\":\"store\""));
    }

}
