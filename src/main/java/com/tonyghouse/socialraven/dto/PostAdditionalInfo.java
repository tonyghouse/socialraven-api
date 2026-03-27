package com.tonyghouse.socialraven.dto;

import com.tonyghouse.socialraven.constant.PostCollectionType;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
public class PostAdditionalInfo {

    private String title;

    private String description;

    private PostCollectionType postType;

    // Supports multiple files
    private List<PostMedia> media;

    private List<ConnectedAccount> connectedAccounts;

    private OffsetDateTime scheduledTime;
}
