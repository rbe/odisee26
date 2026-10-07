/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 *
 * rbe, 02.02.15, 18:35
 */

package org.odisee.io

import org.odisee.api.OdiseeException

import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipException

final class Compression {

    /** Raw HTTP body, before gzip is expanded. */
    static final int MAX_COMPRESSED_BYTES = 8 * 1024 * 1024

    /** Body after gzip, or the raw body when it is not gzip. */
    static final int MAX_PLAIN_BYTES = 32 * 1024 * 1024

    private Compression() {
        throw new AssertionError();
    }

    /**
     * Read a request body, expanding gzip when the magic bytes are present.
     * Either cap is HTTP 400.
     */
    static byte[] readLimited(InputStream input) {
        readLimited(input, MAX_COMPRESSED_BYTES, MAX_PLAIN_BYTES)
    }

    static byte[] readLimited(InputStream input, int maxCompressed, int maxPlain) {
        final byte[] raw = readAtMost(input, maxCompressed, 'Compressed request')
        InputStream plain = null
        try {
            plain = decompress(new ByteArrayInputStream(raw))
            return readAtMost(plain, maxPlain, 'Request body')
        } catch (ZipException e) {
            throw new OdiseeException('Compressed request is not valid gzip', OdiseeException.BAD_REQUEST)
        } finally {
            plain?.close()
        }
    }

    public static isCompressedSignature(final byte[] bytes) {
        return bytes[0] == (byte) 0x1f && bytes[1] == (byte) 0x8b;
    }

    /**
     * Check if InputStream is gzip'ed by looking at the first two bytes (magic number)
     * and if it is, return a GZIPInputStream wrapped stream.
     * @param input An input stream.
     * @return The input or GZIPInputStream(input).
     */
    public static InputStream decompress(InputStream input) {
        PushbackInputStream pushbackInputStream = new PushbackInputStream(input, 2);
        byte[] signature = new byte[2];
        int read = pushbackInputStream.read(signature);
        if (read < 2) {
            if (read > 0) {
                pushbackInputStream.unread(signature, 0, read);
            }
            return pushbackInputStream;
        }
        pushbackInputStream.unread(signature);
        if (signature[0] == (byte) 0x1f && signature[1] == (byte) 0x8b) {
            return new GZIPInputStream(pushbackInputStream);
        }
        return pushbackInputStream;
    }

    public static InputStream decompress(final byte[] bytes) {
        final GZIPInputStream stream = new GZIPInputStream(new ByteArrayInputStream(bytes));
        return stream;
    }

    /**
     * Compress a byte[] using gzip.
     * @param b The byte[] to compress.
     * @params base64 Encode bytes as base64?
     * @return byte[]
     */
    public static byte[] zip(byte[] b, boolean base64 = false) {
        final ByteArrayOutputStream targetStream = new ByteArrayOutputStream()
        final GZIPOutputStream zipStream = new GZIPOutputStream(targetStream)
        zipStream.write(b)
        zipStream.close()
        if (base64) {
            final byte[] zipped = targetStream.toByteArray()
            targetStream.close()
            final Writable encodeBase64 = zipped.encodeBase64()
            byte[] bytes = encodeBase64.toString().bytes
            return bytes
        } else {
            final byte[] bytes = targetStream.toByteArray()
            return bytes
        }
    }

    /**
     * Convenience method for: {@link #zip(byte [])}
     * @param str The string to compress using gzip.
     * @return byte[]
     */
    public static byte[] zip(String str) {
        return zip(str.bytes)
    }

    private static byte[] readAtMost(InputStream input, int max, String label) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream()
        final byte[] buffer = new byte[8192]
        int total = 0
        int read
        while ((read = input.read(buffer)) >= 0) {
            total += read
            if (total > max) {
                throw new OdiseeException("${label} exceeds ${max} bytes", OdiseeException.BAD_REQUEST)
            }
            out.write(buffer, 0, read)
        }
        out.toByteArray()
    }

}
