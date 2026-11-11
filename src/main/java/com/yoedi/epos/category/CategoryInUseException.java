package com.yoedi.epos.category;

import com.yoedi.epos.common.exception.BusinessException;
import com.yoedi.epos.common.exception.ErrorCode;

/** Thrown when trying to delete a category that still has products. */
public class CategoryInUseException extends BusinessException {

    public CategoryInUseException(String name, long productCount) {
        super(ErrorCode.CATEGORY_IN_USE, "Kategori masih digunakan",
                "Kategori " + name + " masih memiliki " + productCount + " produk");
    }

    /** Used when the count is unknown (the FK rejected the delete after a concurrent insert). */
    public CategoryInUseException(String name) {
        super(ErrorCode.CATEGORY_IN_USE, "Kategori masih digunakan",
                "Kategori " + name + " masih memiliki produk");
    }
}
