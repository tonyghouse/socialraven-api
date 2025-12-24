package com.ghouse.socialraven.exception;

import com.ghouse.socialraven.dto.SocialRavenError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle already wrapped SocialRavenException
     */
    @ExceptionHandler(SocialRavenException.class)
    public ResponseEntity<SocialRavenError> handleSocialRavenException(SocialRavenException ex) {
        log.error("SocialRavenException", ex);

        SocialRavenError response = new SocialRavenError(
                ex.getMessage(),
                ex.getErrorCode(),
                Instant.now()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }


    /**
     * Catch-all: EVERY exception is wrapped
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<SocialRavenError> handleAnyException(Exception ex) {
        log.error("Unhandled exception", ex);

        SocialRavenException wrapped = new SocialRavenException(
                "Something went wrong. Please try again later.",
                "INTERNAL_ERROR",
                ex
        );

        SocialRavenError response = new SocialRavenError(
                wrapped.getMessage(),
                wrapped.getErrorCode(),
                Instant.now()
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }
}
