package com.yoedi.epos.product;

import com.yoedi.epos.common.exception.BusinessException;
import com.yoedi.epos.common.exception.ErrorCode;

/** Thrown when creating/updating a product whose SKU is already used by another product. */
public class DuplicateSkuException extends BusinessException {

    public DuplicateSkuException(String sku) {
        super(ErrorCode.DUPLICATE_SKU, "SKU duplikat",
                "SKU \"" + sku + "\" sudah digunakan oleh produk lain");
    }
}
