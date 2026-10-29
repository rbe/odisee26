package org.odisee

import groovy.test.GroovyTestCase

/**
 * Views, codecs, the database plugin, and respond() stay off the classpath.
 * JSON, XML, domain classes, messages, and the document interceptor stay on it.
 */
class GrailsPluginClasspathTest extends GroovyTestCase {

    void testRemovedPluginsAreAbsent() {
        assertNull(resource('org/grails/plugins/web/GroovyPagesGrailsPlugin.class'))
        assertNull(resource('org/apache/grails/web/layout/LayoutGrailsPlugin.class'))
        assertNull(resource('org/grails/plugins/codecs/CodecsGrailsPlugin.class'))
        assertNull(resource('org/grails/plugins/datasource/DataSourceGrailsPlugin.class'))
        assertNull(resource('org/grails/plugins/web/rest/plugin/RestResponderGrailsPlugin.class'))
    }

    void testRequestPathPluginsStay() {
        assertNotNull(resource('org/grails/plugins/databinding/DataBindingGrailsPlugin.class'))
        assertNotNull(resource('org/grails/plugins/i18n/I18nGrailsPlugin.class'))
        assertNotNull(resource('org/grails/plugins/converters/ConvertersGrailsPlugin.class'))
        assertNotNull(resource('org/grails/plugins/domain/DomainClassGrailsPlugin.class'))
        assertNotNull(resource('org/grails/plugins/web/interceptors/InterceptorsGrailsPlugin.class'))
        assertNotNull(resource('org/grails/plugins/web/mime/MimeTypesGrailsPlugin.class'))
        assertNotNull(resource('org/grails/plugins/services/ServicesGrailsPlugin.class'))
        assertNotNull(resource('org/grails/plugins/web/controllers/ControllersGrailsPlugin.class'))
        assertNotNull(resource('org/grails/plugins/web/mapping/UrlMappingsGrailsPlugin.class'))
    }

    private URL resource(String path) {
        getClass().classLoader.getResource(path)
    }

}
