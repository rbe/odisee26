/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 */
package org.odisee.document

/**
 * POSTs a job status to a callback URL. Callers check the allow-list first.
 * Redirects are not followed.
 */
final class CallbackPoster {

    private CallbackPoster() {
    }

    static void post(URI uri, String json) {
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection()
        connection.setInstanceFollowRedirects(false)
        connection.setConnectTimeout(3000)
        connection.setReadTimeout(3000)
        connection.setRequestMethod('POST')
        connection.setDoOutput(true)
        connection.setRequestProperty('Content-Type', 'application/json; charset=UTF-8')
        byte[] bytes = json.getBytes('UTF-8')
        connection.setFixedLengthStreamingMode(bytes.length)
        connection.outputStream.withCloseable { it.write(bytes) }
        connection.responseCode
        connection.disconnect()
    }

}
