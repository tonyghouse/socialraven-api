package com.ghouse.socialraven.dto;

import com.ghouse.socialraven.constant.Platform;
import lombok.Data;

@Data
public class ConnectedAccountCount {

    private Platform platform;

    private Integer count;
}
