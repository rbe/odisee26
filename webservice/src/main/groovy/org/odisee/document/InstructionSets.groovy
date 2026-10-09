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
 * One instruction set per application. A set can grow without adding the tag to the others.
 * There is no shared {@code named} tag.
 */
final class InstructionSets {

    private static final Map<OfficeDocumentType, LinkedHashSet<String>> SETS = new EnumMap<>(OfficeDocumentType)

    static {
        register(OfficeDocumentType.TEXT, 'Userfield', 'Texttable', 'Image', 'Autotext', 'Bookmark', 'Macro')
        register(OfficeDocumentType.SPREADSHEET, 'Cell')
        register(OfficeDocumentType.PRESENTATION, 'Shape')
    }

    private InstructionSets() {
    }

    static void register(OfficeDocumentType type, String... methods) {
        LinkedHashSet<String> set = SETS.computeIfAbsent(type, { new LinkedHashSet<>() })
        methods.each { String method ->
            if (method) {
                set.add(method)
            }
        }
    }

    static void unregister(OfficeDocumentType type, String method) {
        SETS.get(type)?.remove(method)
    }

    static boolean accepts(OfficeDocumentType type, String method) {
        method != null && SETS.get(type)?.contains(method)
    }

    static List<String> names(OfficeDocumentType type) {
        List<String> copy = []
        SETS.get(type)?.each { copy << it }
        copy
    }

}
