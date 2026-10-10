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
 * Puts one object. The stream is the body. Callers do not copy it into a second {@code byte[]}.
 */
interface ObjectStorage {

    void put(BucketFile.Record bucket, String key, String contentType, InputStream body, long length)

}
