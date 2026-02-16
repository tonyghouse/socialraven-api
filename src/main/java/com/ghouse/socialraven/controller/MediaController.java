package com.ghouse.socialraven.controller;

import com.ghouse.socialraven.service.storage.S3PresignedUrlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/media")
public class MediaController {

    @Autowired
    private S3PresignedUrlService s3Service;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    @Value("${aws.region}")
    private String region;

//    @PostMapping("/presign")
//    public Map<String, String> getPresignedUrl(
//            @RequestParam String fileName,
//            @RequestParam String mimeType
//    ) {
//
//        String key = "posts/" + System.currentTimeMillis() + "_" + fileName;
//
//        String uploadUrl = s3Service.generateUploadUrl(key, mimeType);
//
//        // Proper AWS S3 URL
//        String fileUrl = String.format(
//                "https://%s.s3.%s.amazonaws.com/%s",
//                bucketName,
//                region,
//                key
//        );
//
//        return Map.of(
//                "uploadUrl", uploadUrl,
//                "fileKey", key,
//                "fileUrl", fileUrl
//        );
//    }



    @PostMapping("/presign")
    public Map<String, String> getPresignedUrl(
            @RequestParam String fileName,
            @RequestParam String mimeType
    ) {

        String key = "posts/" + System.currentTimeMillis() + "_" + fileName;

        String uploadUrl = s3Service.generateUploadUrl(key, mimeType);

        return Map.of(
                "uploadUrl", uploadUrl,
                "fileKey", key
        );
    }

}
