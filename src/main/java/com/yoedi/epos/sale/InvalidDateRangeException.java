package com.yoedi.epos.sale;

import com.yoedi.epos.common.exception.BusinessException;
import com.yoedi.epos.common.exception.ErrorCode;

/** {@code start_date} is after {@code end_date}. Mapped to 400 VALIDATION_ERROR by {@code GlobalExceptionHandler}. */
public class InvalidDateRangeException extends BusinessException {

    public InvalidDateRangeException() {
        super(ErrorCode.VALIDATION_ERROR, "Validasi gagal", "Tanggal mulai tidak boleh setelah tanggal akhir");
    }
}
