package org.odisee.writer

import com.sun.star.beans.PropertyValue
import com.sun.star.beans.XPropertySet
import com.sun.star.container.XNameAccess
import com.sun.star.frame.XComponentLoader
import com.sun.star.frame.XController
import com.sun.star.frame.XDispatchHelper
import com.sun.star.frame.XDispatchProvider
import com.sun.star.frame.XFrame
import com.sun.star.frame.XModel
import com.sun.star.lang.XComponent
import com.sun.star.lang.XMultiComponentFactory
import com.sun.star.text.XAutoTextEntry
import com.sun.star.text.XAutoTextGroup
import com.sun.star.uno.Any
import com.sun.star.uno.Type
import com.sun.star.uno.XComponentContext
import com.sun.star.util.XCloseable
import groovy.test.GroovyTestCase
import org.odisee.ooo.connection.OfficeConnection
import org.odisee.ooo.connection.StubOfficeConnection

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

class OOoComponentStateTest extends GroovyTestCase {

    void testOpenDoesNotMutatePathOrComponentMetaClassAndIsolatesConcurrentCalls() {
        Path dir = Files.createTempDirectory('odisee-b55')
        ExecutorService pool = Executors.newFixedThreadPool(4)
        try {
            Path sharedTemplate = dir.resolve('Shared.ott')
            Files.writeString(sharedTemplate, 'ott')

            assertNull(sharedTemplate.metaClass.hasProperty(sharedTemplate, '_oooConnection'))
            assertNull(sharedTemplate.metaClass.hasProperty(sharedTemplate, 'oooConnection'))

            List<Callable<Boolean>> tasks = (0..<16).collect { int idx ->
                { ->
                    XComponent component = proxy([XComponent, XModel, XCloseable]) { method, args ->
                        if (method.name == 'close' || method.name == 'dispose') return null
                        null
                    }
                    XComponentLoader loader = proxy([XComponentLoader]) { method, args ->
                        method.name == 'loadComponentFromURL' ? component : null
                    }
                    OfficeConnection connection = new StubOfficeConnection(loader, null, '127.0.0.1', 2000 + idx)
                    XComponent opened = OOoDocumentCategory.open(sharedTemplate, connection, [Hidden: Boolean.TRUE])
                    try {
                        assertSame(component, opened)
                        assertSame(connection, OOoDocumentCategory.getOooConnection(opened))
                        assertEquals(sharedTemplate, OOoDocumentCategory.getFile(opened))
                        use(OOoDocumentCategory) {
                            assertSame(connection, opened.oooConnection)
                        }
                        assertNull(sharedTemplate.metaClass.hasProperty(sharedTemplate, '_oooConnection'))
                        assertNull(opened.metaClass.hasProperty(opened, '_oooConnection'))
                        return true
                    } finally {
                        OOoDocumentCategory.close(opened)
                        assertNull(OOoDocumentCategory.getOooConnection(opened))
                        assertNull(OOoDocumentCategory.getFile(opened))
                    }
                } as Callable<Boolean>
            }

            List<Future<Boolean>> futures = pool.invokeAll(tasks)
            futures.each { Future<Boolean> f ->
                assertTrue(f.get())
            }
            assertEquals(0, OOoDocumentCategory.trackedComponentCount())
        } finally {
            pool.shutdownNow()
            dir.toFile().deleteDir()
        }
    }

    void testAutotextResolvesServiceManagerAndDefaultContextFromConnection() {
        XComponentContext context = proxy([XComponentContext]) { method, args -> null }
        XAutoTextEntry expectedEntry = proxy([XAutoTextEntry]) { method, args -> null }
        XAutoTextGroup group = proxy([XAutoTextGroup, XNameAccess]) { method, args ->
            method.name == 'getByName' && args[0] == 'greeting' ? expectedEntry : null
        }
        XNameAccess container = proxy([XNameAccess]) { method, args ->
            method.name == 'getByName' && args[0] == 'Standard' ? new Any(new Type(XAutoTextGroup), group) : null
        }
        List<Object[]> createdServices = []
        XMultiComponentFactory serviceManager = proxy([XMultiComponentFactory, XPropertySet]) { method, args ->
            if (method.name == 'getPropertyValue' && args[0] == 'DefaultContext') {
                return new Any(new Type(XComponentContext), context)
            }
            if (method.name == 'createInstanceWithContext') {
                createdServices << args
                return container
            }
            null
        }
        OfficeConnection connection = new StubOfficeConnection(null, serviceManager)
        XComponent component = proxy([XComponent]) { method, args -> null }
        OOoDocumentCategory.bindConnection(component, connection)
        try {
            XAutoTextEntry actual = OOoAutotextCategory.getAutotext(component, 'Standard', 'greeting')
            assertSame(expectedEntry, actual)
            assertEquals(1, createdServices.size())
            assertEquals('com.sun.star.text.AutoTextContainer', createdServices[0][0])
            assertSame(context, createdServices[0][1])
        } finally {
            OOoDocumentCategory.unbindConnection(component)
        }
    }

    void testExecuteDispatchUsesRemoteServiceManagerAndDefaultContext() {
        XComponentContext context = proxy([XComponentContext]) { method, args -> null }
        List<Object[]> dispatched = []
        XDispatchHelper helper = proxy([XDispatchHelper]) { method, args ->
            if (method.name == 'executeDispatch') {
                dispatched << args
            }
            null
        }
        XMultiComponentFactory serviceManager = proxy([XMultiComponentFactory, XPropertySet]) { method, args ->
            if (method.name == 'getPropertyValue' && args[0] == 'DefaultContext') {
                return context
            }
            if (method.name == 'createInstanceWithContext' && args[0] == 'com.sun.star.frame.DispatchHelper') {
                assertSame(context, args[1])
                return helper
            }
            null
        }
        OfficeConnection connection = new StubOfficeConnection(null, serviceManager)
        XFrame frame = proxy([XFrame, XDispatchProvider]) { method, args -> null }
        XController controller = proxy([XController]) { method, args ->
            method.name == 'getFrame' ? frame : null
        }
        XComponent component = proxy([XComponent, XModel]) { method, args ->
            method.name == 'getCurrentController' ? controller : null
        }
        OOoDocumentCategory.bindConnection(component, connection)
        try {
            OOoDocumentCategory.executeDispatch(component, '.uno:UpdateAll', [Quiet: Boolean.TRUE])
            assertEquals(1, dispatched.size())
            assertSame(frame, dispatched[0][0])
            assertEquals('.uno:UpdateAll', dispatched[0][1])
            PropertyValue[] props = (PropertyValue[]) dispatched[0][4]
            assertEquals(1, props.length)
            assertEquals('Quiet', props[0].Name)
            assertEquals(Boolean.TRUE, props[0].Value)
        } finally {
            OOoDocumentCategory.unbindConnection(component)
        }
    }

    @SuppressWarnings('unchecked')
    private static <T> T proxy(List<Class<?>> interfaces, Closure<?> handler) {
        (T) Proxy.newProxyInstance(
                OOoComponentStateTest.classLoader,
                interfaces as Class[],
                { Object p, java.lang.reflect.Method method, Object[] args ->
                    if (method.declaringClass == Object) {
                        if (method.name == 'toString') return 'UnoProxy'
                        if (method.name == 'hashCode') return System.identityHashCode(p)
                        if (method.name == 'equals') return p.is(args[0])
                    }
                    handler.call(method, args)
                } as InvocationHandler)
    }

}
