package com.tonyghouse.socialraven.dto;

import com.tonyghouse.socialraven.constant.PostReviewLinkShareScope;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Data;

@Data
public class CreatePostCollectionReviewLinkRequest {
    private OffsetDateTime expiresAt;
    private String passcode;
    private PostReviewLinkShareScope shareScope;
    private List<Long> sharedPostIds;
}
