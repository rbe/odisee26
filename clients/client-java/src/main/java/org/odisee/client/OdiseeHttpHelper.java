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
import java.io.OutputStreamWriter;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.zip.GZIPOutputStream;

final class OdiseeHttpHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(OdiseeHttpHelper.class);

    private static final int BUF_SIZE = 128 << 10;

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

    public byte[] post(final URL url, final String body) {
        final HttpURLConnection connection = getHttpURLConnection(url);
        Objects.requireNonNull(connection);
        connection.setRequestProperty("Content-Length", String.valueOf(body.length()));
        connection.setRequestProperty("Content-Type", "text/xml");
        try (final OutputStreamWriter streamWriter = new OutputStreamWriter(connection.getOutputStream(),
                StandardCharsets.UTF_8);
             final InputStream is = connection.getInputStream();
             final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            streamWriter.write(body);
            streamWriter.flush();
            is.transferTo(baos);
            connection.disconnect();
            return baos.toByteArray();
        } catch (IOException e) {
            LOGGER.warn("", e);
            return null;
        }
    }

    public byte[] postCompressed(URL url, String body) {
        final HttpURLConnection connection = getHttpURLConnection(url);
        Objects.requireNonNull(connection);
        connection.setRequestProperty("Content-Type", "application/x-gzip");
        connection.setRequestProperty("Content-Encoding", "gzip");
        try (final GZIPOutputStream gzipOutputStream = new GZIPOutputStream(connection.getOutputStream());
             final OutputStreamWriter streamWriter = new OutputStreamWriter(gzipOutputStream,
                     StandardCharsets.UTF_8);
             final InputStream is = connection.getInputStream();
             final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            streamWriter.write(body);
            streamWriter.flush();
            is.transferTo(baos);
            connection.disconnect();
            return baos.toByteArray();
        } catch (IOException e) {
            LOGGER.warn("", e);
            return null;
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
            Authenticator.setDefault(new UserPassAuthenticator(username, password));
            return connection;
        } catch (ProtocolException e) {
            LOGGER.warn("", e);
        } catch (IOException e) {
            LOGGER.warn("", e);
        }
        throw new IllegalStateException("No HTTP connection");
    }

    private static class UserPassAuthenticator extends Authenticator {

        private final String user;

        private final String pass;

        private UserPassAuthenticator(final String user, final String pass) {
            this.user = user;
            this.pass = pass;
        }

        protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(user, pass.toCharArray());
        }

    }

}
