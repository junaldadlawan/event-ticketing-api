package com.junaldadlawan.event_ticketing_api.upload.storage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;

/**
 * Cloudflare R2 through its S3-compatible API ({@code app.storage.type=r2}, set in the prod profile). The bucket
 * stays private: files are still served by {@code GET /api/v1/uploads/files/...}, which reads them from here, so
 * stored URLs keep pointing at this server and {@code UploadedFileUrls} keeps validating them the same way.
 */
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "r2")
public class R2FileStorage implements FileStorage {

    private final S3Client s3;
    private final String bucket;

    @Autowired
    public R2FileStorage(@Value("${app.storage.r2.account-id}") String accountId,
                         @Value("${app.storage.r2.access-key-id}") String accessKeyId,
                         @Value("${app.storage.r2.secret-access-key}") String secretAccessKey,
                         @Value("${app.storage.r2.bucket}") String bucket) {
        this(S3Client.builder()
                .endpointOverride(URI.create("https://" + accountId + ".r2.cloudflarestorage.com"))
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                // R2 does not support every checksum the SDK sends by default since 2.30; only send what is required.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build(), bucket);
    }

    R2FileStorage(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        s3.putObject(b -> b.bucket(bucket).key(key).contentType(contentType), RequestBody.fromBytes(bytes));
    }

    @Override
    public Resource get(String key) {
        try {
            return new ByteArrayResource(s3.getObjectAsBytes(b -> b.bucket(bucket).key(key)).asByteArray());
        } catch (NoSuchKeyException e) {
            return null;
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            s3.headObject(b -> b.bucket(bucket).key(key));
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            // HEAD has no body, so a missing key can come back as a bare 404 rather than NoSuchKey.
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(b -> b.bucket(bucket).key(key));
    }
}
