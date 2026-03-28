package com.tonyghouse.socialraven.dto;

import com.tonyghouse.socialraven.constant.Provider;
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
    private Long postCollectionId;
    private Provider provider;
    private String description;
    private String postStatus;
    private OffsetDateTime scheduledTime;
    private List<MediaResponse> media;
    private ConnectedAccount connectedAccount;
}
