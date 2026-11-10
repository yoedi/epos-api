package com.yoedi.epos.common.exception;

import lombok.Getter;

/**
 * Base class for every expected, user-facing error. The HTTP status comes from the
 * {@link ErrorCode}; {@code title} and {@code detail} are shown to the user, so write
 * them in Bahasa Indonesia.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode code;
    private final String title;

    public BusinessException(ErrorCode code, String title, String detail) {
        super(detail);
        this.code = code;
        this.title = title;
    }

    public String getDetail() {
        return getMessage();
    }
}
