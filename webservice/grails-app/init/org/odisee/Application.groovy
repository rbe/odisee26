package org.odisee

import grails.boot.GrailsApp
import grails.boot.config.GrailsAutoConfiguration
import groovy.transform.CompileStatic
import org.odisee.security.OdiseeSecurityConfiguration
import org.springframework.context.annotation.Import

@CompileStatic
@Import(OdiseeSecurityConfiguration)
class Application extends GrailsAutoConfiguration {

    static void main(String[] args) {
        GrailsApp.run(Application, args)
    }

}
