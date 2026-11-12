package com.yoedi.epos.product;

import com.yoedi.epos.common.exception.BusinessException;
import com.yoedi.epos.common.exception.ErrorCode;

/** Thrown when creating/updating a product whose barcode is already used by another product. */
public class DuplicateBarcodeException extends BusinessException {

    public DuplicateBarcodeException(String barcode) {
        super(ErrorCode.DUPLICATE_BARCODE, "Barcode duplikat",
                "Barcode \"" + barcode + "\" sudah digunakan oleh produk lain");
    }
}
