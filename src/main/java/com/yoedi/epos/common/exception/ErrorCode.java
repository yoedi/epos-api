package com.yoedi.epos.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Error codes shared with the frontend. Values must match the "Daftar kode error"
 * table in docs/api-spec.md exactly — do not rename.
 */
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    DUPLICATE_SKU(HttpStatus.CONFLICT),
    DUPLICATE_BARCODE(HttpStatus.CONFLICT),
    DUPLICATE_CATEGORY(HttpStatus.CONFLICT),
    CATEGORY_IN_USE(HttpStatus.CONFLICT),
    INSUFFICIENT_STOCK(HttpStatus.UNPROCESSABLE_ENTITY),
    INSUFFICIENT_PAYMENT(HttpStatus.UNPROCESSABLE_ENTITY),
    PRODUCT_INACTIVE(HttpStatus.UNPROCESSABLE_ENTITY),
    EMPTY_CART(HttpStatus.UNPROCESSABLE_ENTITY),
    NO_STOCK_DIFFERENCE(HttpStatus.UNPROCESSABLE_ENTITY),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
