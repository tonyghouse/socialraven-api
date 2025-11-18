package com.ghouse.socialraven.entity;

import jakarta.persistence.Column;
import lombok.Data;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;


@Data
@Entity
@Table(name = "oauth_token")
public class OAuthToken {

    @Id
    @GeneratedValue
    private Long id;

    @Column(length = 50)
    private String provider;

    @Column(length = 2000)
    private String accessToken;

    @Column(length = 1000)
    private String userName;

    private Long expiresAt;

    private String userId;
}
