package com.ghouse.socialraven.dto;

import com.ghouse.socialraven.constant.Platform;
import lombok.Data;

@Data
public class ConnectedAccountDto {

    private String id;

    private Platform platform;

    private String username;

    private String profile;
}
