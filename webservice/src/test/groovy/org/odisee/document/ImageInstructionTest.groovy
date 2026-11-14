package org.odisee.document

import com.sun.star.beans.XPropertySet
import com.sun.star.container.XEnumerationAccess
import com.sun.star.container.XNameAccess
import com.sun.star.frame.XComponentLoader
import com.sun.star.frame.XStorable
import com.sun.star.lang.XComponent
import com.sun.star.lang.XMultiServiceFactory
import com.sun.star.text.XBookmarksSupplier
import com.sun.star.text.XText
import com.sun.star.text.XTextContent
import com.sun.star.text.XTextCursor
import com.sun.star.text.XTextDocument
import com.sun.star.text.XTextFieldsSupplier
import com.sun.star.text.XTextRange
import com.sun.star.util.XRefreshable
import groovy.test.GroovyTestCase
import groovy.xml.XmlSlurper
import org.odisee.api.OdiseeException
import org.odisee.ooo.connection.OfficeConnection
import org.odisee.ooo.connection.OfficeConnectionFactory
import org.odisee.ooo.connection.StubOfficeConnection
import org.odisee.writer.OOoImageCategory

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.net.InetAddress
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider

class ImageInstructionTest extends GroovyTestCase {

    private static final String ONE_BY_ONE_PNG = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII='

    private Closure<InetAddress[]> originalResolver

    @Override
    protected void setUp() {
        originalResolver = OOoImageCategory.hostResolver
    }

    @Override
    protected void tearDown() {
        OOoImageCategory.hostResolver = originalResolver
    }

    void testFileUrlAndNonHttpSchemesAreRejectedWith422() {
        [
                'file:///etc/passwd',
                'file://image/Odisee_Logo.png',
                'ftp://93.184.216.34/logo.png',
                'jar:file:///tmp/a.jar!/logo.png',
                'data:image/png;base64,' + ONE_BY_ONE_PNG,
                'private:factory/swriter',
                '/etc/passwd',
        ].each { String forbidden ->
            assertDisallowedUrl(forbidden)
        }
    }

    void testInternalAndLoopbackImageUrlsAreRejectedWith422() {
        OOoImageCategory.hostResolver = { String host ->
            if (host == 'rebind.example') {
                return [InetAddress.getByName('10.0.0.5')] as InetAddress[]
            }
            InetAddress.getAllByName(host)
        }
        [
                'http://localhost:8080/logo.png',
                'http://sub.localhost/logo.png',
                'http://minio:9000/odisee/logo.png',
                'http://keycloak:8080/realms/odisee/logo.png',
                'http://127.0.0.1:8080/logo.png',
                'http://[::1]:8080/logo.png',
                'http://[::ffff:127.0.0.1]/logo.png',
                'http://10.0.0.8/logo.png',
                'http://172.16.4.2/logo.png',
                'http://192.168.1.10/logo.png',
                'http://169.254.169.254/latest/meta-data',
                'http://100.64.0.1/logo.png',
                'http://0.0.0.0/logo.png',
                'http://[fc00::1]/logo.png',
                'http://[fe80::1]/logo.png',
                'http://0177.0.0.1/logo.png',
                'http://2130706433/logo.png',
                'https://rebind.example/logo.png',
        ].each { String internalUrl ->
            assertDisallowedUrl(internalUrl)
        }
    }

    void testPublicHttpsImageUrlIsAccepted() {
        OOoImageCategory.hostResolver = { String host ->
            if (host == 'cdn.example.org') {
                return [InetAddress.getByName('93.184.216.34')] as InetAddress[]
            }
            InetAddress.getAllByName(host)
        }
        Map<String, Object> graphicProps = [:]
        XComponent component = graphicComponent('LogoBookmark', graphicProps)
        def image = new XmlSlurper().parseText('<image type="image/png" bookmark="LogoBookmark" url="https://cdn.example.org/logo.png" width="500" height="300"/>')

        OdiseeXmlCategory.processImage(component, [:], image)

        assertEquals('https://cdn.example.org/logo.png', graphicProps.GraphicURL)
        assertEquals(500, graphicProps.Width)
        assertEquals(300, graphicProps.Height)
    }

    void testUnsupportedImageMimeTypeFailsWith422() {
        Path dir = Files.createTempDirectory('odisee-img-mime')
        try {
            ['image/svg+xml', 'application/pdf', 'image/jg', ''].each { String badType ->
                def image = new XmlSlurper().parseText("<image type=\"${badType}\" bookmark=\"Logo\">${ONE_BY_ONE_PNG}</image>")
                try {
                    OdiseeXmlCategory.processImage(graphicComponent('Logo', [:]), [outputPath: dir.toString()], image)
                    fail("Expected unsupported image type '${badType}' to fail")
                } catch (OdiseeException e) {
                    assertEquals(OdiseeException.UNPROCESSABLE, e.httpStatus)
                }
            }
        } finally {
            dir.toFile().deleteDir()
        }
    }

    void testRestrictTempPermissionsIgnoresNonPosixFilesystem() {
        FileSystemProvider nonPosixProvider = new FileSystemProvider() {
            @Override String getScheme() { 'nonposix' }
            @Override FileSystem newFileSystem(URI uri, Map<String, ?> env) { throw new UnsupportedOperationException() }
            @Override FileSystem getFileSystem(URI uri) { throw new UnsupportedOperationException() }
            @Override Path getPath(URI uri) { throw new UnsupportedOperationException() }
            @Override java.nio.channels.SeekableByteChannel newByteChannel(Path path, Set<? extends java.nio.file.OpenOption> options, java.nio.file.attribute.FileAttribute<?>... attrs) { throw new UnsupportedOperationException() }
            @Override java.nio.file.DirectoryStream<Path> newDirectoryStream(Path dir, java.nio.file.DirectoryStream.Filter<? super Path> filter) { throw new UnsupportedOperationException() }
            @Override void createDirectory(Path dir, java.nio.file.attribute.FileAttribute<?>... attrs) { throw new UnsupportedOperationException() }
            @Override void delete(Path path) { throw new UnsupportedOperationException() }
            @Override void copy(Path source, Path target, java.nio.file.CopyOption... options) { throw new UnsupportedOperationException() }
            @Override void move(Path source, Path target, java.nio.file.CopyOption... options) { throw new UnsupportedOperationException() }
            @Override boolean isSameFile(Path path, Path path2) { false }
            @Override boolean isHidden(Path path) { false }
            @Override java.nio.file.FileStore getFileStore(Path path) { throw new UnsupportedOperationException() }
            @Override void checkAccess(Path path, java.nio.file.AccessMode... modes) { throw new UnsupportedOperationException() }
            @Override <V extends java.nio.file.attribute.FileAttributeView> V getFileAttributeView(Path path, Class<V> type, java.nio.file.LinkOption... options) { null }
            @Override <A extends java.nio.file.attribute.BasicFileAttributes> A readAttributes(Path path, Class<A> type, java.nio.file.LinkOption... options) { throw new UnsupportedOperationException() }
            @Override Map<String, Object> readAttributes(Path path, String attributes, java.nio.file.LinkOption... options) { throw new UnsupportedOperationException() }
            @Override void setAttribute(Path path, String attribute, Object value, java.nio.file.LinkOption... options) { throw new UnsupportedOperationException() }
        }
        FileSystem nonPosixFs = new FileSystem() {
            @Override FileSystemProvider provider() { nonPosixProvider }
            @Override void close() {}
            @Override boolean isOpen() { true }
            @Override boolean isReadOnly() { false }
            @Override String getSeparator() { '/' }
            @Override Iterable<Path> getRootDirectories() { [] }
            @Override Iterable<java.nio.file.FileStore> getFileStores() { [] }
            @Override Set<String> supportedFileAttributeViews() { ['basic'] as Set }
            @Override Path getPath(String first, String... more) { null }
            @Override java.nio.file.PathMatcher getPathMatcher(String syntaxAndPattern) { null }
            @Override java.nio.file.attribute.UserPrincipalLookupService getUserPrincipalLookupService() { null }
            @Override java.nio.file.WatchService newWatchService() { null }
        }
        Path nonPosixPath = (Path) Proxy.newProxyInstance(
                Path.classLoader,
                [Path] as Class[],
                { Object p, java.lang.reflect.Method method, Object[] args ->
                    method.name == 'getFileSystem' ? nonPosixFs : null
                } as InvocationHandler)

        OdiseeXmlCategory.restrictTempPermissions(nonPosixPath)
    }

    void testTemporaryDecodedImageFilesAreCleanedUpOnSuccessAndFailure() {
        Path dir = Files.createTempDirectory('odisee-img-cleanup')
        try {
            Map<String, Object> props = [:]
            XComponent directComponent = graphicComponent('Logo', props)
            def directXml = new XmlSlurper().parseText("<image type=\"image/png\" bookmark=\"Logo\">${ONE_BY_ONE_PNG}</image>")

            OdiseeXmlCategory.processImage(directComponent, [outputPath: dir.toString()], directXml)

            assertEquals([], tempImageFiles(dir))

            // Verify inside processTemplate that the temp image exists while saveAs runs, and is deleted afterwards.
            Path templateFile = dir.resolve('ImageTemplate.ott')
            Files.writeString(templateFile, 'ott')
            List<Boolean> existedDuringSave = []
            XComponent templateComponent = graphicComponent('Logo', props) {
                Path graphicPath = Path.of(URI.create(props.GraphicURL as String))
                existedDuringSave << Files.isRegularFile(graphicPath)
            }
            OfficeConnection connection = loaderConnection(templateComponent)
            OfficeConnectionFactory factory = OfficeConnectionFactory.forTest([connection])
            def requestXml = new XmlSlurper().parseText("""
                <odisee>
                  <request name="WithImage">
                    <template name="ImageTemplate" outputFormat="pdf" outputPath="${dir}"/>
                    <instructions>
                      <image type="image/png" bookmark="Logo">${ONE_BY_ONE_PNG}</image>
                    </instructions>
                  </request>
                </odisee>
            """)

            OdiseeXmlCategory.processTemplate([
                    xml               : requestXml,
                    activeRequestIndex: 0,
                    id                : '1',
                    template          : templateFile
            ], connection, factory)

            assertEquals([true], existedDuringSave)
            assertEquals([], tempImageFiles(dir))

            // Verify temp image is also cleaned up when a later instruction fails.
            def failingXml = new XmlSlurper().parseText("""
                <odisee>
                  <request name="WithImageFailure">
                    <template name="ImageTemplate" outputFormat="pdf" outputPath="${dir}"/>
                    <instructions>
                      <image type="image/png" bookmark="Logo">${ONE_BY_ONE_PNG}</image>
                      <image type="image/png" bookmark="MissingBookmark">${ONE_BY_ONE_PNG}</image>
                    </instructions>
                  </request>
                </odisee>
            """)
            try {
                OdiseeXmlCategory.processTemplate([
                        xml               : failingXml,
                        activeRequestIndex: 0,
                        id                : '2',
                        template          : templateFile
                ], connection, factory)
                fail('Expected missing bookmark to fail with 422')
            } catch (OdiseeException e) {
                assertEquals(OdiseeException.UNPROCESSABLE, e.httpStatus)
            }
            assertEquals([], tempImageFiles(dir))
        } finally {
            dir.toFile().deleteDir()
        }
    }

    private void assertDisallowedUrl(String url) {
        try {
            OOoImageCategory.requireSafeImageUrl(url)
            fail("Expected URL '${url}' to be rejected")
        } catch (OdiseeException e) {
            assertEquals(url, OdiseeException.UNPROCESSABLE, e.httpStatus)
        }
    }

    private static List<String> tempImageFiles(Path dir) {
        Files.list(dir).withCloseable { stream ->
            stream.map { it.fileName.toString() }
                    .filter { it.startsWith('image') }
                    .toList()
        }
    }

    private static OfficeConnection loaderConnection(XComponent opened) {
        XComponentLoader loader = proxy([XComponentLoader]) { method, args ->
            method.name == 'loadComponentFromURL' ? opened : null
        }
        new StubOfficeConnection(loader, null)
    }

    private static XComponent graphicComponent(String bookmarkName, Map<String, Object> graphicProps, Closure<?> onSave = null) {
        XTextRange anchor = proxy([XTextRange]) { method, args -> null }
        XTextContent bookmark = proxy([XTextContent]) { method, args ->
            method.name == 'getAnchor' ? anchor : null
        }
        XNameAccess bookmarks = proxy([XNameAccess]) { method, args ->
            if (method.name == 'getByName') {
                if (args[0] == bookmarkName) return bookmark
                throw new com.sun.star.container.NoSuchElementException(args[0] as String)
            }
            null
        }
        Object graphicObject = proxy([XTextContent, XPropertySet]) { method, args ->
            if (method.name == 'setPropertyValue') {
                graphicProps[args[0] as String] = args[1]
            }
            null
        }
        XTextCursor cursor = proxy([XTextCursor]) { method, args -> null }
        XText text = proxy([XText]) { method, args ->
            method.name == 'createTextCursor' ? cursor : null
        }
        Object refreshable = proxy([XEnumerationAccess, XRefreshable]) { method, args -> null }
        proxy([XComponent, XTextDocument, XBookmarksSupplier, XMultiServiceFactory, XTextFieldsSupplier, XStorable]) { method, args ->
            if (method.name == 'getBookmarks') return bookmarks
            if (method.name == 'createInstance' && args[0] == 'com.sun.star.text.TextGraphicObject') return graphicObject
            if (method.name == 'getText') return text
            if (method.name == 'getTextFields') return refreshable
            if (method.name == 'storeToURL') {
                onSave?.call()
                return null
            }
            null
        }
    }

    @SuppressWarnings('unchecked')
    private static <T> T proxy(List<Class<?>> interfaces, Closure<?> handler) {
        (T) Proxy.newProxyInstance(
                ImageInstructionTest.classLoader,
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
