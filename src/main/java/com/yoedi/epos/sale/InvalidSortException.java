package com.yoedi.epos.sale;

import com.yoedi.epos.common.exception.BusinessException;
import com.yoedi.epos.common.exception.ErrorCode;

/** {@code sort} names a property the sales list cannot order by. Mapped to 400 VALIDATION_ERROR. */
public class InvalidSortException extends BusinessException {

    public InvalidSortException(String property) {
        super(ErrorCode.VALIDATION_ERROR, "Validasi gagal", "Pengurutan berdasarkan " + property + " tidak didukung");
    }
}
