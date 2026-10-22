package org.odisee.oxt;

import org.junit.Test;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExtensionDescriptorsTest {

    private static final String MANIFEST_NS = "http://openoffice.org/2001/manifest";

    @Test
    public void versionFromTagUsesTheDottedNumber() {
        assertEquals("2.6.0", ExtensionDescriptors.versionFromTag("v2.6.0", "9.9.9"));
        assertEquals("2.6", ExtensionDescriptors.versionFromTag("v2.6\n", "9.9.9"));
        assertEquals("2.6.0", ExtensionDescriptors.versionFromTag("2.6.0", "9.9.9"));
        assertEquals("1.2.3.4", ExtensionDescriptors.versionFromTag("v1.2.3.4", "9.9.9"));
        assertEquals("9.9.9", ExtensionDescriptors.versionFromTag("", "9.9.9"));
        assertEquals("9.9.9", ExtensionDescriptors.versionFromTag("93f55f2", "9.9.9"));
        assertEquals("9.9.9", ExtensionDescriptors.versionFromTag("v2.6.0-42", "9.9.9"));
        assertEquals("2.6.0", ExtensionDescriptors.versionFromTag(null, "2.6.0"));
    }

    @Test
    public void extensionNameDropsSpacesAndDashes() throws Exception {
        Path dir = Files.createTempDirectory("named");
        try {
            Files.writeString(dir.resolve("extension.properties"), "ext.name=Acme Reports\n");
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(dir.resolve("extension.properties"))) {
                props.load(in);
            }
            assertEquals("AcmeReports", ExtensionDescriptors.extensionName(dir, props));
            props.setProperty("ext.name", "Acme-Reports");
            assertEquals("AcmeReports", ExtensionDescriptors.extensionName(dir, props));
        } finally {
            deleteTree(dir);
        }
    }

    @Test
    public void descriptorsFollowTheExtensionTree() throws Exception {
        Path dir = Files.createTempDirectory("customer");
        Path metadata = dir.resolve("metadata");
        Path update = dir.resolve("dist/Acme-update.xml");
        try {
            Files.writeString(dir.resolve("extension.properties"), """
                    ext.name=Acme Reports
                    ext.vendor.name=Acme GmbH
                    ext.vendor.url=https://acme.example
                    ext.vendor.prefix=vnd.acme
                    ext.icon=images/acme/logo.png
                    ext.update.url.prefix=https://acme.example/oxt
                    ext.ooo.minver=4.1
                    """);
            Path ext = dir.resolve("ext");
            Files.createDirectories(ext.resolve("basic/Beta"));
            Files.createDirectories(ext.resolve("basic/Alpha"));
            Files.writeString(ext.resolve("basic/Beta/script.xlb"), "beta");
            Files.writeString(ext.resolve("basic/Alpha/script.xlb"), "alpha");
            Files.writeString(ext.resolve("Paths.xcu"), "<paths/>");
            Files.createDirectories(ext.resolve("etc"));
            Files.writeString(ext.resolve("etc/pkg-description.txt"), "Acme");
            Files.writeString(ext.resolve("etc/registrymodifications.xcu"), "<reg/>");
            Files.writeString(ext.resolve("etc/LICENSE.txt"), "license");
            Files.createDirectories(ext.resolve("templates"));
            Files.writeString(ext.resolve("templates/Hello World.ott"), "ott");
            Files.writeString(ext.resolve("templates/Skip.txt"), "no");
            Files.createDirectories(ext.resolve("bin"));
            Files.writeString(ext.resolve("bin/tool.sh"), "#!/bin/sh\n");

            ExtensionDescriptors.write(dir, metadata, update, "v1.4.2", "9.9.9", false);

            Element manifest = parse(Files.readAllBytes(metadata.resolve("META-INF/manifest.xml")));
            List<Element> entries = children(manifest, MANIFEST_NS, "file-entry");
            assertEquals(6, entries.size());
            assertEntry(entries.get(0), "basic/Alpha/", "application/vnd.sun.star.basic-library");
            assertEntry(entries.get(1), "basic/Beta/", "application/vnd.sun.star.basic-library");
            assertEntry(entries.get(2), "etc/Addons.xcu", "application/vnd.sun.star.configuration-data");
            assertEntry(entries.get(3), "Paths.xcu", "application/vnd.sun.star.configuration-data");
            assertEntry(entries.get(4), "etc/registrymodifications.xcu", "application/vnd.sun.star.configuration-data");
            assertEntry(entries.get(5), "etc/pkg-description.txt", "application/vnd.sun.star.package-bundle-description");

            String description = Files.readString(metadata.resolve("description.xml"));
            assertTrue(description.contains("value=\"vnd.acme.AcmeReports\""));
            assertTrue(description.contains("value=\"1.4.2\""));
            assertTrue(description.contains("value=\"4.1\""));
            assertTrue(description.contains("Acme GmbH"));
            assertTrue(description.contains("https://acme.example"));
            assertTrue(description.contains("images/acme/logo.png"));
            assertTrue(description.contains("https://acme.example/oxt/AcmeReports-update.xml"));
            assertTrue(description.contains("etc/LICENSE.txt"));

            String addons = Files.readString(metadata.resolve("etc/Addons.xcu"));
            assertTrue(addons.contains("AcmeReports.OfficeMenuBar"));
            assertTrue(addons.contains("mHello_World"));
            assertTrue(addons.contains("Hello World"));
            assertTrue(addons.contains("vnd.sun.star.script:AcmeReports._Menu.Hello_World?language=Basic&amp;location=application"));
            assertTrue(addons.contains("private:separator"));
            assertFalse(addons.contains("Skip"));

            String updateXml = Files.readString(update);
            assertTrue(updateXml.contains("value=\"1.4.2\""));
            assertTrue(updateXml.contains("https://acme.example/oxt/AcmeReports.oxt"));
            assertFalse(updateXml.contains("9.9.9"));
        } finally {
            deleteTree(dir);
        }
    }

    @Test
    public void providedAddonsFileIsKept() throws Exception {
        Path dir = Files.createTempDirectory("provided");
        Path metadata = dir.resolve("metadata");
        try {
            Files.createDirectories(dir.resolve("ext/etc"));
            Files.writeString(dir.resolve("ext/etc/Addons.xcu"), "<provided/>");
            Files.createDirectories(dir.resolve("ext/templates"));
            Files.writeString(dir.resolve("ext/templates/Letter.ott"), "ott");
            ExtensionDescriptors.write(dir, metadata, dir.resolve("dist/provided-update.xml"), "", "2.6.0", false);
            assertFalse(Files.exists(metadata.resolve("etc/Addons.xcu")));
            String manifest = Files.readString(metadata.resolve("META-INF/manifest.xml"));
            assertTrue(manifest.contains("etc/Addons.xcu"));
        } finally {
            deleteTree(dir);
        }
    }

    @Test
    public void overrideBuildsAddonsFromTemplates() throws Exception {
        Path dir = Files.createTempDirectory("override");
        Path metadata = dir.resolve("metadata");
        try {
            Files.createDirectories(dir.resolve("ext/etc"));
            Files.writeString(dir.resolve("ext/etc/Addons.xcu"), "<provided/>");
            Files.createDirectories(dir.resolve("ext/templates"));
            Files.writeString(dir.resolve("ext/templates/Letter.ott"), "ott");
            ExtensionDescriptors.write(dir, metadata, dir.resolve("dist/override-update.xml"), "v2.6.0", "9.9.9", true);
            String addons = Files.readString(metadata.resolve("etc/Addons.xcu"));
            assertTrue(addons.contains("mLetter"));
            assertFalse(addons.contains("<provided/>"));
        } finally {
            deleteTree(dir);
        }
    }

    private static void assertEntry(Element entry, String path, String mediaType) {
        assertEquals(path, entry.getAttributeNS(MANIFEST_NS, "full-path"));
        assertEquals(mediaType, entry.getAttributeNS(MANIFEST_NS, "media-type"));
    }

    private static List<Element> children(Element parent, String namespace, String localName) {
        List<Element> found = new ArrayList<>();
        for (int i = 0; i < parent.getChildNodes().getLength(); i++) {
            if (parent.getChildNodes().item(i) instanceof Element element
                    && namespace.equals(element.getNamespaceURI())
                    && localName.equals(element.getLocalName())) {
                found.add(element);
            }
        }
        return found;
    }

    private static Element parse(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        try (InputStream in = new java.io.ByteArrayInputStream(xml)) {
            return factory.newDocumentBuilder().parse(in).getDocumentElement();
        }
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
