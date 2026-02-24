package com.ghouse.socialraven.dto;

import com.ghouse.socialraven.constant.PostCollectionType;
import com.ghouse.socialraven.constant.PostType;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Data
public class PostCollection {

    private String title;

    private String description;

    private PostCollectionType postType;

    // Supports multiple files
    private List<PostMedia> media;

    private List<ConnectedAccount> connectedAccounts;

    private OffsetDateTime scheduledTime; // stored in UTC in backend

    private Map<String, Object> platformConfigs;
}
