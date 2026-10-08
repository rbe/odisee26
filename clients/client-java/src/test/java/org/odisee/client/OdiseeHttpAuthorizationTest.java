package org.odisee.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * Basic credentials are a header on the request that sets them.
 * A later call with no credentials does not reuse them.
 */
public class OdiseeHttpAuthorizationTest {

    @Test
    public void credentialsStayOnTheRequestThatSetsThem() throws Exception {
        final AtomicReference<String> authorization = new AtomicReference<>();
        final HttpServer server = server(authorization, 200);
        server.start();
        try {
            final String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/odisee/document/generate";
            final OdiseeClient client = new OdiseeClient(url, "ada", "s3cret");
            client.createRequest("Letter", OutputFormat.PDF);
            client.setUserfield("Hallo", "client-hallo");

            final byte[] body = client.process(false);

            assertEquals(basic("ada", "s3cret"), authorization.get());
            assertArrayEquals("pdf-bytes".getBytes(StandardCharsets.UTF_8), body);

            final OdiseeClient noLogin = new OdiseeClient(url);
            noLogin.createRequest("Letter", OutputFormat.PDF);
            noLogin.setUserfield("Hallo", "should-not-land");
            try {
                final byte[] pdf = noLogin.process(false);
                fail("expected HTTP 401, got " + pdf.length + " bytes");
            } catch (OdiseeClientException e) {
                assertEquals(401, e.getHttpStatus());
            }
            assertNull(authorization.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void compressedRequestStillSendsBasicOnThatRequest() throws Exception {
        final AtomicReference<String> authorization = new AtomicReference<>();
        final AtomicReference<String> encoding = new AtomicReference<>();
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/odisee/document/generate", exchange -> {
            exchange.getRequestBody().readAllBytes();
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            encoding.set(exchange.getRequestHeaders().getFirst("Content-Encoding"));
            reply(exchange, 200, "pdf-bytes");
        });
        server.start();
        try {
            final String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/odisee/document/generate";
            final OdiseeClient client = new OdiseeClient(url, "ada", "s3cret");
            client.createRequest("Letter", OutputFormat.PDF);
            client.setUserfield("Hallo", "client-hallo");

            final byte[] body = client.process(true);

            assertEquals(basic("ada", "s3cret"), authorization.get());
            assertEquals("gzip", encoding.get());
            assertArrayEquals("pdf-bytes".getBytes(StandardCharsets.UTF_8), body);
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer server(final AtomicReference<String> authorization, final int successStatus)
            throws IOException {
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/odisee/document/generate", exchange -> {
            exchange.getRequestBody().readAllBytes();
            final String header = exchange.getRequestHeaders().getFirst("Authorization");
            authorization.set(header);
            if (header == null) {
                reply(exchange, 401, "Authentication required\n");
                return;
            }
            reply(exchange, successStatus, "pdf-bytes");
        });
        return server;
    }

    private static void reply(final HttpExchange exchange, final int status, final String message) throws IOException {
        final byte[] body = message.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static String basic(final String username, final String password) {
        final String token = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

}
