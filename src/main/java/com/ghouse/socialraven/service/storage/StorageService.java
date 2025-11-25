package com.ghouse.socialraven.service.storage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.time.Duration;

@Service
public class StorageService {

    @Autowired
    private  S3Presigner s3Presigner;

    @Value("${tigris.s3.bucket}")
    private String bucket;

    public String generatePresignedGetUrl(String fileKey) {

        GetObjectPresignRequest presignRequest =
                GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(10)) // Fresh URL per API call
                        .getObjectRequest(b -> b.bucket(bucket).key(fileKey))
                        .build();

        PresignedGetObjectRequest presigned =
                s3Presigner.presignGetObject(presignRequest);

        return presigned.url().toString();
    }
}
