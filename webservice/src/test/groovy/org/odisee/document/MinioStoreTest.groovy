package org.odisee.document

import groovy.json.JsonSlurper
import groovy.test.GroovyTestCase
import org.springframework.mock.web.MockHttpServletResponse
import org.w3c.dom.Element
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.S3Exception

import java.nio.file.Files

/**
 * A real MinIO round trip. {@code :webservice:test} does not run this class.
 * {@code ./gradlew :webservice:minioTest} starts the container first.
 * The object is not expired or deleted.
 */
class MinioStoreTest extends GroovyTestCase {

    @Override
    protected void setUp() {
        Files.deleteIfExists(BucketFile.location())
    }

    @Override
    protected void tearDown() {
        Files.deleteIfExists(BucketFile.location())
    }

    void testStoreWritesAndReadsTheObjectBack() {
        String endpoint = setting('odisee.minio.endpoint')
        String region = setting('odisee.minio.region')
        String bucket = setting('odisee.minio.bucket')
        String accessKey = setting('odisee.minio.accessKey')
        String secret = setting('odisee.minio.secret')
        BucketFile.save('odisee', endpoint, region, bucket, accessKey, secret)
        BucketFile.Record record = BucketFile.require('odisee')

        S3Client client = open(record)
        try {
            ensureBucket(client, bucket)
        } finally {
            client.close()
        }

        byte[] payload = '%PDF-1.4 minio'.getBytes('UTF-8')
        Document document = new Document()
        document.filename = 'Letter.pdf'
        document.bytes = payload
        MockHttpServletResponse response = new MockHttpServletResponse()

        Delivery.respond(response, 'odisee', storeRequest(), document, new S3ObjectStorage())

        assertEquals(200, response.status)
        assertEquals('application/json; charset=UTF-8', response.contentType)
        assertFalse(Arrays.equals(payload, response.contentAsByteArray))
        Map body = new JsonSlurper().parseText(response.contentAsString) as Map
        assertEquals(bucket, body.bucket)
        assertTrue(body.key ==~ /[0-9a-f-]{36}\/Letter\.pdf/)

        client = open(record)
        try {
            def downloaded = client.getObject(GetObjectRequest.builder().bucket(bucket).key(body.key.toString()).build())
            GetObjectResponse meta = downloaded.response()
            byte[] read = downloaded.readAllBytes()
            assertTrue(Arrays.equals(payload, read))
            assertEquals('application/pdf', meta.contentType())
            assertNull(meta.expiration())
            assertNull(meta.expires())
        } finally {
            client.close()
        }
    }

    private static void ensureBucket(S3Client client, String bucket) {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build())
        } catch (S3Exception e) {
            if (e.statusCode() != 404) {
                throw e
            }
            client.createBucket(CreateBucketRequest.builder().bucket(bucket).build())
        }
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

    private static String setting(String name) {
        String value = System.getProperty(name)
        assertTrue("Missing -D${name}. Run ./gradlew :webservice:minioTest".toString(), value != null && !value.isEmpty())
        value
    }

    private static Element storeRequest() {
        RequestSchema.parse('''<odisee delivery="store">
  <request name="Letter">
    <template name="Letter" outputFormat="pdf"/>
    <instructions><userfield name="Hallo">Odisee</userfield></instructions>
  </request>
</odisee>'''.getBytes('UTF-8'))
    }

}
