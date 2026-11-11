package com.yoedi.epos.category;

import com.yoedi.epos.common.exception.BusinessException;
import com.yoedi.epos.common.exception.ErrorCode;

/** Thrown when creating/updating a category whose name is already taken (case-insensitive). */
public class DuplicateCategoryException extends BusinessException {

    public DuplicateCategoryException(String name) {
        super(ErrorCode.DUPLICATE_CATEGORY, "Kategori duplikat",
                "Kategori \"" + name + "\" sudah ada");
    }
}
