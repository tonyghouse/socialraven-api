package com.ghouse.socialraven.service.storage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.time.Duration;

@Service
public class StorageService {

    @Autowired
    private S3Presigner s3Presigner;

    @Autowired
    private S3Client s3Client; // Add S3Client for direct downloads

    @Value("${tigris.s3.bucket}")
    private String bucket;

    /**
     * Generate presigned GET URL for UI/browser access
     */
    public String generatePresignedGetUrl(String fileKey, Duration duration) {
        GetObjectPresignRequest presignRequest =
                GetObjectPresignRequest.builder()
                        .signatureDuration(duration)
                        .getObjectRequest(b -> b.bucket(bucket).key(fileKey))
                        .build();

        PresignedGetObjectRequest presigned =
                s3Presigner.presignGetObject(presignRequest);

        return presigned.url().toString();
    }

    /**
     * Overloaded method with default duration (1 hour)
     */
    public String generatePresignedGetUrl(String fileKey) {
        return generatePresignedGetUrl(fileKey, Duration.ofHours(1));
    }

    /**
     * Download file bytes directly from S3 (for server-to-server operations like LinkedIn upload)
     * This method avoids presigned URL signature issues
     */
    public byte[] downloadFileBytes(String fileKey) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileKey)
                    .build();

            ResponseBytes<GetObjectResponse> objectBytes =
                    s3Client.getObjectAsBytes(getObjectRequest);

            return objectBytes.asByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Failed to download file from S3: " + fileKey, e);
        }
    }
}