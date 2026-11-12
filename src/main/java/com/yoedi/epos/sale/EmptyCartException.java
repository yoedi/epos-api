package com.yoedi.epos.sale;

import com.yoedi.epos.common.exception.BusinessException;
import com.yoedi.epos.common.exception.ErrorCode;

/** Checkout with no items. Mapped to 422 EMPTY_CART by {@code GlobalExceptionHandler}. */
public class EmptyCartException extends BusinessException {

    public EmptyCartException() {
        super(ErrorCode.EMPTY_CART, "Keranjang kosong", "Keranjang masih kosong");
    }
}
