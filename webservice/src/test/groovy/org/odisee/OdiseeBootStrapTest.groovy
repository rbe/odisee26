package org.odisee

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import grails.config.Config
import groovy.test.GroovyTestCase
import org.grails.config.NavigableMap
import org.grails.config.PropertySourcesConfig
import org.slf4j.LoggerFactory

/**
 * Startup and shutdown banners read {@code odisee.version} without NavigableMap dot access.
 */
class OdiseeBootStrapTest extends GroovyTestCase {

    void testBannerPrintsConfiguredVersionWithoutDotWarning() {
        Config config = new PropertySourcesConfig([odisee: [version: '2.6.0']])
        List<String> warnings = []
        String out = captureStdout {
            dotWarnings(warnings) {
                OdiseeBootStrap bootstrap = new OdiseeBootStrap(grailsApplication: [config: config])
                bootstrap.init(null)
                bootstrap.destroy()
            }
        }

        assertTrue(out.contains('Server 2.6.0 initialized.'))
        assertTrue(out.contains('Server 2.6.0 stopped.'))
        assertEquals('2.6.0', config.odisee.version.toString())
        assertEquals(config.odisee.version.toString(), OdiseeBootStrap.serverVersion(config))
        assertEquals([], warnings)
    }

    void testFlatKeyMatchesNestedDotValue() {
        Config config = new PropertySourcesConfig(['odisee.version': '2.6.0-9'])
        List<String> warnings = []
        dotWarnings(warnings) {
            assertEquals('2.6.0-9', OdiseeBootStrap.serverVersion(config))
        }
        assertEquals('2.6.0-9', config.odisee.version.toString())
        assertEquals([], warnings)
    }

    void testMissingVersionDoesNotWalkOdisee() {
        Config config = new PropertySourcesConfig([:])
        List<String> warnings = []
        String out = captureStdout {
            dotWarnings(warnings) {
                OdiseeBootStrap bootstrap = new OdiseeBootStrap(grailsApplication: [config: config])
                bootstrap.init(null)
                bootstrap.destroy()
            }
        }

        assertNull(OdiseeBootStrap.serverVersion(config))
        assertTrue(out.contains('Server null initialized.'))
        assertTrue(out.contains('Server null stopped.'))
        assertEquals([], warnings)
    }

    void testDotAccessOfMissingVersionWarns() {
        Config config = new PropertySourcesConfig([:])
        List<String> warnings = []
        dotWarnings(warnings) {
            config.odisee.version
        }

        assertTrue(warnings.any { it.contains('[odisee]') })
        assertTrue(warnings.any { it.contains('[odisee, version]') })
    }

    private static String captureStdout(Closure body) {
        PrintStream previous = System.out
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()
        System.out = new PrintStream(bytes, true, 'UTF-8')
        try {
            body.call()
            return bytes.toString('UTF-8')
        } finally {
            System.out = previous
        }
    }

    private static void dotWarnings(List<String> warnings, Closure body) {
        Logger logger = (Logger) LoggerFactory.getLogger(NavigableMap)
        Level previous = logger.getLevel()
        ListAppender<ILoggingEvent> appender = new ListAppender<>()
        appender.start()
        logger.addAppender(appender)
        logger.setLevel(Level.WARN)
        try {
            body.call()
        } finally {
            logger.detachAppender(appender)
            logger.setLevel(previous)
        }
        appender.list.each { ILoggingEvent event ->
            if (event.formattedMessage.contains('dot notation')) {
                warnings << event.formattedMessage
            }
        }
    }

}
