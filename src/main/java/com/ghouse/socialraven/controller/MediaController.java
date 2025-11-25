package com.ghouse.socialraven.controller;

import com.ghouse.socialraven.service.storage.S3PresignedUrlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/media")
public class MediaController {

    @Autowired
    private S3PresignedUrlService s3Service;

    @Value("${tigris.s3.bucket}")
    private String bucketName;

    @Value("${tigris.s3.endpoint}")
    private String tigrisEndpoint;

    @PostMapping("/presign")
    public Map<String, String> getPresignedUrl(
            @RequestParam String fileName,
            @RequestParam String mimeType
    ) {
        // 1. Create key first
        String key = "posts/" + System.currentTimeMillis() + "_" + fileName;

        // 2. Generate presigned PUT with the SAME key
        String uploadUrl = s3Service.generateUploadUrl(key, mimeType);

        // 3. Public URL
        String fileUrl = tigrisEndpoint + "/" + bucketName + "/" + key;

        return Map.of(
                "uploadUrl", uploadUrl,
                "fileKey", key,
                "fileUrl", fileUrl
        );
    }
}

