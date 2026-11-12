package com.yoedi.epos.stock;

import com.yoedi.epos.common.exception.BusinessException;
import com.yoedi.epos.common.exception.ErrorCode;

/** The physical count equals the recorded stock, so there is nothing to adjust. Mapped to 422. */
public class NoStockDifferenceException extends BusinessException {

    public NoStockDifferenceException() {
        super(ErrorCode.NO_STOCK_DIFFERENCE, "Penyesuaian stok tidak diperlukan", "Tidak ada selisih stok");
    }
}
