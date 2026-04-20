package com.tonyghouse.socialraven.dto.reporting;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientReportTopPostResponse {
    private Long postId;
    private String provider;
    private String platformLabel;
    private String accountName;
    private Long campaignId;
    private String campaignLabel;
    private String content;
    private String postType;
    private String mediaFormat;
    private OffsetDateTime publishedAt;
    private Long impressions;
    private Long reach;
    private Long likes;
    private Long comments;
    private Long shares;
    private Long saves;
    private Long clicks;
    private Long videoViews;
    private Long watchTimeMinutes;
    private Long engagements;
    private Double engagementRate;
}
