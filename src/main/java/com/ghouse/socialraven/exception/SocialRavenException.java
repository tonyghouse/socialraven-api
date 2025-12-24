package com.ghouse.socialraven.exception;

import org.springframework.http.HttpStatus;

public class SocialRavenException extends RuntimeException {

    private final String errorCode;

    public SocialRavenException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public SocialRavenException(String message, HttpStatus httpStatus) {
        super(message);
        this.errorCode = String.valueOf(httpStatus.value());
    }

    public SocialRavenException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
