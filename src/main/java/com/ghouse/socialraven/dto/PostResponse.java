package com.ghouse.socialraven.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PostResponse {
    private Long id;
    private String title;
    private String description;
    private String provider;
    private String postStatus;
    private OffsetDateTime scheduledTime;
    private List<MediaResponse> media;
    private List<String> userNames;
}
