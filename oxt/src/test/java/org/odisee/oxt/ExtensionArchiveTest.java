package org.odisee.oxt;

import org.junit.Test;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The LibreOffice extension archive is the tree under {@code ext/}
 * plus the descriptors stamped with the git tag version.
 */
public class ExtensionArchiveTest {

    private static final String MANIFEST_NS = "http://openoffice.org/2001/manifest";
    private static final String DESCRIPTION_NS = "http://openoffice.org/extensions/description/2006";
    private static final String UPDATE_NS = "http://openoffice.org/extensions/update/2006";

    @Test
    public void zipMatchesTheExtensionTree() throws Exception {
        final Path ext = Path.of(System.getProperty("odisee.ext"));
        final Map<String, byte[]> entries = zipEntries(Path.of(System.getProperty("odisee.oxt")));

        final Map<String, byte[]> expected = new LinkedHashMap<>();
        try (var paths = Files.walk(ext)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                final String name = ext.relativize(path).toString().replace('\\', '/');
                try {
                    expected.put(name, Files.readAllBytes(path));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
        expected.put("META-INF/manifest.xml", entries.get("META-INF/manifest.xml"));
        expected.put("description.xml", entries.get("description.xml"));

        assertEquals(expected.keySet(), entries.keySet());
        for (Map.Entry<String, byte[]> file : expected.entrySet()) {
            if (file.getKey().equals("META-INF/manifest.xml") || file.getKey().equals("description.xml")) {
                continue;
            }
            assertArrayEquals(file.getKey(), file.getValue(), entries.get(file.getKey()));
        }

        for (String name : entries.keySet()) {
            assertFalse(name, name.endsWith("Addons.xcu"));
            assertFalse(name, name.startsWith("templates/") || name.equals("templates"));
            assertFalse(name, name.startsWith("bin/") || name.equals("bin"));
            assertFalse(name, name.equals("Odisee-update.xml"));
        }
    }

    @Test
    public void developmentArchiveMatchesProduction() throws Exception {
        final Map<String, byte[]> production = zipEntries(Path.of(System.getProperty("odisee.oxt")));
        final Map<String, byte[]> development = zipEntries(Path.of(System.getProperty("odisee.dev")));
        assertEquals(production.keySet(), development.keySet());
        for (String name : production.keySet()) {
            assertArrayEquals(name, production.get(name), development.get(name));
        }
    }

    @Test
    public void descriptorsUseTheGitTagVersion() throws Exception {
        final String version = ExtensionDescriptors.versionFromTag(
                System.getProperty("odisee.gitTag"),
                System.getProperty("odisee.versionFallback"));
        assertEquals("2.6.0", System.getProperty("odisee.versionFallback"));
        if (System.getProperty("odisee.gitTag").isBlank()) {
            assertEquals("2.6.0", version);
        }
        assertFalse(version.equals("2.6.34"));

        final Map<String, byte[]> entries = zipEntries(Path.of(System.getProperty("odisee.oxt")));
        final Element manifest = parse(entries.get("META-INF/manifest.xml"));
        final List<Element> fileEntries = children(manifest, MANIFEST_NS, "file-entry");
        assertEquals(3, fileEntries.size());
        assertEntry(fileEntries.get(0), "basic/Odisee/", "application/vnd.sun.star.basic-library");
        assertEntry(fileEntries.get(1), "Paths.xcu", "application/vnd.sun.star.configuration-data");
        assertEntry(fileEntries.get(2), "etc/pkg-description.txt", "application/vnd.sun.star.package-bundle-description");

        final Element description = parse(entries.get("description.xml"));
        assertEquals(DESCRIPTION_NS, description.getNamespaceURI());
        assertEquals("vnd.artofcoding.odisee.Odisee", textAttr(description, "identifier", "value"));
        assertEquals(version, textAttr(description, "version", "value"));
        assertEquals("3.3", textAttr(description, "OpenOffice.org-minimal-version", "value"));
        assertEquals("https://www.odisee.org/update/oxt/Odisee-update.xml", textAttr(description, "src", "href"));
        assertEquals("etc/LICENSE.txt", textAttr(description, "license-text", "href"));
        assertEquals("images/odisee/Odisee_Signet_24x32.png", textAttr(description, "default", "href"));
        final String descriptionText = new String(entries.get("description.xml"));
        assertTrue(descriptionText.contains("art of coding UG"));
        assertTrue(descriptionText.contains("http://www.odisee.org"));
        assertFalse(descriptionText.contains("2.6.34"));

        final Path updatePath = Path.of(System.getProperty("odisee.update"));
        assertTrue(Files.isRegularFile(updatePath));
        final byte[] updateBytes = Files.readAllBytes(updatePath);
        final Element update = parse(updateBytes);
        assertEquals(UPDATE_NS, update.getNamespaceURI());
        assertEquals("vnd.artofcoding.odisee.Odisee", textAttr(update, "identifier", "value"));
        assertEquals(version, textAttr(update, "version", "value"));
        assertEquals("https://www.odisee.org/update/oxt/Odisee.oxt", textAttr(update, "src", "href"));
        assertFalse(new String(updateBytes).contains("2.6.34"));
    }

    private static void assertEntry(Element entry, String path, String mediaType) {
        assertEquals(path, entry.getAttributeNS(MANIFEST_NS, "full-path"));
        assertEquals(mediaType, entry.getAttributeNS(MANIFEST_NS, "media-type"));
    }

    private static String textAttr(Element root, String localName, String attribute) {
        final List<Element> found = descendants(root, localName);
        assertEquals(localName, 1, found.size());
        final String value = found.get(0).getAttribute(attribute);
        if (!value.isEmpty()) {
            return value;
        }
        return found.get(0).getAttributeNS("http://www.w3.org/1999/xlink", attribute);
    }

    private static List<Element> children(Element parent, String namespace, String localName) {
        final List<Element> found = new ArrayList<>();
        for (int i = 0; i < parent.getChildNodes().getLength(); i++) {
            if (parent.getChildNodes().item(i) instanceof Element element
                    && namespace.equals(element.getNamespaceURI())
                    && localName.equals(element.getLocalName())) {
                found.add(element);
            }
        }
        return found;
    }

    private static List<Element> descendants(Element parent, String localName) {
        final List<Element> found = new ArrayList<>();
        collect(parent, localName, found);
        return found;
    }

    private static void collect(Element parent, String localName, List<Element> found) {
        for (int i = 0; i < parent.getChildNodes().getLength(); i++) {
            if (!(parent.getChildNodes().item(i) instanceof Element element)) {
                continue;
            }
            if (localName.equals(element.getLocalName())) {
                found.add(element);
            }
            collect(element, localName, found);
        }
    }

    private static Element parse(byte[] xml) throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        try (InputStream in = new java.io.ByteArrayInputStream(xml)) {
            return factory.newDocumentBuilder().parse(in).getDocumentElement();
        }
    }

    private static Map<String, byte[]> zipEntries(Path archive) throws Exception {
        assertTrue(Files.isRegularFile(archive));
        final Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            final Enumeration<? extends ZipEntry> enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                final ZipEntry entry = enumeration.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    entries.put(entry.getName(), in.readAllBytes());
                }
            }
        }
        return entries;
    }

    private static void assertArrayEquals(String name, byte[] expected, byte[] actual) {
        assertEquals(name, expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] != actual[i]) {
                org.junit.Assert.fail(name + " differs at byte " + i);
            }
        }
    }
}
