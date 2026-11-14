package org.odisee.document

import com.sun.net.httpserver.HttpServer
import groovy.test.GroovyTestCase
import org.odisee.api.OdiseeException

import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

class CallbackSecurityTest extends GroovyTestCase {

    @Override
    protected void setUp() {
        Files.deleteIfExists(CallbackAllowList.location())
    }

    @Override
    protected void tearDown() {
        Files.deleteIfExists(CallbackAllowList.location())
    }

    void testAllowListRejectsLoopbackPrivateLinkLocalAndInternalHosts() {
        [
                '127.0.0.1',
                '0.0.0.0',
                '10.0.0.8',
                '172.16.0.5',
                '192.168.1.1',
                '169.254.169.254',
                '100.64.0.1',
                '0177.0.0.1',
                '127.1',
                '2130706433',
                '[::1]',
                '[fe80::1]',
                '[fc00::1]',
                '[::ffff:127.0.0.1]',
                'localhost',
                'api.localhost',
                'printer.local',
                'keycloak',
                'minio',
        ].each { String forbidden ->
            try {
                CallbackAllowList.add('ada', forbidden)
                fail("accepted ${forbidden}")
            } catch (OdiseeException e) {
                assertEquals(OdiseeException.BAD_REQUEST, e.httpStatus)
            }
        }

        Files.createDirectories(CallbackAllowList.location().parent)
        Files.writeString(CallbackAllowList.location(), [
                'ada 127.0.0.1',
                'ada 10.0.0.8',
                'ada 169.254.169.254',
                'ada localhost',
                'ada keycloak',
                'ada hooks.example',
                'ada 203.0.113.5',
        ].join('\n') + '\n', StandardCharsets.UTF_8)

        assertFalse(CallbackAllowList.permits('ada', 'http://127.0.0.1/done'))
        assertFalse(CallbackAllowList.permits('ada', 'http://10.0.0.8:8080/done'))
        assertFalse(CallbackAllowList.permits('ada', 'http://169.254.169.254/latest/meta-data/'))
        assertFalse(CallbackAllowList.permits('ada', 'http://localhost:8080/done'))
        assertFalse(CallbackAllowList.permits('ada', 'http://keycloak:8080/realms/odisee'))
        assertFalse(CallbackAllowList.permits('ada', 'https://user:pass@hooks.example/done'))
        assertFalse(CallbackAllowList.permits('ada', 'https://hooks.example@203.0.113.5/done'))
        assertTrue(CallbackAllowList.permits('ada', 'https://hooks.example/done'))
        assertTrue(CallbackAllowList.permits('ada', 'http://203.0.113.5/done'))
    }

    void testPosterBlocksPrivateResolvedAddressesAndMixedRoundRobin() {
        List<InetAddress[]> blockedResolutions = [
                [InetAddress.getByAddress('hooks.example', [127, 0, 0, 1] as byte[])] as InetAddress[],
                [InetAddress.getByAddress('hooks.example', [10, 0, 0, 5] as byte[])] as InetAddress[],
                [InetAddress.getByAddress('hooks.example', [(byte) 169, (byte) 254, (byte) 169, (byte) 254] as byte[])] as InetAddress[],
                [InetAddress.getByName('fc00::1')] as InetAddress[],
                [
                        InetAddress.getByAddress('hooks.example', [(byte) 203, 0, 113, 5] as byte[]),
                        InetAddress.getByAddress('hooks.example', [127, 0, 0, 1] as byte[]),
                ] as InetAddress[],
        ]

        blockedResolutions.each { InetAddress[] resolved ->
            CallbackPoster.Resolver resolver = { String host -> resolved } as CallbackPoster.Resolver
            try {
                CallbackPoster.post(URI.create('http://hooks.example/done'), '{"id":"1"}', resolver)
                fail("allowed non-public resolution ${Arrays.asList(resolved)}")
            } catch (OdiseeException e) {
                assertEquals(OdiseeException.BAD_REQUEST, e.httpStatus)
            }
        }
    }

    void testPosterPinsSingleResolutionAgainstDnsRebindingAndIgnoresRedirects() {
        HttpServer trap = HttpServer.create(new InetSocketAddress('127.0.0.1', 0), 0)
        AtomicInteger trapHits = new AtomicInteger()
        trap.createContext('/secret') { exchange ->
            trapHits.incrementAndGet()
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        HttpServer callbackServer = HttpServer.create(new InetSocketAddress('127.0.0.1', 0), 0)
        List<String> seenHosts = []
        List<String> seenBodies = []
        callbackServer.createContext('/done') { exchange ->
            seenHosts << exchange.requestHeaders.getFirst('Host')
            seenBodies << new String(exchange.requestBody.readAllBytes(), StandardCharsets.UTF_8)
            exchange.responseHeaders.set('Location', "http://127.0.0.1:${trap.address.port}/secret")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        trap.start()
        callbackServer.start()
        try {
            AtomicInteger lookups = new AtomicInteger()
            CallbackPoster.Resolver rebindingResolver = new RebindingResolver(lookups)
            URI uri = URI.create("http://hooks.example:${callbackServer.address.port}/done?token=abc")
            CallbackPoster.post(uri, '{"id":"job-1","status":"succeeded"}', rebindingResolver)

            assertEquals(1, lookups.get())
            assertEquals(["hooks.example:${callbackServer.address.port}".toString()], seenHosts)
            assertEquals(['{"id":"job-1","status":"succeeded"}'], seenBodies)
            assertEquals(0, trapHits.get())
        } finally {
            callbackServer.stop(0)
            trap.stop(0)
        }
    }

    private static final class RebindingResolver implements CallbackPoster.Resolver {
        private final AtomicInteger lookups

        RebindingResolver(AtomicInteger lookups) {
            this.lookups = lookups
        }

        @Override
        InetAddress[] resolve(String host) throws IOException {
            int call = lookups.incrementAndGet()
            if (call > 1) {
                throw new AssertionError('Second DNS lookup attempted')
            }
            [InetAddress.getByAddress('hooks.example', [127, 0, 0, 1] as byte[])] as InetAddress[]
        }

        @Override
        boolean isAllowedAddress(InetAddress address) {
            address != null && address.isLoopbackAddress()
        }
    }

}
