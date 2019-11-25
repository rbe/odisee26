/*
 * Odisee
 * Copyright (C) 2011-2019 art of coding UG (haftungsbeschränkt).
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann.
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 *
 */

package org.odisee

import org.odisee.io.OdiseePath

class OdiseeBootStrap {

    def grailsApplication

    def init = { servletContext ->
        def version = grailsApplication.config.odisee.version
        println """
           _ \\      |_)               
          |   |  _` | |  __|  _ \\  _ \\
          |   | (   | |\\__ \\  __/  __/
         \\___/ \\__,_|_|____/\\___|\\___| ${version}
         
         Server initialized.
"""
        OdiseePath.dumpEnv()
    }

    def destroy = {
        def version = grailsApplication.config.odisee.version
        println """
           ____      ___              
          / __ \\____/ (_)_______  ___ 
         / / / / __  / / ___/ _ \\/ _ \\
        / /_/ / /_/ / (__  )  __/  __/
        \\____/\\__,_/_/____/\\___/\\___/ ${version}
         
         Server stopped.
"""
    }

}
