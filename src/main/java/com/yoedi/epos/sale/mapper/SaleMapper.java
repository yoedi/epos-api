package com.yoedi.epos.sale.mapper;

import com.yoedi.epos.sale.Sale;
import com.yoedi.epos.sale.SaleItem;
import com.yoedi.epos.sale.dto.CashierRefResponse;
import com.yoedi.epos.sale.dto.SaleItemResponse;
import com.yoedi.epos.sale.dto.SaleResponse;
import com.yoedi.epos.user.User;
import org.mapstruct.Mapper;

/** Maps {@link Sale} to the response shape; {@code cashier} is flattened to id + name. */
@Mapper(componentModel = "spring")
public interface SaleMapper {

    SaleResponse toResponse(Sale sale);

    SaleItemResponse toItemResponse(SaleItem item);

    default CashierRefResponse toCashierRef(User cashier) {
        if (cashier == null) {
            return null;
        }
        return new CashierRefResponse(cashier.getId(), cashier.getName());
    }
}
