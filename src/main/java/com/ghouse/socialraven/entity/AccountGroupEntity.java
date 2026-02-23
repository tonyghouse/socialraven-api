package com.ghouse.socialraven.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "account_group")
@Data
public class AccountGroupEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String name;

    @Column(length = 20, nullable = false)
    private String color;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @ElementCollection
    @CollectionTable(
            name = "account_group_member",
            joinColumns = @JoinColumn(name = "group_id")
    )
    @Column(name = "provider_user_id")
    private List<String> accountIds = new ArrayList<>();
}
