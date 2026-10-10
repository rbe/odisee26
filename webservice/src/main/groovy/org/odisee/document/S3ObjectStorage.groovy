/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 */
package org.odisee.document

import groovy.util.logging.Slf4j
import org.odisee.api.OdiseeException
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.http.ContentStreamProvider
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.PutObjectRequest

/**
 * S3-compatible put. Endpoint, region, bucket, and keys come from the user's
 * {@code etc/buckets} line, so MinIO works in development. The body is the
 * caller's stream. {@link RequestBody#fromBytes(byte[])} is not used.
 * Objects are not expired, deleted, or given a lifecycle.
 */
@Slf4j
class S3ObjectStorage implements ObjectStorage {

    @Override
    void put(BucketFile.Record bucket, String key, String contentType, InputStream body, long length) {
        S3Client client = null
        try {
            client = open(bucket)
            client.putObject(putRequest(bucket, key, contentType, length), streamBody(body, length, contentType))
        } catch (OdiseeException e) {
            throw e
        } catch (RuntimeException e) {
            log.error("Object storage put failed for bucket ${bucket?.bucket}", e)
            OdiseeException error = new OdiseeException('Object storage failed', e)
            error.httpStatus = OdiseeException.SERVER_ERROR
            throw error
        } finally {
            client?.close()
        }
    }

    /**
     * The SDK streams this body. {@code fromInputStream} does not copy a markable stream into a {@code byte[]}.
     * {@code RequestBody.fromBytes} is not used.
     */
    static RequestBody streamBody(InputStream body, long length) {
        streamBody(body, length, 'application/octet-stream')
    }

    static RequestBody streamBody(InputStream body, long length, String contentType) {
        String type = contentType ?: 'application/octet-stream'
        RequestBody.fromContentProvider(ContentStreamProvider.fromInputStream(body), length, type)
    }

    private static PutObjectRequest putRequest(BucketFile.Record bucket, String key, String contentType, long length) {
        PutObjectRequest.builder()
                .bucket(bucket.bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(length)
                .build()
    }

    private static S3Client open(BucketFile.Record bucket) {
        S3Client.builder()
                .endpointOverride(URI.create(bucket.endpoint))
                .region(Region.of(bucket.region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(bucket.accessKey, bucket.secret)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build()
    }

}
