package org.odisee.client;

import org.junit.Test;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Source and schema contract checks for B58, B59, B60, and B61.
 */
public class ClientsSkillsDocsContractTest {

    @Test
    public void phpClientUsesBearerCleansTempFileThrowsOnHttpErrorsAndKeepsOutputFormat() throws Exception {
        final Path root = repoRoot();
        final String odisee = read(root.resolve("clients/client-php/src/main/php/class/Odisee/Odisee.class.php"));
        final String http = read(root.resolve("clients/client-php/src/main/php/class/Odisee/OdiseeHttpHelper.class.php"));
        final String json = read(root.resolve("clients/client-php/src/main/php/class/Odisee/OdiseeJson.class.php"));

        assertTrue(odisee.contains("public static function bearer($serviceURL, $accessToken)"));
        assertFalse(odisee.contains("$username"));
        assertFalse(odisee.contains("$password"));

        assertTrue(http.contains("'Authorization: Bearer '"));
        assertFalse(http.contains("CURLOPT_USERPWD"));
        assertTrue(http.contains("dirname(__FILE__) . '/var/tmp'"));
        assertFalse(http.contains("dirname(__FILE__) . 'var/tmp'"));
        assertTrue(http.contains("unlink($filename)"));
        assertTrue(http.contains("CURLINFO_HTTP_CODE"));
        assertTrue(http.contains("$status >= 400"));

        assertTrue(json.contains("case 'output':"));
        assertTrue(json.contains("private static function output("));
        assertTrue(json.contains("private static function format("));
        assertTrue(json.contains("$root->getAttribute('delivery')"));
    }

    @Test
    public void vbnetClientUsesBearerUtf8ThrowsOnErrorsFixesXmlAndKeepsOutputFormat() throws Exception {
        final Path root = repoRoot();
        final String client = read(root.resolve("clients/client-vbnet/src/vb/OdiseeClient/OdiseeClient.vb"));
        final String simple = read(root.resolve("clients/client-vbnet/src/vb/OdiseeClient/OdiseeSimpleClient.vb"));
        final String http = read(root.resolve("clients/client-vbnet/src/vb/OdiseeClient/OdiseeHelper.HttpPost.vb"));
        final String xml = read(root.resolve("clients/client-vbnet/src/vb/OdiseeClient/OdiseeHelper.Xml.vb"));
        final String json = read(root.resolve("clients/client-vbnet/src/vb/OdiseeClient/OdiseeHelper.Json.vb"));

        assertTrue(client.contains("Public Shared Function bearer("));
        assertFalse(client.contains("doBasicAuthPost"));
        assertFalse(client.contains("doDigestAuthPost"));
        assertTrue(client.contains("inputFileElement.SetAttribute(\"filename\", filepath)"));
        assertFalse(client.contains("inputFileElement.SetAttribute(\"file\", filepath)"));
        assertTrue(client.contains("Return setLatestTemplate(actualRequest, template, outputFormat)"));

        assertTrue(simple.contains("Public Shared Function bearer("));
        assertFalse(simple.contains("doDigestAuthPost"));
        assertTrue(simple.contains("inputFileElement.SetAttribute(\"filename\", filepath)"));
        assertFalse(simple.contains("inputFileElement.SetAttribute(\"file\", filepath)"));

        assertTrue(http.contains("\"Authorization\", \"Bearer \""));
        assertTrue(http.contains("UTF8Encoding"));
        assertFalse(http.contains("Encoding.Default"));
        assertFalse(http.contains("\"Basic \""));
        assertFalse(http.contains("\"Digest\""));
        assertTrue(http.contains("StatusCode) >= 400"));
        final int addIdx = http.indexOf("totalReadCount = totalReadCount + readCount");
        final int secondReadIdx = http.indexOf("readCount = responseStream.Read(byteBuffer, 0, bufferSize)", addIdx);
        assertTrue(addIdx > 0 && secondReadIdx > addIdx);

        assertTrue(xml.contains("postProcessNode.AppendChild(xmlElement)"));
        assertFalse(xml.contains("CreateElement(\"instructions\")"));

        assertTrue(json.contains("Case \"output\""));
        assertTrue(json.contains("Private Shared Sub WriteOutput("));
        assertTrue(json.contains("GetAttribute(\"delivery\")"));
    }

    @Test
    public void oracleClientUsesBearerPostsToV2RouteEmitsValidV2XmlFixesTypoAndUsesUtf8ByteLength() throws Exception {
        final Path root = repoRoot();
        final String odiseeSpec = read(root.resolve("clients/client-oracle/src/main/oracle/odisee.pks"));
        final String odiseeBody = read(root.resolve("clients/client-oracle/src/main/oracle/odisee.pkb"));
        final String httpSpec = read(root.resolve("clients/client-oracle/src/main/oracle/httputil.pks"));
        final String httpBody = read(root.resolve("clients/client-oracle/src/main/oracle/httputil.pkb"));

        assertTrue(odiseeSpec.contains("PROCEDURE set_bearer_token(token IN VARCHAR2);"));
        assertFalse(odiseeSpec.contains("alings"));
        assertTrue(odiseeSpec.contains("aligns IN VARCHAR2"));

        assertTrue(odiseeBody.contains("/odisee/document/generate"));
        assertFalse(odiseeBody.contains("/oooDocument"));
        assertFalse(odiseeBody.contains("<!DOCTYPE"));
        assertFalse(odiseeBody.contains("<userfields>"));
        assertFalse(odiseeBody.contains("</userfields>"));
        assertFalse(odiseeBody.contains("<insert "));
        assertFalse(odiseeBody.contains("post-set-macro="));
        assertFalse(odiseeBody.contains("alings"));
        assertTrue(odiseeBody.contains("http://xmlns.odisee.de/v2/request"));
        assertTrue(odiseeBody.contains("<instructions>"));
        assertTrue(odiseeBody.contains("<image type="));

        assertTrue(httpSpec.contains("bearer_token IN VARCHAR2 DEFAULT NULL"));
        assertTrue(httpBody.contains("'Authorization', 'Bearer '"));
        assertTrue(httpBody.contains("UTL_I18N.STRING_TO_RAW(NVL(data, ''), 'AL32UTF8')"));
        assertTrue(httpBody.contains("UTL_RAW.LENGTH(raw_body)"));
        assertTrue(httpBody.contains("UTL_HTTP.WRITE_RAW(httpreq, raw_body)"));
        assertFalse(httpBody.contains("SET_HEADER(httpreq, 'Content-Length', LENGTH(data))"));
        assertFalse(httpBody.contains("set_header(httpreq, 'Content-Length', LENGTH(data))"));
        assertTrue(httpBody.contains("status_code >= 400"));

        final String emittedV2Xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<odisee xmlns=\"http://xmlns.odisee.de/v2/request\">"
                + "<request name=\"IT-Vertrag\" id=\"1\">"
                + "<template name=\"IT-Vertrag\" outputFormat=\"pdf\" revision=\"LATEST\" pre-save-macro=\"Standard.Module1.Before\"/>"
                + "<archive database=\"true\" files=\"false\"/>"
                + "<instructions>"
                + "<userfield name=\"firma\" post-macro=\"Standard.Module1.After\">IRB &amp; Co</userfield>"
                + "<userfield name=\"Tabelle1$1$1\">42</userfield>"
                + "<userfield name=\"setwidth_Tabelle1\">20,80</userfield>"
                + "<userfield name=\"align_Tabelle1\">L,R</userfield>"
                + "<image type=\"image/png\" bookmark=\"Logo\" url=\"https://example.org/logo.png\"/>"
                + "</instructions>"
                + "</request>"
                + "</odisee>";
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(root.resolve("clients/client-java/src/main/schema/request.xsd").toFile())
                .newValidator()
                .validate(new StreamSource(new StringReader(emittedV2Xml)));
    }

    @Test
    public void skillsDocsReadmesAndHttpFixturesMatchBearerAndReleaseContract() throws Exception {
        final Path root = repoRoot();
        final String releaseSkill = read(root.resolve(".cursor/skills/release/SKILL.md"));
        assertTrue(releaseSkill.contains("A missing tag fails the publish."));
        assertTrue(releaseSkill.contains("A tag push does not publish."));
        assertTrue(releaseSkill.contains("does not create a git tag."));
        assertFalse(releaseSkill.contains("${GITHUB_REF_NAME}"));

        final String usersGuide = read(root.resolve("documentation/src/docs/asciidoc/UsersGuide.adoc"));
        assertTrue(usersGuide.contains("OdiseeClient.bearer("));
        assertTrue(usersGuide.contains("Authorization: Bearer"));
        assertTrue(usersGuide.contains("http://127.0.0.1:8080/odisee/document/generate"));
        assertTrue(usersGuide.contains("Odisee::bearer("));
        assertTrue(usersGuide.contains("odisee.set_bearer_token("));
        assertFalse(usersGuide.contains("OdiseeClient.createClient("));
        assertFalse(usersGuide.contains("curl -u "));
        assertFalse(usersGuide.contains("Authorization: Basic"));
        assertFalse(usersGuide.contains("/oooDocument/generate"));

        final String adminGuide = read(root.resolve("documentation/src/docs/asciidoc/AdministratorsGuide.adoc"));
        assertTrue(adminGuide.contains("Ubuntu 26.04"));
        assertTrue(adminGuide.contains("odictl -q findooo"));
        assertTrue(adminGuide.contains("libreoffice.jar"));
        assertTrue(adminGuide.contains("proxy_http"));
        assertFalse(adminGuide.contains("Ubuntu 18.04"));
        assertFalse(adminGuide.contains("find-inst"));
        assertFalse(adminGuide.contains("proxy_ajp"));
        assertFalse(adminGuide.contains("etc/apache/odisee"));

        final String phpReadme = read(root.resolve("clients/client-php/README.md"));
        assertTrue(phpReadme.contains("Odisee::bearer("));
        assertTrue(phpReadme.contains("/odisee/document/generate"));
        assertFalse(phpReadme.contains("'username', 'password'"));

        final String vbReadme = read(root.resolve("clients/client-vbnet/README.md"));
        assertTrue(vbReadme.contains("OdiseeClient.bearer("));
        assertTrue(vbReadme.contains("/odisee/document/generate"));
        assertFalse(vbReadme.contains("OdiseeSimpleHttpClient"));
        assertFalse(vbReadme.contains("\"Basic\""));
        assertFalse(vbReadme.contains("\"odisee\", \"odisee\""));

        final Path intellijDir = root.resolve("webservice/src/test/resources/intellij");
        try (Stream<Path> files = Files.list(intellijDir)) {
            final List<Path> httpFiles = files.filter(p -> p.toString().endsWith(".http")).toList();
            assertFalse(httpFiles.isEmpty());
            for (Path httpFile : httpFiles) {
                final String content = read(httpFile);
                assertTrue(httpFile.toString(), content.contains("POST http://localhost:8080/odisee/document/generate"));
                assertTrue(httpFile.toString(), content.contains("Authorization: Bearer {{token}}"));
                assertFalse(httpFile.toString(), content.contains("POST http://localhost:8080/document/generate"));
                for (String line : content.lines().toList()) {
                    if (line.startsWith("< ")) {
                        final Path referenced = httpFile.getParent().resolve(line.substring(2).trim()).normalize();
                        assertTrue("Missing fixture " + referenced + " in " + httpFile, Files.isRegularFile(referenced));
                    }
                }
            }
        }

        final String bugs = read(root.resolve("docs/BUGS.md"));
        assertTrue(bugs.contains("| B25 | major | partial in wave 4 |"));
        for (String id : List.of("B57", "B58", "B59", "B60", "B61")) {
            assertTrue("Missing " + id, bugs.contains("| " + id + " |"));
        }
    }

    @Test
    public void productDocsMatchMergedOfficeCallbackAndSingleBugRows() throws Exception {
        final Path root = repoRoot();
        final String admin = read(root.resolve("documentation/src/docs/asciidoc/AdministratorsGuide.adoc"));
        assertTrue(admin.contains("libreoffice-writer libreoffice-calc libreoffice-impress"));
        assertTrue(admin.contains("headless LibreOffice Writer, Calc, and Impress"));
        assertTrue(admin.contains("The host must be a public external host."));
        assertTrue(admin.contains("A manual run of another ref does not delete published versions."));

        final String users = read(root.resolve("documentation/src/docs/asciidoc/UsersGuide.adoc"));
        assertTrue(users.contains("archive/@files"));
        assertTrue(users.contains("500 is an unexpected server error."));
        assertTrue(users.contains("The host must be a public external host."));
        assertTrue(users.contains("`image/@url` is `http` or `https` on a public host."));

        final String tenancy = read(root.resolve("documentation/src/docs/asciidoc/Multitenancy.adoc"));
        assertTrue(tenancy.contains("The host must be a public external host."));

        final String features = read(root.resolve("docs/FEATURES.md"));
        assertTrue(features.contains("OdiseeClient.bearer(serviceUrl, accessToken)"));
        assertFalse(features.contains("new OdiseeClient(serviceUrl, username, password)"));
        assertTrue(features.contains("public external host"));

        final String readme = read(root.resolve("README.md"));
        assertTrue(readme.contains("headless LibreOffice Writer, Calc, and Impress"));
        assertTrue(readme.contains("A manual run of another ref does not delete published versions."));

        final String waves = read(root.resolve("docs/WAVES.md"));
        assertFalse(waves.contains("headless LibreOffice Writer)"));
        assertTrue(waves.contains("headless LibreOffice Writer, Calc, and Impress"));
        assertTrue(waves.contains("public external host"));

        final String bugs = read(root.resolve("docs/BUGS.md"));
        assertTrue(bugs.contains("| B25 | major | partial in wave 4 |"));
        for (int n = 40; n <= 61; n++) {
            final String row = "| B" + n + " |";
            final int first = bugs.indexOf(row);
            assertTrue("Missing " + row, first >= 0);
            assertTrue("Duplicate " + row, bugs.indexOf(row, first + row.length()) < 0);
        }
    }

    private static String read(final Path file) throws Exception {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.isRegularFile(dir.resolve("settings.gradle.kts"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException("Repository root not found");
        }
        return dir;
    }

}
