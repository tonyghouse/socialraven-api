package com.tonyghouse.socialraven.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tonyghouse.socialraven.constant.PostCollectionType;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Data
public class PostCollection {

    private String description;

    private PostCollectionType postType;

    // Supports multiple files
    private List<PostMedia> media;

    private List<ConnectedAccount> connectedAccounts;

    private OffsetDateTime scheduledTime; // stored in UTC in backend

    private Map<String, Object> platformConfigs;

    /** When true, saves as a draft without scheduling or requiring a scheduledTime. */
    @JsonProperty("isDraft")
    private boolean isDraft = false;
}
