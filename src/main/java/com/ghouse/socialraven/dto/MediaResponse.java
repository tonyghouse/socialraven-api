package com.ghouse.socialraven.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;



@Data
@AllArgsConstructor
@NoArgsConstructor
public class MediaResponse {
    private Long id;
    private String fileName;
    private String mimeType;
    private long size;
    private String fileUrl;   // pre-signed URL
    private String fileKey;
}
