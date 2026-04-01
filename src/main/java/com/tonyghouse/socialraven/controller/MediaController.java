package com.tonyghouse.socialraven.controller;

import com.tonyghouse.socialraven.annotation.RequiresRole;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.service.storage.S3PresignedUrlService;
import com.tonyghouse.socialraven.util.WorkspaceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/media")
@Slf4j
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



    @RequiresRole(WorkspaceRole.MEMBER)
    @PostMapping("/presign")
    public ResponseEntity<Map<String, String>> getPresignedUrl(
            @RequestParam String fileName,
            @RequestParam String mimeType
    ) {
        long startedAt = System.nanoTime();

        String workspaceId = WorkspaceContext.getWorkspaceId();
        String key = workspaceId + "/posts/" + System.currentTimeMillis() + "_" + fileName;

        String uploadUrl = s3Service.generateUploadUrl(key, mimeType);

        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        if (durationMs > 300) {
            log.warn("Slow presign generation: {} ms, key={}", durationMs, key);
        }

        return ResponseEntity.ok()
                .header("X-Presign-Latency-Ms", String.valueOf(durationMs))
                .header("Server-Timing", "presign;dur=" + durationMs)
                .body(Map.of(
                        "uploadUrl", uploadUrl,
                        "fileKey", key
                ));
    }

}
