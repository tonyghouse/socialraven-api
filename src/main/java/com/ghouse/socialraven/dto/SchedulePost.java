package com.ghouse.socialraven.dto;

import com.ghouse.socialraven.constant.PostType;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
public class SchedulePost {

    private String title;

    private String description;

    private PostType postType;

    // Supports multiple files
    private List<PostMedia> media;

    private List<ConnectedAccount> connectedAccounts;

    private OffsetDateTime scheduledTime; // stored in UTC in backend
}
