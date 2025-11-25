package com.ghouse.socialraven.entity;

import com.ghouse.socialraven.constant.Provider;
import com.ghouse.socialraven.model.AdditionalOAuthInfo;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;

@Data
@Entity
@Table(name = "oauth_info")
public class OAuthInfoEntity {

    @Id
    @GeneratedValue
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(length = 50, nullable = false)
    private Provider provider;

    @Column(nullable = false)
    private String providerUserId;

    @Column(length = 2000, nullable = false)
    private String accessToken;

    @Column(nullable = false)
    private Long expiresAt;

    @Column(name = "expires_at_utc", nullable = false)
    private OffsetDateTime expiresAtUtc;

    @Column(nullable = false)
    private String userId;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    private AdditionalOAuthInfo additionalInfo;
}
