/*
 * odisee-client-java
 * Copyright (C) 2011-2013 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Alle Rechte vorbehalten. Nutzung unterliegt Lizenzbedingungen.
 * All rights reserved. Use is subject to license terms.
 */

package org.odisee.client;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Converts an Odisee XML request into the JSON document the service accepts.
 */
public final class OdiseeJson {

    private OdiseeJson() {
        throw new AssertionError();
    }

    public static String fromXml(final String xml) {
        final Document document = parse(xml);
        final Element root = document.getDocumentElement();
        if (root == null || !"odisee".equals(localName(root))) {
            throw new OdiseeClientException("XML request root must be odisee");
        }
        final List<Object> requests = new ArrayList<>();
        Map<String, Object> postProcess = null;
        Map<String, Object> response = null;
        for (Element child : elements(root)) {
            switch (localName(child)) {
                case "request":
                    requests.add(request(child));
                    break;
                case "post-process":
                    postProcess = postProcess(child);
                    break;
                case "response":
                    response = response(child);
                    break;
                default:
                    break;
            }
        }
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("request", requests);
        if (postProcess != null) {
            body.put("postProcess", postProcess);
        }
        if (response != null) {
            body.put("response", response);
        }
        final String delivery = root.getAttribute("delivery");
        if (delivery != null && !delivery.isEmpty()) {
            body.put("delivery", delivery);
        }
        return write(body);
    }

    private static Map<String, Object> request(final Element element) {
        final Map<String, Object> map = new LinkedHashMap<>();
        putAttributes(map, element);
        final List<Object> instructions = new ArrayList<>();
        boolean sawInstructions = false;
        Map<String, Object> requestPostProcess = null;
        for (Element child : elements(element)) {
            switch (localName(child)) {
                case "template":
                case "archive":
                case "group":
                    map.put(localName(child), attributes(child));
                    break;
                case "instructions":
                    sawInstructions = true;
                    for (Element instruction : elements(child)) {
                        instructions.add(instruction(instruction));
                    }
                    break;
                case "post-process":
                    requestPostProcess = postProcess(child);
                    break;
                default:
                    break;
            }
        }
        if (sawInstructions) {
            map.put("instructions", instructions);
        }
        if (requestPostProcess != null) {
            map.put("postProcess", requestPostProcess);
        }
        return map;
    }

    private static Map<String, Object> instruction(final Element element) {
        final Map<String, Object> map = new LinkedHashMap<>();
        final String name = localName(element);
        map.put("instruction", name);
        putAttributes(map, element);
        if ("macro".equals(name)) {
            final List<Object> parameters = new ArrayList<>();
            for (Element child : elements(element)) {
                if (!"parameter".equals(localName(child))) {
                    continue;
                }
                final Map<String, Object> parameter = new LinkedHashMap<>();
                final String attribute = child.getAttribute("value");
                final String text = directText(child);
                parameter.put("value", attribute != null && !attribute.isEmpty() ? attribute : text);
                parameters.add(parameter);
            }
            if (!parameters.isEmpty()) {
                map.put("parameter", parameters);
            }
        } else {
            final String text = directText(element);
            if (!text.isEmpty()) {
                map.put("value", text);
            }
        }
        return map;
    }

    private static Map<String, Object> postProcess(final Element element) {
        final List<Object> actions = new ArrayList<>();
        collectActions(element, actions);
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("action", actions);
        return map;
    }

    private static void collectActions(final Element element, final List<Object> actions) {
        for (Element child : elements(element)) {
            final String name = localName(child);
            if ("action".equals(name)) {
                actions.add(action(child));
            } else if ("instructions".equals(name)) {
                collectActions(child, actions);
            }
        }
    }

    private static Map<String, Object> action(final Element element) {
        final Map<String, Object> map = new LinkedHashMap<>();
        putAttributes(map, element);
        final List<Object> content = new ArrayList<>();
        for (Element child : elements(element)) {
            final Map<String, Object> step = new LinkedHashMap<>();
            final String name = localName(child);
            step.put("element", name);
            putAttributes(step, child);
            if ("input".equals(name) && step.containsKey("file") && !step.containsKey("filename")) {
                step.put("filename", step.remove("file"));
            }
            content.add(step);
        }
        if (!content.isEmpty()) {
            map.put("content", content);
        }
        return map;
    }

    private static Map<String, Object> response(final Element element) {
        final Map<String, Object> map = new LinkedHashMap<>();
        for (Element child : elements(element)) {
            if ("base64".equals(localName(child))) {
                map.put("base64", jsonScalar("base64", directText(child)));
            }
        }
        return map;
    }

    private static Map<String, Object> attributes(final Element element) {
        final Map<String, Object> map = new LinkedHashMap<>();
        putAttributes(map, element);
        return map;
    }

    private static void putAttributes(final Map<String, Object> target, final Element element) {
        final Map<String, Object> sorted = new TreeMap<>();
        final NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            final Node attribute = attributes.item(i);
            String name = attribute.getNodeName();
            final int colon = name.indexOf(':');
            if (colon >= 0) {
                name = name.substring(colon + 1);
            }
            if (name.startsWith("xmlns") || "schemaLocation".equals(name)) {
                continue;
            }
            sorted.put(jsonKey(name), jsonScalar(name, attribute.getNodeValue()));
        }
        target.putAll(sorted);
    }

    static String jsonKey(final String xmlAttr) {
        switch (xmlAttr) {
            case "pre-save-macro":
                return "preSaveMacro";
            case "post-save-macro":
                return "postSaveMacro";
            case "post-macro":
            case "post-set-macro":
                return "postMacro";
            case "local-debug":
                return "localDebug";
            case "cell-align":
                return "cellAlign";
            case "cell-width":
                return "cellWidth";
            default:
                return xmlAttr;
        }
    }

    private static Object jsonScalar(final String xmlName, final String raw) {
        if ("files".equals(xmlName) || "database".equals(xmlName) || "atend".equals(xmlName)
                || "local-debug".equals(xmlName) || "base64".equals(xmlName)) {
            if ("true".equalsIgnoreCase(raw)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(raw)) {
                return Boolean.FALSE;
            }
        }
        if (("width".equals(xmlName) || "height".equals(xmlName)) && raw.matches("-?\\d+")) {
            try {
                return Integer.valueOf(raw);
            } catch (NumberFormatException ignored) {
                return raw;
            }
        }
        return raw;
    }

    private static List<Element> elements(final Element parent) {
        final List<Element> list = new ArrayList<>();
        final NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            final Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                list.add((Element) node);
            }
        }
        return list;
    }

    private static String directText(final Element element) {
        final StringBuilder sb = new StringBuilder();
        final NodeList nodes = element.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            final Node node = nodes.item(i);
            if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
                sb.append(node.getNodeValue());
            }
        }
        int start = 0;
        int end = sb.length();
        while (start < end && Character.isWhitespace(sb.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(sb.charAt(end - 1))) {
            end--;
        }
        return sb.substring(start, end);
    }

    private static String localName(final Element element) {
        final String local = element.getLocalName();
        if (local != null && !local.isEmpty()) {
            return local;
        }
        final String name = element.getNodeName();
        final int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }

    private static Document parse(final String xml) {
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setExpandEntityReferences(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            try {
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            } catch (ParserConfigurationException ignored) {
                // Parser does not advertise the feature; secure processing stays on.
            }
            final DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(xml)));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new OdiseeClientException("Cannot read XML request", e);
        }
    }

    static String write(final Object value) {
        final StringBuilder sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    private static void write(final StringBuilder sb, final Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            sb.append(quote((String) value));
        } else if (value instanceof Boolean || value instanceof Integer || value instanceof Long) {
            sb.append(value.toString());
        } else if (value instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(quote(String.valueOf(entry.getKey())));
                sb.append(':');
                write(sb, entry.getValue());
            }
            sb.append('}');
        } else if (value instanceof List) {
            sb.append('[');
            boolean first = true;
            for (Object item : (List<?>) value) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                write(sb, item);
            }
            sb.append(']');
        } else {
            sb.append(quote(String.valueOf(value)));
        }
    }

    private static String quote(final String value) {
        final StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            final char ch = value.charAt(i);
            switch (ch) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                    break;
            }
        }
        sb.append('"');
        return sb.toString();
    }

}
