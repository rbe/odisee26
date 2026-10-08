/*
 * odisee-client-java
 * odisee-client-java
 * Copyright (C) 2011-2013 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Alle Rechte vorbehalten. Nutzung unterliegt Lizenzbedingungen.
 * All rights reserved. Use is subject to license terms.
 *
 * rbe, 14.01.13 12:36
 */

package org.odisee.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.zip.GZIPOutputStream;

final class OdiseeHttpHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(OdiseeHttpHelper.class);

    public static final byte[] EMPY_BYTES = new byte[0];

    private final String username;

    private final String password;

    OdiseeHttpHelper() {
        this.username = "";
        this.password = "";
    }

    OdiseeHttpHelper(final String username, final String password) {
        this.username = username;
        this.password = password;
    }

    public byte[] post(final URL url, final String body, final String contentType) {
        final HttpURLConnection connection = getHttpURLConnection(url);
        Objects.requireNonNull(connection);
        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));
        connection.setRequestProperty("Content-Type", contentType);
        return send(connection, body, false);
    }

    public byte[] postCompressed(URL url, String body, final String contentType) {
        final HttpURLConnection connection = getHttpURLConnection(url);
        Objects.requireNonNull(connection);
        connection.setRequestProperty("Content-Type", contentType);
        connection.setRequestProperty("Content-Encoding", "gzip");
        return send(connection, body, true);
    }

    /**
     * Basic credentials travel on this request. {@link java.net.Authenticator#setDefault}
     * is JVM-wide and would attach the same user to a later call that has none.
     */
    private void applyAuthorization(final HttpURLConnection connection) {
        if (username == null || username.isEmpty()) {
            return;
        }
        final String token = username + ":" + (password == null ? "" : password);
        final String encoded = Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
        connection.setRequestProperty("Authorization", "Basic " + encoded);
    }

    private byte[] send(final HttpURLConnection connection, final String body, final boolean gzip) {
        applyAuthorization(connection);
        try {
            writeBody(connection, body, gzip);
            final int status = connection.getResponseCode();
            if (status >= 400) {
                throw new OdiseeClientException("HTTP " + status, status);
            }
            try (InputStream in = connection.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                in.transferTo(out);
                return out.toByteArray();
            }
        } catch (OdiseeClientException e) {
            throw e;
        } catch (IOException e) {
            final int status = responseCode(connection);
            if (status >= 400) {
                throw new OdiseeClientException("HTTP " + status, status);
            }
            LOGGER.warn("", e);
            throw new OdiseeClientException(e);
        } finally {
            connection.disconnect();
        }
    }

    private static void writeBody(final HttpURLConnection connection, final String body, final boolean gzip)
            throws IOException {
        if (gzip) {
            try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(connection.getOutputStream());
                 OutputStreamWriter writer = new OutputStreamWriter(gzipOutputStream, StandardCharsets.UTF_8)) {
                writer.write(body);
            }
            return;
        }
        try (OutputStream raw = connection.getOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(raw, StandardCharsets.UTF_8)) {
            writer.write(body);
        }
    }

    private static int responseCode(final HttpURLConnection connection) {
        try {
            return connection.getResponseCode();
        } catch (IOException e) {
            return -1;
        }
    }

    // TODO Java 11 HttpClient
    private HttpURLConnection getHttpURLConnection(final URL url) {
        try {
            final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("User-Agent", "Odisee/Java Client");
            CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
            System.setProperty("http.maxRedirects", "3");
            return connection;
        } catch (IOException e) {
            LOGGER.warn("", e);
        }
        throw new IllegalStateException("No HTTP connection");
    }

}
