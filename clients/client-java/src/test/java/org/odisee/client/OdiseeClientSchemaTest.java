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

    @Test
    public void multipleCreateRequestCallsAppendDistinctRequests() throws Exception {
        final OdiseeClient client = OdiseeClient.bearer("http://127.0.0.1:8080/odisee/document/generate", "token");
        final de.odisee.xml.server.request.Request first = client.createRequest("FirstTemplate", OutputFormat.PDF);
        client.setUserfield(first, "FirstField", "alpha");
        final de.odisee.xml.server.request.Request second = client.createRequest("SecondTemplate", OutputFormat.ODT);
        client.setUserfield(second, "SecondField", "beta");

        org.junit.Assert.assertNotSame(first, second);
        org.junit.Assert.assertEquals("FirstTemplate", first.getTemplate().getName());
        org.junit.Assert.assertEquals("SecondTemplate", second.getTemplate().getName());

        final Path out = Files.createTempFile("odisee-multi", ".xml");
        client.saveRequestTo(out);
        final String xml = Files.readString(out);
        assertTrue(xml.contains("name=\"FirstTemplate\""));
        assertTrue(xml.contains("name=\"SecondTemplate\""));
        assertTrue(xml.contains(">alpha<"));
        assertTrue(xml.contains(">beta<"));

        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(Path.of("src/main/schema/request.xsd").toFile())
                .newValidator()
                .validate(new StreamSource(out.toFile()));
    }

    @Test
    public void executeBasicMacroWithoutParametersMarshalsValidSchema() throws Exception {
        final OdiseeClient client = OdiseeClient.bearer("http://127.0.0.1:8080/odisee/document/generate", "token");
        client.createRequest("HalloOdisee", OutputFormat.PDF);
        client.executeBasicMacroInDocument("Standard.Module1.Main");
        client.executeMacro("Standard.Module1.Second", OdiseeClient.LOCATION_DOCUMENT, OdiseeClient.LANGUAGE_BASIC, null);

        final Path out = Files.createTempFile("odisee-macro", ".xml");
        client.saveRequestTo(out);
        final String xml = Files.readString(out);
        assertTrue(xml.contains("Standard.Module1.Main"));
        assertTrue(xml.contains("Standard.Module1.Second"));

        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(Path.of("src/main/schema/request.xsd").toFile())
                .newValidator()
                .validate(new StreamSource(out.toFile()));
    }

    @Test
    public void javaClientBuildDropsUnusedSpringSecurityCrypto() throws Exception {
        final String gradle = Files.readString(Path.of("build.gradle.kts"));
        org.junit.Assert.assertFalse(gradle.contains("spring-security-crypto"));
    }

}
