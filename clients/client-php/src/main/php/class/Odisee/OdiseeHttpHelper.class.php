<?php

/**
 * odisee
 * odisee-client-php
 * Copyright (C) 2011-2013 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Alle Rechte vorbehalten. Nutzung unterliegt Lizenzbedingungen.
 * All rights reserved. Use is subject to license terms.
 *
 * rbe
 */

namespace Odisee;

//require_once(dirname(__FILE__) . '/OdiseeException.class.php');

/**
 * Helper for HTTP protocol.
 */
class OdiseeHttpHelper
{

    /**
     * @var int Timeout when connecting to Odisee service.
     */
    public static $CONNECT_TIMEOUT = 5;

    /**
     * @var int Timeout when waiting for a document.
     */
    public static $TIMEOUT = 25;

    /**
     * @var int Size for buffer, default is 128 kB.
     */
    public static $BUF_SIZE = 131072;

    /**
     * @var string URI of Odisee service, do not modify default value.
     */
    private static $ODISEE_URI = '/odisee/document/generate';

    /**
     * Resolve endpoint URL without duplicating the generate path.
     * @param string $serviceUrl
     * @param string $path
     * @return string
     */
    private static function resolveUrl($serviceUrl, $path)
    {
        $trimmed = rtrim($serviceUrl, '/');
        if ($path === '' || substr($trimmed, -strlen($path)) === $path) {
            return $trimmed;
        }
        return $trimmed . $path;
    }

    /**
     * Do an HTTP POST to the Odisee service using curl and Bearer authentication.
     * @param string $host Odisee service base URL or full endpoint URL.
     * @param string $accessToken Bearer access token.
     * @param string $path Relative URI.
     * @param string $referer Who is calling.
     * @param string $data_to_send Request payload.
     * @param string $contentType MIME type.
     * @return string Response body bytes.
     * @throws OdiseeException On transport or HTTP >= 400 error.
     */
    private static function curl_post($host, $accessToken, $path, $referer, $data_to_send, $contentType = 'text/xml')
    {
        if (!OdiseeStringHelper::checkStr($accessToken) || trim($accessToken) === '') {
            throw new OdiseeException('Bearer access token is required');
        }
        if (!is_callable('curl_init')) {
            throw new OdiseeException('Cannot call curl functions');
        }
        $old_umask = umask(077);
        $tmpDir = dirname(__FILE__) . '/var/tmp';
        if (!is_dir($tmpDir) || !is_writable($tmpDir)) {
            $tmpDir = sys_get_temp_dir();
        }
        $filename = tempnam($tmpDir, 'odisee_temp_');
        if ($filename === false) {
            umask($old_umask);
            throw new OdiseeException('Cannot create temporary file');
        }
        $fp = fopen($filename, 'wb');
        if ($fp === false) {
            @unlink($filename);
            umask($old_umask);
            throw new OdiseeException('Cannot open temporary file');
        }
        $ch = curl_init();
        $execOk = false;
        $curlErrno = 0;
        $curlError = '';
        $status = 0;
        $body = '';
        try {
            $headers = array(
                'Content-Type: ' . $contentType,
                'Authorization: Bearer ' . trim($accessToken),
            );
            curl_setopt($ch, CURLOPT_HEADER, FALSE);
            curl_setopt($ch, CURLOPT_HTTPHEADER, $headers);
            curl_setopt($ch, CURLOPT_URL, self::resolveUrl($host, $path));
            curl_setopt($ch, CURLOPT_REFERER, $referer);
            curl_setopt($ch, CURLOPT_BUFFERSIZE, self::$BUF_SIZE);
            curl_setopt($ch, CURLOPT_CONNECTTIMEOUT, self::$CONNECT_TIMEOUT);
            curl_setopt($ch, CURLOPT_TIMEOUT, self::$TIMEOUT);
            curl_setopt($ch, CURLOPT_POST, TRUE);
            curl_setopt($ch, CURLOPT_POSTFIELDS, $data_to_send);
            curl_setopt($ch, CURLOPT_FILE, $fp);
            $execOk = curl_exec($ch);
            $curlErrno = curl_errno($ch);
            $curlError = curl_error($ch);
            $status = (int) curl_getinfo($ch, CURLINFO_HTTP_CODE);
            fclose($fp);
            $fp = null;
            $read = file_get_contents($filename);
            $body = $read === false ? '' : $read;
        } finally {
            if (is_resource($fp)) {
                fclose($fp);
            }
            curl_close($ch);
            if (is_string($filename) && file_exists($filename)) {
                @unlink($filename);
            }
            umask($old_umask);
        }
        if ($execOk === false || $curlErrno !== 0) {
            throw new OdiseeException('HTTP request failed: ' . $curlError);
        }
        if ($status >= 400) {
            $detail = trim($body);
            throw new OdiseeException($detail !== '' ? ('HTTP ' . $status . ': ' . $detail) : ('HTTP ' . $status), $status);
        }
        return $body;
    }

    /**
     * Request a document from Odisee service.
     * @param string $host Odisee service URL.
     * @param string $accessToken Bearer access token.
     * @param string $referer Who is calling.
     * @param string $data_to_send Request payload.
     * @param string $contentType MIME type.
     * @return string HTTP response body.
     * @throws OdiseeException On transport or HTTP >= 400 error.
     */
    public static function post($host, $accessToken, $referer, $data_to_send, $contentType = 'text/xml')
    {
        return self::curl_post($host, $accessToken, self::$ODISEE_URI, $referer, $data_to_send, $contentType);
    }

}

?>
