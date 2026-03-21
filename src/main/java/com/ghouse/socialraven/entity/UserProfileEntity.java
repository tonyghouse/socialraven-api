package com.ghouse.socialraven.entity;

import com.ghouse.socialraven.constant.UserType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Entity
@Table(name = "user_profile")
public class UserProfileEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", length = 20, nullable = false)
    private UserType userType;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
