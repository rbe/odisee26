/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 *
 * rbe, 16.03.17, 19:34
 */
package eu.artofcoding.grails.helper

final class NameHelper {

    public static final String S_DOT = ".";

    private NameHelper() {
        throw new AssertionError();
    }

    /**
     * Get name of document w/o extension.
     * @param filename
     * @return
     */
    static String getName(final String filename) {
        final String[] s = filename.split('\\.')
        s[0..s.length - 2].join(S_DOT)
    }

    /**
     * HelloWorld -> Hello_World
     * helloWorld -> hello_World
     */
    static String mapCamelCaseToUnderscore(s) {
        def buf = new StringBuilder()
        s.eachWithIndex { c, i ->
            if (i > 0 && Character.isUpperCase((char) c)) {
                buf << "_"
            }
            buf << c
        }
        buf.charAt(0) == "_" ? buf.substring(1) : buf.toString()
    }

    /**
     * Hello_World -> HelloWorld
     * hello_World -> helloWorld
     */
    static String mapUnderscoreToCamelCase(s) {
        def buf = new StringBuilder()
        s.eachWithIndex { c, i ->
            if (c == "_") {
            } else if (i > 0 && s.charAt(i - 1) == "_") {
                buf << c.toUpperCase()
            } else {
                buf << c
            }
        }
        buf.toString()
    }

    /**
     * Hello-World -> HelloWorld
     * hello-World -> helloWorld
     */
    static String mapDashToCamelCase(s) {
        def buf = new StringBuilder()
        s.eachWithIndex { c, i ->
            if (c == "-") {
            } else if (i > 0 && s.charAt(i - 1) == "-") {
                buf << c.toUpperCase()
            } else {
                buf << c
            }
        }
        buf.toString()
    }

}
