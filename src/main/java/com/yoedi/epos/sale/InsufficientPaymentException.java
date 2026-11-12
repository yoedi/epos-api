package com.yoedi.epos.sale;

import com.yoedi.epos.common.exception.BusinessException;
import com.yoedi.epos.common.exception.ErrorCode;

import java.text.NumberFormat;
import java.util.Locale;

/** Paid amount is below the total. Mapped to 422 INSUFFICIENT_PAYMENT by {@code GlobalExceptionHandler}. */
public class InsufficientPaymentException extends BusinessException {

    public InsufficientPaymentException(long shortfall) {
        super(ErrorCode.INSUFFICIENT_PAYMENT, "Uang kurang",
                "Uang kurang Rp " + NumberFormat.getIntegerInstance(Locale.forLanguageTag("id-ID")).format(shortfall));
    }
}
