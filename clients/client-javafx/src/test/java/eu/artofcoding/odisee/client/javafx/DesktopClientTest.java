package eu.artofcoding.odisee.client.javafx;

import com.sun.net.httpserver.HttpServer;
import org.junit.Test;
import org.odisee.client.OdiseeClient;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DesktopClientTest {

    @Test
    public void preparedRequestMatchesV2AndOmitsArchive() throws Exception {
        final OdiseeClient client = DesktopClient.open(
                "http://127.0.0.1:8080/odisee/document/generate", "bearer-secret");
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

    @Test
    public void openSendsBearerAuthorizationHeader() throws Exception {
        final AtomicReference<String> authorization = new AtomicReference<>();
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/odisee/document/generate", exchange -> {
            exchange.getRequestBody().readAllBytes();
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            final byte[] pdf = "pdf-bytes".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, pdf.length);
            exchange.getResponseBody().write(pdf);
            exchange.close();
        });
        server.start();
        try {
            final String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/odisee/document/generate";
            final OdiseeClient client = DesktopClient.open(url, "javafx-token-123");
            DesktopClient.preparePdf(client, "HalloOdisee", "Hallo", "Odisee");

            final byte[] result = client.process(false);

            assertEquals("Bearer javafx-token-123", authorization.get());
            assertArrayEquals("pdf-bytes".getBytes(StandardCharsets.UTF_8), result);
        } finally {
            server.stop(0);
        }
    }

}
