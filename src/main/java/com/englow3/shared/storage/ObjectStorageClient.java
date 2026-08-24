package com.englow3.shared.storage;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;

import org.springframework.stereotype.Component;

import com.englow3.config.S3Properties;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Component
@RequiredArgsConstructor
public class ObjectStorageClient {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties properties;

    public String defaultBucket() {
        return properties.bucket();
    }

    public void upload(String bucket, String key, InputStream content, long contentLength, String contentType) {
        s3Client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                RequestBody.fromInputStream(content, contentLength));
    }

    public URL presignGet(String bucket, String key, Duration ttl) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder().signatureDuration(ttl)
                .getObjectRequest(request -> request.bucket(bucket).key(key)).build();
        return s3Presigner.presignGetObject(presignRequest).url();
    }

    public URL presignPut(String bucket, String key, String contentType, Duration ttl) {
        PutObjectRequest putRequest = PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType)
                .build();
        return s3Presigner
                .presignPutObject(
                        PutObjectPresignRequest.builder().signatureDuration(ttl).putObjectRequest(putRequest).build())
                .url();
    }

    public StoredObjectMetadata metadata(String bucket, String key) {
        var response = s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
        return new StoredObjectMetadata(response.contentLength(), response.contentType());
    }

    public byte[] download(String bucket, String key) {
        return s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
    }

    public void delete(String bucket, String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    public record StoredObjectMetadata(long contentLength, String contentType) {
    }
}
