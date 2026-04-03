package com.tonyghouse.socialraven.dto;

import com.tonyghouse.socialraven.constant.PostCollaborationThreadType;
import com.tonyghouse.socialraven.constant.PostCollaborationVisibility;
import java.util.List;
import lombok.Data;

@Data
public class PostCollaborationThreadRequest {
    private PostCollaborationThreadType threadType;
    private PostCollaborationVisibility visibility;
    private String body;
    private List<String> mentionUserIds;
    private Integer anchorStart;
    private Integer anchorEnd;
    private String anchorText;
    private String suggestedText;
}
