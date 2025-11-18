package com.ghouse.socialraven.exception;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
@AllArgsConstructor
public class SocialRavenException {
    private String errorMessage;
    private String errorCode;
}
