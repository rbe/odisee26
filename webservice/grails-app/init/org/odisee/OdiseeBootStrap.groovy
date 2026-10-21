/*
 * Odisee
 * Copyright (C) 2011-2019 art of coding UG (haftungsbeschränkt).
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann.
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 *
 */

package org.odisee

import grails.config.Config
import groovy.transform.CompileStatic
import org.odisee.io.OdiseePath

class OdiseeBootStrap {

    def grailsApplication

    def init = { servletContext ->
        String version = serverVersion(grailsApplication.config)
        println """
           _ \\      |_)               
          |   |  _` | |  __|  _ \\  _ \\
          |   | (   | |\\__ \\  __/  __/
         \\___/ \\__,_|_|____/\\___|\\___|
         
         Server ${version} initialized.
"""
        OdiseePath.dumpEnv()
    }

    def destroy = {
        String version = serverVersion(grailsApplication.config)
        println """
           ____      ___              
          / __ \\____/ (_)_______  ___ 
         / / / / __  / / ___/ _ \\/ _ \\
        / /_/ / /_/ / (__  )  __/  __/
        \\____/\\__,_/_/____/\\___/\\___/
         
         Server ${version} stopped.
"""
    }

    @CompileStatic
    static String serverVersion(Config config) {
        config.getProperty('odisee.version', String)
    }

}
