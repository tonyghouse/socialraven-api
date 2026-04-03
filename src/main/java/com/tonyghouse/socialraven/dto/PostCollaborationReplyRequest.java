package com.tonyghouse.socialraven.dto;

import java.util.List;
import lombok.Data;

@Data
public class PostCollaborationReplyRequest {
    private String body;
    private List<String> mentionUserIds;
}
