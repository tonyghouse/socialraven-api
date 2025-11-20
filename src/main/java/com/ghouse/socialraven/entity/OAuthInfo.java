package com.ghouse.socialraven.entity;

import com.ghouse.socialraven.constant.Provider;
import com.ghouse.socialraven.model.AdditionalOAuthInfo;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Type;

@Data
@Entity
@Table(name = "oauth_info")
public class OAuthInfo {

    @Id
    @GeneratedValue
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private Provider provider;

    private String providerUserId;

    @Column(length = 2000)
    private String accessToken;

    private Long expiresAt;

    private String userId;

    // Store as JSONB in Postgres
    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private AdditionalOAuthInfo additionalInfo;
}
