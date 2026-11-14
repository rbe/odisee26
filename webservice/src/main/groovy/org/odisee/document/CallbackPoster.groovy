/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 */
package org.odisee.document

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.odisee.api.OdiseeException

import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * POSTs a job status to a callback URL. Callers check the allow-list first.
 * DNS is resolved once, validated as a public address, and pinned to the socket.
 * Redirects are not followed.
 */
@CompileStatic
final class CallbackPoster {

    private static final int CONNECT_TIMEOUT_MS = 3000

    private static final int READ_TIMEOUT_MS = 3000

    private static final int MAX_STATUS_LINE_BYTES = 4096

    static interface Resolver {
        InetAddress[] resolve(String host) throws IOException

        default boolean isAllowedHost(String host) {
            CallbackAllowList.normalizeAllowedHost(host) != null
        }

        default boolean isAllowedAddress(InetAddress address) {
            CallbackAllowList.isPublicAddress(address)
        }
    }

    private static final class SystemResolver implements Resolver {
        @Override
        InetAddress[] resolve(String host) throws IOException {
            InetAddress.getAllByName(host)
        }
    }

    private static final class LoopbackTestResolver implements Resolver {
        private final String expected

        LoopbackTestResolver(String expected) {
            this.expected = expected
        }

        @Override
        InetAddress[] resolve(String host) throws IOException {
            String normalized = host?.trim()?.toLowerCase(Locale.ROOT)
            if (normalized != expected) {
                throw new UnknownHostException("Unexpected test callback host '${host}'")
            }
            [InetAddress.getByAddress(expected, [127, 0, 0, 1] as byte[])] as InetAddress[]
        }

        @Override
        boolean isAllowedAddress(InetAddress address) {
            address != null && address.isLoopbackAddress()
        }
    }

    static final Resolver SYSTEM_RESOLVER = new SystemResolver()

    private CallbackPoster() {
    }

    static void post(URI uri, String json) {
        post(uri, json, SYSTEM_RESOLVER)
    }

    static void post(URI uri, String json, Resolver resolver) {
        if (uri == null) {
            throw new OdiseeException('Callback URL is missing', OdiseeException.BAD_REQUEST)
        }
        String scheme = uri.scheme?.toLowerCase(Locale.ROOT)
        if ((scheme != 'http' && scheme != 'https') || uri.userInfo != null || !uri.host) {
            throw new OdiseeException("Invalid callback URL '${uri}'", OdiseeException.BAD_REQUEST)
        }
        if (uri.port != -1 && (uri.port < 1 || uri.port > 65535)) {
            throw new OdiseeException("Invalid callback port '${uri.port}'", OdiseeException.BAD_REQUEST)
        }
        Resolver active = resolver ?: SYSTEM_RESOLVER
        if (!active.isAllowedHost(uri.host)) {
            throw new OdiseeException("Callback host '${uri.host}' is not allowed", OdiseeException.BAD_REQUEST)
        }
        String connectHost = unbracket(uri.host)
        InetAddress[] resolved = active.resolve(connectHost)
        if (resolved == null || resolved.length == 0) {
            throw new UnknownHostException(connectHost)
        }
        for (InetAddress candidate : resolved) {
            if (!active.isAllowedAddress(candidate)) {
                throw new OdiseeException(
                        "Callback host '${uri.host}' resolved to a non-public address",
                        OdiseeException.BAD_REQUEST)
            }
        }
        InetAddress pinned = resolved[0]
        int port = uri.port > 0 ? uri.port : ('https' == scheme ? 443 : 80)
        String path = uri.rawPath ?: '/'
        String target = uri.rawQuery != null ? "${path}?${uri.rawQuery}" : path
        String hostHeader = uri.port > 0 ? "${uri.host}:${uri.port}" : uri.host
        if (containsLineBreak(target) || containsLineBreak(hostHeader)) {
            throw new OdiseeException('Invalid callback URL', OdiseeException.BAD_REQUEST)
        }
        byte[] body = (json ?: '').getBytes(StandardCharsets.UTF_8)
        byte[] head = ("POST ${target} HTTP/1.1\r\n"
                + "Host: ${hostHeader}\r\n"
                + "Content-Type: application/json; charset=UTF-8\r\n"
                + "Content-Length: ${body.length}\r\n"
                + "Connection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1)

        Socket raw = new Socket()
        Socket socket = raw
        try {
            raw.setSoTimeout(READ_TIMEOUT_MS)
            raw.connect(new InetSocketAddress(pinned, port), CONNECT_TIMEOUT_MS)
            if ('https' == scheme) {
                SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                        .createSocket(raw, connectHost, port, true)
                ssl.setSoTimeout(READ_TIMEOUT_MS)
                SSLParameters params = ssl.getSSLParameters()
                params.setEndpointIdentificationAlgorithm('HTTPS')
                ssl.setSSLParameters(params)
                ssl.startHandshake()
                socket = ssl
            }
            OutputStream out = socket.getOutputStream()
            out.write(head)
            out.write(body)
            out.flush()
            readStatusLine(socket.getInputStream())
        } finally {
            try {
                socket?.close()
            } catch (IOException ignored) {
            }
        }
    }

    @PackageScope
    static Resolver loopbackResolverForTest(String allowedHost) {
        String expected = allowedHost?.trim()?.toLowerCase(Locale.ROOT)
        if (!expected) {
            throw new IllegalArgumentException('allowedHost is required')
        }
        new LoopbackTestResolver(expected)
    }

    private static String unbracket(String host) {
        host != null && host.startsWith('[') && host.endsWith(']')
                ? host.substring(1, host.length() - 1)
                : host
    }

    private static boolean containsLineBreak(String value) {
        value != null && (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0)
    }

    private static String readStatusLine(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(64)
        int b
        while ((b = input.read()) != -1) {
            if (b == 10) {
                break
            }
            if (b != 13) {
                if (buffer.size() >= MAX_STATUS_LINE_BYTES) {
                    throw new IOException('Callback HTTP status line is too long')
                }
                buffer.write(b)
            }
        }
        String line = buffer.toString(StandardCharsets.ISO_8859_1)
        if (!line.startsWith('HTTP/')) {
            throw new IOException('Invalid HTTP status line from callback host')
        }
        line
    }

}
