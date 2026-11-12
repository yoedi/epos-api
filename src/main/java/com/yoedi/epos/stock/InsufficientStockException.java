package com.yoedi.epos.stock;

import com.yoedi.epos.common.exception.BusinessException;
import com.yoedi.epos.common.exception.ErrorCode;

/** Stock would go below zero. Mapped to 422 INSUFFICIENT_STOCK by {@code GlobalExceptionHandler}. */
public class InsufficientStockException extends BusinessException {

    public InsufficientStockException(String productName, int available) {
        super(ErrorCode.INSUFFICIENT_STOCK, "Stok tidak mencukupi",
                "Stok " + productName + " tersisa " + available);
    }
}
