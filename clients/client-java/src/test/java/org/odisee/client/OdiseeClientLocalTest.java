package org.odisee.client;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The Hallo request through {@link OdiseeClient} against a local server.
 * LibreOffice is the container started by {@code :webservice:startLibreOffice}.
 * This class is not part of the default test task.
 */
public class OdiseeClientLocalTest {

    private static final String USER = "odisee";

    private static final String PASSWORD = "hallo-secret";

    private static final String VALUE = "client-hallo";

    private static Path home;

    private static Path serverLog;

    private static Process server;

    private static String generateUrl;

    @BeforeClass
    public static void startLocalServer() throws Exception {
        home = Path.of(required("odisee.home"));
        final Path jar = Path.of(required("odisee.serverJar"));
        final int officePort = Integer.getInteger("odisee.libreoffice.port", 2002);
        serverLog = Path.of(System.getProperty("odisee.serverLog", "build/java-client-server.log"));
        if (!Files.isRegularFile(jar)) {
            fail("Server jar is missing: " + jar);
        }
        assertOfficeIsUp(officePort);
        prepareHome(officePort);
        final int httpPort = freePort();
        generateUrl = "http://127.0.0.1:" + httpPort + "/odisee/document/generate";
        Files.createDirectories(serverLog.getParent() == null ? Path.of("build") : serverLog.getParent());
        final ProcessBuilder builder = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xmx768m",
                "-Dspring.output.ansi.enabled=never",
                "-jar", jar.toString(),
                "--server.port=" + httpPort);
        builder.environment().put("ODISEE_HOME", home.toString());
        builder.environment().remove("ODISEE_BOOTSTRAP_USER");
        builder.environment().remove("ODISEE_BOOTSTRAP_PASSWORD");
        builder.environment().remove("ODISEE_VAR");
        builder.directory(home.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(serverLog.toFile()));
        try {
            server = builder.start();
            waitUntilReady(httpPort);
        } catch (AssertionError | Exception e) {
            stopServer();
            throw e;
        }
    }

    @AfterClass
    public static void stopLocalServer() {
        stopServer();
    }

    @Test
    public void halloValueIsInThePdf() throws Exception {
        final OdiseeClient client = new OdiseeClient(generateUrl, USER, PASSWORD);
        client.createRequest("Letter", OutputFormat.PDF);
        client.setUserfield("Hallo", VALUE);

        final byte[] pdf = client.process(false);

        assertTrue("PDF is empty. " + tail(serverLog), pdf != null && pdf.length > 4);
        assertEquals('%', (char) pdf[0]);
        try (PDDocument document = Loader.loadPDF(pdf)) {
            final String text = new PDFTextStripper().getText(document);
            assertTrue(text, text.contains(VALUE));
        }
    }

    @Test
    public void noCredentialsIs401AndWritesNoPdf() throws Exception {
        final Path output = home.resolve("var/user").resolve(USER).resolve("output");
        final Set<String> before = filesUnder(output);
        final OdiseeClient client = new OdiseeClient(generateUrl);
        client.createRequest("Letter", OutputFormat.PDF);
        client.setUserfield("Hallo", "should-not-land");

        try {
            final byte[] pdf = client.process(false);
            fail("expected HTTP 401, got " + pdf.length + " bytes");
        } catch (OdiseeClientException e) {
            assertEquals(e.getMessage(), 401, e.getHttpStatus());
        }
        assertEquals(before, filesUnder(output));
    }

    private static void prepareHome(final int officePort) throws IOException {
        Files.createDirectories(home.resolve("etc"));
        Files.writeString(home.resolve("etc/odiinst"),
                "odi1|127.0.0.1|" + officePort + "|/usr/lib/libreoffice||nologo|true\n");
        final String hash = new BCryptPasswordEncoder().encode(PASSWORD);
        Files.writeString(home.resolve("etc/users"),
                "# Odisee users. One line: username bcrypt-hash [admin]\n"
                        + USER + " " + hash + "\n");
        final Path userRoot = home.resolve("var/user").resolve(USER);
        Files.createDirectories(userRoot.resolve("template"));
        Files.createDirectories(userRoot.resolve("work"));
        Files.createDirectories(userRoot.resolve("output"));
        LetterTemplate.write(userRoot.resolve("template").resolve("Letter.ott"));
    }

    private static void assertOfficeIsUp(final int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 2000);
        } catch (IOException e) {
            fail("LibreOffice is not accepting connections on 127.0.0.1:" + port
                    + ". Run ./gradlew :clients:client-java:javaClientOfficeTest");
        }
    }

    private static void waitUntilReady(final int httpPort) throws Exception {
        final URI ready = URI.create("http://127.0.0.1:" + httpPort + "/odisee/ready");
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(180);
        while (System.nanoTime() < deadline) {
            if (!server.isAlive()) {
                fail("Server exited before /ready. " + tail(serverLog));
            }
            try {
                final HttpURLConnection connection = (HttpURLConnection) ready.toURL().openConnection();
                connection.setConnectTimeout(1000);
                connection.setReadTimeout(2000);
                final int status = connection.getResponseCode();
                connection.disconnect();
                if (status == 200) {
                    return;
                }
            } catch (IOException ignored) {
                // The port is not open yet.
            }
            Thread.sleep(1000);
        }
        fail("Server did not answer GET /odisee/ready with 200. " + tail(serverLog));
    }

    private static void stopServer() {
        final Process process = server;
        server = null;
        if (process == null) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static Set<String> filesUnder(final Path root) throws IOException {
        if (!Files.exists(root)) {
            return Set.of();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .map(path -> root.relativize(path).toString())
                    .collect(Collectors.toSet());
        }
    }

    private static String required(final String key) {
        final String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new AssertionError("Missing system property " + key
                    + ". Run ./gradlew :clients:client-java:javaClientOfficeTest");
        }
        return value;
    }

    private static String tail(final Path log) throws IOException {
        if (log == null || !Files.isRegularFile(log)) {
            return "(no server log)";
        }
        final List<String> lines = Files.readAllLines(log, StandardCharsets.UTF_8);
        final int from = Math.max(0, lines.size() - 80);
        return String.join("\n", lines.subList(from, lines.size()));
    }

}
