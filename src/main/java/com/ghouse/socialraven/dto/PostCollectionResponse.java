package com.ghouse.socialraven.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PostCollectionResponse {
    private Long id;
    private String title;
    private String description;
    private OffsetDateTime scheduledTime;
    private String postCollectionType;
    private String overallStatus;
    private List<PostResponse> posts;
    private List<MediaResponse> media;
}
