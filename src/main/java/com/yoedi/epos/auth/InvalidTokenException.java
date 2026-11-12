package com.yoedi.epos.auth;

/** The JWT is malformed, tampered with, expired, or missing required claims. */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
