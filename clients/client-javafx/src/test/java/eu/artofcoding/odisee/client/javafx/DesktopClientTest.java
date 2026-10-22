package eu.artofcoding.odisee.client.javafx;

import org.junit.Test;
import org.odisee.client.OdiseeClient;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DesktopClientTest {

    @Test
    public void preparedRequestMatchesV2AndOmitsArchive() throws Exception {
        final OdiseeClient client = DesktopClient.open(
                "http://127.0.0.1:8080/odisee/document/generate", "odisee", "secret");
        DesktopClient.preparePdf(client, "HalloOdisee", "Hallo", "Odisee");
        final Path out = Files.createTempFile("odisee-javafx", ".xml");
        client.saveRequestTo(out);
        final String xml = Files.readString(out);

        assertTrue(xml.contains("http://xmlns.odisee.de/v2/request"));
        assertTrue(xml.contains("outputFormat=\"PDF\""));
        assertTrue(xml.contains(">Odisee<"));
        assertFalse(xml.contains("archive"));

        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(Path.of("../client-java/src/main/schema/request.xsd").toFile())
                .newValidator()
                .validate(new StreamSource(out.toFile()));
    }

}
