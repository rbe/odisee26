/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 *
 * rbe, 02.02.15, 18:35
 */

package eu.artofcoding.grails.helper

final class WallTime {

    private long startMilliseconds

    private long stopMilliseconds

    def start() {
        startMilliseconds = System.currentTimeMillis()
    }

    def stop() {
        stopMilliseconds = System.currentTimeMillis()
    }

    long diff() {
        stopMilliseconds - startMilliseconds
    }

}
