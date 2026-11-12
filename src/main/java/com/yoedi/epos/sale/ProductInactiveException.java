package com.yoedi.epos.sale;

import com.yoedi.epos.common.exception.BusinessException;
import com.yoedi.epos.common.exception.ErrorCode;

/** An inactive product is in the cart. Mapped to 422 PRODUCT_INACTIVE by {@code GlobalExceptionHandler}. */
public class ProductInactiveException extends BusinessException {

    public ProductInactiveException(String productName) {
        super(ErrorCode.PRODUCT_INACTIVE, "Produk tidak aktif",
                "Produk " + productName + " sudah tidak aktif");
    }
}
