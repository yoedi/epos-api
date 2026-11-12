package com.yoedi.epos.auth;

import com.yoedi.epos.common.exception.BusinessException;
import com.yoedi.epos.common.exception.ErrorCode;

/**
 * Wrong username or password. Also used for inactive users so account status is not leaked
 * (docs/api-spec.md §1).
 */
public class InvalidCredentialsException extends BusinessException {

    public InvalidCredentialsException() {
        super(ErrorCode.INVALID_CREDENTIALS, "Login gagal", "Username atau password salah");
    }
}
