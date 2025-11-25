package com.ghouse.socialraven.dto;

import lombok.Data;

@Data
public class PostMedia {
    private String fileName;
    private String mimeType;
    private String fileUrl;
    private String fileKey;
    private Long size;
}
