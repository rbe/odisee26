package org.odisee.oxt;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Writes the LibreOffice descriptors for one extension directory.
 * The version is a git tag ({@code v2.6.0} becomes {@code 2.6.0}).
 * A missing or non-numeric tag uses the fallback version.
 */
public final class ExtensionDescriptors {

    private static final Pattern TAG_VERSION = Pattern.compile("v?(\\d+(?:\\.\\d+){0,3})");
    private static final Pattern TEMPLATE = Pattern.compile(".*\\.o.*");
    private static final Pattern OFFICE_EXT = Pattern.compile("\\.o\\wt$");
    private static final String VENDOR_PREFIX = "vnd.artofcoding.odisee";

    private ExtensionDescriptors() {
    }

    public static void main(String[] args) throws IOException {
        write(
                Path.of(args[0]),
                Path.of(args[1]),
                Path.of(args[2]),
                args[3],
                args[4],
                Boolean.parseBoolean(args[5]));
    }

    public static String versionFromTag(String tag, String fallback) {
        if (tag == null) {
            return fallback;
        }
        String trimmed = tag.trim();
        var matcher = TAG_VERSION.matcher(trimmed);
        if (!matcher.matches()) {
            return fallback;
        }
        return matcher.group(1);
    }

    /**
     * Directory name, or {@code ext.name}, with spaces and dashes removed.
     * The same rule names the {@code .oxt} file.
     */
    public static String extensionName(Path extensionDir, Properties props) {
        String raw = props.getProperty("ext.name");
        if (raw == null || raw.isBlank()) {
            raw = extensionDir.getFileName().toString();
        }
        return raw.replace(" ", "").replace("-", "");
    }

    public static void write(
            Path extensionDir,
            Path metadataDir,
            Path updateFile,
            String tag,
            String fallback,
            boolean overrideAddons) throws IOException {
        Properties props = loadProperties(extensionDir.resolve("extension.properties"));
        String name = extensionName(extensionDir, props);
        String version = versionFromTag(tag, fallback);
        Path ext = extensionDir.resolve("ext");
        boolean providedAddons = Files.isRegularFile(ext.resolve("etc/Addons.xcu"));
        List<Path> templates = templates(ext.resolve("templates"));
        boolean generateAddons = overrideAddons || (!providedAddons && !templates.isEmpty());

        deleteTree(metadataDir);
        Files.createDirectories(metadataDir.resolve("META-INF"));
        Files.writeString(metadataDir.resolve("META-INF/manifest.xml"), manifestXml(ext, generateAddons || providedAddons));
        Files.writeString(metadataDir.resolve("description.xml"), descriptionXml(props, name, version, ext));
        if (generateAddons) {
            Files.createDirectories(metadataDir.resolve("etc"));
            Files.writeString(metadataDir.resolve("etc/Addons.xcu"), addonsXml(name, templates));
        }
        Files.createDirectories(updateFile.getParent());
        Files.writeString(updateFile, updateXml(props, name, version));
    }

    private static Properties loadProperties(Path file) throws IOException {
        Properties props = new Properties();
        if (!Files.isRegularFile(file)) {
            return props;
        }
        try (InputStream in = Files.newInputStream(file)) {
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return props;
    }

    private static String manifestXml(Path ext, boolean addons) throws IOException {
        StringBuilder xml = new StringBuilder();
        xml.append("""
                <?xml version="1.0" encoding="UTF-8"?>
                <manifest:manifest xmlns:manifest="http://openoffice.org/2001/manifest">
                """);
        for (String library : basicLibraries(ext)) {
            entry(xml, "basic/" + library + "/", "application/vnd.sun.star.basic-library");
        }
        if (addons) {
            entry(xml, "etc/Addons.xcu", "application/vnd.sun.star.configuration-data");
        }
        if (Files.isRegularFile(ext.resolve("Paths.xcu"))) {
            entry(xml, "Paths.xcu", "application/vnd.sun.star.configuration-data");
        }
        if (Files.isRegularFile(ext.resolve("etc/registrymodifications.xcu"))) {
            entry(xml, "etc/registrymodifications.xcu", "application/vnd.sun.star.configuration-data");
        }
        if (Files.isRegularFile(ext.resolve("etc/pkg-description.txt"))) {
            entry(xml, "etc/pkg-description.txt", "application/vnd.sun.star.package-bundle-description");
        }
        xml.append("</manifest:manifest>\n");
        return xml.toString();
    }

    private static void entry(StringBuilder xml, String path, String mediaType) {
        xml.append("    <manifest:file-entry manifest:full-path=\"")
                .append(xmlAttr(path))
                .append("\" manifest:media-type=\"")
                .append(xmlAttr(mediaType))
                .append("\"/>\n");
    }

    private static List<String> basicLibraries(Path ext) throws IOException {
        Path basic = ext.resolve("basic");
        if (!Files.isDirectory(basic)) {
            return List.of();
        }
        try (Stream<Path> children = Files.list(basic)) {
            return children.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        }
    }

    private static String descriptionXml(Properties props, String name, String version, Path ext) {
        String prefix = value(props, "ext.vendor.prefix", VENDOR_PREFIX);
        String vendor = value(props, "ext.vendor.name", "art of coding UG");
        String vendorUrl = value(props, "ext.vendor.url", "http://www.odisee.org");
        String icon = props.getProperty("ext.icon", "");
        String updatePrefix = value(props, "ext.update.url.prefix", "https://www.odisee.org/update/oxt");
        String minver = value(props, "ext.ooo.minver", "3.3");
        StringBuilder xml = new StringBuilder();
        xml.append("""
                <?xml version="1.0" encoding="UTF-8"?>
                <description xmlns="http://openoffice.org/extensions/description/2006" xmlns:dep="http://openoffice.org/extensions/description/2006" xmlns:xlink="http://www.w3.org/1999/xlink">
                    <identifier value="%s"/>
                    <version value="%s"/>
                    <dependencies>
                        <OpenOffice.org-minimal-version value="%s" dep:name="OpenOffice.org %s"/>
                    </dependencies>
                    <display-name>
                        <name lang="en">%s</name>
                        <name lang="de">%s</name>
                    </display-name>
                    <publisher>
                        <name xlink:href="%s" lang="en">%s</name>
                    </publisher>
                """.formatted(
                xmlAttr(prefix + "." + name),
                xmlAttr(version),
                xmlAttr(minver),
                xmlAttr(minver),
                xmlText(name),
                xmlText(name),
                xmlAttr(vendorUrl),
                xmlText(vendor)));
        if (!icon.isBlank()) {
            xml.append("""
                        <icon>
                            <default xlink:href="%s"/>
                        </icon>
                    """.formatted(xmlAttr(icon)));
        }
        xml.append("""
                    <update-information>
                        <src xlink:href="%s"/>
                    </update-information>
                """.formatted(xmlAttr(updatePrefix + "/" + name + "-update.xml")));
        if (Files.isRegularFile(ext.resolve("etc/LICENSE.txt"))) {
            xml.append("""
                        <registration>
                            <simple-license accept-by="admin" default-license-id="ID0" suppress-on-update="true">
                                <license-text xlink:href="etc/LICENSE.txt" lang="en" license-id="ID0"/>
                            </simple-license>
                        </registration>
                    """);
        }
        xml.append("</description>\n");
        return xml.toString();
    }

    private static String updateXml(Properties props, String name, String version) {
        String prefix = value(props, "ext.vendor.prefix", VENDOR_PREFIX);
        String updatePrefix = value(props, "ext.update.url.prefix", "https://www.odisee.org/update/oxt");
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <description xmlns="http://openoffice.org/extensions/update/2006" xmlns:xlink="http://www.w3.org/1999/xlink">
                    <identifier value="%s"/>
                    <version value="%s"/>
                    <update-download>
                        <src xlink:href="%s"/>
                    </update-download>
                </description>
                """.formatted(
                xmlAttr(prefix + "." + name),
                xmlAttr(version),
                xmlAttr(updatePrefix + "/" + name + ".oxt"));
    }

    private static String addonsXml(String name, List<Path> templates) {
        StringBuilder items = new StringBuilder();
        for (Path template : templates) {
            String filename = template.getFileName().toString();
            String functionName = OFFICE_EXT.matcher(filename.replace(" ", "_").replace("-", "_")).replaceFirst("");
            String title = OFFICE_EXT.matcher(filename).replaceFirst("");
            String url = "vnd.sun.star.script:" + name + "._Menu." + functionName + "?language=Basic&location=application";
            items.append("""
                                    <node oor:name="%s" oor:op="replace">
                                        <prop oor:name="URL" oor:type="xs:string">
                                            <value>%s</value>
                                        </prop>
                                        <prop oor:name="Title" oor:type="xs:string">
                                            <value>%s</value>
                                        </prop>
                                        <prop oor:name="Target" oor:type="xs:string">
                                            <value>_self</value>
                                        </prop>
                                    </node>
                    """.formatted(xmlAttr("m" + functionName), xmlText(url), xmlText(title)));
        }
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <oor:component-data xmlns:oor="http://openoffice.org/2001/registry" xmlns:xs="http://www.w3.org/2001/XMLSchema" oor:name="Addons" oor:package="org.openoffice.Office">
                    <node oor:name="AddonUI">
                        <node oor:name="OfficeMenuBar">
                            <node oor:name="%s" oor:op="replace">
                                <prop oor:name="Title" oor:type="xs:string">
                                    <value>%s</value>
                                </prop>
                                <node oor:name="Submenu">
                %s                    <node oor:name="sepAboutBox" oor:op="replace">
                                        <prop oor:name="URL" oor:type="xs:string">
                                            <value>private:separator</value>
                                        </prop>
                                    </node>
                                </node>
                            </node>
                        </node>
                    </node>
                </oor:component-data>
                """.formatted(xmlAttr(name + ".OfficeMenuBar"), xmlText(name), items);
    }

    private static List<Path> templates(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile)
                    .filter(path -> TEMPLATE.matcher(path.getFileName().toString()).matches())
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private static String value(Properties props, String key, String fallback) {
        String raw = props.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return raw.trim();
    }

    private static String xmlAttr(String value) {
        return xmlText(value).replace("\"", "&quot;");
    }

    private static String xmlText(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }
}
