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
     * Do a HTTP POST to Odisee service using f-functions like fsockopen().
     * @param string $host Odisee service host (server name or IP).
     * @param string $username Odisee username.
     * @param string $password Password.
     * @param string $path Relative URI.
     * @param string $referer Who are we? Who is calling?
     * @param string $data_to_send Data.
     * @return mixed HTTP response.
     * @throws OdiseeException When fsockopen() or fputs() cannot be called.
     */
    private static function f_post($host, $username, $password, $path, $referer, $data_to_send)
    {
        if (is_callable('fsockopen') && is_callable('fputs')) {
            $fp = fsockopen($host, 80);
            fputs($fp, "POST $path HTTP/1.1\r\n");
            fputs($fp, "Host: $host\r\n");
            fputs($fp, "Referer: $referer\r\n");
            fputs($fp, "Content-type: text/xml\r\n");
            fputs($fp, "Content-length: " . strlen($data_to_send) . "\r\n");
            fputs($fp, "Connection: close\r\n");
            fputs($fp, "\r\n");
            fputs($fp, $data_to_send);
            $res = "";
            while (!feof($fp)) {
                $res .= fgets($fp, self::$BUF_SIZE);
            }
            fclose($fp);
            return $res;
        } else {
            throw new OdiseeException('Cannot call fsockopen() or fputs()');
        }
    }

    /**
     * Do a HTTP POST to Odisee service using curl.
     * @param string $host Odisee service host (server name or IP).
     * @param string $username Odisee username.
     * @param string $password Password.
     * @param string $path Relative URI.
     * @param string $referer Who are we? Who is calling?
     * @param string $data_to_send Data.
     * @return mixed HTTP response.
     * @throws OdiseeException When curl_xxx() functions cannot be called.
     */
    private static function &curl_post($host, $username, $password, $path, $referer, $data_to_send)
    {
        if (is_callable('curl_init')) { // extension_loaded
            // Initialize curl
            $ch = curl_init();
            curl_setopt($ch, CURLOPT_HEADER, FALSE);
            curl_setopt($ch, CURLOPT_HTTPHEADER, array('Content-Type: text/xml' /*, $additionalHeaders*/));
            curl_setopt($ch, CURLOPT_URL, $host . $path);
            curl_setopt($ch, CURLOPT_REFERER, $referer);
            curl_setopt($ch, CURLOPT_USERPWD, $username . ":" . $password);
            curl_setopt($ch, CURLOPT_BUFFERSIZE, self::$BUF_SIZE);
            curl_setopt($ch, CURLOPT_CONNECTTIMEOUT, self::$CONNECT_TIMEOUT);
            curl_setopt($ch, CURLOPT_TIMEOUT, self::$TIMEOUT);
            curl_setopt($ch, CURLOPT_RETURNTRANSFER, TRUE);
            curl_setopt($ch, CURLOPT_POST, TRUE);
            curl_setopt($ch, CURLOPT_POSTFIELDS, $data_to_send);
            // Make temporary file
            $old_umask = umask(077);
            $filename = tempnam(dirname(__FILE__) . 'var/tmp', 'odisee_temp_');
            $fp = fopen($filename, 'w');
            // Call Odisee
            curl_setopt($ch, CURLOPT_FILE, $fp);
            curl_exec($ch);
            // Cleanup
            curl_close($ch);
            fclose($fp);
            // Restore old umask
            umask($old_umask);
            return $filename;
        } else {
            throw new OdiseeException('Cannot call curl functions');
        }
    }

    /**
     * Request a document from Odisee service.
     * @param string $host Odisee service host (server name or IP).
     * @param string $username Odisee username.
     * @param string $password Password.
     * @param string $referer Who are we? Who is calling?
     * @param string $data_to_send Data.
     * @return mixed HTTP response.
     * @throws OdiseeException When fsockopen() or fputs() cannot be called.
     */
    public static function &post($host, $username, $password, $referer, $data_to_send)
    {
        return self::curl_post($host, $username, $password, self::$ODISEE_URI, $referer, $data_to_send);
    }

}

?>
