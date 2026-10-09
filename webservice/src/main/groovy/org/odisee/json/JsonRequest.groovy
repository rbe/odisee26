/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 */
package org.odisee.json

import groovy.json.JsonSlurper
import groovy.xml.MarkupBuilder
import org.odisee.api.OdiseeException
import org.odisee.xml.XmlHelper
import org.w3c.dom.Element

/**
 * Turns a JSON document request into the XML DOM the generator already walks.
 * XML stays the stored and executed form; JSON is another way to submit the same request.
 */
final class JsonRequest {

    private static final Map<String, String> XML_ATTR = [
            preSaveMacro : 'pre-save-macro',
            postSaveMacro: 'post-save-macro',
            postMacro    : 'post-macro',
            postSetMacro : 'post-macro',
            localDebug   : 'local-debug',
            cellAlign    : 'cell-align',
            cellWidth    : 'cell-width'
    ]

    private static final Set<String> INSTRUCTION_NAMES = [
            'userfield', 'texttable', 'image', 'autotext', 'bookmark', 'macro', 'cell', 'shape'
    ] as Set<String>

    private static final Set<String> ACTION_STEPS = ['result-placeholder', 'input'] as Set<String>

    private static final Collection<String> STRUCTURAL_KEYS = [
            'instruction', 'kind', 'element', 'value', 'parameter', 'content'
    ]

    private JsonRequest() {
    }

    static boolean looksLikeJson(byte[] body) {
        if (body == null) {
            return false
        }
        int i = 0
        if (body.length >= 3 && (body[0] & 0xFF) == 0xEF && (body[1] & 0xFF) == 0xBB && (body[2] & 0xFF) == 0xBF) {
            i = 3
        }
        while (i < body.length) {
            int c = body[i] & 0xFF
            if (c == 0x20 || c == 0x09 || c == 0x0A || c == 0x0D) {
                i++
                continue
            }
            return c == 0x7B
        }
        false
    }

    static Element toElement(InputStream inputStream) {
        toElement(inputStream.getText('UTF-8'))
    }

    static Element toElement(String json) {
        if (!json?.trim()) {
            throw new OdiseeException('Invalid or missing JSON request')
        }
        final Object parsed
        try {
            parsed = new JsonSlurper().parseText(json)
        } catch (Exception e) {
            throw new OdiseeException('Invalid JSON request: ' + e.message, e)
        }
        if (!(parsed instanceof Map)) {
            throw new OdiseeException('JSON request must be an object')
        }
        final String xml = toXml((Map) parsed)
        try {
            final Element element = XmlHelper.asElement(xml)
            if (element == null) {
                throw new OdiseeException('Invalid or missing JSON request')
            }
            return element
        } catch (OdiseeException e) {
            throw e
        } catch (Exception e) {
            throw new OdiseeException('Invalid JSON request', e)
        }
    }

    static String toXml(Map root) {
        final List requests = asList(root.request)
        if (!requests) {
            throw new OdiseeException('JSON request must contain at least one request')
        }
        final StringWriter writer = new StringWriter()
        final MarkupBuilder xml = new MarkupBuilder(writer)
        xml.doubleQuotes = true
        xml.mkp.xmlDeclaration(version: '1.0', encoding: 'UTF-8')
        xml.odisee {
            requests.each { Object req ->
                appendRequest(xml, req)
            }
            if (root.postProcess != null) {
                appendPostProcess(xml, root.postProcess)
            }
            if (root.response != null) {
                appendResponse(xml, root.response)
            }
        }
        writer.toString()
    }

    private static void appendRequest(MarkupBuilder xml, Object req) {
        if (!(req instanceof Map)) {
            throw new OdiseeException('Each request must be an object')
        }
        final Map request = (Map) req
        if (!(request.template instanceof Map) || !((Map) request.template).name) {
            throw new OdiseeException('JSON request is missing template.name')
        }
        xml.request(requestAttrs(request)) {
            if (request.group != null) {
                appendGroup(xml, request.group)
            }
            xml.template(scalarAttrs((Map) request.template, []))
            if (request.archive instanceof Map) {
                xml.archive(scalarAttrs((Map) request.archive, []))
            }
            xml.instructions {
                asList(request.instructions).each { Object item ->
                    appendInstruction(xml, item)
                }
            }
            if (request.postProcess != null) {
                appendPostProcess(xml, request.postProcess)
            }
            if (request.output != null) {
                appendOutput(xml, request.output)
            }
        }
    }

    private static void appendOutput(MarkupBuilder xml, Object output) {
        if (!(output instanceof Map)) {
            throw new OdiseeException('output must be an object')
        }
        final Map map = (Map) output
        final Object format = map.format
        if (!(format instanceof Map)) {
            throw new OdiseeException('output.format must be an object')
        }
        final Map formatMap = (Map) format
        final Map<String, String> attrs = new LinkedHashMap<>()
        if (formatMap.type != null) {
            attrs.put('type', stringify(formatMap.type))
        }
        xml.output {
            xml.format(attrs) {
                final List options = asList(formatMap.options ?: formatMap.option)
                if (options) {
                    xml.options {
                        options.each { Object option ->
                            appendOption(xml, option)
                        }
                    }
                }
            }
        }
    }

    private static void appendOption(MarkupBuilder xml, Object option) {
        if (!(option instanceof Map)) {
            throw new OdiseeException('Each format option must be an object')
        }
        final Map map = (Map) option
        if (!map.name) {
            throw new OdiseeException('A format option needs a name')
        }
        xml.option(name: stringify(map.name), value: stringify(map.value))
    }

    private static void appendGroup(MarkupBuilder xml, Object group) {
        if (group instanceof CharSequence) {
            xml.group(name: group.toString())
        } else if (group instanceof Map) {
            xml.group(scalarAttrs((Map) group, []))
        } else {
            throw new OdiseeException('group must be a name or an object')
        }
    }

    private static void appendInstruction(MarkupBuilder xml, Object item) {
        if (!(item instanceof Map)) {
            throw new OdiseeException('Each instruction must be an object')
        }
        final Map instr = (Map) item
        String name = (instr.instruction ?: instr.kind ?: '') as String
        if (name == 'table') {
            name = 'texttable'
        }
        if (!INSTRUCTION_NAMES.contains(name)) {
            throw new OdiseeException("Unknown instruction '${name}'")
        }
        final Map<String, String> attrs = scalarAttrs(instr, STRUCTURAL_KEYS)
        final List params = asList(instr.parameter)
        if (params) {
            xml."${name}"(attrs) {
                params.each { Object param ->
                    if (param == null) {
                        return
                    }
                    final String paramValue = param instanceof Map ? stringify(((Map) param).value) : stringify(param)
                    xml.parameter([value: paramValue], paramValue)
                }
            }
        } else if (instr.containsKey('value') && instr.value != null) {
            xml."${name}"(attrs, stringify(instr.value))
        } else {
            xml."${name}"(attrs)
        }
    }

    private static void appendPostProcess(MarkupBuilder xml, Object postProcess) {
        if (!(postProcess instanceof Map)) {
            throw new OdiseeException('postProcess must be an object')
        }
        final Map pp = (Map) postProcess
        xml.'post-process' {
            asList(pp.action ?: pp.actions).each { Object action ->
                appendAction(xml, action)
            }
        }
    }

    private static void appendAction(MarkupBuilder xml, Object action) {
        if (!(action instanceof Map)) {
            throw new OdiseeException('Each postProcess action must be an object')
        }
        final Map map = (Map) action
        final String type = map.type == null ? '' : stringify(map.type)
        if (!(type ==~ /[A-Za-z][A-Za-z0-9_-]*/)) {
            throw new OdiseeException("Invalid action type '${type}'")
        }
        xml.action(type: type) {
            asList(map.content).each { Object step ->
                appendActionStep(xml, step)
            }
        }
    }

    private static void appendActionStep(MarkupBuilder xml, Object step) {
        if (!(step instanceof Map)) {
            throw new OdiseeException('Each action step must be an object')
        }
        final Map map = (Map) step
        final String element = (map.element ?: map.instruction ?: '') as String
        if (!ACTION_STEPS.contains(element)) {
            throw new OdiseeException("Unknown action step '${element}'")
        }
        final Map<String, String> attrs = scalarAttrs(map, STRUCTURAL_KEYS)
        if (map.containsKey('value') && map.value != null) {
            xml."${element}"(attrs, stringify(map.value))
        } else {
            xml."${element}"(attrs)
        }
    }

    private static void appendResponse(MarkupBuilder xml, Object response) {
        if (!(response instanceof Map)) {
            throw new OdiseeException('response must be an object')
        }
        final Object encoded = ((Map) response).base64
        final String text = encoded == null ? 'false' : stringify(encoded)
        xml.response {
            'base64'(text)
        }
    }

    private static Map<String, String> requestAttrs(Map request) {
        final Map<String, String> attrs = new LinkedHashMap<>()
        ['name', 'id', 'localDebug'].each { String key ->
            final Object value = request[key]
            if (value != null && !(value instanceof Map) && !(value instanceof Collection)) {
                attrs.put(xmlAttr(key), stringify(value))
            }
        }
        attrs
    }

    private static Map<String, String> scalarAttrs(Map source, Collection<String> skip) {
        final Map<String, String> attrs = new LinkedHashMap<>()
        source.each { Object key, Object value ->
            if (value == null || value instanceof Map || value instanceof Collection) {
                return
            }
            final String name = key.toString()
            if (skip.contains(name)) {
                return
            }
            attrs.put(xmlAttr(name), stringify(value))
        }
        attrs
    }

    private static String xmlAttr(String key) {
        XML_ATTR[key] ?: key
    }

    private static String stringify(Object value) {
        if (value == null) {
            return ''
        }
        if (value instanceof Boolean) {
            return ((Boolean) value) ? 'true' : 'false'
        }
        if (value instanceof BigDecimal && ((BigDecimal) value).scale() <= 0) {
            return ((BigDecimal) value).toBigInteger().toString()
        }
        value.toString()
    }

    private static List asList(Object value) {
        if (value == null) {
            return []
        }
        if (value instanceof List) {
            return (List) value
        }
        [value]
    }

}
