package com.yoedi.epos.common.exception;

public class NotFoundException extends BusinessException {

    public NotFoundException(String detail) {
        super(ErrorCode.NOT_FOUND, "Data tidak ditemukan", detail);
    }
}
