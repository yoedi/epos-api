package com.yoedi.epos.auth;

import com.yoedi.epos.common.exception.BusinessException;
import com.yoedi.epos.common.exception.ErrorCode;

/** A syntactically valid token whose user no longer exists or has been deactivated. */
public class UnauthorizedException extends BusinessException {

    public UnauthorizedException() {
        super(ErrorCode.UNAUTHORIZED, "Sesi tidak valid", "Silakan login kembali");
    }
}
