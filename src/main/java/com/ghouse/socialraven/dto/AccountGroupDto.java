package com.ghouse.socialraven.dto;

import lombok.Data;

import java.util.List;

@Data
public class AccountGroupDto {

    private String id;
    private String name;
    private String color;
    private List<String> accountIds;
    private String createdAt;
}
