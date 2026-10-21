package org.odisee

import groovy.test.GroovyTestCase

import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.NodeList

/**
 * The production profile hides Grails bean-override notices and leaves overriding on.
 */
class ProductionLogTest extends GroovyTestCase {

    private static final String BEAN_FACTORY = 'org.springframework.beans.factory.support.DefaultListableBeanFactory'

    void testProductionBeanFactoryIsWarn() {
        Element config = document()
        List<Element> profiles = children(config, 'springProfile').findAll { it.getAttribute('name') == 'production' }
        assertEquals(1, profiles.size())
        List<Element> loggers = children(profiles[0], 'logger').findAll { it.getAttribute('name') == BEAN_FACTORY }
        assertEquals(1, loggers.size())
        assertEquals('WARN', loggers[0].getAttribute('level'))
        assertEquals('INFO', children(config, 'root')[0].getAttribute('level'))
    }

    void testBeanFactoryLevelIsNotGlobal() {
        List<Element> outside = children(document(), 'logger').findAll { it.getAttribute('name') == BEAN_FACTORY }
        assertEquals(0, outside.size())
    }

    void testSpringFrameworkLevelIsUnchanged() {
        eachLogger { Element logger ->
            assertFalse(logger.getAttribute('name'), logger.getAttribute('name') == 'org.springframework')
        }
    }

    void testBeanOverrideStaysEnabled() {
        String yml = new File('grails-app/conf/application.yml').getText('UTF-8')
        String logging = logback().getText('UTF-8')
        assertFalse(yml.contains('allow-bean-definition-overriding'))
        assertFalse(logging.contains('allow-bean-definition-overriding'))
    }

    private static void eachLogger(Closure body) {
        Element config = document()
        children(config, 'logger').each(body)
        children(config, 'springProfile').each { Element profile ->
            children(profile, 'logger').each(body)
        }
    }

    private static List<Element> children(Element parent, String name) {
        NodeList nodes = parent.getElementsByTagName(name)
        List<Element> found = []
        for (int i = 0; i < nodes.length; i++) {
            Element element = (Element) nodes.item(i)
            if (element.parentNode.isSameNode(parent)) {
                found << element
            }
        }
        found
    }

    private static Element document() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance()
        factory.setNamespaceAware(false)
        factory.newDocumentBuilder().parse(logback()).documentElement
    }

    private static File logback() {
        new File('grails-app/conf/logback-spring.xml')
    }

}
