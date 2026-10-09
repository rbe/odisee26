/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 */
package org.odisee.document

/**
 * {@code template/@outputFormat} selects the extension when {@code output} is absent.
 * When {@code output/format/@type} is present, that type is the extension.
 * {@code format/options/option} is a list of name/value pairs.
 */
final class OutputFormats {

    private OutputFormats() {
    }

    static final class Choice {
        final String extension
        final List<Map<String, String>> options

        Choice(String extension, List<Map<String, String>> options) {
            this.extension = extension
            this.options = options
        }
    }

    static List<Choice> fromRequest(request) {
        def format = request?.output?.format
        String type = textOf(format?.'@type')
        if (type) {
            return [new Choice(type, readOptions(format))]
        }
        List<Choice> choices = []
        def template = request?.template ? request.template[0] : null
        splitAttribute(textOf(template?.'@outputFormat')).each { String extension ->
            choices << new Choice(extension, [])
        }
        choices
    }

    static List<String> extensions(request) {
        fromRequest(request)*.extension
    }

    private static List<Map<String, String>> readOptions(format) {
        List<Map<String, String>> options = []
        format?.options?.option?.each { option ->
            String name = textOf(option?.'@name')
            if (name) {
                options << [name: name, value: textOf(option?.'@value')]
            }
        }
        options
    }

    private static List<String> splitAttribute(String attribute) {
        if (!attribute) {
            return []
        }
        attribute.split(',').collect { it.trim() }.findAll { it }
    }

    private static String textOf(value) {
        if (value == null) {
            return ''
        }
        value.toString().trim()
    }

}
